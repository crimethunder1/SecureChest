package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.util.BookUtil;
import de.SecureChest.plugin.ui.UndoSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SqlLockService implements LockService {

    private final ChestSecurePlugin plugin;
    private final DatabaseManager db;

    // cache lockId -> entry (includes trusted map)
    private final Map<String, LockEntry> lockCache = new ConcurrentHashMap<>();
    // cache secure hopper id -> entry
    private final Map<String, SecureHopperEntry> hopperCache = new ConcurrentHashMap<>();
    // settings cache
    private final Map<UUID, Boolean> quickLockCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> quickRemoveCache = new ConcurrentHashMap<>();

    public SqlLockService(ChestSecurePlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        warmCachesAsync();
    }

    private void warmCachesAsync() {
        // Load caches async to avoid startup lag on huge DBs
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                loadAllLocks();
                loadAllSecureHoppers();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Cache warmup failed", ex);
            }
        });
    }

    private void loadAllLocks() throws SQLException {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT world_uuid,x,y,z,block_type,owner_uuid,created_at FROM locks")) {

            while (rs.next()) {
                UUID world = UUID.fromString(rs.getString(1));
                int x = rs.getInt(2), y = rs.getInt(3), z = rs.getInt(4);
                Material type = Material.matchMaterial(rs.getString(5));
                UUID owner = UUID.fromString(rs.getString(6));
                long createdAt = rs.getLong(7);

                LockKey key = new LockKey(world, x, y, z);
                LockEntry entry = new LockEntry(key, type == null ? Material.CHEST : type, owner, createdAt);
                lockCache.put(key.asId(), entry);
            }
        }

        // load trusted
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT world_uuid,x,y,z,target_uuid,level FROM trusted")) {
            while (rs.next()) {
                LockKey key = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                UUID target = UUID.fromString(rs.getString(5));
                AccessLevel lvl = AccessLevel.fromString(rs.getString(6));
                LockEntry entry = lockCache.get(key.asId());
                if (entry != null && lvl != null) {
                    entry.trusted().put(target, lvl);
                }
            }
        }
    }

    private void loadAllSecureHoppers() throws SQLException {
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z FROM secure_hoppers")) {
            while (rs.next()) {
                LockKey hopperKey = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                UUID owner = UUID.fromString(rs.getString(5));
                LockKey bound = new LockKey(UUID.fromString(rs.getString(6)), rs.getInt(7), rs.getInt(8), rs.getInt(9));
                hopperCache.put(hopperKey.asId(), new SecureHopperEntry(hopperKey, owner, bound));
            }
        }
    }

    // -------- Locks --------

    @Override
    public boolean isLocked(Block block) {
        if (!LockUtil.isLockable(block)) return false;
        String id = LockUtil.canonicalKey(block).asId();
        if (lockCache.containsKey(id)) return true;
        // fallback DB if not warmed yet
        return getLock(block).isPresent();
    }

    @Override
    public Optional<LockEntry> getLock(Block block) {
        if (!LockUtil.isLockable(block)) return Optional.empty();
        LockKey key = LockUtil.canonicalKey(block);
        return getLockFromId(key.asId());
    }

    @Override
    public Optional<LockEntry> getLockFromId(String lockId) {
        LockEntry cached = lockCache.get(lockId);
        if (cached != null) return Optional.of(cached);

        LockKey key;
        try {
            key = LockKey.fromId(lockId);
        } catch (Exception ex) {
            return Optional.empty();
        }

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT block_type,owner_uuid,created_at FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Material type = Material.matchMaterial(rs.getString(1));
                UUID owner = UUID.fromString(rs.getString(2));
                long createdAt = rs.getLong(3);
                LockEntry entry = new LockEntry(key, type == null ? Material.CHEST : type, owner, createdAt);

                // trusted
                try (PreparedStatement ps2 = c.prepareStatement("SELECT target_uuid,level FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                    ps2.setString(1, key.worldUuid().toString());
                    ps2.setInt(2, key.x());
                    ps2.setInt(3, key.y());
                    ps2.setInt(4, key.z());
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        while (rs2.next()) {
                            UUID target = UUID.fromString(rs2.getString(1));
                            AccessLevel lvl = AccessLevel.fromString(rs2.getString(2));
                            if (lvl != null) entry.trusted().put(target, lvl);
                        }
                    }
                }

                lockCache.put(lockId, entry);
                return Optional.of(entry);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB getLock failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean lock(Player owner, Block block) {
        if (!LockUtil.isLockable(block)) return false;
        LockKey key = LockUtil.canonicalKey(block);
        String id = key.asId();
        if (lockCache.containsKey(id)) return false;

        long now = Instant.now().toEpochMilli();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO locks(world_uuid,x,y,z,block_type,owner_uuid,created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setString(5, block.getType().name());
            ps.setString(6, owner.getUniqueId().toString());
            ps.setLong(7, now);
            int changed = ps.executeUpdate();
            if (changed > 0) {
                LockEntry entry = new LockEntry(key, block.getType(), owner.getUniqueId(), now);
                lockCache.put(id, entry);
                return true;
            }
            return false;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB lock failed", ex);
            return false;
        }
    }

    @Override
    public boolean unlock(Player caller, Block block) {
        if (!LockUtil.isLockable(block)) return true;
        LockKey key = LockUtil.canonicalKey(block);
        return unlockByKey(caller, key);
    }

    @Override
    public boolean unlockById(Player caller, String lockId) {
        LockKey key;
        try {
            key = LockKey.fromId(lockId);
        } catch (Exception ex) {
            return false;
        }
        return unlockByKey(caller, key);
    }

    @Override
    public boolean unlockByIdWithUndo(Player actor, String lockId) {
        LockKey key;
        try {
            key = LockKey.fromId(lockId);
        } catch (Exception ex) {
            return false;
        }

        String id = key.asId();
        LockEntry entry = lockCache.get(id);
        if (entry == null) entry = getLockFromId(id).orElse(null);
        if (entry == null) return true;

        boolean allowed = actor.hasPermission("chestsecure.admin") || entry.owner().equals(actor.getUniqueId());
        if (!allowed) return false;

        long now = System.currentTimeMillis();
        int ttlSeconds = plugin.getConfig().getInt("ui.undo_seconds", 20);
        long expiresAt = now + (ttlSeconds * 1000L);

        // Snapshot lock row + trust rows
        UndoSnapshot snapshot;
        try {
            snapshot = snapshotForSingleLock(key, entry);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB snapshot failed", ex);
            return false;
        }

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, key.worldUuid().toString());
                ps.setInt(2, key.x());
                ps.setInt(3, key.y());
                ps.setInt(4, key.z());
                ps.executeUpdate();
            }
            // trusted rows cascade
            c.commit();
            lockCache.remove(id);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB unlock (with undo) failed", ex);
            return false;
        }

        plugin.undos().set(actor.getUniqueId(), new UndoSnapshot(
                snapshot.locks(),
                snapshot.trusts(),
                java.util.List.of(),
                now,
                expiresAt
        ));

        return true;
    }

    private boolean unlockByKey(Player caller, LockKey key) {
        String id = key.asId();
        LockEntry entry = lockCache.get(id);
        if (entry == null) entry = getLockFromId(id).orElse(null);
        if (entry == null) return true;

        boolean allowed = caller.hasPermission("chestsecure.admin") || entry.owner().equals(caller.getUniqueId());
        if (!allowed) return false;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.executeUpdate();
            lockCache.remove(id);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB unlock failed", ex);
            return false;
        }
        return true;
    }

    @Override
    public boolean unlockAll(UUID owner) {
        int locksDeleted;
        int hoppersDeleted;
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE owner_uuid=?")) {
                ps.setString(1, owner.toString());
                locksDeleted = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM secure_hoppers WHERE owner_uuid=?")) {
                ps.setString(1, owner.toString());
                hoppersDeleted = ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB unlockAll failed", ex);
            return false;
        }

        // purge caches
        lockCache.entrySet().removeIf(e -> e.getValue().owner().equals(owner));
        hopperCache.entrySet().removeIf(e -> e.getValue().owner().equals(owner));

        return locksDeleted > 0 || hoppersDeleted > 0;
    }

    @Override
    public boolean unlockAllWithUndo(Player actor, UUID owner) {
        long now = System.currentTimeMillis();
        int ttlSeconds = plugin.getConfig().getInt("ui.undo_seconds", 20);
        long expiresAt = now + (ttlSeconds * 1000L);

        UndoSnapshot snapshot;
        try {
            snapshot = snapshotForOwner(owner, now, expiresAt);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB snapshot failed", ex);
            return false;
        }

        int locksDeleted;
        int hoppersDeleted;
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM locks WHERE owner_uuid=?")) {
                ps.setString(1, owner.toString());
                locksDeleted = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM secure_hoppers WHERE owner_uuid=?")) {
                ps.setString(1, owner.toString());
                hoppersDeleted = ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB unlockAllWithUndo failed", ex);
            return false;
        }

        lockCache.entrySet().removeIf(e -> e.getValue().owner().equals(owner));
        hopperCache.entrySet().removeIf(e -> e.getValue().owner().equals(owner));

        if (!snapshot.locks().isEmpty() || !snapshot.secureHoppers().isEmpty()) {
            plugin.undos().set(actor.getUniqueId(), snapshot);
        }
        return locksDeleted > 0 || hoppersDeleted > 0;
    }

    @Override
    public boolean restoreUndo(Player actor) {
        var snapOpt = plugin.undos().consume(actor.getUniqueId());
        if (snapOpt.isEmpty()) return false;
        UndoSnapshot snapshot = snapOpt.get();

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);

            // locks
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO locks(world_uuid,x,y,z,block_type,owner_uuid,created_at) VALUES(?,?,?,?,?,?,?)")) {
                for (UndoSnapshot.LockRow lr : snapshot.locks()) {
                    ps.setString(1, lr.key().worldUuid().toString());
                    ps.setInt(2, lr.key().x());
                    ps.setInt(3, lr.key().y());
                    ps.setInt(4, lr.key().z());
                    ps.setString(5, lr.blockType());
                    ps.setString(6, lr.owner().toString());
                    ps.setLong(7, lr.createdAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // trusts
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO trusted(world_uuid,x,y,z,target_uuid,level) VALUES(?,?,?,?,?,?)")) {
                for (UndoSnapshot.TrustRow tr : snapshot.trusts()) {
                    ps.setString(1, tr.key().worldUuid().toString());
                    ps.setInt(2, tr.key().x());
                    ps.setInt(3, tr.key().y());
                    ps.setInt(4, tr.key().z());
                    ps.setString(5, tr.target().toString());
                    ps.setString(6, tr.level());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // secure hoppers
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO secure_hoppers(world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                for (UndoSnapshot.SecureHopperRow hr : snapshot.secureHoppers()) {
                    ps.setString(1, hr.hopperKey().worldUuid().toString());
                    ps.setInt(2, hr.hopperKey().x());
                    ps.setInt(3, hr.hopperKey().y());
                    ps.setInt(4, hr.hopperKey().z());
                    ps.setString(5, hr.owner().toString());
                    ps.setString(6, hr.boundKey().worldUuid().toString());
                    ps.setInt(7, hr.boundKey().x());
                    ps.setInt(8, hr.boundKey().y());
                    ps.setInt(9, hr.boundKey().z());
                    ps.setLong(10, hr.createdAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            c.commit();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB undo restore failed", ex);
            return false;
        }

        // Refresh caches for restored locks/hoppers
        for (UndoSnapshot.LockRow lr : snapshot.locks()) {
            Material type = Material.matchMaterial(lr.blockType());
            LockEntry le = new LockEntry(lr.key(), type == null ? Material.CHEST : type, lr.owner(), lr.createdAt());
            lockCache.put(lr.key().asId(), le);
        }
        for (UndoSnapshot.TrustRow tr : snapshot.trusts()) {
            LockEntry le = lockCache.get(tr.key().asId());
            if (le != null) {
                AccessLevel lvl = AccessLevel.fromString(tr.level());
                if (lvl != null) le.trusted().put(tr.target(), lvl);
            }
        }
        for (UndoSnapshot.SecureHopperRow hr : snapshot.secureHoppers()) {
            hopperCache.put(hr.hopperKey().asId(), new SecureHopperEntry(hr.hopperKey(), hr.owner(), hr.boundKey()));
        }

        return true;
    }

    @Override
    public boolean hasUndo(Player actor) {
        return plugin.undos().get(actor.getUniqueId()).isPresent();
    }

    private UndoSnapshot snapshotForSingleLock(LockKey key, LockEntry entry) throws SQLException {
        // Trust rows
        List<UndoSnapshot.TrustRow> trusts = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT target_uuid,level FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID target = UUID.fromString(rs.getString(1));
                    String level = rs.getString(2);
                    trusts.add(new UndoSnapshot.TrustRow(key, target, level));
                }
            }
        }

        List<UndoSnapshot.LockRow> locks = java.util.List.of(
                new UndoSnapshot.LockRow(key, entry.blockType().name(), entry.owner(), entry.createdAt())
        );
        long now = System.currentTimeMillis();
        int ttlSeconds = plugin.getConfig().getInt("ui.undo_seconds", 20);
        long expiresAt = now + (ttlSeconds * 1000L);
        return new UndoSnapshot(locks, trusts, java.util.List.of(), now, expiresAt);
    }

    private UndoSnapshot snapshotForOwner(UUID owner, long now, long expiresAt) throws SQLException {
        List<UndoSnapshot.LockRow> locks = new ArrayList<>();
        List<UndoSnapshot.TrustRow> trusts = new ArrayList<>();
        List<UndoSnapshot.SecureHopperRow> hoppers = new ArrayList<>();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT world_uuid,x,y,z,block_type,created_at FROM locks WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LockKey key = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                    String type = rs.getString(5);
                    long created = rs.getLong(6);
                    locks.add(new UndoSnapshot.LockRow(key, type, owner, created));

                    // trusts for this lock
                    try (PreparedStatement ps2 = c.prepareStatement("SELECT target_uuid,level FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
                        ps2.setString(1, key.worldUuid().toString());
                        ps2.setInt(2, key.x());
                        ps2.setInt(3, key.y());
                        ps2.setInt(4, key.z());
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                trusts.add(new UndoSnapshot.TrustRow(key, UUID.fromString(rs2.getString(1)), rs2.getString(2)));
                            }
                        }
                    }
                }
            }
        }

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT world_uuid,x,y,z,bound_world_uuid,bound_x,bound_y,bound_z,created_at FROM secure_hoppers WHERE owner_uuid=?")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LockKey hk = new LockKey(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4));
                    LockKey bound = new LockKey(UUID.fromString(rs.getString(5)), rs.getInt(6), rs.getInt(7), rs.getInt(8));
                    long created = rs.getLong(9);
                    hoppers.add(new UndoSnapshot.SecureHopperRow(hk, owner, bound, created));
                }
            }
        }

        return new UndoSnapshot(locks, trusts, hoppers, now, expiresAt);
    }

    @Override
    public boolean canOpen(Player player, Block block, boolean usingAdminKey) {
        if (!LockUtil.isLockable(block)) return true;

        LockEntry entry = getLock(block).orElse(null);
        if (entry == null) return true;

        if (player.hasPermission("chestsecure.admin")) {
            if (usingAdminKey) return true;
        }
        if (entry.owner().equals(player.getUniqueId())) return true;

        return entry.trusted().containsKey(player.getUniqueId());
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        // Locked container
        LockEntry lock = getLock(block).orElse(null);
        if (lock != null) {
            return player.hasPermission("chestsecure.admin") || lock.owner().equals(player.getUniqueId());
        }

        // Secure hopper
        if (block.getType() == Material.HOPPER) {
            SecureHopperEntry sh = getSecureHopper(block).orElse(null);
            if (sh != null) {
                return player.hasPermission("chestsecure.admin") || sh.owner().equals(player.getUniqueId());
            }
        }

        return true;
    }

    @Override
    public AccessLevel getAccessLevel(Player player, Block block) {
        LockEntry entry = getLock(block).orElse(null);
        if (entry == null) return null;
        if (player.hasPermission("chestsecure.admin")) return AccessLevel.CO_OWNER;
        if (entry.owner().equals(player.getUniqueId())) return AccessLevel.CO_OWNER;
        return entry.trusted().get(player.getUniqueId());
    }

    @Override
    public boolean setTrust(Player owner, Block block, UUID target, AccessLevel level) {
        if (!LockUtil.isLockable(block)) return false;
        LockKey key = LockUtil.canonicalKey(block);
        return setTrustByKey(owner, key, target, level);
    }

    @Override
    public boolean setTrustById(Player owner, String lockId, UUID target, AccessLevel level) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }
        return setTrustByKey(owner, key, target, level);
    }

    private boolean setTrustByKey(Player owner, LockKey key, UUID target, AccessLevel level) {
        String id = key.asId();
        LockEntry entry = getLockFromId(id).orElse(null);
        if (entry == null) return false;
        if (!entry.owner().equals(owner.getUniqueId())) return false;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO trusted(world_uuid,x,y,z,target_uuid,level) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setString(5, target.toString());
            ps.setString(6, level.name());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB setTrust failed", ex);
            return false;
        }

        entry.trusted().put(target, level);
        lockCache.put(id, entry);
        return true;
    }

    @Override
    public boolean removeTrust(Player owner, Block block, UUID target) {
        if (!LockUtil.isLockable(block)) return false;
        LockKey key = LockUtil.canonicalKey(block);
        return removeTrustByKey(owner, key, target);
    }

    @Override
    public boolean removeTrustById(Player owner, String lockId, UUID target) {
        LockKey key;
        try { key = LockKey.fromId(lockId); } catch (Exception ex) { return false; }
        return removeTrustByKey(owner, key, target);
    }

    private boolean removeTrustByKey(Player owner, LockKey key, UUID target) {
        String id = key.asId();
        LockEntry entry = getLockFromId(id).orElse(null);
        if (entry == null) return false;
        if (!owner.hasPermission("chestsecure.admin") && !entry.owner().equals(owner.getUniqueId())) return false;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM trusted WHERE world_uuid=? AND x=? AND y=? AND z=? AND target_uuid=?")) {
            ps.setString(1, key.worldUuid().toString());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setString(5, target.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB removeTrust failed", ex);
            return false;
        }

        entry.trusted().remove(target);
        lockCache.put(id, entry);
        return true;
    }

    @Override
    public List<Location> listLocks(UUID owner) {
        // Use in-memory cache: faster and shows locks from unloaded worlds too.
        List<Location> out = new ArrayList<>();
        for (LockEntry entry : lockCache.values()) {
            if (!entry.owner().equals(owner)) continue;
            LockKey k = entry.key();
            World world = Bukkit.getWorld(k.worldUuid());
            // Location with null world is allowed; GuiManager skips null-world entries gracefully.
            out.add(new Location(world, k.x(), k.y(), k.z()));
        }
        out.sort(Comparator.comparing((Location l) -> l.getWorld() != null ? l.getWorld().getName() : "(unloaded)")
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));
        return out;
    }

    // -------- Secure Hopper --------

    @Override
    public boolean isSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return false;
        String id = LockKey.fromLocation(hopperBlock.getLocation()).asId();
        if (hopperCache.containsKey(id)) return true;

        // Also check tile PDC marker
        if (hopperBlock.getState() instanceof TileState tile) {
            Byte mark = tile.getPersistentDataContainer().get(plugin.keySecureHopperBlock(), PersistentDataType.BYTE);
            if (mark != null && mark == (byte) 1) {
                return getSecureHopper(hopperBlock).isPresent();
            }
        }
        return getSecureHopper(hopperBlock).isPresent();
    }

    @Override
    public Optional<SecureHopperEntry> getSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return Optional.empty();
        LockKey hk = LockKey.fromLocation(hopperBlock.getLocation());
        String id = hk.asId();

        SecureHopperEntry cached = hopperCache.get(id);
        if (cached != null) return Optional.of(cached);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, hk.worldUuid().toString());
            ps.setInt(2, hk.x());
            ps.setInt(3, hk.y());
            ps.setInt(4, hk.z());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID owner = UUID.fromString(rs.getString(1));
                LockKey bound = new LockKey(UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getInt(4), rs.getInt(5));
                SecureHopperEntry sh = new SecureHopperEntry(hk, owner, bound);
                hopperCache.put(id, sh);
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
        if (lock == null) return false;
        if (!lock.owner().equals(placer.getUniqueId())) return false;

        LockKey hopperKey = LockKey.fromLocation(hopperBlock.getLocation());
        LockKey boundKey = LockUtil.canonicalKey(boundContainer);
        long now = Instant.now().toEpochMilli();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO secure_hoppers(world_uuid,x,y,z,owner_uuid,bound_world_uuid,bound_x,bound_y,bound_z,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, hopperKey.worldUuid().toString());
            ps.setInt(2, hopperKey.x());
            ps.setInt(3, hopperKey.y());
            ps.setInt(4, hopperKey.z());
            ps.setString(5, placer.getUniqueId().toString());
            ps.setString(6, boundKey.worldUuid().toString());
            ps.setInt(7, boundKey.x());
            ps.setInt(8, boundKey.y());
            ps.setInt(9, boundKey.z());
            ps.setLong(10, now);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB bindSecureHopper failed", ex);
            return false;
        }

        // Mark tile PDC
        if (hopperBlock.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().set(plugin.keySecureHopperBlock(), PersistentDataType.BYTE, (byte) 1);
            tile.update(true, false);
        }

        SecureHopperEntry sh = new SecureHopperEntry(hopperKey, placer.getUniqueId(), boundKey);
        hopperCache.put(hopperKey.asId(), sh);
        return true;
    }

    @Override
    public boolean removeSecureHopper(Block hopperBlock) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return false;
        LockKey hk = LockKey.fromLocation(hopperBlock.getLocation());
        String id = hk.asId();

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM secure_hoppers WHERE world_uuid=? AND x=? AND y=? AND z=?")) {
            ps.setString(1, hk.worldUuid().toString());
            ps.setInt(2, hk.x());
            ps.setInt(3, hk.y());
            ps.setInt(4, hk.z());
            ps.executeUpdate();
            hopperCache.remove(id);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB removeSecureHopper failed", ex);
            return false;
        }

        if (hopperBlock.getState() instanceof TileState tile) {
            tile.getPersistentDataContainer().remove(plugin.keySecureHopperBlock());
            tile.update(true, false);
        }
        return true;
    }

    @Override
    public List<Location> listSecureHoppers(UUID owner) {
        // Use in-memory cache: consistent with listLocks(), avoids DB hit on every GUI open.
        List<Location> out = new ArrayList<>();
        for (SecureHopperEntry entry : hopperCache.values()) {
            if (!entry.owner().equals(owner)) continue;
            LockKey k = entry.hopperKey();
            World world = Bukkit.getWorld(k.worldUuid());
            out.add(new Location(world, k.x(), k.y(), k.z()));
        }
        out.sort(Comparator.comparing((Location l) -> l.getWorld() != null ? l.getWorld().getName() : "(unloaded)")
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));
        return out;
    }

    // -------- Settings --------

    @Override
    public boolean getQuickLock(UUID player) {
        return quickLockCache.computeIfAbsent(player, this::loadQuickLock);
    }

    @Override
    public boolean getQuickRemove(UUID player) {
        return quickRemoveCache.computeIfAbsent(player, this::loadQuickRemove);
    }

    private boolean loadQuickLock(UUID player) {
        return loadSetting(player, "quick_lock");
    }

    private boolean loadQuickRemove(UUID player) {
        return loadSetting(player, "quick_remove");
    }

    private boolean loadSetting(UUID player, String field) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + field + " FROM player_settings WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB loadSetting failed", ex);
        }
        // default create row
        ensureSettingsRow(player);
        return false;
    }

    private void ensureSettingsRow(UUID player) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO player_settings(player_uuid,quick_lock,quick_remove) VALUES(?,?,?)")) {
            ps.setString(1, player.toString());
            ps.setInt(2, 0);
            ps.setInt(3, 0);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB ensureSettingsRow failed", ex);
        }
    }

    @Override
    public void setQuickLock(UUID player, boolean value) {
        setSetting(player, "quick_lock", value);
        quickLockCache.put(player, value);
    }

    @Override
    public void setQuickRemove(UUID player, boolean value) {
        setSetting(player, "quick_remove", value);
        quickRemoveCache.put(player, value);
    }

    private void setSetting(UUID player, String field, boolean value) {
        ensureSettingsRow(player);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE player_settings SET " + field + "=? WHERE player_uuid=?")) {
            ps.setInt(1, value ? 1 : 0);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB setSetting failed", ex);
        }
    }

    // -------- Logs --------

    @Override
    public void logUnauthorizedBreak(Player actor, Block target) {
        insertLog("UNAUTHORIZED_BREAK", actor, target, null);
    }

    @Override
    public void logAdminBreak(Player actor, Block target) {
        insertLog("ADMIN_BREAK", actor, target, null);
    }

    private void insertLog(String action, Player actor, Block target, String extra) {
        long now = Instant.now().toEpochMilli();
        World w = target.getWorld();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO logs(created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, now);
            ps.setString(2, action);
            ps.setString(3, actor.getUniqueId().toString());
            ps.setString(4, actor.getName());
            ps.setString(5, w.getName());
            ps.setString(6, w.getUID().toString());
            ps.setInt(7, target.getX());
            ps.setInt(8, target.getY());
            ps.setInt(9, target.getZ());
            ps.setString(10, extra);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB log insert failed", ex);
        }
    }

    @Override
    public ItemStack createLogsBook(int maxEntries) {
        List<String> lines = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT created_at,action,actor_name,world_name,x,y,z FROM logs ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, maxEntries));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong(1);
                    String action = rs.getString(2);
                    String name = rs.getString(3);
                    String world = rs.getString(4);
                    int x = rs.getInt(5), y = rs.getInt(6), z = rs.getInt(7);
                    lines.add(BookUtil.formatLogLine(ts, action, name, world, x, y, z));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB read logs failed", ex);
        }
        if (lines.isEmpty()) lines.add("No logs yet.");
        return BookUtil.buildLogsBook(lines);
    }

    // -------- Logs UI / Report --------

    @Override
    public List<LogRecord> listLogs(String actionFilter, long minCreatedAt, String actorNameLike, int offset, int limit) {
        List<LogRecord> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id,created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra FROM logs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equalsIgnoreCase("ALL")) {
            sql.append(" AND action=?");
            params.add(actionFilter);
        }
        if (minCreatedAt > 0) {
            sql.append(" AND created_at>=?");
            params.add(minCreatedAt);
        }
        if (actorNameLike != null && !actorNameLike.isBlank()) {
            sql.append(" AND actor_name LIKE ?");
            params.add("%" + actorNameLike + "%");
        }

        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) ps.setString(i + 1, s);
                else if (p instanceof Long l) ps.setLong(i + 1, l);
                else if (p instanceof Integer n) ps.setInt(i + 1, n);
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    long createdAt = rs.getLong(2);
                    String action = rs.getString(3);
                    UUID actorUuid = UUID.fromString(rs.getString(4));
                    String actorName = rs.getString(5);
                    String worldName = rs.getString(6);
                    UUID worldUuid = UUID.fromString(rs.getString(7));
                    int x = rs.getInt(8), y = rs.getInt(9), z = rs.getInt(10);
                    String extra = rs.getString(11);
                    out.add(new LogRecord(id, createdAt, action, actorUuid, actorName, worldName, worldUuid, x, y, z, extra));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB listLogs failed", ex);
        }
        return out;
    }

    @Override
    public int countLogs(String actionFilter, long minCreatedAt, String actorNameLike) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM logs WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (actionFilter != null && !actionFilter.isBlank() && !actionFilter.equalsIgnoreCase("ALL")) {
            sql.append(" AND action=?");
            params.add(actionFilter);
        }
        if (minCreatedAt > 0) {
            sql.append(" AND created_at>=?");
            params.add(minCreatedAt);
        }
        if (actorNameLike != null && !actorNameLike.isBlank()) {
            sql.append(" AND actor_name LIKE ?");
            params.add("%" + actorNameLike + "%");
        }

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) ps.setString(i + 1, s);
                else if (p instanceof Long l) ps.setLong(i + 1, l);
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB countLogs failed", ex);
        }
        return 0;
    }

    @Override
    public Optional<LogRecord> getLogById(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id,created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra FROM logs WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long createdAt = rs.getLong(2);
                String action = rs.getString(3);
                UUID actorUuid = UUID.fromString(rs.getString(4));
                String actorName = rs.getString(5);
                String worldName = rs.getString(6);
                UUID worldUuid = UUID.fromString(rs.getString(7));
                int x = rs.getInt(8), y = rs.getInt(9), z = rs.getInt(10);
                String extra = rs.getString(11);
                return Optional.of(new LogRecord(id, createdAt, action, actorUuid, actorName, worldName, worldUuid, x, y, z, extra));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB getLogById failed", ex);
            return Optional.empty();
        }
    }

    @Override
    public ItemStack createAdminReportBook() {
        List<String> lines = new ArrayList<>();

        int totalLocks = 0;
        int totalHoppers = 0;
        int logs24h = 0;
        int logs7d = 0;
        int unauth24h = 0;
        int admin24h = 0;

        long now = System.currentTimeMillis();
        long h24 = now - 24L * 60L * 60L * 1000L;
        long d7 = now - 7L * 24L * 60L * 60L * 1000L;

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM locks");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalLocks = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM secure_hoppers");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalHoppers = rs.getInt(1);
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM logs WHERE created_at>=?");) {
                ps.setLong(1, h24);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) logs24h = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM logs WHERE created_at>=?");) {
                ps.setLong(1, d7);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) logs7d = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM logs WHERE created_at>=? AND action='UNAUTHORIZED_BREAK'");) {
                ps.setLong(1, h24);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) unauth24h = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM logs WHERE created_at>=? AND action='ADMIN_BREAK'");) {
                ps.setLong(1, h24);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) admin24h = rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB admin report failed", ex);
        }

        lines.add("ChestSecure Admin Report");
        lines.add("Generated: " + BookUtil.formatLogLine(now, "REPORT", "SYSTEM", "-", 0, 0, 0).substring(1, 20));
        lines.add("");
        lines.add("Totals:");
        lines.add("- Locks: " + totalLocks);
        lines.add("- Secure Hoppers: " + totalHoppers);
        lines.add("");
        lines.add("Logs:");
        lines.add("- Last 24h: " + logs24h);
        lines.add("- Last 7d: " + logs7d);
        lines.add("- Unauthorized breaks (24h): " + unauth24h);
        lines.add("- Admin breaks (24h): " + admin24h);
        lines.add("");

        // Top actors for unauthorized breaks (7d)
        lines.add("Top Unauthorized (7d):");
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT actor_name,COUNT(*) AS c FROM logs WHERE created_at>=? AND action='UNAUTHORIZED_BREAK' GROUP BY actor_name ORDER BY c DESC LIMIT 10")) {
            ps.setLong(1, d7);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    lines.add("- " + rs.getString(1) + ": " + rs.getInt(2));
                }
                if (!any) lines.add("- none");
            }
        } catch (SQLException ex) {
            lines.add("- (error reading)");
        }

        return BookUtil.buildBook("ChestSecure Report", "ChestSecure Report", "ChestSecure", lines);
    }
}
