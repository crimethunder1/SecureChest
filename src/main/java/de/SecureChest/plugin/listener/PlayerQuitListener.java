package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

<<<<<<< HEAD
=======
/**
 * Cleans up all per-player in-memory state when a player disconnects,
 * preventing unbounded memory growth on long-running servers.
 */
>>>>>>> a213fa6b4069e01c09e863d0744f947774f9fe82
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
<<<<<<< HEAD
        if (plugin.locks() instanceof de.SecureChest.plugin.lock.SqlLockService sls) {
            sls.evictPlayerCache(uuid);
        }
=======
>>>>>>> a213fa6b4069e01c09e863d0744f947774f9fe82
    }
}
