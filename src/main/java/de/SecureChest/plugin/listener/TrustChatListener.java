package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaCoordinateParser;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.util.PlayerNameValidator;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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

    void handleOnMain(UUID uuid, String msg) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        PendingChatAction action = plugin.pendingChat().get(uuid);
        if (action == null) return;

        if (msg.equalsIgnoreCase("cancel")) {
            plugin.pendingChat().remove(uuid);
            p.sendMessage(Component.text(plugin.msg().get("trust-abgebrochen"), NamedTextColor.YELLOW));
            return;
        }

        if (action instanceof PendingChatAction.AddTrust at) {
            AccessLevel level = AccessLevel.fromString(at.level());
            if (level == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-ungueltige-stufe"), NamedTextColor.RED));
                return;
            }

            if (!PlayerNameValidator.isValid(msg)) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-eingabe-ungueltig"), NamedTextColor.RED));
                return;
            }
            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-spieler-nicht-gefunden", msg), NamedTextColor.RED));
                return;
            }

            boolean ok = plugin.locks().setTrustById(p, at.lockId(), target.getUniqueId(), level);
            plugin.pendingChat().remove(uuid);

            String name = target.getName() != null ? target.getName() : msg;
            p.sendMessage(ok
                    ? Component.text(plugin.msg().get("trust-vergeben", name, level), NamedTextColor.GREEN)
                    : Component.text(plugin.msg().get("trust-vergeben-fehlgeschlagen"), NamedTextColor.RED));

            plugin.gui().openLockManage(p, at.lockId());
            return;
        }

        if (action instanceof PendingChatAction.GlobalUnlock) {
            if (!p.hasPermission(Permissions.ADMIN)) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-keine-berechtigung"), NamedTextColor.RED));
                return;
            }

            if (!PlayerNameValidator.isValid(msg)) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-eingabe-ungueltig"), NamedTextColor.RED));
                return;
            }

            OfflinePlayer online = Bukkit.getPlayerExact(msg);

            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-spieler-nicht-gefunden", msg), NamedTextColor.RED));
                return;
            }

            plugin.pendingChat().remove(uuid);

            UUID targetId = target.getUniqueId();
            int ttlSeconds = plugin.cfg().confirmTimeoutSeconds();
            long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);

            plugin.confirms().set(uuid, new ConfirmRequest(
                    ConfirmType.GLOBAL_UNLOCK,
                    targetId.toString(),
                    "ADMIN",
                    expiresAt
            ));

            String display = target.getName() != null ? target.getName() : msg;
            plugin.gui().openConfirm(p,
                    plugin.msg().get("confirm-global-unlock-titel"),
                    java.util.List.of(
                            plugin.msg().get("confirm-global-unlock-zeile1"),
                            plugin.msg().get("confirm-global-unlock-zeile2", display),
                            plugin.msg().get("confirm-global-unlock-zeile3"),
                            plugin.msg().get("confirm-global-unlock-zeile4")
                    )
            );
            return;
        }

        if (action instanceof PendingChatAction.LogActorFilter) {
            plugin.pendingChat().remove(uuid);

            String filter = msg.equalsIgnoreCase("clear") ? "" : msg;
            plugin.ui().logs(uuid).actorFilter = filter;

            p.sendMessage(filter.isEmpty()
                    ? Component.text(plugin.msg().get("logs-filter-geloescht"), NamedTextColor.YELLOW)
                    : Component.text(plugin.msg().get("logs-filter-gesetzt", filter), NamedTextColor.YELLOW));

            plugin.gui().openLogs(p, 0);
            return;

        }

        if (action instanceof PendingChatAction.AreaCreateName) {
            if (!hasAreaPermission(p, uuid)) return;
            handleAreaName(p, uuid, msg);
            return;
        }

        if (action instanceof PendingChatAction.AreaCreateCoordinates createCoordinates) {
            if (!hasAreaPermission(p, uuid)) return;
            handleAreaCreateCoordinates(p, uuid, createCoordinates.name(), msg);
            return;
        }

        if (action instanceof PendingChatAction.AreaResizeCoordinates resizeCoordinates) {
            if (!hasAreaPermission(p, uuid)) return;
            handleAreaResizeCoordinates(p, uuid, resizeCoordinates.name(), msg);
            return;
        }

        if (action instanceof PendingChatAction.TransferLock tl) {
            if (!PlayerNameValidator.isValid(msg)) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-eingabe-ungueltig"), NamedTextColor.RED));
                return;
            }
            OfflinePlayer online = Bukkit.getPlayerExact(msg);
            OfflinePlayer target = (online != null) ? online : Bukkit.getOfflinePlayerIfCached(msg);
            if (target == null || target.getUniqueId() == null) {
                plugin.pendingChat().remove(uuid);
                p.sendMessage(Component.text(plugin.msg().get("trust-spieler-nicht-gefunden", msg), NamedTextColor.RED));
                return;
            }
            plugin.pendingChat().remove(uuid);

            String display = target.getName() != null ? target.getName() : msg;
            int ttlSeconds = plugin.cfg().confirmTimeoutSeconds();
            long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);

            plugin.confirms().set(uuid, new de.SecureChest.plugin.ui.ConfirmRequest(
                    de.SecureChest.plugin.ui.ConfirmType.TRANSFER_LOCK,
                    tl.lockId() + "|" + target.getUniqueId(),
                    "LOCK_MANAGE|" + tl.lockId(),
                    expiresAt
            ));

            plugin.gui().openConfirm(p,
                    plugin.msg().get("confirm-transfer-titel"),
                    java.util.List.of(
                            plugin.msg().get("confirm-transfer-zeile1"),
                            plugin.msg().get("confirm-transfer-zeile2", display),
                            plugin.msg().get("confirm-transfer-zeile3"),
                            plugin.msg().get("confirm-transfer-zeile4"),
                            plugin.msg().get("confirm-transfer-zeile5")
                    )
            );
        }
    }

    private boolean hasAreaPermission(Player p, UUID uuid) {
        if (p.hasPermission(Permissions.ADMIN)) return true;
        plugin.pendingChat().remove(uuid);
        p.sendMessage(Component.text(plugin.msg().get("trust-keine-berechtigung"), NamedTextColor.RED));
        return false;
    }

    private void handleAreaName(Player p, UUID uuid, String msg) {
        AreaValidationError.Code errorCode = null;
        if (!Area.isValidName(msg)) {
            errorCode = AreaValidationError.Code.NAME_INVALID;
        } else if (plugin.areas().find(msg).isPresent()) {
            errorCode = AreaValidationError.Code.NAME_ALREADY_USED;
        }
        if (errorCode != null) {
            plugin.gui().sendAreaErrors(p, List.of(new AreaValidationError(errorCode, msg)));
            plugin.gui().promptAreaName(p);
            return;
        }

        plugin.pendingChat().put(uuid, new PendingChatAction.AreaCreateCoordinates(msg));
        plugin.gui().promptAreaCreateCoordinates(p, msg);
    }

    private void handleAreaCreateCoordinates(Player p, UUID uuid, String name, String msg) {
        switch (AreaCoordinateParser.parse(msg)) {
            case AreaCoordinateParser.Rejected rejected -> {
                plugin.gui().sendAreaCoordinateError(p, rejected);
                plugin.gui().promptAreaCreateCoordinates(p, name);
            }
            case AreaCoordinateParser.Coordinates coordinates -> {
                AreaChangeResult result = plugin.areas().apply(new AreaChange.Create(
                        name, p.getWorld().getName(),
                        coordinates.firstX(), coordinates.firstZ(), coordinates.secondX(), coordinates.secondZ()));
                if (result.applied()) {
                    plugin.pendingChat().remove(uuid);
                    plugin.gui().sendAreaResult(p, result);
                    plugin.gui().openAreas(p, 0);
                    return;
                }

                plugin.gui().sendAreaErrors(p, result.errors());
                if (hasNameError(result.errors())) {
                    plugin.pendingChat().put(uuid, new PendingChatAction.AreaCreateName());
                    plugin.gui().promptAreaName(p);
                } else {
                    plugin.gui().promptAreaCreateCoordinates(p, name);
                }
            }
        }
    }

    private void handleAreaResizeCoordinates(Player p, UUID uuid, String name, String msg) {
        Optional<Area> area = plugin.areas().find(name);
        if (area.isEmpty()) {
            plugin.pendingChat().remove(uuid);
            plugin.gui().sendAreaErrors(p, List.of(new AreaValidationError(
                    AreaValidationError.Code.AREA_NOT_FOUND, name)));
            plugin.gui().openAreas(p, 0);
            return;
        }

        switch (AreaCoordinateParser.parse(msg)) {
            case AreaCoordinateParser.Rejected rejected -> {
                plugin.gui().sendAreaCoordinateError(p, rejected);
                plugin.gui().promptAreaResizeCoordinates(p, area.get());
            }
            case AreaCoordinateParser.Coordinates coordinates -> {
                AreaChangePreview preview = plugin.areas().previewResize(name,
                        coordinates.firstX(), coordinates.firstZ(), coordinates.secondX(), coordinates.secondZ());
                plugin.pendingChat().remove(uuid);
                if (!preview.isValid()) {
                    plugin.gui().sendAreaErrors(p, preview.errors());
                    plugin.gui().openAreas(p, 0);
                    return;
                }
                plugin.gui().openAreaChangeConfirm(p, preview);
            }
        }
    }

    private static boolean hasNameError(List<AreaValidationError> errors) {
        for (AreaValidationError error : errors) {
            if (error.code() == AreaValidationError.Code.NAME_INVALID
                    || error.code() == AreaValidationError.Code.NAME_ALREADY_USED) return true;
        }
        return false;
    }
}
