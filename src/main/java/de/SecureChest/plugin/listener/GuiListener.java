package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockStats;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GuiListener implements Listener {

    static final Set<String> ADMIN_ONLY_ACTIONS = Set.of(
            "open_admin", "back_admin",
            "give_admin_key", "give_logs_book", "give_admin_report",
            "toggle_inspect", "global_unlock",
            "open_logs", "logs_page", "logs_cycle_action", "logs_cycle_time",
            "logs_set_actor", "log_entry"
    );

    private final ChestSecurePlugin plugin;

    public GuiListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isAdminOnlyAction(String action) {
        return ADMIN_ONLY_ACTIONS.contains(action);
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission(Permissions.ADMIN)) return true;
        player.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
        plugin.gui().openMain(player);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof de.SecureChest.plugin.gui.ChestSecureHolder)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;
        ItemMeta itemMeta = clickedItem.getItemMeta();
        if (itemMeta == null) return;
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();

        String action = persistentDataContainer.get(plugin.keyGuiAction(), PersistentDataType.STRING);
        if (action == null || action.isBlank() || action.equals("noop")) return;
        String actionContext = persistentDataContainer.get(plugin.keyGuiContext(), PersistentDataType.STRING);
        if (actionContext == null) actionContext = "";

        if (isAdminOnlyAction(action) && !requireAdmin(player)) return;

        switch (action) {
            case "back_main"         -> plugin.gui().openMain(player);
            case "open_help"         -> plugin.gui().openHelp(player);
            case "open_settings", "back_settings" -> plugin.gui().openSettings(player);
            case "open_stats"        -> plugin.gui().openStats(player);
            case "open_locks", "back_locks" ->
                    plugin.gui().openLocksList(player, plugin.ui().locks(player.getUniqueId()).page);
            case "open_hoppers"      -> plugin.gui().openHoppersList(player, 0);
            case "open_recipe_key"   -> plugin.gui().openRecipePreview(player, true);
            case "open_recipe_hopper"-> plugin.gui().openRecipePreview(player, false);

            case "toggle_quicklock" -> {
                boolean newQuickLockState = !plugin.locks().getQuickLock(player.getUniqueId());
                plugin.locks().setQuickLock(player.getUniqueId(), newQuickLockState);
                plugin.gui().openSettings(player);
            }
            case "toggle_quickremove" -> {
                boolean newQuickRemoveState = !plugin.locks().getQuickRemove(player.getUniqueId());
                plugin.locks().setQuickRemove(player.getUniqueId(), newQuickRemoveState);
                plugin.gui().openSettings(player);
            }

            case "confirm_unlock_all_self" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                LockStats playerStats = plugin.locks().getLockStats(player.getUniqueId());
                if (playerStats.totalLocks() == 0 && playerStats.totalSecureHoppers() == 0) {
                    player.sendMessage(Component.text(
                            "Du hast keine Schlösser oder Secure Hopper zum Entfernen.",
                            NamedTextColor.YELLOW));
                    plugin.gui().openSettings(player);
                    return;
                }
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNLOCK_ALL_SELF, player.getUniqueId().toString(), "SETTINGS", expiresAt));
                plugin.gui().openConfirm(player, "Alle Schlösser entfernen", List.of(
                        "§7Folgendes wird unwiderruflich entfernt:",
                        "",
                        "§c " + playerStats.totalLocks() + " §7Schlösser",
                        "§c " + playerStats.totalSecureHoppers() + " §7Secure Hopper",
                        "§c " + playerStats.totalTrustedRelations() + " §7Vertrauens-Einträge",
                        "",
                        "§aEin Undo ist " + plugin.cfg().undoSeconds() + " Sekunden lang verfügbar."
                ));
            }

            case "confirm_undo_restore" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                int undoCount  = plugin.undos().count(player.getUniqueId());
                if (undoCount == 0) {
                    player.sendMessage(Component.text(
                            "Keine Aktion zum Rückgängigmachen verfügbar.",
                            NamedTextColor.YELLOW));
                    plugin.gui().openMain(player);
                    return;
                }
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNDO_RESTORE, "", "MAIN", expiresAt));
                plugin.gui().openConfirm(player, "Rückgängig machen", List.of(
                        "§7Letzte Aktion rückgängig machen?",
                        "",
                        "§7" + undoCount + " Snapshot(s) im Verlauf."
                ));
            }

            case "locks_page"        -> plugin.gui().openLocksList(player, parseIntSafe(actionContext));
            case "locks_cycle_sort"  -> {
                var lockListState = plugin.ui().locks(player.getUniqueId());
                lockListState.sort = lockListState.sort.next();
                plugin.gui().openLocksList(player, lockListState.page);
            }
            case "locks_cycle_world" -> {
                var lockListState = plugin.ui().locks(player.getUniqueId());
                lockListState.world = lockListState.world.next();
                plugin.gui().openLocksList(player, lockListState.page);
            }
            case "locks_cycle_type"  -> {
                var lockListState = plugin.ui().locks(player.getUniqueId());
                lockListState.type = lockListState.type.next();
                plugin.gui().openLocksList(player, lockListState.page);
            }

            case "manage_lock", "back_lock_manage" -> plugin.gui().openLockManage(player, actionContext);

            case "ping_lock" -> {
                var lockEntry = plugin.locks().getLockFromId(actionContext).orElse(null);
                if (lockEntry == null) {
                    player.sendMessage(Component.text(plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
                    plugin.gui().openLocksList(player, 0);
                    return;
                }
                World targetWorld = Bukkit.getWorld(lockEntry.key().worldUuid());
                if (targetWorld == null) {
                    player.sendMessage(Component.text(
                            "Welt ist nicht geladen — Ping nicht möglich.", NamedTextColor.RED));
                    plugin.gui().openLockManage(player, actionContext);
                    return;
                }
                Location pingLocation = new Location(targetWorld,
                        lockEntry.key().x() + 0.5,
                        lockEntry.key().y() + 0.5,
                        lockEntry.key().z() + 0.5);
                plugin.ping().startPing(player, pingLocation);
                player.sendMessage(Component.text(
                        "Ping aktiv! Richtungsanzeige über der Hotbar gestartet.", NamedTextColor.AQUA));
                player.closeInventory();
            }

            case "confirm_unlock_one" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                var lockOptional = plugin.locks().getLockFromId(actionContext);
                if (lockOptional.isEmpty()) {
                    player.sendMessage(Component.text(plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
                    plugin.gui().openLocksList(player, 0);
                    return;
                }
                int trustCount = lockOptional.get().trusted().size();
                String blockName = lockOptional.get().blockType().name().replace('_', ' ').toLowerCase();
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNLOCK_ONE, actionContext,
                        "LOCK_MANAGE|" + actionContext, expiresAt));
                List<String> confirmLines = new ArrayList<>(List.of(
                        "§7Dieses Schloss wirklich entfernen?",
                        "",
                        "§c " + blockName + " §7wird freigegeben"));
                if (trustCount > 0) {
                    confirmLines.add("§c " + trustCount + " §7Vertrauens"
                            + (trustCount == 1 ? "-Eintrag" : "-Einträge")
                            + " gehen verloren");
                }
                confirmLines.add("");
                confirmLines.add("§aEin Undo ist " + plugin.cfg().undoSeconds() + " Sekunden lang verfügbar.");
                plugin.gui().openConfirm(player, "Schloss entfernen", confirmLines);
            }

            case "confirm_remove_trust" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                String lockId = actionContext.contains("|") ? actionContext.split("\\|", 2)[0] : "";
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.REMOVE_TRUST, actionContext,
                        "LOCK_MANAGE|" + lockId, expiresAt));
                plugin.gui().openConfirm(player, "Zugriff entfernen", List.of(
                        "§7Zugriff dieses Spielers wirklich entfernen?",
                        "",
                        "§cDies kann nicht rückgängig gemacht werden."
                ));
            }

            case "open_trust_nearby" -> plugin.gui().openTrustNearby(player, actionContext);

            case "add_trust_name" -> {
                String[] contextParts = actionContext.split("\\|", 2);
                if (contextParts.length != 2) return;
                String trustLevel  = contextParts[0];
                String lockId      = contextParts[1];
                String roleLabel = switch (trustLevel) {
                    case "VIEW"     -> "Viewer";
                    case "STOCKER"  -> "Stocker";
                    default         -> trustLevel;
                };
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.AddTrust(lockId, trustLevel));
                player.sendMessage(Component.text(
                        "Namen des Spielers eingeben, der als " + roleLabel
                                + " hinzugefügt werden soll.  'cancel' zum Abbrechen.",
                        NamedTextColor.YELLOW));
            }

            case "open_trust_role" -> {
                String[] contextParts = actionContext.split("\\|", 2);
                if (contextParts.length != 2) return;
                try {
                    plugin.gui().openTrustRole(player, contextParts[0], UUID.fromString(contextParts[1]));
                } catch (IllegalArgumentException ignored) {
                    player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
                }
            }

            case "back_trust_nearby" -> plugin.gui().openTrustNearby(player, actionContext);

            case "set_trust_role" -> {
                String[] contextParts = actionContext.split("\\|", 3);
                if (contextParts.length != 3) return;
                String trustLevel  = contextParts[0];
                String lockId      = contextParts[1];
                try {
                    UUID targetPlayerUuid = UUID.fromString(contextParts[2]);
                    var accessLevel = de.SecureChest.plugin.lock.AccessLevel.fromString(trustLevel);
                    boolean actionSuccessful = accessLevel != null
                            && plugin.locks().setTrustById(player, lockId, targetPlayerUuid, accessLevel);
                    player.sendMessage(actionSuccessful
                            ? Component.text(plugin.msg().get("gui-vertrauen-aktualisiert"), NamedTextColor.GREEN)
                            : Component.text(plugin.msg().get("gui-transfer-fehlgeschlagen"),
                            NamedTextColor.RED));
                } catch (IllegalArgumentException ignored) {
                    player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
                }
                plugin.gui().openLockManage(player, lockId);
            }

            case "transfer_lock_name" -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(),
                        new de.SecureChest.plugin.PendingChatAction.TransferLock(actionContext));
                player.sendMessage(Component.text(
                        "Namen des Spielers eingeben, an den dieses Schloss übertragen werden soll."
                                + "  'cancel' zum Abbrechen.",
                        NamedTextColor.YELLOW));
            }

            case "hoppers_page"      -> plugin.gui().openHoppersList(player, parseIntSafe(actionContext));
            case "open_admin", "back_admin" -> plugin.gui().openAdmin(player);

            case "give_admin_key" -> {
                player.getInventory().addItem(plugin.items().adminKey());
                player.sendMessage(Component.text(plugin.msg().get("admin-key-erhalten"), NamedTextColor.GREEN));
                plugin.gui().openAdmin(player);
            }

            case "give_logs_book" -> {
                int maxBookEntries = plugin.cfg().adminLogsBookEntries();
                player.getInventory().addItem(plugin.locks().createLogsBook(maxBookEntries));
                player.sendMessage(Component.text(plugin.msg().get("log-buch-erhalten"), NamedTextColor.GREEN));
                plugin.gui().openAdmin(player);
            }

            case "give_admin_report" -> {
                player.getInventory().addItem(plugin.locks().createAdminReportBook());
                player.sendMessage(Component.text(plugin.msg().get("admin-bericht-erhalten"), NamedTextColor.GREEN));
                plugin.gui().openAdmin(player);
            }

            case "toggle_inspect" -> {
                boolean newInspectModeState = !plugin.ui().inspect(player.getUniqueId());
                plugin.ui().setInspect(player.getUniqueId(), newInspectModeState);
                plugin.locks().logInspectToggle(player, newInspectModeState);
                plugin.gui().openAdmin(player);
            }

            case "global_unlock" -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.GlobalUnlock());
                player.sendMessage(Component.text(
                        "Namen des Spielers eingeben, dessen Schlösser entfernt werden sollen."
                                + "  'cancel' zum Abbrechen.",
                        NamedTextColor.YELLOW));
            }

            case "open_logs"         -> plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
            case "logs_page"         -> plugin.gui().openLogs(player, parseIntSafe(actionContext));
            case "logs_cycle_action" -> {
                var logsState = plugin.ui().logs(player.getUniqueId());
                logsState.action = logsState.action.next();
                plugin.gui().openLogs(player, logsState.page);
            }
            case "logs_cycle_time"   -> {
                var logsState = plugin.ui().logs(player.getUniqueId());
                logsState.time = logsState.time.next();
                plugin.gui().openLogs(player, logsState.page);
            }
            case "logs_set_actor"    -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.LogActorFilter());
                player.sendMessage(Component.text(
                        "Spielernamen zum Filtern der Logs eingeben, oder 'clear' zum Zurücksetzen."
                                + "  'cancel' zum Abbrechen.",
                        NamedTextColor.YELLOW));
            }

            case "log_entry" -> {
                long logEntryId = parseLongSafe(actionContext);
                if (logEntryId <= 0) return;

                Optional<LogRecord> logRecordOptional = plugin.locks().getLogById(logEntryId);
                if (logRecordOptional.isEmpty()) {
                    player.sendMessage(Component.text(plugin.msg().get("log-nicht-gefunden"), NamedTextColor.RED));
                    plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
                    return;
                }
                LogRecord logRecord = logRecordOptional.get();
                World targetWorld = Bukkit.getWorld(logRecord.worldUuid());

                if (event.getClick() == ClickType.SHIFT_LEFT
                        || event.getClick() == ClickType.SHIFT_RIGHT) {
                    if (!player.hasPermission(Permissions.ADMIN_TELEPORT)) {
                        player.sendMessage(Component.text(
                                plugin.msg().get("teleport-keine-berechtigung"), NamedTextColor.RED));
                        plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
                        return;
                    }
                    boolean teleportEnabled = plugin.cfg().adminLogsTeleportEnabled();
                    if (!teleportEnabled) {
                        player.sendMessage(Component.text(
                                plugin.msg().get("teleport-deaktiviert"), NamedTextColor.RED));
                        plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
                        return;
                    }
                    int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                    long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                    plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                            ConfirmType.LOG_TELEPORT, String.valueOf(logEntryId), "LOGS", expiresAt));
                    plugin.gui().openConfirm(player, "Teleportieren", List.of(
                            "§7Zu dieser Log-Position teleportieren?",
                            "§f" + logRecord.worldName()
                                    + " " + logRecord.x()
                                    + " " + logRecord.y()
                                    + " " + logRecord.z(),
                            "",
                            "§eErfordert " + Permissions.ADMIN_TELEPORT + "."
                    ));
                    return;
                }

                if (targetWorld != null) {
                    Location targetLocation = new Location(targetWorld,
                            logRecord.x() + 0.5,
                            logRecord.y() + 0.5,
                            logRecord.z() + 0.5);
                    plugin.ping().startPing(player, targetLocation);
                    player.sendMessage(Component.text(
                            "Ping aktiv, Richtungsanzeige über der Hotbar gestartet.",
                            NamedTextColor.AQUA));
                    player.closeInventory();
                    return;
                }
                player.sendMessage(Component.text(
                        "Welt der Log-Position ist nicht geladen.", NamedTextColor.RED));
                plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
            }

            case "confirm_yes" -> handleConfirmAction(player, true);
            case "confirm_no"  -> handleConfirmAction(player, false);

            default -> { }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof de.SecureChest.plugin.gui.ChestSecureHolder)) return;

        if (plugin.pendingChat().containsKey(player.getUniqueId())) {
            plugin.pendingChat().remove(player.getUniqueId());
            player.sendMessage(Component.text(plugin.msg().get("chat-abgebrochen"),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    private void handleConfirmAction(Player player, boolean confirmed) {
        var confirmRequestOptional = plugin.confirms().get(player.getUniqueId());
        if (confirmRequestOptional.isEmpty()) {
            player.sendMessage(Component.text(
                    "Bestätigung abgelaufen — bitte erneut versuchen.", NamedTextColor.YELLOW));
            plugin.gui().openMain(player);
            return;
        }
        ConfirmRequest confirmRequest = confirmRequestOptional.get();
        plugin.confirms().clear(player.getUniqueId());

        if (!confirmed) {
            routeToDestination(player, confirmRequest.returnTo());
            return;
        }

        boolean actionSuccessful = switch (confirmRequest.type()) {
            case LOCK_BLOCK      -> handleLockBlockConfirmation(player, confirmRequest.data());
            case UNLOCK_ONE      -> plugin.locks().unlockByIdWithUndo(player, confirmRequest.data());
            case UNLOCK_ALL_SELF -> plugin.locks().unlockAllWithUndo(player, player.getUniqueId());
            case GLOBAL_UNLOCK   -> {
                try {
                    yield plugin.locks().unlockAllWithUndo(player, UUID.fromString(confirmRequest.data()));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case REMOVE_TRUST -> {
                String[] parts = confirmRequest.data().split("\\|", 2);
                if (parts.length != 2) yield false;
                try {
                    yield plugin.locks().removeTrustById(player, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case TRANSFER_LOCK -> {
                String[] parts = confirmRequest.data().split("\\|", 2);
                if (parts.length != 2) yield false;
                try {
                    yield plugin.locks().transferLock(player, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case UNDO_RESTORE  -> plugin.locks().restoreUndo(player);
            case LOG_TELEPORT  -> handleLogTeleport(player, confirmRequest.data());
        };

        player.sendMessage(actionSuccessful
                ? Component.text(plugin.msg().get("gui-erledigt"), NamedTextColor.GREEN)
                : Component.text(plugin.msg().get("gui-aktion-fehlgeschlagen"), NamedTextColor.RED));
        routeToDestination(player, confirmRequest.returnTo());
    }

    private void routeToDestination(Player player, String destination) {
        if (destination == null || destination.isBlank() || destination.equals("MAIN")) {
            plugin.gui().openMain(player);
            return;
        }
        if (destination.equals("SETTINGS")) {
            plugin.gui().openSettings(player);
            return;
        }
        if (destination.equals("ADMIN")) {
            plugin.gui().openAdmin(player);
            return;
        }
        if (destination.equals("LOGS")) {
            plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
            return;
        }
        if (destination.startsWith("LOCK_MANAGE|")) {
            String lockId = destination.substring("LOCK_MANAGE|".length());
            plugin.gui().openLockManage(player, lockId);
            return;
        }
        if (destination.equals("LOCKS")) {
            plugin.gui().openLocksList(player, plugin.ui().locks(player.getUniqueId()).page);
        }
    }

    private int parseIntSafe(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private long parseLongSafe(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return -1L; }
    }

    private boolean handleLockBlockConfirmation(Player player, String lockKeyId) {
        try {
            de.SecureChest.plugin.lock.LockKey lockKey = de.SecureChest.plugin.lock.LockKey.fromId(lockKeyId);
            org.bukkit.World targetWorld = org.bukkit.Bukkit.getWorld(lockKey.worldUuid());
            if (targetWorld == null) return false;
            org.bukkit.block.Block targetBlock = targetWorld.getBlockAt(lockKey.x(), lockKey.y(), lockKey.z());
            return plugin.locks().lock(player, targetBlock);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean handleLogTeleport(Player player, String logIdString) {
        if (!player.hasPermission(Permissions.ADMIN_TELEPORT)) return false;
        boolean teleportEnabled = plugin.cfg().adminLogsTeleportEnabled();
        if (!teleportEnabled) return false;

        long logId = parseLongSafe(logIdString);
        if (logId <= 0) return false;

        Optional<LogRecord> logRecordOptional = plugin.locks().getLogById(logId);
        if (logRecordOptional.isEmpty()) return false;

        LogRecord logRecord = logRecordOptional.get();
        World targetWorld = Bukkit.getWorld(logRecord.worldUuid());
        if (targetWorld == null) return false;

        Location teleportLocation = new Location(
                targetWorld, logRecord.x() + 0.5, logRecord.y() + 1.0, logRecord.z() + 0.5);
        player.teleportAsync(teleportLocation);
        return true;
    }
}
