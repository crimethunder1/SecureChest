package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.Notification;
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

    private static final DateTimeFormatter NOTIFICATION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;

    public PlayerQuitListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.locks().preloadPlayerSettings(playerUuid);

            if (!plugin.cfg().notificationsEnabled()) return;
            List<Notification> notifications = plugin.locks().fetchAndClearNotifications(playerUuid);
                if (notifications.isEmpty()) return;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer == null || !onlinePlayer.isOnline()) return;

                    int notificationCount = notifications.size();
                    String pluralLabel = notificationCount == 1
                            ? plugin.msg().get("notification-singular")
                            : plugin.msg().get("notification-plural");
                    onlinePlayer.sendMessage(
                            Component.text(plugin.msg().get("notification-header",
                                    notificationCount, pluralLabel)));
                    for (Notification notification : notifications) {
                        String formattedTime = NOTIFICATION_DATE_FORMATTER.format(Instant.ofEpochMilli(notification.createdAt()));
                        onlinePlayer.sendMessage(
                                Component.text("  " + formattedTime + "  ", NamedTextColor.DARK_GRAY)
                                        .append(Component.text(notification.message(), NamedTextColor.WHITE)));
                    }
                    onlinePlayer.sendMessage(
                            Component.text(plugin.msg().get("notification-hinweis"), NamedTextColor.GRAY));
                }, 200L);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        plugin.pendingChat().remove(playerUuid);
        plugin.confirms().clear(playerUuid);
        plugin.undos().clear(playerUuid);
        plugin.ui().cleanup(playerUuid);
        plugin.ping().stopPing(event.getPlayer());

        plugin.locks().evictPlayerCache(playerUuid);
    }
}