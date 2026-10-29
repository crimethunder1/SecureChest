package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.gui.ChestSecureHolder;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockStats;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.recipe.RecipeChangeResult;
import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GuiListener implements Listener {

    static final Set<String> ADMIN_ONLY_ACTIONS = Set.of(
            "open_admin", "back_admin",
            "give_admin_key", "give_logs_book", "give_admin_report", "give_chest_key",
            "toggle_inspect", "global_unlock",
            "open_logs", "logs_page", "logs_cycle_action", "logs_cycle_time",
            "logs_set_actor", "log_entry",
            "open_areas", "areas_page", "area_toggle", "area_create", "area_entry",
            "open_recipes", "back_recipes", "recipe_entry", "recipe_cell", "recipe_toggle",
            "recipe_save", "recipe_reset", "recipe_amount",
            "toggle_owner_name_display", "toggle_owner_name_hide_when_covered", "toggle_chest_key_consumption",
            "admin_lock_remove", "admin_lock_transfer", "admin_trust_add", "admin_trust_entry",
            "admin_lock_close"
    );

    private final ChestSecurePlugin plugin;
    private final Predicate<Material> itemMaterial;

    public GuiListener(ChestSecurePlugin plugin) {
        this(plugin, Material::isItem);
    }

    GuiListener(ChestSecurePlugin plugin, Predicate<Material> itemMaterial) {
        this.plugin = plugin;
        this.itemMaterial = itemMaterial;
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
        if (!(topInventory.getHolder() instanceof ChestSecureHolder holder)) return;

        event.setCancelled(true);

        if (holder.id() == ChestSecureHolder.GuiId.RECIPE_EDIT
                && event.getClickedInventory() != null
                && event.getClickedInventory() == event.getView().getBottomInventory()) {
            selectRecipeMaterial(player, holder, event.getClick(), event.getCurrentItem());
            return;
        }

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
            case "open_recipe_key"   -> plugin.gui().openRecipePreview(player);

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
            case "toggle_show_owner_name" -> toggleShowOwnerName(player);

            case "confirm_unlock_all_self" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                LockStats playerStats = plugin.locks().getLockStats(player.getUniqueId());
                if (playerStats.totalLocks() == 0 && playerStats.totalSecureHoppers() == 0) {
                    player.sendMessage(Component.text(
                            plugin.msg().get("gui-unlock-all-nothing"),
                            NamedTextColor.YELLOW));
                    plugin.gui().openSettings(player);
                    return;
                }
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNLOCK_ALL_SELF, player.getUniqueId().toString(), "SETTINGS", expiresAt));
                List<String> confirmLines = new ArrayList<>();
                confirmLines.add(plugin.msg().get("confirm-unlock-all-header"));
                confirmLines.add("");
                confirmLines.addAll(plugin.msg().getList("confirm-unlock-all-counts",
                        playerStats.totalLocks(),
                        playerStats.totalSecureHoppers(),
                        playerStats.totalTrustedRelations()));
                confirmLines.add("");
                confirmLines.add(plugin.msg().get("confirm-undo-available", plugin.cfg().undoSeconds()));
                plugin.gui().openConfirm(player, plugin.msg().get("confirm-unlock-all-title"), confirmLines);
            }

            case "confirm_undo_restore" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                int undoCount  = plugin.undos().count(player.getUniqueId());
                if (undoCount == 0) {
                    player.sendMessage(Component.text(
                            plugin.msg().get("gui-undo-nothing"),
                            NamedTextColor.YELLOW));
                    plugin.gui().openMain(player);
                    return;
                }
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNDO_RESTORE, "", "MAIN", expiresAt));
                plugin.gui().openConfirm(player, plugin.msg().get("confirm-undo-title"), List.of(
                        plugin.msg().get("confirm-undo-question"),
                        "",
                        plugin.msg().get("confirm-undo-snapshots", undoCount)
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

            case "ping_lock" -> pingLock(player, actionContext);

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
                        plugin.msg().get("confirm-unlock-one-question"),
                        "",
                        plugin.msg().get("confirm-unlock-one-block", blockName)));
                if (trustCount > 0) {
                    confirmLines.add(trustCount == 1
                            ? plugin.msg().get("confirm-unlock-one-trust-single", trustCount)
                            : plugin.msg().get("confirm-unlock-one-trust-multiple", trustCount));
                }
                confirmLines.add("");
                confirmLines.add(plugin.msg().get("confirm-undo-available", plugin.cfg().undoSeconds()));
                plugin.gui().openConfirm(player, plugin.msg().get("confirm-unlock-one-title"), confirmLines);
            }

            case "confirm_remove_trust" -> {
                int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
                long expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L;
                String lockId = actionContext.contains("|") ? actionContext.split("\\|", 2)[0] : "";
                plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                        ConfirmType.REMOVE_TRUST, actionContext,
                        "LOCK_MANAGE|" + lockId, expiresAt));
                plugin.gui().openConfirm(player, plugin.msg().get("confirm-remove-trust-title"), List.of(
                        plugin.msg().get("confirm-remove-trust-question"),
                        "",
                        plugin.msg().get("confirm-remove-trust-warning")
                ));
            }

            case "open_trust_nearby" -> plugin.gui().openTrustNearby(player, actionContext);

            case "add_trust_name" -> {
                String[] contextParts = actionContext.split("\\|", 2);
                if (contextParts.length != 2) return;
                String trustLevel  = contextParts[0];
                String lockId      = contextParts[1];
                String roleLabel = switch (trustLevel) {
                    case "VIEW"     -> plugin.msg().get("gui-role-viewer");
                    case "STOCKER"  -> plugin.msg().get("gui-role-stocker");
                    default         -> trustLevel;
                };
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.AddTrust(lockId, trustLevel));
                player.sendMessage(Component.text(
                        plugin.msg().get("chat-prompt-add-trust", roleLabel),
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

            case "set_trust_role" -> setTrustRole(player, actionContext);

            case "transfer_lock_name" -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(),
                        new de.SecureChest.plugin.PendingChatAction.TransferLock(actionContext));
                player.sendMessage(Component.text(
                        plugin.msg().get("chat-prompt-transfer"),
                        NamedTextColor.YELLOW));
            }

            case "hoppers_page"      -> plugin.gui().openHoppersList(player, parseIntSafe(actionContext));
            case "open_admin", "back_admin" -> plugin.gui().openAdmin(player);

            case "give_admin_key" -> {
                player.getInventory().addItem(plugin.items().adminKey());
                player.sendMessage(Component.text(plugin.msg().get("admin-key-erhalten"), NamedTextColor.GREEN));
                plugin.gui().openAdmin(player);
            }

            case "give_chest_key" -> startChestKeyAmountInput(player);

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

            case "toggle_owner_name_display" -> toggleOwnerNameDisplay(player);
            case "toggle_owner_name_hide_when_covered" -> toggleOwnerNameHideWhenCovered(player);
            case "toggle_chest_key_consumption" -> toggleChestKeyConsumption(player);

            case "global_unlock" -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.GlobalUnlock());
                player.sendMessage(Component.text(
                        plugin.msg().get("chat-prompt-global-unlock"),
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
                        plugin.msg().get("chat-prompt-log-filter"),
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
                    plugin.gui().openConfirm(player, plugin.msg().get("confirm-teleport-title"), List.of(
                            plugin.msg().get("confirm-teleport-question"),
                            plugin.msg().get("confirm-teleport-location",
                                    logRecord.worldName(), logRecord.x(), logRecord.y(), logRecord.z()),
                            "",
                            plugin.msg().get("confirm-teleport-permission", Permissions.ADMIN_TELEPORT)
                    ));
                    return;
                }

                pingLogEntry(player, logRecord);
            }

            case "open_areas"  -> plugin.gui().openAreas(player, 0);
            case "areas_page"  -> plugin.gui().openAreas(player, parseIntSafe(actionContext));

            case "area_toggle" -> {
                if (plugin.areas().isEnabled()) {
                    plugin.gui().sendAreaResult(player, plugin.areas().apply(new AreaChange.Disable()));
                    plugin.gui().openAreas(player, 0);
                    return;
                }
                openAreaChangeConfirm(player, plugin.areas().previewEnable());
            }

            case "area_create" -> {
                player.closeInventory();
                plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.AreaCreateName());
                plugin.gui().promptAreaName(player);
            }

            case "area_entry" -> {
                Optional<Area> areaOptional = plugin.areas().find(actionContext);
                if (areaOptional.isEmpty()) {
                    plugin.gui().sendAreaErrors(player, List.of(new AreaValidationError(
                            AreaValidationError.Code.AREA_NOT_FOUND, actionContext)));
                    plugin.gui().openAreas(player, 0);
                    return;
                }
                Area area = areaOptional.get();
                if (event.getClick().isLeftClick()) {
                    player.closeInventory();
                    plugin.pendingChat().put(player.getUniqueId(),
                            new PendingChatAction.AreaResizeCoordinates(area.name()));
                    plugin.gui().promptAreaResizeCoordinates(player, area);
                } else if (event.getClick().isRightClick()) {
                    openAreaChangeConfirm(player, plugin.areas().previewDelete(area.name()));
                }
            }

            case "open_recipes", "back_recipes" -> {
                plugin.ui().clearRecipeEditor(player.getUniqueId());
                plugin.gui().openRecipes(player);
            }

            case "recipe_entry" -> {
                RecipeKind kind = RecipeKind.fromConfigKey(actionContext);
                if (kind == null) {
                    player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
                    plugin.gui().openRecipes(player);
                    return;
                }
                plugin.gui().openRecipeEditor(player, kind);
            }

            case "recipe_cell"   -> editRecipeCell(player, editorKind(holder), parseIntSafe(actionContext),
                    event.getClick());
            case "recipe_toggle" -> {
                RecipeEditorModel model = recipeEditor(player, editorKind(holder));
                if (model == null) return;
                model.toggleEnabled();
                plugin.gui().refreshRecipeEditor(player);
            }
            case "recipe_amount" -> changeRecipeAmount(player, editorKind(holder), event.getClick());
            case "recipe_save"   -> saveRecipe(player, editorKind(holder));
            case "recipe_reset"  -> requestRecipeReset(player, editorKind(holder));

            case "admin_lock_remove"   -> requestAdminUnlock(player, actionContext);
            case "admin_lock_transfer" -> startAdminTransferInput(player, actionContext);
            case "admin_trust_add"     -> startAdminAddTrustInput(player, actionContext);
            case "admin_trust_entry"   -> handleAdminTrustEntry(player, actionContext, event.getClick());
            case "admin_lock_close"    -> player.closeInventory();

            case "confirm_yes" -> handleConfirmAction(player, true);
            case "confirm_no"  -> handleConfirmAction(player, false);

            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChestSecureHolder holder
                && holder.id() == ChestSecureHolder.GuiId.RECIPE_EDIT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ChestSecureHolder holder)) return;

        if (holder.id() == ChestSecureHolder.GuiId.RECIPE_EDIT) {
            plugin.ui().clearRecipeEditor(player.getUniqueId());
        }

        if (plugin.pendingChat().containsKey(player.getUniqueId())) {
            plugin.pendingChat().remove(player.getUniqueId());
            player.sendMessage(Component.text(plugin.msg().get("chat-abgebrochen"),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    void handleConfirmAction(Player player, boolean confirmed) {
        var confirmRequestOptional = plugin.confirms().get(player.getUniqueId());
        if (confirmRequestOptional.isEmpty()) {
            player.sendMessage(Component.text(
                    plugin.msg().get("gui-confirm-expired"), NamedTextColor.YELLOW));
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
            case UNLOCK_ONE      -> !isForeignLock(player, confirmRequest.data())
                    && plugin.locks().unlockByIdWithUndo(player, confirmRequest.data());
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
                    yield !isForeignLock(player, parts[0])
                            && plugin.locks().removeTrustById(player, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case TRANSFER_LOCK -> {
                String[] parts = confirmRequest.data().split("\\|", 2);
                if (parts.length != 2) yield false;
                try {
                    yield !isForeignLock(player, parts[0])
                            && plugin.locks().transferLock(player, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case UNDO_RESTORE  -> plugin.locks().restoreUndo(player);
            case LOG_TELEPORT  -> handleLogTeleport(player, confirmRequest.data());
            case AREA_CHANGE   -> applyConfirmedAreaChange(player, confirmRequest.areaChange());
            case RECIPE_RESET  -> applyConfirmedRecipeReset(player, confirmRequest.data());
            case ADMIN_UNLOCK        -> applyAdminUnlock(player, confirmRequest.data());
            case ADMIN_TRANSFER_LOCK -> applyAdminTransfer(player, confirmRequest.data());
            case ADMIN_REMOVE_TRUST  -> applyAdminRemoveTrust(player, confirmRequest.data());
        };

        if (confirmRequest.type() != ConfirmType.AREA_CHANGE && confirmRequest.type() != ConfirmType.RECIPE_RESET) {
            player.sendMessage(actionSuccessful
                    ? Component.text(plugin.msg().get("gui-erledigt"), NamedTextColor.GREEN)
                    : Component.text(plugin.msg().get("gui-aktion-fehlgeschlagen"), NamedTextColor.RED));
        }
        routeToDestination(player, confirmRequest.returnTo());
    }

    void setTrustRole(Player player, String actionContext) {
        String[] contextParts = actionContext.split("\\|", 3);
        if (contextParts.length != 3) return;
        String trustLevel  = contextParts[0];
        String lockId      = contextParts[1];
        try {
            UUID targetPlayerUuid = UUID.fromString(contextParts[2]);
            var accessLevel = de.SecureChest.plugin.lock.AccessLevel.fromString(trustLevel);
            boolean actionSuccessful = accessLevel != null
                    && !isForeignLock(player, lockId)
                    && plugin.locks().setTrustById(player, lockId, targetPlayerUuid, accessLevel);
            player.sendMessage(actionSuccessful
                    ? Component.text(plugin.msg().get("gui-vertrauen-aktualisiert"), NamedTextColor.GREEN)
                    : Component.text(plugin.msg().get("trust-vergeben-fehlgeschlagen"),
                    NamedTextColor.RED));
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
        }
        plugin.gui().openLockManage(player, lockId);
    }

    private boolean isForeignLock(Player player, String lockId) {
        return LockUtil.isForeignLock(plugin.locks(), lockId, player.getUniqueId());
    }

    void requestAdminUnlock(Player player, String lockId) {
        if (!requireAdmin(player)) return;
        LockEntry lockEntry = findLockOrClose(player, lockId);
        if (lockEntry == null) return;
        int trustCount = lockEntry.trusted().size();
        plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                ConfirmType.ADMIN_UNLOCK, lockId, "ADMIN_LOCK_MANAGE|" + lockId, confirmExpiresAt()));
        List<String> confirmLines = new ArrayList<>(List.of(
                plugin.msg().get("confirm-admin-unlock-question", plugin.names().resolve(lockEntry.owner())),
                "",
                plugin.msg().get("confirm-admin-unlock-block", blockTypeName(lockEntry), locationText(lockEntry.key()))));
        if (trustCount > 0) {
            confirmLines.add(trustCount == 1
                    ? plugin.msg().get("confirm-unlock-one-trust-single", trustCount)
                    : plugin.msg().get("confirm-unlock-one-trust-multiple", trustCount));
        }
        confirmLines.add("");
        confirmLines.add(plugin.msg().get("confirm-undo-available", plugin.cfg().undoSeconds()));
        if (!lockEntry.owner().equals(player.getUniqueId())) {
            confirmLines.add(plugin.msg().get("confirm-admin-owner-notified"));
        }
        plugin.gui().openConfirm(player, plugin.msg().get("confirm-admin-unlock-title"), confirmLines);
    }

    void startAdminTransferInput(Player player, String lockId) {
        if (!requireAdmin(player)) return;
        if (findLockOrClose(player, lockId) == null) return;
        player.closeInventory();
        plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.AdminTransferLock(lockId));
        player.sendMessage(Component.text(plugin.msg().get("chat-prompt-transfer"), NamedTextColor.YELLOW));
    }

    void startAdminAddTrustInput(Player player, String actionContext) {
        if (!requireAdmin(player)) return;
        String[] contextParts = actionContext.split("\\|", 2);
        AccessLevel level = contextParts.length == 2 ? AccessLevel.fromString(contextParts[0]) : null;
        if (level == null) {
            player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
            return;
        }
        String lockId = contextParts[1];
        if (findLockOrClose(player, lockId) == null) return;
        player.closeInventory();
        plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.AdminAddTrust(lockId, level.name()));
        player.sendMessage(Component.text(
                plugin.msg().get("chat-prompt-add-trust", accessLevelLabel(level)), NamedTextColor.YELLOW));
    }

    void handleAdminTrustEntry(Player player, String actionContext, ClickType click) {
        if (!requireAdmin(player)) return;
        String[] contextParts = actionContext.split("\\|", 2);
        if (contextParts.length != 2) return;
        String lockId = contextParts[0];
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(contextParts[1]);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(Component.text(plugin.msg().get("ungueltige-daten"), NamedTextColor.RED));
            return;
        }
        LockEntry lockEntry = findLockOrClose(player, lockId);
        if (lockEntry == null) return;

        if (click.isRightClick()) {
            plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                    ConfirmType.ADMIN_REMOVE_TRUST, lockId + "|" + targetUuid,
                    "ADMIN_LOCK_MANAGE|" + lockId, confirmExpiresAt()));
            plugin.gui().openConfirm(player, plugin.msg().get("confirm-admin-remove-trust-title"), List.of(
                    plugin.msg().get("confirm-admin-remove-trust-question",
                            plugin.names().resolve(targetUuid), plugin.names().resolve(lockEntry.owner())),
                    "",
                    plugin.msg().get("confirm-remove-trust-warning")));
            return;
        }
        if (!click.isLeftClick()) return;

        AccessLevel currentLevel = lockEntry.trusted().get(targetUuid);
        boolean actionSuccessful = currentLevel != null
                && plugin.locks().setTrustById(player, lockId, targetUuid, toggledAccessLevel(currentLevel));
        player.sendMessage(actionSuccessful
                ? Component.text(plugin.msg().get("gui-vertrauen-aktualisiert"), NamedTextColor.GREEN)
                : Component.text(plugin.msg().get("gui-aktion-fehlgeschlagen"), NamedTextColor.RED));
        plugin.gui().openAdminLockManage(player, lockId);
    }

    private boolean applyAdminUnlock(Player player, String lockId) {
        if (!player.hasPermission(Permissions.ADMIN)) return false;
        LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
        if (lockEntry == null) return false;
        if (!plugin.locks().unlockByIdWithUndo(player, lockId)) return false;
        notifyOwnerAboutAdminChange(player, lockEntry.owner(), plugin.msg().get("notification-admin-lock-removed",
                blockTypeName(lockEntry), locationText(lockEntry.key())));
        return true;
    }

    private boolean applyAdminTransfer(Player player, String data) {
        if (!player.hasPermission(Permissions.ADMIN)) return false;
        String[] parts = data.split("\\|", 2);
        if (parts.length != 2) return false;
        UUID newOwner;
        try {
            newOwner = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        LockEntry lockEntry = plugin.locks().getLockFromId(parts[0]).orElse(null);
        if (lockEntry == null || lockEntry.owner().equals(newOwner)) return false;
        if (!plugin.locks().transferLock(player, parts[0], newOwner)) return false;
        notifyOwnerAboutAdminChange(player, lockEntry.owner(), plugin.msg().get("notification-admin-lock-transferred",
                blockTypeName(lockEntry), locationText(lockEntry.key()), plugin.names().resolve(newOwner)));
        return true;
    }

    private boolean applyAdminRemoveTrust(Player player, String data) {
        if (!player.hasPermission(Permissions.ADMIN)) return false;
        String[] parts = data.split("\\|", 2);
        if (parts.length != 2) return false;
        try {
            return plugin.locks().removeTrustById(player, parts[0], UUID.fromString(parts[1]));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void notifyOwnerAboutAdminChange(Player admin, UUID owner, String message) {
        if (owner.equals(admin.getUniqueId())) return;
        Player onlineOwner = Bukkit.getPlayer(owner);
        if (onlineOwner != null && onlineOwner.isOnline()) {
            onlineOwner.sendMessage(Component.text(message, NamedTextColor.RED));
        } else {
            plugin.locks().addAlwaysDeliveredNotification(owner, message);
        }
    }

    private LockEntry findLockOrClose(Player player, String lockId) {
        LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
        if (lockEntry == null) {
            player.sendMessage(Component.text(plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
            player.closeInventory();
        }
        return lockEntry;
    }

    private long confirmExpiresAt() {
        return System.currentTimeMillis() + plugin.cfg().confirmTimeoutSeconds() * 1000L;
    }

    private static String blockTypeName(LockEntry lockEntry) {
        return lockEntry.blockType().name().replace('_', ' ').toLowerCase();
    }

    private static String locationText(LockKey key) {
        World world = Bukkit.getWorld(key.worldUuid());
        String worldName = world != null ? world.getName() : key.worldUuid().toString();
        return worldName + " " + key.x() + " " + key.y() + " " + key.z();
    }

    private static AccessLevel toggledAccessLevel(AccessLevel level) {
        return level == AccessLevel.VIEW ? AccessLevel.STOCKER : AccessLevel.VIEW;
    }

    private String accessLevelLabel(AccessLevel level) {
        return switch (level) {
            case VIEW    -> plugin.msg().get("gui-role-viewer");
            case STOCKER -> plugin.msg().get("gui-role-stocker");
        };
    }

    void pingLock(Player player, String lockId) {
        LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
        if (lockEntry == null) {
            player.sendMessage(Component.text(plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
            plugin.gui().openLocksList(player, 0);
            return;
        }
        if (plugin.ping().isPinging(player.getUniqueId(), lockEntry.key())) {
            plugin.ping().stopPing(player);
            player.sendMessage(Component.text(plugin.msg().get("ping-stopped"), NamedTextColor.AQUA));
            plugin.gui().openLockManage(player, lockId);
            return;
        }
        World targetWorld = Bukkit.getWorld(lockEntry.key().worldUuid());
        if (targetWorld == null) {
            player.sendMessage(Component.text(
                    plugin.msg().get("gui-ping-world-not-loaded"), NamedTextColor.RED));
            plugin.gui().openLockManage(player, lockId);
            return;
        }
        plugin.ping().startPing(player, lockEntry.key());
        player.sendMessage(Component.text(
                plugin.msg().get("gui-ping-started"), NamedTextColor.AQUA));
        player.closeInventory();
    }

    void pingLogEntry(Player player, LogRecord logRecord) {
        if (logRecord.worldUuid() != null) {
            LockKey target = new LockKey(logRecord.worldUuid(), logRecord.x(), logRecord.y(), logRecord.z());
            if (plugin.ping().isPinging(player.getUniqueId(), target)) {
                plugin.ping().stopPing(player);
                player.sendMessage(Component.text(plugin.msg().get("ping-stopped"), NamedTextColor.AQUA));
                plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
                return;
            }
            if (Bukkit.getWorld(logRecord.worldUuid()) != null) {
                plugin.ping().startPing(player, target);
                player.sendMessage(Component.text(
                        plugin.msg().get("gui-ping-started-log"),
                        NamedTextColor.AQUA));
                player.closeInventory();
                return;
            }
        }
        player.sendMessage(Component.text(
                plugin.msg().get("gui-log-world-not-loaded"), NamedTextColor.RED));
        plugin.gui().openLogs(player, plugin.ui().logs(player.getUniqueId()).page);
    }

    void startChestKeyAmountInput(Player player) {
        if (!requireAdmin(player)) return;
        player.closeInventory();
        plugin.pendingChat().put(player.getUniqueId(), new PendingChatAction.ChestKeyAmount());
        plugin.gui().promptChestKeyAmount(player);
    }

    void toggleOwnerNameDisplay(Player player) {
        if (!requireAdmin(player)) return;
        OwnerNameDisplaySetting.ChangeResult result =
                plugin.ownerNameDisplay().set(!plugin.ownerNameDisplay().isEnabled());
        if (result.enabled()) {
            plugin.signs().spawnAllSigns();
        } else {
            plugin.signs().removeAllSigns();
        }
        player.sendMessage(Component.text(plugin.msg().get(result.enabled()
                ? "namensanzeige-eingeschaltet"
                : "namensanzeige-ausgeschaltet"), NamedTextColor.GREEN));
        if (!result.isSaved()) {
            player.sendMessage(Component.text(
                    plugin.msg().get("namensanzeige-speichern-fehlgeschlagen", result.saveError()),
                    NamedTextColor.RED));
        }
        plugin.gui().openAdmin(player);
    }

    void toggleOwnerNameHideWhenCovered(Player player) {
        if (!requireAdmin(player)) return;
        OwnerNameDisplaySetting.ChangeResult result = plugin.ownerNameDisplay()
                .setHideWhenCovered(!plugin.ownerNameDisplay().isHideWhenCoveredEnabled());
        plugin.signs().refreshAllGroups();
        player.sendMessage(Component.text(plugin.msg().get(result.enabled()
                ? "verdeckte-namen-verstecken-eingeschaltet"
                : "verdeckte-namen-verstecken-ausgeschaltet"), NamedTextColor.GREEN));
        if (!result.isSaved()) {
            player.sendMessage(Component.text(
                    plugin.msg().get("namensanzeige-speichern-fehlgeschlagen", result.saveError()),
                    NamedTextColor.RED));
        }
        plugin.gui().openAdmin(player);
    }

    void toggleChestKeyConsumption(Player player) {
        if (!requireAdmin(player)) return;
        ChestKeyConsumptionSetting.ChangeResult result =
                plugin.chestKeyConsumption().set(!plugin.chestKeyConsumption().isEnabled());
        player.sendMessage(Component.text(plugin.msg().get(result.enabled()
                ? "chest-key-verbrauch-eingeschaltet"
                : "chest-key-verbrauch-ausgeschaltet"), NamedTextColor.GREEN));
        if (!result.isSaved()) {
            player.sendMessage(Component.text(
                    plugin.msg().get("namensanzeige-speichern-fehlgeschlagen", result.saveError()),
                    NamedTextColor.RED));
        }
        plugin.gui().openAdmin(player);
    }

    void toggleShowOwnerName(Player player) {
        boolean newShowOwnerNameState = !plugin.locks().getShowOwnerName(player.getUniqueId());
        plugin.locks().setShowOwnerName(player.getUniqueId(), newShowOwnerNameState);
        plugin.gui().openSettings(player);
    }

    private boolean applyConfirmedAreaChange(Player player, AreaChange change) {
        if (change == null || !player.hasPermission(Permissions.ADMIN)) return false;
        AreaChangeResult result = plugin.areas().apply(change);
        plugin.gui().sendAreaResult(player, result);
        return result.applied();
    }

    private void openAreaChangeConfirm(Player player, AreaChangePreview preview) {
        if (!preview.isValid()) {
            plugin.gui().sendAreaErrors(player, preview.errors());
            plugin.gui().openAreas(player, 0);
            return;
        }
        plugin.gui().openAreaChangeConfirm(player, preview);
    }

    void selectRecipeMaterial(Player player, ChestSecureHolder holder, ClickType click, ItemStack item) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        if (item == null || RecipeEditorModel.isAir(item.getType())) return;
        if (!requireAdmin(player)) return;
        RecipeEditorModel model = recipeEditor(player, editorKind(holder));
        if (model == null) return;
        if (!model.select(item.getType(), itemMaterial)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("rezept-auswahl-ungueltig", item.getType().name()), NamedTextColor.RED));
            return;
        }
        plugin.gui().refreshRecipeEditor(player);
    }

    void editRecipeCell(Player player, RecipeKind kind, int index, ClickType click) {
        RecipeEditorModel model = recipeEditor(player, kind);
        if (model == null || index < 0 || index >= RecipeEditorModel.CELL_COUNT) return;
        switch (click) {
            case LEFT -> {
                if (!model.placeSelection(index)) {
                    player.sendMessage(Component.text(
                            plugin.msg().get("rezept-keine-auswahl"), NamedTextColor.YELLOW));
                    return;
                }
            }
            case RIGHT -> model.clearCell(index);
            default -> {
                return;
            }
        }
        plugin.gui().refreshRecipeEditor(player);
    }

    void changeRecipeAmount(Player player, RecipeKind kind, ClickType click) {
        int change = switch (click) {
            case LEFT        -> 1;
            case RIGHT       -> -1;
            case SHIFT_LEFT  -> RecipeEditorModel.LARGE_AMOUNT_STEP;
            case SHIFT_RIGHT -> -RecipeEditorModel.LARGE_AMOUNT_STEP;
            default          -> 0;
        };
        if (change == 0) return;
        RecipeEditorModel model = recipeEditor(player, kind);
        if (model == null) return;
        model.changeAmount(change);
        plugin.gui().refreshRecipeEditor(player);
    }

    void saveRecipe(Player player, RecipeKind kind) {
        if (!requireAdmin(player)) return;
        RecipeEditorModel model = recipeEditor(player, kind);
        if (model == null) return;
        RecipeChangeResult result = plugin.recipes().apply(kind, model.toDefinition());
        plugin.gui().sendRecipeSaveResult(player, result);
        if (!result.applied()) return;
        plugin.ui().clearRecipeEditor(player.getUniqueId());
        plugin.gui().openRecipes(player);
    }

    void requestRecipeReset(Player player, RecipeKind kind) {
        if (kind == null) return;
        if (!plugin.recipes().isOverridden(kind)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("rezept-bereits-standard", kind.displayName()), NamedTextColor.YELLOW));
            return;
        }
        plugin.gui().openRecipeResetConfirm(player, kind);
    }

    private boolean applyConfirmedRecipeReset(Player player, String configKey) {
        RecipeKind kind = RecipeKind.fromConfigKey(configKey);
        if (kind == null || !player.hasPermission(Permissions.ADMIN)) return false;
        RecipeChangeResult result = plugin.recipes().resetToDefault(kind);
        plugin.gui().sendRecipeResetResult(player, result);
        return result.applied();
    }

    private RecipeEditorModel recipeEditor(Player player, RecipeKind kind) {
        RecipeEditorModel model = kind == null ? null : plugin.ui().recipeEditor(player.getUniqueId(), kind);
        if (model == null) {
            player.sendMessage(Component.text(plugin.msg().get("rezept-arbeitskopie-fehlt"), NamedTextColor.RED));
            plugin.gui().openRecipes(player);
        }
        return model;
    }

    private static RecipeKind editorKind(ChestSecureHolder holder) {
        if (holder.id() != ChestSecureHolder.GuiId.RECIPE_EDIT) return null;
        return RecipeKind.fromConfigKey(holder.context());
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
        if (destination.equals("AREAS")) {
            plugin.gui().openAreas(player, 0);
            return;
        }
        if (destination.equals("RECIPES")) {
            plugin.gui().openRecipes(player);
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
        if (destination.startsWith("ADMIN_LOCK_MANAGE|")) {
            String lockId = destination.substring("ADMIN_LOCK_MANAGE|".length());
            if (plugin.locks().getLockFromId(lockId).isPresent()) {
                plugin.gui().openAdminLockManage(player, lockId);
            } else {
                player.closeInventory();
            }
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
            org.bukkit.block.Block hopperBindTarget =
                    LockUtil.chestKeyHopperBindTarget(plugin.locks(), targetBlock, player.getUniqueId());
            if (hopperBindTarget != null) return bindHopperFromConfirmation(player, targetBlock, hopperBindTarget);
            ChestKeyPayment payment = takeChestKeyForConfirmation(player);
            if (payment == null) return false;
            return payment.spendOn(() -> plugin.locks().lock(player, targetBlock));
        } catch (Exception ignored) {
            return false;
        }
    }

    private ChestKeyPayment takeChestKeyForConfirmation(Player player) {
        ChestKeyPayment payment = ChestKeyPayment.take(plugin, player);
        if (payment == null) {
            player.sendMessage(Component.text(plugin.msg().get("sperren-key-erforderlich"), NamedTextColor.RED));
        }
        return payment;
    }

    private boolean bindHopperFromConfirmation(Player player, org.bukkit.block.Block hopperBlock,
                                               org.bukkit.block.Block targetBlock) {
        if (!plugin.areas().isLockPlacementAllowed(hopperBlock) || !plugin.areas().isLockPlacementAllowed(targetBlock)) {
            player.sendMessage(Component.text(plugin.msg().get("wirkungsbereich-hier-nicht-aktiv"), NamedTextColor.RED));
            return false;
        }
        ChestKeyPayment payment = takeChestKeyForConfirmation(player);
        if (payment == null) return false;
        if (!payment.spendOn(() -> plugin.locks().bindSecureHopper(player, hopperBlock, targetBlock))) {
            player.sendMessage(Component.text(plugin.msg().get("hopper-bind-fehlgeschlagen"), NamedTextColor.RED));
            return false;
        }
        player.sendMessage(Component.text(plugin.msg().get("hopper-gebunden"), NamedTextColor.GREEN));
        return true;
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
