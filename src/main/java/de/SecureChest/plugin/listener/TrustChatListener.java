package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        
        PendingChatAction action = plugin.pendingChat().get(uuid);
        if (action == null) return;

        if (msg.equalsIgnoreCase("cancel")) {
            plugin.pendingChat().remove(uuid);
            p.sendMessage(Component.text("Cancelled.", NamedTextColor.YELLOW));
            return;
        }

        if (action instanceof PendingChatAction.AddTrust at) {
            AccessLevel level = AccessLevel.fromString(at.level());
            if (level == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("Invalid trust level.", NamedTextColor.RED));
                return;
            }

            // BUG FIX: Bukkit.getOfflinePlayer(String) can block the main thread with a
            // Mojang UUID lookup. Use getOfflinePlayerIfCached instead and reject unknown names.
            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("Player '" + msg + "' not found. They must have joined this server at least once.", NamedTextColor.RED));
                return;
            }

            boolean ok = plugin.locks().setTrustById(p, at.lockId(), target.getUniqueId(), level);
            plugin.pendingChat().remove(uuid);

            String name = target.getName() != null ? target.getName() : msg;
            p.sendMessage(ok
                    ? Component.text("Trusted " + name + " as " + level + ".", NamedTextColor.GREEN)
                    : Component.text("Failed (not owner / lock missing).", NamedTextColor.RED));

            plugin.gui().openLockManage(p, at.lockId());
            return;
        }

        if (action instanceof PendingChatAction.GlobalUnlock) {
            if (!p.hasPermission("chestsecure.admin")) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return;
            }

            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            // BUG FIX: same non-blocking lookup as AddTrust above
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("Player '" + msg + "' not found. They must have joined this server at least once.", NamedTextColor.RED));
                return;
            }

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

            p.sendMessage(filter.isEmpty()
                    ? Component.text("Logs actor filter cleared.", NamedTextColor.YELLOW)
                    : Component.text("Logs actor filter set to: " + filter, NamedTextColor.YELLOW));

            plugin.gui().openLogs(p, 0);
            return; // BUG FIX: missing return — without this the TransferLock check below
                    // also runs on the same action object (harmless due to sealed types, but
                    // wrong and could break if the sealed hierarchy is ever extended)
        }

        // FEATURE: Lock Transfer – chat input for new owner name
        if (action instanceof PendingChatAction.TransferLock tl) {
            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text("Player '" + msg + "' not found. They must have joined this server at least once.", NamedTextColor.RED));
                return;
            }
            plugin.pendingChat().remove(uuid);

            String display = target.getName() != null ? target.getName() : msg;
            int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
            long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);

            plugin.confirms().set(uuid, new de.SecureChest.plugin.ui.ConfirmRequest(
                    de.SecureChest.plugin.ui.ConfirmType.TRANSFER_LOCK,
                    tl.lockId() + "|" + target.getUniqueId(),
                    "LOCK_MANAGE|" + tl.lockId(),
                    expiresAt
            ));

            plugin.gui().openConfirm(p,
                    "Transfer Lock",
                    java.util.List.of(
                            "Transfer this lock to:",
                            display,
                            "",
                            "The trust list will be cleared.",
                            "An Undo will be available after.",
                            ""
                    )
            );
        }
    }
}
