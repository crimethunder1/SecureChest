package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.Notification;
import de.SecureChest.plugin.lock.SqlLockService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public final class PlayerQuitListener implements Listener {

    private static final DateTimeFormatter DF =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;

    public PlayerQuitListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        if (plugin.locks() instanceof SqlLockService sls) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

                sls.preloadPlayerSettings(uuid);

                if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;
                List<Notification> notifications = sls.fetchAndClearNotifications(uuid);
                if (notifications.isEmpty()) return;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) return;

                    int count = notifications.size();
                    String plural = count == 1 ? "alert" : "alerts";
                    player.sendMessage(
                        Component.text("[ ChestSecure ] ", NamedTextColor.GOLD)
                            .append(Component.text(count + " missed " + plural + " while you were offline:",
                                    NamedTextColor.YELLOW)));
                    for (Notification n : notifications) {
                        String time = DF.format(Instant.ofEpochMilli(n.createdAt()));
                        player.sendMessage(
                            Component.text("  " + time + "  ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(n.message(), NamedTextColor.WHITE)));
                    }
                    player.sendMessage(
                        Component.text("Use /cs to manage your locks.", NamedTextColor.GRAY));
                }, 200L);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        plugin.pendingChat().remove(uuid);
        plugin.confirms().clear(uuid);
        plugin.undos().clear(uuid);
        plugin.ui().cleanup(uuid);

        if (plugin.locks() instanceof SqlLockService sls) {
            sls.evictPlayerCache(uuid);
        }
    }
}
