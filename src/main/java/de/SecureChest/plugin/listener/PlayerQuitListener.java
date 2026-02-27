package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Cleans up all per-player in-memory state when a player disconnects,
 * preventing unbounded memory growth on long-running servers.
 */
public final class PlayerQuitListener implements Listener {

    private final ChestSecurePlugin plugin;

    public PlayerQuitListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        plugin.pendingChat().remove(uuid);
        plugin.confirms().clear(uuid);
        plugin.undos().clear(uuid);
        plugin.ui().cleanup(uuid);
        // Clear per-player settings caches to prevent unbounded memory growth
        if (plugin.locks() instanceof de.SecureChest.plugin.lock.SqlLockService sls) {
            sls.evictPlayerCache(uuid);
        }
    }
}
