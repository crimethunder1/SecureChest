package de.SecureChest.plugin.sign;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.SecureHopperEntry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class SignManager implements Listener {

    private static final double SIGN_Y_OFFSET = 1.35;

    private final ChestSecurePlugin plugin;

    private final Map<String, UUID> signEntities = new ConcurrentHashMap<>();

    public SignManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.cfg().signsEnabled();
    }

    public void spawnSign(LockKey key, String ownerName) {
        if (!isEnabled()) return;
        World world = Bukkit.getWorld(key.worldUuid());
        if (world == null) return;
        String lockId = key.asId();
        removeSign(lockId);
        if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return;
        try {
            Location loc = new Location(world, key.x() + 0.5, key.y() + SIGN_Y_OFFSET, key.z() + 0.5);
            TextDisplay td = world.spawn(loc, TextDisplay.class, entity -> {
                entity.text(
                    Component.text("\uD83D\uDD12 ", NamedTextColor.GOLD)
                             .append(Component.text(ownerName, NamedTextColor.YELLOW)
                                              .decorate(TextDecoration.BOLD))
                );
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setDefaultBackground(true);
                entity.setSeeThrough(false);
                entity.setViewRange(0.45f);
            });
            signEntities.put(lockId, td.getUniqueId());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn sign for " + lockId, ex);
        }
    }

    public void removeSign(String lockId) {
        UUID eid = signEntities.remove(lockId);
        if (eid == null) return;
        Entity e = Bukkit.getEntity(eid);
        if (e != null && e.isValid()) e.remove();
    }

    public void spawnAllSigns() {
        if (!isEnabled()) return;

        java.util.List<LockEntry> all = new java.util.ArrayList<>(plugin.locks().getAllCachedLocks());
        if (all.isEmpty()) return;

        final int BATCH_SIZE  = 50;
        final int TICK_DELAY  = 1;
        final int total       = all.size();

        for (int batchStart = 0; batchStart < total; batchStart += BATCH_SIZE) {
            final int start = batchStart;
            long delay = (long) (batchStart / BATCH_SIZE) * TICK_DELAY;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int end = Math.min(start + BATCH_SIZE, total);
                for (int i = start; i < end; i++) {
                    LockEntry entry = all.get(i);
                    spawnSign(entry.key(), ownerName(entry.owner()));
                }
                if (end == total) {
                    plugin.getLogger().info("SignManager: " + signEntities.size()
                            + " Signs gespawnt (Batches à " + BATCH_SIZE + ").");
                }
            }, delay);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!isEnabled()) return;
        World world = e.getWorld();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (LockEntry entry : plugin.locks().getAllCachedLocks()) {
                LockKey k = entry.key();
                if (!k.worldUuid().equals(world.getUID())) continue;
                if ((k.x() >> 4) != cx || (k.z() >> 4) != cz) continue;
                Block block = world.getBlockAt(k.x(), k.y(), k.z());
                if (!LockUtil.isLockable(block)) {
                    plugin.getLogger().info("[SecureChest] Orphan-Lock bei " + k.asId()
                            + " (Blocktyp: " + block.getType() + "), räume auf.");
                    plugin.locks().forceRemoveLockAt(k);
                    continue;
                }

                if (signEntities.containsKey(k.asId())) continue;
                spawnSign(k, ownerName(entry.owner()));
            }
        });
    }

    public void validateAndCleanOrphans() {
        if (!isEnabled()) return;
        int cleaned = 0;

        for (LockEntry entry : plugin.locks().getAllCachedLocks()) {
            LockKey k = entry.key();
            World world = Bukkit.getWorld(k.worldUuid());
            if (world == null) continue;
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;

            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (!LockUtil.isLockable(block)) {
                plugin.getLogger().info("[SecureChest] Orphan-Lock (periodisch) bei " + k.asId()
                        + " (Blocktyp: " + block.getType() + "), räume auf.");
                plugin.locks().forceRemoveLockAt(k);
                cleaned++;
            }
        }

        for (SecureHopperEntry entry : plugin.locks().getAllCachedHoppers()) {
            LockKey k = entry.hopperKey();
            World world = Bukkit.getWorld(k.worldUuid());
            if (world == null) continue;
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;

            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (block.getType() != Material.HOPPER) {
                plugin.getLogger().info("[SecureChest] Orphan-Hopper (periodisch) bei " + k.asId()
                        + " (Blocktyp: " + block.getType() + "), räume auf.");
                plugin.locks().forceRemoveHopperAt(k);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().info("[SecureChest] Periodische Validierung: " + cleaned + " Orphan(s) bereinigt.");
        }
    }

    private String ownerName(UUID uuid) {

        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (op != null && op.getName() != null) return op.getName();

        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        return uuid.toString().substring(0, 8) + "…";
    }
}
