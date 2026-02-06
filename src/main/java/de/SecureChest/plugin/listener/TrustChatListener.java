package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class TrustChatListener implements Listener {

    private final ChestSecurePlugin plugin;

    public TrustChatListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        // AsyncChatEvent runs asynchronously. We cancel and then hand off to the main thread
        // to avoid unsafe Bukkit calls (OfflinePlayer lookup, GUI operations, etc.).
        final UUID uuid = e.getPlayer().getUniqueId();
        final PendingChatAction action = plugin.pendingChat().get(uuid);
        if (action == null) return;

        final String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.message())
                .trim();
        if (msg.isEmpty()) return;

        e.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> handleOnMain(uuid, msg));
    }

    private void handleOnMain(UUID uuid, String msg) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        // Action might have been cleared between async and sync.
        PendingChatAction action = plugin.pendingChat().get(uuid);
        if (action == null) return;

        if (msg.equalsIgnoreCase("cancel")) {
            plugin.pendingChat().remove(uuid);
            p.sendMessage(Component.text("Cancelled."));
            return;
        }

        if (action instanceof PendingChatAction.AddTrust at) {
            AccessLevel level = AccessLevel.fromString(at.level());
            if (level == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("Invalid trust level."));
                return;
            }

            // Resolve player safely on main thread.
            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayer(msg);

            boolean ok = plugin.locks().setTrustById(p, at.lockId(), target.getUniqueId(), level);
            plugin.pendingChat().remove(uuid);

            String name = target.getName() != null ? target.getName() : msg;
            p.sendMessage(Component.text(ok
                    ? ("Trusted " + name + " as " + level + ".")
                    : "Failed (not owner / lock missing)."));

            plugin.gui().openLockManage(p, at.lockId());
            return;
        }

        if (action instanceof PendingChatAction.GlobalUnlock) {
            if (!p.hasPermission("chestsecure.admin")) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("No permission."));
                return;
            }

            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayer(msg);

            plugin.pendingChat().remove(uuid);

            UUID targetId = target.getUniqueId();
            int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
            long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);

            plugin.confirms().set(uuid, new ConfirmRequest(
                    ConfirmType.GLOBAL_UNLOCK,
                    targetId.toString(),
                    "ADMIN",
                    expiresAt
            ));

            String display = target.getName() != null ? target.getName() : msg;
            plugin.gui().openConfirm(p,
                    "Global Unlock",
                    java.util.List.of(
                            "Unlock ALL locks & secure hoppers of:",
                            display,
                            "",
                            "Undo will be available after.",
                            ""
                    )
            );
            return;
        }

        if (action instanceof PendingChatAction.LogActorFilter) {
            plugin.pendingChat().remove(uuid);

            String filter = msg.equalsIgnoreCase("clear") ? "" : msg;
            plugin.ui().logs(uuid).actorFilter = filter;

            p.sendMessage(Component.text(filter.isEmpty()
                    ? "Logs actor filter cleared."
                    : "Logs actor filter set to: " + filter));

            plugin.gui().openLogs(p, 0);
        }
    }
}
