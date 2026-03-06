package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SignManager implements Listener {

    private static final double SIGN_Y_OFFSET = 1.35;

    private final ChestSecurePlugin plugin;

    private final Map<String, UUID> signEntities = new ConcurrentHashMap<>();

    public SignManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("signs.enabled", true);
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
        for (LockEntry entry : plugin.locks().getAllCachedLocks()) {
            spawnSign(entry.key(), ownerName(entry.owner()));
        }
        plugin.getLogger().info("SignManager: spawned signs for " + signEntities.size() + " lock(s).");
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
                if (signEntities.containsKey(k.asId())) continue;
                spawnSign(k, ownerName(entry.owner()));
            }
        });
    }

    private String ownerName(UUID uuid) {

        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (op != null && op.getName() != null) return op.getName();

        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        return uuid.toString().substring(0, 8) + "…";
    }
}
