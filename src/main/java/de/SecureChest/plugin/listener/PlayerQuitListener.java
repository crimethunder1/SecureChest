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

    private static final DateTimeFormatter NOTIFICATION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;

    public PlayerQuitListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        if (plugin.locks() instanceof SqlLockService sqlLockService) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                sqlLockService.preloadPlayerSettings(playerUuid);

                if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;
                List<Notification> notifications = sqlLockService.fetchAndClearNotifications(playerUuid);
                if (notifications.isEmpty()) return;

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer == null || !onlinePlayer.isOnline()) return;

                    int notificationCount = notifications.size();
                    String pluralLabel = notificationCount == 1 ? "Meldung" : "Meldungen";
                    onlinePlayer.sendMessage(
                            Component.text("[ ChestSecure ] ", NamedTextColor.GOLD)
                                    .append(Component.text(notificationCount + " verpasste " + pluralLabel + " während deiner Abwesenheit:",
                                            NamedTextColor.YELLOW)));
                    for (Notification notification : notifications) {
                        String formattedTime = NOTIFICATION_DATE_FORMATTER.format(Instant.ofEpochMilli(notification.createdAt()));
                        onlinePlayer.sendMessage(
                                Component.text("  " + formattedTime + "  ", NamedTextColor.DARK_GRAY)
                                        .append(Component.text(notification.message(), NamedTextColor.WHITE)));
                    }
                    onlinePlayer.sendMessage(
                            Component.text("Tippe /cs um deine Locks zu verwalten.", NamedTextColor.GRAY));
                }, 200L);
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        plugin.pendingChat().remove(playerUuid);
        plugin.confirms().clear(playerUuid);
        plugin.undos().clear(playerUuid);
        plugin.ui().cleanup(playerUuid);
        plugin.ping().stopPing(event.getPlayer());

        if (plugin.locks() instanceof SqlLockService sqlLockService) {
            sqlLockService.evictPlayerCache(playerUuid);
        }
    }
}