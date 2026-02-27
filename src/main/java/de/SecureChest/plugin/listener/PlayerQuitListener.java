package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

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
        if (plugin.locks() instanceof de.SecureChest.plugin.lock.SqlLockService sls) {
            sls.evictPlayerCache(uuid);
        }
    }
}
