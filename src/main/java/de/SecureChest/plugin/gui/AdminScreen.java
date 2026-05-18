package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static de.SecureChest.plugin.gui.GuiHelper.*;

final class AdminScreen {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String LOG_SEPARATOR = "§8────────────────────────────";

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;
    private final GuiManager        mgr;

    AdminScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager) {
        this.plugin = plugin;
        this.h      = helper;
        this.mgr    = manager;
    }

    void openAdmin(Player player) {
        if (!player.hasPermission("chestsecure.admin")) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openMain(player);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("§6⚿ §lChestSecure Admin"));
        holder.setInventory(inv);

        boolean inspectOn = plugin.ui().inspect(player.getUniqueId());

        inv.setItem(19, h.buildButton(Material.NETHER_STAR, "§fAdmin-Key erhalten",
                List.of("§7Klicken um Admin-Key zu empfangen"), "give_admin_key", ""));
        inv.setItem(21, h.buildButton(Material.WRITTEN_BOOK, "§fAktivitätslog als Buch",
                List.of(formatInfoLine("Neueste Log-Einträge als Buch")), "give_logs_book", ""));
        inv.setItem(23, h.buildButton(Material.WRITTEN_BOOK, "§fServer-Bericht",
                List.of(formatInfoLine("Vollständige Server-Schlossübersicht als Buch")),
                "give_admin_report", ""));
        inv.setItem(25, h.buildButton(Material.PAPER, "§fLogs durchsuchen",
                List.of(formatInfoLine("Aktivitätslogs filtern und durchsuchen"),
                        formatInfoLine("Klick → Kompass  |  Shift+Klick → Teleport (OP)")),
                "open_logs", ""));
        inv.setItem(28, h.buildToggleButton("Inspect-Modus", inspectOn, "toggle_inspect",
                inspectOn ? "Schleichen + Rechtsklick zeigt Schloss-Informationen."
                          : "Aktivieren um Behälter per Schleich-Klick zu untersuchen."));
        inv.setItem(31, h.buildButton(Material.BARRIER, "§cGlobal entsperren",
                List.of(formatWarningLine("Alle Schlösser eines Spielers entfernen"),
                        formatHintLine("Spielername im Chat — Bestätigung erforderlich")),
                "global_unlock", ""));
        inv.setItem(49, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openLogs(Player player, int page) {
        if (!player.hasPermission("chestsecure.admin")) {
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
                Component.text("§6Aktivitätslogs §8(§f" + (state.page + 1)
                        + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inv);

        int slot = 0;
        for (LogRecord rec : records) {
            if (slot >= 45) break;
            inv.setItem(slot++, buildLogListEntry(rec));
        }

        inv.setItem(45, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Admin-Panel"), "back_admin", ""));
        inv.setItem(47, h.buildButton(Material.BOOK,
                "§fAktion: " + state.action.label(),
                List.of("§7Klicken zum Wechseln"), "logs_cycle_action", ""));
        inv.setItem(48, h.buildButton(Material.CLOCK,
                "§fZeitraum: §e" + state.time.label(),
                List.of("§7Klicken zum Wechseln"), "logs_cycle_time", ""));
        inv.setItem(49, h.buildButton(Material.PLAYER_HEAD, "§fSpielerfilter",
                List.of(actorFilter.isEmpty() ? "§8(nicht gesetzt)" : "§f" + actorFilter,
                        "§7Klicken zum Setzen (Name tippen)",
                        "§7'clear' zum Zurücksetzen"), "logs_set_actor", ""));

        if (state.page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + state.page),
                    "logs_page", String.valueOf(state.page - 1)));
        if (state.page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (state.page + 2)),
                    "logs_page", String.valueOf(state.page + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildLogListEntry(LogRecord r) {
        Material icon = switch (r.action()) {
            case "UNAUTHORIZED_BREAK" -> Material.RED_CONCRETE;
            case "ADMIN_BREAK"        -> Material.ORANGE_CONCRETE;
            case "LOCK"               -> Material.LIME_CONCRETE;
            case "UNLOCK"             -> Material.YELLOW_CONCRETE;
            default                   -> Material.PAPER;
        };
        String colorCode = switch (r.action()) {
            case "UNAUTHORIZED_BREAK" -> "§c";
            case "ADMIN_BREAK"        -> "§6";
            case "LOCK"               -> "§a";
            case "UNLOCK"             -> "§e";
            default                   -> "§7";
        };
        String label = switch (r.action()) {
            case "UNAUTHORIZED_BREAK" -> "Unerlaubter Abbauversuch";
            case "ADMIN_BREAK"        -> "Admin-Abbau";
            case "LOCK"               -> "Gesperrt";
            case "UNLOCK"             -> "Entsperrt";
            default                   -> r.action();
        };

        String relTime = buildRelativeTimeLabel(r.createdAt());
        String absTime = DATE_FMT.format(Instant.ofEpochMilli(r.createdAt()));
        String title   = colorCode + label + "  §8│  §f" + r.actorName() + "  §8│  §7" + relTime;

        List<String> lore = new ArrayList<>();
        lore.add(LOG_SEPARATOR);
        lore.add(formatInfoLine("Spieler:  §f" + r.actorName()));
        lore.add(formatInfoLine("Aktion:   " + colorCode + label));
        lore.add(formatInfoLine("Zeit:     §f" + absTime));
        lore.add(formatInfoLine("          §7(" + relTime + ")"));
        lore.add(formatInfoLine("Ort:      §f" + r.worldName()
                + "  " + r.x() + " " + r.y() + " " + r.z()));
        if (r.extra() != null && !r.extra().isBlank())
            lore.add(formatInfoLine("Hinweis:  §f" + r.extra()));
        lore.add(LOG_SEPARATOR);
        lore.add(formatActionLine("Klick → Kompass setzen"));
        lore.add(formatActionLine("Shift+Klick → Teleport (OP, Bestätigung)"));

        return h.buildButton(icon, title, lore, "log_entry", String.valueOf(r.id()));
    }

    private String buildRelativeTimeLabel(long ts) {
        long min = (System.currentTimeMillis() - ts) / 60_000L;
        if (min < 1)  return "Gerade eben";
        if (min < 60) return "vor " + min + " Min";
        long h = min / 60;
        if (h < 24)   return "vor " + h + " Std";
        long d = h / 24;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }
}
