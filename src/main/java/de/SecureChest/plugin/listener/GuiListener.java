package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GuiListener implements Listener {

    private final ChestSecurePlugin plugin;

    public GuiListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof de.SecureChest.plugin.gui.ChestSecureHolder)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        var meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String action = pdc.get(plugin.keyGuiAction(), PersistentDataType.STRING);
        if (action == null || action.isBlank() || action.equals("noop")) return;
        String ctx = pdc.get(plugin.keyGuiContext(), PersistentDataType.STRING);
        if (ctx == null) ctx = "";

        switch (action) {
            case "back_main" -> plugin.gui().openMain(p);
            case "open_help" -> plugin.gui().openHelp(p);
            case "open_stats" -> plugin.gui().openStats(p);
            case "open_locks", "back_locks" -> plugin.gui().openLocksList(p, plugin.ui().locks(p.getUniqueId()).page);
            case "open_hoppers" -> plugin.gui().openHoppersList(p, 0);
            case "open_recipe_key" -> plugin.gui().openRecipePreview(p, true);
            case "open_recipe_hopper" -> plugin.gui().openRecipePreview(p, false);

            case "toggle_quicklock" -> {
                boolean now = !plugin.locks().getQuickLock(p.getUniqueId());
                plugin.locks().setQuickLock(p.getUniqueId(), now);
                plugin.gui().openMain(p);
            }
            case "toggle_quickremove" -> {
                boolean now = !plugin.locks().getQuickRemove(p.getUniqueId());
                plugin.locks().setQuickRemove(p.getUniqueId(), now);
                plugin.gui().openMain(p);
            }

            case "confirm_unlock_all_self" -> {
                int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                plugin.confirms().set(p.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNLOCK_ALL_SELF,
                        p.getUniqueId().toString(),
                        "MAIN",
                        expiresAt
                ));
                plugin.gui().openConfirm(p, "Unlock ALL", List.of(
                        "This will unlock all your locked blocks",
                        "and your secure hoppers.",
                        "",
                        "An Undo will be available afterwards."
                ));
            }

            case "confirm_undo_restore" -> {
                int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                int undoCount = plugin.undos().count(p.getUniqueId());
                plugin.confirms().set(p.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNDO_RESTORE,
                        "",
                        "MAIN",
                        expiresAt
                ));
                plugin.gui().openConfirm(p, "Undo", List.of(
                        "Restore the most recent unlock or transfer?",
                        "",
                        undoCount + " snapshot(s) remaining in history.",
                        ""
                ));
            }

            case "locks_page" -> plugin.gui().openLocksList(p, parseInt(ctx));
            case "locks_cycle_sort" -> {
                var st = plugin.ui().locks(p.getUniqueId());
                st.sort = st.sort.next();
                plugin.gui().openLocksList(p, st.page);
            }
            case "locks_cycle_world" -> {
                var st = plugin.ui().locks(p.getUniqueId());
                st.world = st.world.next();
                plugin.gui().openLocksList(p, st.page);
            }
            case "locks_cycle_type" -> {
                var st = plugin.ui().locks(p.getUniqueId());
                st.type = st.type.next();
                plugin.gui().openLocksList(p, st.page);
            }

            case "manage_lock", "back_lock_manage" -> plugin.gui().openLockManage(p, ctx);
            case "ping_lock" -> {
                var le = plugin.locks().getLockFromId(ctx).orElse(null);
                if (le == null) {
                    p.sendMessage(Component.text("Lock not found.", NamedTextColor.RED));
                    plugin.gui().openLocksList(p, 0);
                    return;
                }
                Location loc = new Location(Bukkit.getWorld(le.key().worldUuid()), le.key().x(), le.key().y(), le.key().z());
                if (loc.getWorld() != null) {
                    p.setCompassTarget(loc);
                    p.sendMessage(Component.text("Compass set to lock location.", NamedTextColor.AQUA));
                } else {
                    p.sendMessage(Component.text("World is unloaded - cannot set compass.", NamedTextColor.RED));
                }
                plugin.gui().openLockManage(p, ctx);
            }
            case "copy_lock" -> {
                var le = plugin.locks().getLockFromId(ctx).orElse(null);
                if (le != null) {
                    World w = Bukkit.getWorld(le.key().worldUuid());
                    String worldName = w != null ? w.getName() : le.key().worldUuid().toString();
                    p.sendMessage(Component.text("Lock coords: " + worldName + " " + le.key().x() + " " + le.key().y() + " " + le.key().z(), NamedTextColor.AQUA));
                }
                plugin.gui().openLockManage(p, ctx);
            }
            case "confirm_unlock_one" -> {
                int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                plugin.confirms().set(p.getUniqueId(), new ConfirmRequest(
                        ConfirmType.UNLOCK_ONE,
                        ctx,
                        "LOCK_MANAGE|" + ctx,
                        expiresAt
                ));
                plugin.gui().openConfirm(p, "Unlock", List.of(
                        "Unlock this lock?",
                        "",
                        "An Undo will be available afterwards."
                ));
            }
            case "confirm_remove_trust" -> {
                
                int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
                long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                String lockId = ctx.contains("|") ? ctx.split("\\|", 2)[0] : "";
                plugin.confirms().set(p.getUniqueId(), new ConfirmRequest(
                        ConfirmType.REMOVE_TRUST,
                        ctx,
                        "LOCK_MANAGE|" + lockId,
                        expiresAt
                ));
                plugin.gui().openConfirm(p, "Remove Trust", List.of(
                        "Remove this player's access?",
                        "",
                        "This cannot be undone."
                ));
            }
            case "open_trust_nearby" -> plugin.gui().openTrustNearby(p, ctx);
            case "add_trust_name" -> {
                String[] parts = ctx.split("\\|", 2);
                if (parts.length != 2) return;
                String level  = parts[0];
                String lockId = parts[1];
                // UX2: friendly role label in prompt instead of raw enum name
                String roleLabel = switch (level) {
                    case "VIEW"     -> "Viewer";
                    case "STOCKER"  -> "Stocker";
                    case "CO_OWNER" -> "Co-owner";
                    default         -> level;
                };
                p.closeInventory();
                plugin.pendingChat().put(p.getUniqueId(), new PendingChatAction.AddTrust(lockId, level));
                p.sendMessage(Component.text("Type the name of the player to add as " + roleLabel
                        + ".  Type 'cancel' to abort.", NamedTextColor.YELLOW));
            }

            case "open_trust_role" -> {
                
                String[] parts = ctx.split("\\|", 2);
                if (parts.length != 2) return;
                try {
                    plugin.gui().openTrustRole(p, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    p.sendMessage(Component.text("Invalid data.", NamedTextColor.RED));
                }
            }
            case "back_trust_nearby" -> plugin.gui().openTrustNearby(p, ctx);
            case "set_trust_role" -> {
                
                String[] parts = ctx.split("\\|", 3);
                if (parts.length != 3) return;
                String level = parts[0];
                String lockId = parts[1];
                try {
                    UUID target = UUID.fromString(parts[2]);
                    var lvl = de.SecureChest.plugin.lock.AccessLevel.fromString(level);
                    boolean ok = lvl != null && plugin.locks().setTrustById(p, lockId, target, lvl);
                    p.sendMessage(ok ? Component.text("Trust updated.", NamedTextColor.GREEN) : Component.text("Failed (not owner / lock missing).", NamedTextColor.RED));
                } catch (IllegalArgumentException ignored) {
                    p.sendMessage(Component.text("Invalid data.", NamedTextColor.RED));
                }
                plugin.gui().openLockManage(p, lockId);
            }

            case "transfer_lock_name" -> {
                p.closeInventory();
                plugin.pendingChat().put(p.getUniqueId(), new de.SecureChest.plugin.PendingChatAction.TransferLock(ctx));
                p.sendMessage(Component.text(
                        "Type the name of the player to receive this lock.  Type 'cancel' to abort.",
                        NamedTextColor.YELLOW));
            }

            case "hoppers_page" -> plugin.gui().openHoppersList(p, parseInt(ctx));

            case "open_admin", "back_admin" -> plugin.gui().openAdmin(p);
            case "give_admin_key" -> {
                if (!p.hasPermission("chestsecure.admin")) {
                    p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    plugin.gui().openMain(p);
                    return;
                }
                p.getInventory().addItem(plugin.items().adminKey());
                p.sendMessage(Component.text("Admin Key given.", NamedTextColor.GREEN));
                plugin.gui().openAdmin(p);
            }
            case "give_logs_book" -> {
                if (!p.hasPermission("chestsecure.admin")) {
                    p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    plugin.gui().openMain(p);
                    return;
                }
                int max = plugin.getConfig().getInt("admin.logs.book_entries", plugin.getConfig().getInt("admin.logs_book_entries", 200));
                p.getInventory().addItem(plugin.locks().createLogsBook(max));
                p.sendMessage(Component.text("Logs book given.", NamedTextColor.GREEN));
                plugin.gui().openAdmin(p);
            }
            case "give_admin_report" -> {
                if (!p.hasPermission("chestsecure.admin")) {
                    p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    plugin.gui().openMain(p);
                    return;
                }
                p.getInventory().addItem(plugin.locks().createAdminReportBook());
                p.sendMessage(Component.text("Admin report given.", NamedTextColor.GREEN));
                plugin.gui().openAdmin(p);
            }
            case "toggle_inspect" -> {
                boolean now = !plugin.ui().inspect(p.getUniqueId());
                plugin.ui().setInspect(p.getUniqueId(), now);
                plugin.gui().openAdmin(p);
            }
            case "global_unlock" -> {
                if (!p.hasPermission("chestsecure.admin")) {
                    p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    plugin.gui().openMain(p);
                    return;
                }
                p.closeInventory();
                plugin.pendingChat().put(p.getUniqueId(), new PendingChatAction.GlobalUnlock());
                p.sendMessage(Component.text(
                        "Type the player name whose locks you want to remove.  Type 'cancel' to abort.",
                        NamedTextColor.YELLOW));
            }
            case "open_logs" -> plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
            case "logs_page" -> plugin.gui().openLogs(p, parseInt(ctx));
            case "logs_cycle_action" -> {
                var st = plugin.ui().logs(p.getUniqueId());
                st.action = st.action.next();
                plugin.gui().openLogs(p, st.page);
            }
            case "logs_cycle_time" -> {
                var st = plugin.ui().logs(p.getUniqueId());
                st.time = st.time.next();
                plugin.gui().openLogs(p, st.page);
            }
            case "logs_set_actor" -> {
                p.closeInventory();
                plugin.pendingChat().put(p.getUniqueId(), new PendingChatAction.LogActorFilter());
                p.sendMessage(Component.text(
                        "Type a player name to filter logs, or 'clear' to reset.  Type 'cancel' to abort.",
                        NamedTextColor.YELLOW));
            }
            case "log_entry" -> {
                long logId = parseLong(ctx);
                if (logId <= 0) return;

                Optional<LogRecord> recOpt = plugin.locks().getLogById(logId);
                if (recOpt.isEmpty()) {
                    p.sendMessage(Component.text("Log not found.", NamedTextColor.RED));
                    plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
                    return;
                }
                LogRecord r = recOpt.get();
                World w = Bukkit.getWorld(r.worldUuid());
                if (w != null) {
                    Location loc = new Location(w, r.x(), r.y(), r.z());
                    p.setCompassTarget(loc);
                    p.sendMessage(Component.text("Compass set to log location.", NamedTextColor.AQUA));
                }

                
                if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    if (!p.isOp()) {
                        p.sendMessage(Component.text("Teleport requires OP.", NamedTextColor.RED));
                        plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
                        return;
                    }
                    boolean teleportEnabled = plugin.getConfig().getBoolean("admin.logs.teleport_enabled", plugin.getConfig().getBoolean("admin.allow_teleport", false));
                    if (!teleportEnabled) {
                        p.sendMessage(Component.text("Teleport is disabled in config.", NamedTextColor.RED));
                        plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
                        return;
                    }
                    int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
                    long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
                    plugin.confirms().set(p.getUniqueId(), new ConfirmRequest(
                            ConfirmType.LOG_TELEPORT,
                            String.valueOf(logId),
                            "LOGS",
                            expiresAt
                    ));
                    plugin.gui().openConfirm(p, "Teleport", List.of(
                            "Teleport to this log location?",
                            r.worldName() + " " + r.x() + " " + r.y() + " " + r.z(),
                            "",
                            "OP only."
                    ));
                    return;
                }
                plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
            }

            case "confirm_yes" -> handleConfirm(p, true);
            case "confirm_no" -> handleConfirm(p, false);

            default -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        // BUG FIX (B1): Only intercept close events for our own GUIs.
        // Previously this fired for ALL inventory closes (chests, furnaces, etc.), which
        // incorrectly cleared a pending chat action when the player opened a regular container
        // after starting a chat-input flow (e.g. "Add Viewer by name").
        if (!(e.getInventory().getHolder() instanceof de.SecureChest.plugin.gui.ChestSecureHolder)) return;

        // If a chat-input action was already started from the GUI that just closed,
        // cancel it — the player left without typing.
        if (plugin.pendingChat().containsKey(p.getUniqueId())) {
            plugin.pendingChat().remove(p.getUniqueId());
            p.sendMessage(net.kyori.adventure.text.Component.text(
                    "Chat input cancelled.", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    private void handleConfirm(Player p, boolean yes) {
        var reqOpt = plugin.confirms().get(p.getUniqueId());
        if (reqOpt.isEmpty()) {
            // B5 FIX: "Nothing to confirm." was confusing — tell the player exactly what happened
            p.sendMessage(Component.text("Confirmation timed out — please try again.", NamedTextColor.YELLOW));
            plugin.gui().openMain(p);
            return;
        }
        ConfirmRequest req = reqOpt.get();
        plugin.confirms().clear(p.getUniqueId());

        if (!yes) {
            route(p, req.returnTo());
            return;
        }

        boolean ok = switch (req.type()) {
            case UNLOCK_ONE -> plugin.locks().unlockByIdWithUndo(p, req.data());
            case UNLOCK_ALL_SELF -> plugin.locks().unlockAllWithUndo(p, p.getUniqueId());
            case GLOBAL_UNLOCK -> {
                try {
                    yield plugin.locks().unlockAllWithUndo(p, UUID.fromString(req.data()));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case REMOVE_TRUST -> {
                String[] parts = req.data().split("\\|", 2);
                if (parts.length != 2) yield false;
                try {
                    yield plugin.locks().removeTrustById(p, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case TRANSFER_LOCK -> {
                // data = "lockId|newOwnerUUID"
                String[] parts = req.data().split("\\|", 2);
                if (parts.length != 2) yield false;
                try {
                    yield plugin.locks().transferLock(p, parts[0], UUID.fromString(parts[1]));
                } catch (IllegalArgumentException ignored) {
                    yield false;
                }
            }
            case UNDO_RESTORE -> plugin.locks().restoreUndo(p);
            case LOG_TELEPORT -> handleLogTeleport(p, req.data());
        };

        p.sendMessage(ok ? Component.text("Done.", NamedTextColor.GREEN) : Component.text("Failed.", NamedTextColor.RED));
        route(p, req.returnTo());
    }

    private void route(Player p, String route) {
        if (route == null || route.isBlank() || route.equals("MAIN")) {
            plugin.gui().openMain(p);
            return;
        }
        if (route.equals("ADMIN")) {
            plugin.gui().openAdmin(p);
            return;
        }
        if (route.equals("LOGS")) {
            plugin.gui().openLogs(p, plugin.ui().logs(p.getUniqueId()).page);
            return;
        }
        if (route.startsWith("LOCK_MANAGE|")) {
            String lockId = route.substring("LOCK_MANAGE|".length());
            plugin.gui().openLockManage(p, lockId);
            return;
        }
        if (route.equals("LOCKS")) {
            plugin.gui().openLocksList(p, plugin.ui().locks(p.getUniqueId()).page);
        }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return -1L; }
    }

    private boolean handleLogTeleport(Player p, String logIdStr) {
        if (!p.isOp()) return false;

        boolean teleportEnabled = plugin.getConfig().getBoolean(
                "admin.logs.teleport_enabled",
                plugin.getConfig().getBoolean("admin.allow_teleport", false)
        );
        if (!teleportEnabled) return false;

        long logId = parseLong(logIdStr);
        if (logId <= 0) return false;

        Optional<LogRecord> recOpt = plugin.locks().getLogById(logId);
        if (recOpt.isEmpty()) return false;

        LogRecord r = recOpt.get();
        World w = Bukkit.getWorld(r.worldUuid());
        if (w == null) return false;

        Location loc = new Location(w, r.x() + 0.5, r.y() + 1.0, r.z() + 0.5);
        p.teleportAsync(loc);
        return true;
    }
}
