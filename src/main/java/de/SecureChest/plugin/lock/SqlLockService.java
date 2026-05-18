package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.ui.UndoSnapshot;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class SqlLockService implements LockService {

    private final ChestSecurePlugin plugin;
    private final DatabaseManager db;

    private final LockCacheManager               cacheManager     = new LockCacheManager();
    private final Map<UUID, Boolean>             quickLockCache   = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean>             quickRemoveCache = new ConcurrentHashMap<>();

    private final Runnable                 postWarmCallback;
    private final LogRepository            logRepo;
    private final PlayerSettingsRepository settingsRepo;

    public SqlLockService(ChestSecurePlugin plugin, DatabaseManager db, Runnable postWarmCallback) {
        this.plugin       = plugin;
        this.db           = db;
        this.logRepo      = new LogRepository(plugin, db);
        this.settingsRepo = new PlayerSettingsRepository(plugin, db);

        this.postWarmCallback = postWarmCallback;
        warmCachesAsync();
    }

    private void warmCachesAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                loadAllLocks();
                loadAllSecureHoppers();
                Runnable cb = postWarmCallback;
                if (cb != null) {
                    Bukkit.getScheduler().runTask(plugin, cb);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Cache warmup failed", ex);
            }
        });
    }

    private void loadAllLocks() throws SQLException {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT world_uuid,x,y,z,block_type,owner_uuid,created_at FROM locks")) {
            while (rs.next()) {
                UUID world  = UUID.fromString(rs.getString(1));
                int x = rs.getInt(2), y = rs.getInt(3), z = rs.getInt(4);
                Material type = Material.matchMaterial(rs.getString(5));
                UUID owner    = UUID.fromString(rs.getString(6));
                long created  = rs.getLong(7);
                LockKey key   = new LockKey(world, x, y, z);
                cacheManager.putLock(key.asId(), new LockEntry(key, type == null ? Material.CHEST : type, owner, created));
            }
        }

        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT world_uuid,x,y,z,target_uuid,level FROM trusted")) {
            while (rs.next()) {
                LockKey key  = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                UUID target  = UUID.fromString(rs.getString(5));
                AccessLevel lvl = AccessLevel.fromString(rs.getString(6));
                LockEntry entry = cacheManager.getLock(key.asId());
                if (entry != null && lvl != null) entry.trustedMutable().put(target, lvl);
            }
        }
    }

    private void loadAllSecureHoppers() throws SQLException {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z FROM secure_hoppers")) {
            while (rs.next()) {
                LockKey hk   = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                UUID owner   = UUID.fromString(rs.getString(5));
                LockKey bound = new LockKey(UUID.fromString(rs.getString(6)), rs.getInt(7), rs.getInt(8), rs.getInt(9));
                cacheManager.putHopper(hk.asId(), new SecureHopperEntry(hk, owner, bound));
            }
        }
    }

    @Override
    public boolean isLocked(Block block) {
        if (!LockUtil.isLockable(block)) return false;
        String id = LockUtil.canonicalKey(block).asId();
        if (cacheManager.hasLock(id)) return true;
        return getLock(block).isPresent();
    }

    @Override
    public Optional<LockEntry> getLock(Block block) {
        if (!LockUtil.isLockable(block)) return Optional.empty();
        return getLockFromId(LockUtil.canonicalKey(block).asId());
    }

    @Override
    public Optional<LockEntry> getLockFromId(String lockId) {
        LockEntry cached = cacheManager.getLock(lockId);
        if (cached != null) return Optional.of(cached);

        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return Optional.empty(); }

        if (Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("[SecureChest] getLockFromId(\"" + lockId
                    + "\") trifft synchronen DB-Fallback auf dem Main-Thread! "
                    + "Sicherstellen, dass der Cache-Warmup vor Spieler-Events abgeschlossen ist.");
        }

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT block_type,owner_uuid,created_at FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Material type = Material.matchMaterial(rs.getString(1));
                UUID owner    = UUID.fromString(rs.getString(2));
                long created  = rs.getLong(3);
                LockEntry entry = new LockEntry(key, type == null ? Material.CHEST : type, owner, created);
                try (PreparedStatement ps2 = c.prepareStatement(
                        "SELECT target_uuid,level FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps2.setString(1, key.worldUuid().toString());
                    ps2.setInt(2, key.x()); ps2.setInt(3, key.y()); ps2.setInt(4, key.z());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        while (rs2.next()) {
                            AccessLevel lvl = AccessLevel.fromString(rs2.getString(2));
                            if (lvl != null) entry.trustedMutable().put(UUID.fromString(rs2.getString(1)), lvl);
                        }
                    }
                }
                cacheManager.putLock(lockId, entry);
                return Optional.of(entry);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB getLock failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public Collection<SecureHopperEntry> getAllCachedHoppers() {
        return cacheManager.hopperValues();
    }

    @Override
    public void forceRemoveLockAt(LockKey key) {
        String id = key.asId();
        if (cacheManager.removeLock(id) == null) return;
        plugin.signs().removeSign(id);
        plugin.getLogger().info("[SecureChest] ForceRemove lock " + id + " (block zerstört durch Weltmechanik)");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps.setString(1, key.worldUuid().toString());
                    ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps.setString(1, key.worldUuid().toString());
                    ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB forceRemoveLockAt failed for " + id, ex);
            }
        });
    }

    @Override
    public void forceRemoveHopperAt(LockKey key) {
        String id = key.asId();
        if (cacheManager.removeHopper(id) == null) return;
        plugin.getLogger().info("[SecureChest] ForceRemove hopper " + id + " (block zerstört durch Weltmechanik)");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB forceRemoveHopperAt failed for " + id, ex);
            }
        });
    }

    @Override
    public Collection<LockEntry> getAllCachedLocks() {
        return cacheManager.lockValues();
    }

    @Override
    public boolean lock(Player owner, Block block) {
        if (!LockUtil.isLockable(block)) return false;
        LockKey key = LockUtil.canonicalKey(block);
        String id   = key.asId();

        int maxLocks = plugin.cfg().maxLocksPerPlayer();
        if (maxLocks > 0 && cacheManager.countLocksFor(owner.getUniqueId()) >= maxLocks) {
            sendLimitReachedMessage(owner, "limit-locks-erreicht", maxLocks);
            return false;
        }

        long now = Instant.now().toEpochMilli();
        LockEntry entry = new LockEntry(key, block.getType(), owner.getUniqueId(), now);

        if (cacheManager.putLockIfAbsent(id, entry) != null) return false;

        plugin.signs().spawnSign(key, owner.getName());
        logRepo.insertLog(LogRepository.ACTION_LOCK_CREATE, owner, block, block.getType().name());

        final String blockTypeName = block.getType().name();
        final String ownerStr = owner.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT OR IGNORE INTO locks(world_uuid,x,y,z,block_type,owner_uuid,created_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                ps.setString(5, blockTypeName);
                ps.setString(6, ownerStr);
                ps.setLong(7, now);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB lock persist failed", ex);
                cacheManager.removeLock(id);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.signs().removeSign(id));
            }
        });
        return true;
    }

    @Override
    public boolean unlock(Player caller, Block block) {
        if (!LockUtil.isLockable(block)) return true;
        return unlockByKey(caller, LockUtil.canonicalKey(block));
    }

    @Override
    public boolean unlockById(Player caller, String lockId) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }
        return unlockByKey(caller, key);
    }

    @Override
    public boolean unlockByIdWithUndo(Player actor, String lockId) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }

        return performUnlock(actor, key, true);
    }

    private boolean unlockByKey(Player caller, LockKey key) {
        return performUnlock(caller, key, false);
    }

    private boolean performUnlock(Player actor, LockKey key, boolean storeUndoSnapshot) {
        String id       = key.asId();
        LockEntry entry = cacheManager.getLock(id);
        if (entry == null) entry = getLockFromId(id).orElse(null);
        if (entry == null) return true;

        if (!canManageLock(actor, entry)) return false;

        final LockEntry finalEntry = entry;
        if (!cacheManager.removeLockAtomic(id, finalEntry)) {
            return true;
        }

        boolean isAdminAction = !finalEntry.owner().equals(actor.getUniqueId());
        logRepo.insertLog(
                isAdminAction ? LogRepository.ACTION_LOCK_REMOVE_ADMIN : LogRepository.ACTION_LOCK_REMOVE,
                actor, key,
                isAdminAction ? "target_owner=" + finalEntry.owner() : null);

        UndoSnapshot snapshot = storeUndoSnapshot ? snapshotForSingleLock(key, finalEntry) : null;

        plugin.signs().removeSign(id);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB unlock persist failed", ex);
                cacheManager.putLock(id, finalEntry);
                Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.signs().spawnSign(key, ownerName(finalEntry.owner())));
            }
        });

        if (storeUndoSnapshot && snapshot != null) {
            long now = System.currentTimeMillis();
            int ttlSeconds = plugin.cfg().undoSeconds();
            plugin.undos().set(actor.getUniqueId(), new UndoSnapshot(
                    snapshot.locks(), snapshot.trusts(), List.of(),
                    now, now + (ttlSeconds * 1000L)
            ));
        }
        return true;
    }

    @Deprecated
    @Override
    public boolean unlockAll(UUID owner) {
        plugin.getLogger().warning("[SecureChest] unlockAll(UUID) wurde aufgerufen ohne Actor-Kontext. "
                + "Bitte unlockAllWithUndo(Player, UUID) verwenden.");
        boolean hasLocks   = cacheManager.lockValues().stream().anyMatch(e -> e.owner().equals(owner));
        boolean hasHoppers = cacheManager.hopperValues().stream().anyMatch(e -> e.owner().equals(owner));
        if (!hasLocks && !hasHoppers) return false;

        cacheManager.lockEntries().stream()
                .filter(e -> e.getValue().owner().equals(owner))
                .forEach(e -> plugin.signs().removeSign(e.getKey()));

        cacheManager.clearLocksOfOwner(owner);
        cacheManager.clearHoppersOfOwner(owner);

        final String ownerStr = owner.toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE owner_uuid=?")) {
                    ps.setString(1, ownerStr); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM secure_hoppers WHERE owner_uuid=?")) {
                    ps.setString(1, ownerStr); ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB unlockAll persist failed", ex);
            }
        });
        return true;
    }

    @Override
    public boolean unlockAllWithUndo(Player actor, UUID owner) {
        if (!isUnlockAllAllowed(actor, owner)) return false;

        long now = System.currentTimeMillis();
        int ttlSeconds = plugin.cfg().undoSeconds();

        UndoSnapshot snapshot = snapshotForOwner(owner, now, now + (ttlSeconds * 1000L));

        boolean hasLocks   = cacheManager.lockValues().stream().anyMatch(e -> e.owner().equals(owner));
        boolean hasHoppers = cacheManager.hopperValues().stream().anyMatch(e -> e.owner().equals(owner));
        if (!hasLocks && !hasHoppers && snapshot.locks().isEmpty() && snapshot.secureHoppers().isEmpty()) {
            return false;
        }

        cacheManager.lockEntries().stream()
                .filter(e -> e.getValue().owner().equals(owner))
                .forEach(e -> plugin.signs().removeSign(e.getKey()));

        cacheManager.clearLocksOfOwner(owner);
        cacheManager.clearHoppersOfOwner(owner);

        if (!snapshot.locks().isEmpty() || !snapshot.secureHoppers().isEmpty()) {
            plugin.undos().set(actor.getUniqueId(), snapshot);
        }

        boolean isSelfAction = actor.getUniqueId().equals(owner);
        String extra = "locks=" + snapshot.locks().size()
                + ",hoppers=" + snapshot.secureHoppers().size()
                + (isSelfAction ? "" : ",target_owner=" + owner);
        logRepo.insertLogAtActor(
                isSelfAction ? LogRepository.ACTION_LOCK_UNLOCK_ALL_SELF
                             : LogRepository.ACTION_LOCK_UNLOCK_ALL_ADMIN,
                actor, extra);

        final String ownerStr = owner.toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE owner_uuid=?")) {
                    ps.setString(1, ownerStr); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM secure_hoppers WHERE owner_uuid=?")) {
                    ps.setString(1, ownerStr); ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB unlockAllWithUndo persist failed", ex);
            }
        });
        return true;
    }

    @Override
    public boolean restoreUndo(Player actor) {
        var peekOpt = plugin.undos().get(actor.getUniqueId());
        if (peekOpt.isEmpty()) return false;
        UndoSnapshot peek = peekOpt.get();
        if (peek.locks().isEmpty() && peek.secureHoppers().isEmpty()) {
            plugin.undos().consume(actor.getUniqueId());
            return false;
        }
        if (!isSnapshotRestorableBy(peek, actor)) {
            plugin.getLogger().warning("[SecureChest] restoreUndo abgelehnt: "
                    + actor.getName() + " besitzt keine Berechtigung fuer Snapshot.");
            return false;
        }

        var snapOpt = plugin.undos().consume(actor.getUniqueId());
        if (snapOpt.isEmpty()) return false;
        UndoSnapshot snapshot = snapOpt.get();

        logRepo.insertLogAtActor(LogRepository.ACTION_UNDO_RESTORE, actor,
                "locks=" + snapshot.locks().size()
                + ",hoppers=" + snapshot.secureHoppers().size());

        for (UndoSnapshot.LockRow lr : snapshot.locks()) {
            Material type = Material.matchMaterial(lr.blockType());
            LockEntry le  = new LockEntry(lr.key(), type == null ? Material.CHEST : type, lr.owner(), lr.createdAt());
            cacheManager.putLock(lr.key().asId(), le);

            plugin.signs().spawnSign(lr.key(), ownerName(lr.owner()));
        }
        for (UndoSnapshot.TrustRow tr : snapshot.trusts()) {
            LockEntry le = cacheManager.getLock(tr.key().asId());
            if (le != null) {
                AccessLevel lvl = AccessLevel.fromString(tr.level());
                if (lvl != null) le.trustedMutable().put(tr.target(), lvl);
            }
        }
        for (UndoSnapshot.SecureHopperRow hr : snapshot.secureHoppers()) {
            cacheManager.putHopper(hr.hopperKey().asId(), new SecureHopperEntry(hr.hopperKey(), hr.owner(), hr.boundKey()));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO locks(world_uuid,x,y,z,block_type,owner_uuid,created_at) VALUES(?,?,?,?,?,?,?)")) {
                    for (UndoSnapshot.LockRow lr : snapshot.locks()) {
                        ps.setString(1, lr.key().worldUuid().toString());
                        ps.setInt(2, lr.key().x()); ps.setInt(3, lr.key().y()); ps.setInt(4, lr.key().z());
                        ps.setString(5, lr.blockType()); ps.setString(6, lr.owner().toString());
                        ps.setLong(7, lr.createdAt()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR REPLACE INTO trusted(world_uuid,x,y,z,target_uuid,level) VALUES(?,?,?,?,?,?)")) {
                    for (UndoSnapshot.TrustRow tr : snapshot.trusts()) {
                        ps.setString(1, tr.key().worldUuid().toString());
                        ps.setInt(2, tr.key().x()); ps.setInt(3, tr.key().y()); ps.setInt(4, tr.key().z());
                        ps.setString(5, tr.target().toString()); ps.setString(6, tr.level()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO secure_hoppers(world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    for (UndoSnapshot.SecureHopperRow hr : snapshot.secureHoppers()) {
                        ps.setString(1, hr.hopperKey().worldUuid().toString());
                        ps.setInt(2, hr.hopperKey().x()); ps.setInt(3, hr.hopperKey().y()); ps.setInt(4, hr.hopperKey().z());
                        ps.setString(5, hr.owner().toString());
                        ps.setString(6, hr.boundKey().worldUuid().toString());
                        ps.setInt(7, hr.boundKey().x()); ps.setInt(8, hr.boundKey().y()); ps.setInt(9, hr.boundKey().z());
                        ps.setLong(10, hr.createdAt()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB undo restore persist failed", ex);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (UndoSnapshot.LockRow lr : snapshot.locks()) {
                        cacheManager.removeLock(lr.key().asId());
                        plugin.signs().removeSign(lr.key().asId());
                    }
                    for (UndoSnapshot.TrustRow tr : snapshot.trusts()) {
                        LockEntry le = cacheManager.getLock(tr.key().asId());
                        if (le != null) le.trustedMutable().remove(tr.target());
                    }
                    for (UndoSnapshot.SecureHopperRow hr : snapshot.secureHoppers()) {
                        cacheManager.removeHopper(hr.hopperKey().asId());
                    }
                    plugin.getLogger().warning("[SecureChest] Undo-Restore fehlgeschlagen – "
                            + "Cache-Rollback durchgeführt. Bitte DB prüfen.");
                });
            }
        });
        return true;
    }

    @Override
    public boolean hasUndo(Player actor) {
        return plugin.undos().hasUndo(actor.getUniqueId());
    }

    @Override
    public boolean transferLock(Player actor, String lockId, UUID newOwner) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }

        String id       = key.asId();
        LockEntry entry = cacheManager.getLock(id);
        if (entry == null) entry = getLockFromId(id).orElse(null);
        if (entry == null) return false;

        final LockKey finalKey = key;
        final String finalId = id;
        final LockEntry originalEntry = entry;

        if (!canManageLock(actor, entry)) return false;
        if (entry.owner().equals(newOwner)) return false;

        java.util.List<LockKey> foreignHoppersToRemove = new java.util.ArrayList<>();
        for (SecureHopperEntry hopperEntry : cacheManager.hopperValues()) {
            if (hopperEntry.boundLock().asId().equals(finalId)
                    && !hopperEntry.owner().equals(newOwner)) {
                foreignHoppersToRemove.add(hopperEntry.hopperKey());
            }
        }

        logRepo.insertLog(LogRepository.ACTION_LOCK_TRANSFER, actor, key,
                "from=" + entry.owner() + ",to=" + newOwner
                + (foreignHoppersToRemove.isEmpty() ? "" : ",unbound_foreign_hoppers=" + foreignHoppersToRemove.size()));

        long now = System.currentTimeMillis();
        int ttlSeconds = plugin.cfg().undoSeconds();
        UndoSnapshot snapshot = snapshotForSingleLock(key, entry);

        LockEntry transferred = new LockEntry(finalKey, originalEntry.blockType(), newOwner, originalEntry.createdAt());
        cacheManager.putLock(finalId, transferred);

        plugin.signs().removeSign(finalId);
        plugin.signs().spawnSign(finalKey, ownerName(newOwner));

        for (LockKey hopperKey : foreignHoppersToRemove) {
            cacheManager.removeHopper(hopperKey.asId());
            org.bukkit.World hopperWorld = Bukkit.getWorld(hopperKey.worldUuid());
            if (hopperWorld != null) {
                Block hopperBlock = hopperWorld.getBlockAt(hopperKey.x(), hopperKey.y(), hopperKey.z());
                if (hopperBlock.getState() instanceof TileState tile) {
                    tile.getPersistentDataContainer().remove(plugin.keySecureHopperBlock());
                    tile.update(true, false);
                }
            }
        }

        final String newOwnerStr = newOwner.toString();
        final java.util.List<LockKey> finalForeignHoppers = foreignHoppersToRemove;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE locks SET owner_uuid=? WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps.setString(1, newOwnerStr);
                    ps.setString(2, finalKey.worldUuid().toString());
                    ps.setInt(3, finalKey.x()); ps.setInt(4, finalKey.y()); ps.setInt(5, finalKey.z());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps.setString(1, finalKey.worldUuid().toString());
                    ps.setInt(2, finalKey.x()); ps.setInt(3, finalKey.y()); ps.setInt(4, finalKey.z());
                    ps.executeUpdate();
                }

                if (!finalForeignHoppers.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                        for (LockKey hk : finalForeignHoppers) {
                            ps.setString(1, hk.worldUuid().toString());
                            ps.setInt(2, hk.x()); ps.setInt(3, hk.y()); ps.setInt(4, hk.z());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB transferLock persist failed", ex);

                cacheManager.putLock(finalId, originalEntry);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.signs().removeSign(finalId);
                    plugin.signs().spawnSign(finalKey, ownerName(originalEntry.owner()));
                });
            }
        });

        plugin.undos().set(actor.getUniqueId(), new UndoSnapshot(
                snapshot.locks(), snapshot.trusts(), List.of(),
                now, now + (ttlSeconds * 1000L)
        ));
        return true;
    }

    @Override
    public LockStats getLockStats(UUID owner) {
        int totalLocks = 0, chestCount = 0, trappedChestCount = 0,
                barrelCount = 0, shulkerCount = 0, hopperCount = 0,
                totalTrusted = 0, totalHoppers = 0;
        long oldest = 0, newest = 0;

        for (LockEntry entry : cacheManager.lockValues()) {
            if (!entry.owner().equals(owner)) continue;
            totalLocks++;
            totalTrusted += entry.trusted().size();
            long ca = entry.createdAt();
            if (oldest == 0 || ca < oldest) oldest = ca;
            if (ca > newest) newest = ca;

            Material m = entry.blockType();
            if (m == Material.CHEST)                    chestCount++;
            else if (m == Material.TRAPPED_CHEST)       trappedChestCount++;
            else if (m == Material.BARREL)              barrelCount++;
            else if (m == Material.HOPPER)              hopperCount++;
            else if (m.name().endsWith("SHULKER_BOX")) shulkerCount++;
        }
        for (SecureHopperEntry he : cacheManager.hopperValues()) {
            if (he.owner().equals(owner)) totalHoppers++;
        }
        return new LockStats(totalLocks, chestCount, trappedChestCount, barrelCount,
                shulkerCount, hopperCount, totalTrusted, totalHoppers, oldest, newest);
    }

    @Override
    public boolean canOpen(Player player, Block block, boolean usingAdminKey) {
        LockEntry entry = getLock(block).orElse(null);
        if (entry == null) return true;
        if (player.hasPermission(Permissions.ADMIN) && usingAdminKey) return true;
        if (entry.owner().equals(player.getUniqueId())) return true;
        return entry.trusted().containsKey(player.getUniqueId());
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        LockEntry lock = getLock(block).orElse(null);
        if (lock != null) return player.hasPermission(Permissions.ADMIN) || lock.owner().equals(player.getUniqueId());
        if (block.getType() == Material.HOPPER) {
            SecureHopperEntry sh = getSecureHopper(block).orElse(null);
            if (sh != null) return player.hasPermission(Permissions.ADMIN) || sh.owner().equals(player.getUniqueId());
        }
        return true;
    }

    @Override
    public AccessLevel getAccessLevel(Player player, Block block) {
        LockEntry entry = getLock(block).orElse(null);
        if (entry == null) return null;
        if (player.hasPermission(Permissions.ADMIN)) return AccessLevel.CO_OWNER;
        if (entry.owner().equals(player.getUniqueId())) return AccessLevel.CO_OWNER;
        return entry.trusted().get(player.getUniqueId());
    }

    @Override
    public boolean setTrust(Player owner, Block block, UUID target, AccessLevel level) {
        if (!LockUtil.isLockable(block)) return false;
        return setTrustByKey(owner, LockUtil.canonicalKey(block), target, level);
    }

    @Override
    public boolean setTrustById(Player owner, String lockId, UUID target, AccessLevel level) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }
        return setTrustByKey(owner, key, target, level);
    }

    private boolean canManageLock(Player actor, LockEntry entry) {
        return entry.owner().equals(actor.getUniqueId()) || actor.hasPermission(Permissions.ADMIN);
    }

    private void sendLimitReachedMessage(Player player, String messageKey, int limit) {
        player.sendMessage(net.kyori.adventure.text.Component.text(
                plugin.msg().get(messageKey, limit),
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    private boolean setTrustByKey(Player owner, LockKey key, UUID target, AccessLevel level) {
        String id       = key.asId();
        LockEntry entry = getLockFromId(id).orElse(null);

        if (entry == null) return false;
        if (!canManageLock(owner, entry)) return false;

        entry.trustedMutable().put(target, level);
        cacheManager.putLock(id, entry);

        logRepo.insertLog(LogRepository.ACTION_TRUST_SET, owner, key,
                "target=" + target + ",level=" + level.name());

        final String targetStr = target.toString(), levelName = level.name();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT OR REPLACE INTO trusted(world_uuid,x,y,z,target_uuid,level) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                ps.setString(5, targetStr); ps.setString(6, levelName);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB setTrust persist failed", ex);
            }
        });
        return true;
    }

    @Override
    public boolean removeTrust(Player owner, Block block, UUID target) {
        if (!LockUtil.isLockable(block)) return false;
        return removeTrustByKey(owner, LockUtil.canonicalKey(block), target);
    }

    @Override
    public boolean removeTrustById(Player owner, String lockId, UUID target) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }
        return removeTrustByKey(owner, key, target);
    }

    private boolean removeTrustByKey(Player owner, LockKey key, UUID target) {
        String id       = key.asId();
        LockEntry entry = getLockFromId(id).orElse(null);
        if (entry == null) return false;
        if (!canManageLock(owner, entry)) return false;

        entry.trustedMutable().remove(target);
        cacheManager.putLock(id, entry);

        logRepo.insertLog(LogRepository.ACTION_TRUST_REMOVE, owner, key, "target=" + target);

        final String targetStr = target.toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=? AND target_uuid=?")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x()); ps.setInt(3, key.y()); ps.setInt(4, key.z());
                ps.setString(5, targetStr);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB removeTrust persist failed", ex);
            }
        });
        return true;
    }

    @Override
    public List<Location> listLocks(UUID owner) {
        return listLocks(owner, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<Location> listLocks(UUID owner, int offset, int limit) {
        List<Location> out = new ArrayList<>();
        for (LockEntry entry : cacheManager.lockValues()) {
            if (!entry.owner().equals(owner)) continue;
            LockKey k = entry.key();
            out.add(new Location(Bukkit.getWorld(k.worldUuid()), k.x(), k.y(), k.z()));
        }
        out.sort(Comparator.comparing((Location l) -> l.getWorld() != null ? l.getWorld().getName() : "(unloaded)")
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));
        int from = Math.min(offset, out.size());
        int to   = Math.min(from + limit, out.size());
        return out.subList(from, to);
    }

    @Override
    public int countLocks(UUID owner) {
        int count = 0;
        for (LockEntry entry : cacheManager.lockValues()) {
            if (entry.owner().equals(owner)) count++;
        }
        return count;
    }

    @Override
    public boolean isSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return false;
        String id = LockKey.fromLocation(hopperBlock.getLocation()).asId();
        if (cacheManager.hasHopper(id)) return true;
        if (hopperBlock.getState() instanceof TileState tile) {
            Byte mark = tile.getPersistentDataContainer().get(plugin.keySecureHopperBlock(), PersistentDataType.BYTE);
            if (mark != null && mark == (byte) 1) return getSecureHopper(hopperBlock).isPresent();
        }
        return getSecureHopper(hopperBlock).isPresent();
    }

    @Override
    public Optional<SecureHopperEntry> getSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return Optional.empty();
        LockKey hk = LockKey.fromLocation(hopperBlock.getLocation());
        String id  = hk.asId();

        SecureHopperEntry cached = cacheManager.getHopper(id);
        if (cached != null) return Optional.of(cached);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, hk.worldUuid().toString());
            ps.setInt(2, hk.x()); ps.setInt(3, hk.y()); ps.setInt(4, hk.z());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID owner  = UUID.fromString(rs.getString(1));
                LockKey bnd = new LockKey(UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5));
                SecureHopperEntry sh = new SecureHopperEntry(hk, owner, bnd);
                cacheManager.putHopper(id, sh);
                return Optional.of(sh);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB getSecureHopper failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean bindSecureHopper(Player placer, Block hopperBlock, Block boundContainer) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return false;
        if (!LockUtil.isLockable(boundContainer)) return false;
        LockEntry lock = getLock(boundContainer).orElse(null);
        if (lock == null || !lock.owner().equals(placer.getUniqueId())) return false;

        int maxHoppers = plugin.cfg().maxHoppersPerPlayer();
        if (maxHoppers > 0 && cacheManager.countHoppersFor(placer.getUniqueId()) >= maxHoppers) {
            sendLimitReachedMessage(placer, "limit-hopper-erreicht", maxHoppers);
            return false;
        }

        LockKey hk  = LockKey.fromLocation(hopperBlock.getLocation());
        LockKey bnd = LockUtil.canonicalKey(boundContainer);
        long now    = Instant.now().toEpochMilli();

        SecureHopperEntry candidate = new SecureHopperEntry(hk, placer.getUniqueId(), bnd);
        if (cacheManager.putHopperIfAbsent(hk.asId(), candidate) != null) {
            return false;
        }

        if (hopperBlock.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(plugin.keySecureHopperBlock(), PersistentDataType.BYTE, (byte) 1);
            tile.update(true, false);
        }

        logRepo.insertLog(LogRepository.ACTION_HOPPER_BIND, placer, hopperBlock, "bound=" + bnd.asId());

        final String placerStr = placer.getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT OR IGNORE INTO secure_hoppers(world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, hk.worldUuid().toString());
                ps.setInt(2, hk.x()); ps.setInt(3, hk.y()); ps.setInt(4, hk.z());
                ps.setString(5, placerStr);
                ps.setString(6, bnd.worldUuid().toString());
                ps.setInt(7, bnd.x()); ps.setInt(8, bnd.y()); ps.setInt(9, bnd.z());
                ps.setLong(10, now);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB bindSecureHopper persist failed", ex);
                cacheManager.removeHopper(hk.asId());
            }
        });
        return true;
    }

    @Override
    public boolean removeSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return false;
        LockKey hk = LockKey.fromLocation(hopperBlock.getLocation());
        cacheManager.removeHopper(hk.asId());

        if (hopperBlock.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(plugin.keySecureHopperBlock());
            tile.update(true, false);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, hk.worldUuid().toString());
                ps.setInt(2, hk.x()); ps.setInt(3, hk.y()); ps.setInt(4, hk.z());
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB removeSecureHopper persist failed", ex);
            }
        });
        return true;
    }

    @Override
    public List<Location> listSecureHoppers(UUID owner) {
        List<Location> out = new ArrayList<>();
        for (SecureHopperEntry entry : cacheManager.hopperValues()) {
            if (!entry.owner().equals(owner)) continue;
            LockKey k = entry.hopperKey();
            out.add(new Location(Bukkit.getWorld(k.worldUuid()), k.x(), k.y(), k.z()));
        }
        out.sort(Comparator.comparing((Location l) -> l.getWorld() != null ? l.getWorld().getName() : "(unloaded)")
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));
        return out;
    }

    @Override
    public boolean getQuickLock(UUID player) {
        return quickLockCache.computeIfAbsent(player, u -> {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> quickLockCache.put(u, settingsRepo.loadSetting(u, "quick_lock")));
                return false;
            }
            return settingsRepo.loadSetting(u, "quick_lock");
        });
    }

    @Override
    public boolean getQuickRemove(UUID player) {
        return quickRemoveCache.computeIfAbsent(player, u -> {
            if (Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> quickRemoveCache.put(u, settingsRepo.loadSetting(u, "quick_remove")));
                return false;
            }
            return settingsRepo.loadSetting(u, "quick_remove");
        });
    }

    @Override
    public void setQuickLock(UUID player, boolean value) {
        quickLockCache.put(player, value);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> settingsRepo.saveSetting(player, "quick_lock", value));
    }

    @Override
    public void setQuickRemove(UUID player, boolean value) {
        quickRemoveCache.put(player, value);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> settingsRepo.saveSetting(player, "quick_remove", value));
    }

    public void evictPlayerCache(UUID player) {
        quickLockCache.remove(player);
        quickRemoveCache.remove(player);
    }

    public void preloadPlayerSettings(UUID player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            quickLockCache.put(player,   settingsRepo.loadSetting(player, "quick_lock"));
            quickRemoveCache.put(player, settingsRepo.loadSetting(player, "quick_remove"));
        });
    }

    @Override
    public void addNotification(UUID ownerUuid, String message) {
        settingsRepo.addNotification(ownerUuid, message);
    }

    @Override
    public List<Notification> fetchAndClearNotifications(UUID playerUuid) {
        return settingsRepo.fetchAndClearNotifications(playerUuid);
    }

    @Override
    public void logUnauthorizedBreak(Player actor, Block target) {
        logRepo.insertLog(LogRepository.ACTION_UNAUTHORIZED_BREAK, actor, target, null);
    }

    @Override
    public void logAdminBreak(Player actor, Block target) {
        logRepo.insertLog(LogRepository.ACTION_ADMIN_BREAK, actor, target, null);
    }

    @Override
    public void logSecureHopperRemove(Player actor, Block hopperBlock) {
        logRepo.insertLog(LogRepository.ACTION_HOPPER_REMOVE, actor, hopperBlock, null);
    }

    @Override
    public void logInspectToggle(Player actor, boolean newState) {
        logRepo.insertLogAtActor(LogRepository.ACTION_INSPECT_TOGGLE, actor,
                "enabled=" + newState);
    }

    @Override
    public ItemStack createLogsBook(int maxEntries) {
        return logRepo.createLogsBook(maxEntries);
    }

    @Override
    public List<LogRecord> listLogs(String actionFilter, long minCreatedAt, String actorNameLike, int offset, int limit) {
        return logRepo.listLogs(actionFilter, minCreatedAt, actorNameLike, offset, limit);
    }

    @Override
    public int countLogs(String actionFilter, long minCreatedAt, String actorNameLike) {
        return logRepo.countLogs(actionFilter, minCreatedAt, actorNameLike);
    }

    @Override
    public Optional<LogRecord> getLogById(long id) {
        return logRepo.getLogById(id);
    }

    @Override
    public ItemStack createAdminReportBook() {
        return logRepo.createAdminReportBook();
    }

    private UndoSnapshot snapshotForSingleLock(LockKey key, LockEntry entry) {

        List<UndoSnapshot.TrustRow> trusts = entry.trusted().entrySet().stream()
                .map(e -> new UndoSnapshot.TrustRow(key, e.getKey(), e.getValue().name()))
                .collect(java.util.stream.Collectors.toList());
        long now = System.currentTimeMillis();
        int ttl = plugin.cfg().undoSeconds();
        return new UndoSnapshot(
                List.of(new UndoSnapshot.LockRow(key, entry.blockType().name(), entry.owner(), entry.createdAt())),
                trusts, List.of(), now, now + (ttl * 1000L));
    }

    private boolean isSnapshotRestorableBy(UndoSnapshot snapshot, Player actor) {
        return isSnapshotRestorableBy(snapshot, actor.getUniqueId(),
                actor.hasPermission(Permissions.ADMIN));
    }

    static boolean isSnapshotRestorableBy(UndoSnapshot snapshot, UUID actorUuid, boolean isAdmin) {
        for (UndoSnapshot.LockRow lockRow : snapshot.locks()) {
            if (!lockRow.owner().equals(actorUuid) && !isAdmin) return false;
        }
        for (UndoSnapshot.SecureHopperRow hopperRow : snapshot.secureHoppers()) {
            if (!hopperRow.owner().equals(actorUuid) && !isAdmin) return false;
        }
        return true;
    }

    private boolean isUnlockAllAllowed(Player actor, UUID owner) {
        return isUnlockAllAllowed(actor.getUniqueId(), owner,
                actor.hasPermission(Permissions.ADMIN));
    }

    static boolean isUnlockAllAllowed(UUID actorUuid, UUID targetOwner, boolean isAdmin) {
        if (actorUuid.equals(targetOwner)) return true;
        return isAdmin;
    }

    private UndoSnapshot snapshotForOwner(UUID owner, long now, long expiresAt) {
        List<UndoSnapshot.LockRow>         locks   = new ArrayList<>();
        List<UndoSnapshot.TrustRow>        trusts  = new ArrayList<>();
        List<UndoSnapshot.SecureHopperRow> hoppers = new ArrayList<>();

        for (LockEntry entry : cacheManager.lockValues()) {
            if (!entry.owner().equals(owner)) continue;
            LockKey key = entry.key();
            locks.add(new UndoSnapshot.LockRow(key, entry.blockType().name(), owner, entry.createdAt()));

            for (Map.Entry<UUID, AccessLevel> te : entry.trusted().entrySet()) {
                trusts.add(new UndoSnapshot.TrustRow(key, te.getKey(), te.getValue().name()));
            }
        }
        for (SecureHopperEntry he : cacheManager.hopperValues()) {
            if (!he.owner().equals(owner)) continue;
            hoppers.add(new UndoSnapshot.SecureHopperRow(he.hopperKey(), owner, he.boundLock(), now));
        }
        return new UndoSnapshot(locks, trusts, hoppers, now, expiresAt);
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String s)  ps.setString(i + 1, s);
            else if (p instanceof Long l)  ps.setLong(i + 1, l);
            else if (p instanceof Integer n) ps.setInt(i + 1, n);
            else ps.setObject(i + 1, p);
        }
    }

    private String nonBlockingName(UUID uuid) {

        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (cached != null && cached.getName() != null) return cached.getName();
        return uuid.toString().substring(0, 8);
    }

    private String ownerName(UUID uuid) {
        return nonBlockingName(uuid);
    }
}
