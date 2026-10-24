package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.lock.LogRepository;
import de.SecureChest.plugin.ui.UiStateStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class AdminScreen {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;
    private final GuiManager        mgr;

    AdminScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager) {
        this.plugin = plugin;
        this.h      = helper;
        this.mgr    = manager;
    }

    void openAdmin(Player player) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openMain(player);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-admin-title")));
        holder.setInventory(inv);

        boolean inspectOn = plugin.ui().inspect(player.getUniqueId());
        boolean ownerNameDisplayOn = plugin.ownerNameDisplay().isEnabled();

        inv.setItem(19, h.buildButton(Material.NETHER_STAR, text("gui-admin-key-name"),
                lines("gui-admin-key-lore"), "give_admin_key", ""));
        inv.setItem(21, h.buildButton(Material.WRITTEN_BOOK, text("gui-admin-logs-book-name"),
                lines("gui-admin-logs-book-lore"), "give_logs_book", ""));
        inv.setItem(23, h.buildButton(Material.ENCHANTED_BOOK, text("gui-admin-report-name"),
                lines("gui-admin-report-lore"),
                "give_admin_report", ""));
        inv.setItem(25, h.buildButton(Material.PAPER, text("gui-admin-logs-name"),
                lines("gui-admin-logs-lore"),
                "open_logs", ""));
        inv.setItem(28, h.buildToggleButton(text("gui-admin-inspect-label"), inspectOn, "toggle_inspect",
                inspectOn ? text("gui-admin-inspect-on")
                          : text("gui-admin-inspect-off")));
        inv.setItem(31, h.buildButton(Material.BARRIER, text("gui-admin-global-unlock-name"),
                lines("gui-admin-global-unlock-lore"),
                "global_unlock", ""));
        inv.setItem(34, h.buildButton(Material.FILLED_MAP, text("gui-admin-areas-name"),
                lines("gui-admin-areas-lore",
                        plugin.areas().isEnabled() ? text("gui-areas-state-on") : text("gui-areas-state-off"),
                        plugin.areas().areas().size()),
                "open_areas", ""));
        inv.setItem(37, h.buildButton(Material.CRAFTING_TABLE, text("gui-admin-recipes-name"),
                lines("gui-admin-recipes-lore"), "open_recipes", ""));
        inv.setItem(40, h.buildToggleButton(text("gui-admin-owner-name-display-label"), ownerNameDisplayOn,
                "toggle_owner_name_display",
                ownerNameDisplayOn ? text("gui-admin-owner-name-display-on")
                                   : text("gui-admin-owner-name-display-off")));
        inv.setItem(49, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openLogs(Player player, int page) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openMain(player);
            return;
        }

        UiStateStore.LogsState state = plugin.ui().logs(player.getUniqueId());
        state.page = Math.max(0, page);

        String actionFilter = state.action.databaseValue();
        long   minTs        = state.time.minTimestamp();
        String actorFilter  = state.actorFilter == null ? "" : state.actorFilter;

        int pageSize  = 45;
        int totalLogs = plugin.locks().countLogs(actionFilter, minTs, actorFilter);
        int maxPage   = Math.max(0, (totalLogs - 1) / pageSize);
        state.page    = Math.max(0, Math.min(state.page, maxPage));

        List<LogRecord> records = plugin.locks().listLogs(
                actionFilter, minTs, actorFilter, state.page * pageSize, pageSize);

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOGS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-logs-title", state.page + 1, maxPage + 1)));
        holder.setInventory(inv);

        int slot = 0;
        for (LogRecord rec : records) {
            if (slot >= 45) break;
            inv.setItem(slot++, buildLogListEntry(rec));
        }

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-admin-lore"), "back_admin", ""));
        inv.setItem(47, h.buildButton(Material.BOOK,
                text("gui-logs-action-name", text(state.action.labelKey())),
                lines("gui-cycle-lore"), "logs_cycle_action", ""));
        inv.setItem(48, h.buildButton(Material.CLOCK,
                text("gui-logs-time-name", text(state.time.labelKey())),
                lines("gui-cycle-lore"), "logs_cycle_time", ""));
        inv.setItem(49, h.buildButton(Material.PLAYER_HEAD, text("gui-logs-actor-name"),
                lines("gui-logs-actor-lore", actorFilter.isEmpty()
                        ? text("gui-logs-actor-not-set")
                        : text("gui-logs-actor-value", actorFilter)), "logs_set_actor", ""));

        if (state.page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, text("gui-page-previous-name"),
                    lines("gui-page-lore", state.page),
                    "logs_page", String.valueOf(state.page - 1)));
        if (state.page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, text("gui-page-next-name"),
                    lines("gui-page-lore", state.page + 2),
                    "logs_page", String.valueOf(state.page + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildLogListEntry(LogRecord r) {
        Material icon = switch (r.action()) {
            case LogRepository.ACTION_UNAUTHORIZED_BREAK    -> Material.RED_CONCRETE;
            case LogRepository.ACTION_ADMIN_BREAK           -> Material.ORANGE_CONCRETE;
            case LogRepository.ACTION_LOCK_CREATE           -> Material.LIME_CONCRETE;
            case LogRepository.ACTION_LOCK_REMOVE           -> Material.YELLOW_CONCRETE;
            case LogRepository.ACTION_LOCK_REMOVE_ADMIN     -> Material.ORANGE_TERRACOTTA;
            case LogRepository.ACTION_LOCK_REMOVE_AREA      -> Material.YELLOW_GLAZED_TERRACOTTA;
            case LogRepository.ACTION_LOCK_TRANSFER         -> Material.LIGHT_BLUE_CONCRETE;
            case LogRepository.ACTION_LOCK_UNLOCK_ALL_SELF  -> Material.YELLOW_CONCRETE_POWDER;
            case LogRepository.ACTION_LOCK_UNLOCK_ALL_ADMIN -> Material.ORANGE_CONCRETE_POWDER;
            case LogRepository.ACTION_TRUST_SET             -> Material.LIME_WOOL;
            case LogRepository.ACTION_TRUST_REMOVE          -> Material.YELLOW_WOOL;
            case LogRepository.ACTION_HOPPER_BIND           -> Material.LIME_TERRACOTTA;
            case LogRepository.ACTION_HOPPER_REMOVE         -> Material.YELLOW_TERRACOTTA;
            case LogRepository.ACTION_UNDO_RESTORE          -> Material.CYAN_CONCRETE;
            case LogRepository.ACTION_INSPECT_TOGGLE        -> Material.LIGHT_GRAY_CONCRETE;
            default                                         -> Material.PAPER;
        };
        String label = switch (r.action()) {
            case LogRepository.ACTION_UNAUTHORIZED_BREAK    -> text("gui-logs-entry-action-unauthorized-break");
            case LogRepository.ACTION_ADMIN_BREAK           -> text("gui-logs-entry-action-admin-break");
            case LogRepository.ACTION_LOCK_CREATE           -> text("gui-logs-entry-action-lock-create");
            case LogRepository.ACTION_LOCK_REMOVE           -> text("gui-logs-entry-action-lock-remove");
            case LogRepository.ACTION_LOCK_REMOVE_ADMIN     -> text("gui-logs-entry-action-lock-remove-admin");
            case LogRepository.ACTION_LOCK_REMOVE_AREA      -> text("gui-logs-entry-action-lock-remove-area");
            case LogRepository.ACTION_LOCK_TRANSFER         -> text("gui-logs-entry-action-lock-transfer");
            case LogRepository.ACTION_LOCK_UNLOCK_ALL_SELF  -> text("gui-logs-entry-action-lock-unlock-all-self");
            case LogRepository.ACTION_LOCK_UNLOCK_ALL_ADMIN -> text("gui-logs-entry-action-lock-unlock-all-admin");
            case LogRepository.ACTION_TRUST_SET             -> text("gui-logs-entry-action-trust-set");
            case LogRepository.ACTION_TRUST_REMOVE          -> text("gui-logs-entry-action-trust-remove");
            case LogRepository.ACTION_HOPPER_BIND           -> text("gui-logs-entry-action-hopper-bind");
            case LogRepository.ACTION_HOPPER_REMOVE         -> text("gui-logs-entry-action-hopper-remove");
            case LogRepository.ACTION_UNDO_RESTORE          -> text("gui-logs-entry-action-undo-restore");
            case LogRepository.ACTION_INSPECT_TOGGLE        -> text("gui-logs-entry-action-inspect-toggle");
            default                                         -> text("gui-logs-entry-action-other", r.action());
        };

        String relTime = buildRelativeTimeLabel(r.createdAt());
        String absTime = DATE_FMT.format(Instant.ofEpochMilli(r.createdAt()));
        String title   = text("gui-logs-entry-name", label, r.actorName(), relTime);
        String separator = text("gui-logs-entry-separator");

        List<String> lore = new ArrayList<>();
        lore.add(separator);
        lore.addAll(lines("gui-logs-entry-details-lore", r.actorName(), label, absTime, relTime,
                r.worldName(), r.x(), r.y(), r.z()));
        if (r.extra() != null && !r.extra().isBlank())
            lore.add(text("gui-logs-entry-note", r.extra()));
        lore.add(separator);
        lore.addAll(lines("gui-logs-entry-actions-lore"));

        return h.buildButton(icon, title, lore, "log_entry", String.valueOf(r.id()));
    }

    private String buildRelativeTimeLabel(long ts) {
        long min = (System.currentTimeMillis() - ts) / 60_000L;
        if (min < 1)  return text("gui-time-just-now");
        if (min < 60) return text("gui-time-minutes-ago", min);
        long h = min / 60;
        if (h < 24)   return text("gui-time-hours-ago", h);
        long d = h / 24;
        return d == 1 ? text("gui-time-day-ago", d) : text("gui-time-days-ago", d);
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
