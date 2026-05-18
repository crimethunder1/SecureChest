package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.SecureChest.plugin.gui.GuiHelper.*;

final class MiscScreen {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;
    private final GuiManager        mgr;

    MiscScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager) {
        this.plugin = plugin;
        this.h      = helper;
        this.mgr    = manager;
    }

    void openMain(Player player) {
        LockStats stats = plugin.locks().getLockStats(player.getUniqueId());
        boolean qlOn  = plugin.locks().getQuickLock(player.getUniqueId());
        boolean qrOn  = plugin.locks().getQuickRemove(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.MAIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("§6⚿ §lChestSecure"));
        holder.setInventory(inv);

        inv.setItem(10, h.buildButton(Material.CHEST, "§fMeine gesperrten Blöcke",
                List.of(formatInfoLine("Schlösser anzeigen und verwalten"),
                        formatInfoLine(stats.totalLocks() + " Schloss/Schlösser vorhanden")),
                "open_locks", ""));
        inv.setItem(11, h.buildButton(Material.WRITABLE_BOOK, "§fMeine Statistiken",
                List.of(formatInfoLine("Übersicht über alle deine Schlösser")),
                "open_stats", ""));
        inv.setItem(12, h.buildButton(Material.HOPPER, "§fMeine Secure Hopper",
                List.of(formatInfoLine("Secure Hopper anzeigen"),
                        formatInfoLine(stats.totalSecureHoppers() + " Secure Hopper vorhanden")),
                "open_hoppers", ""));

        inv.setItem(14, h.buildButton(Material.TRIPWIRE_HOOK, "§fRezept: Chest Key",
                List.of(formatInfoLine("Crafting-Rezept anzeigen")), "open_recipe_key", ""));
        inv.setItem(16, h.buildButton(Material.HOPPER, "§fRezept: Secure Hopper",
                List.of(formatInfoLine("Crafting-Rezept anzeigen")), "open_recipe_hopper", ""));

        inv.setItem(28, h.buildToggleButton("Quick Lock", qlOn, "toggle_quicklock",
                qlOn ? "Rechtsklick mit leerer Hand sperrt sofort."
                     : "Rechtsklick mit Chest Key öffnet Bestätigungsfenster."));
        inv.setItem(30, h.buildToggleButton("Quick Remove", qrOn, "toggle_quickremove",
                qrOn ? "Rechtsklick mit Chest Key entsperrt sofort."
                     : "Rechtsklick mit Chest Key öffnet Bestätigungsfenster."));

        inv.setItem(32, h.buildButton(Material.BARRIER, "§cAlle Schlösser entfernen",
                List.of(formatWarningLine("Entfernt alle Schlösser und Secure Hopper"),
                        formatHintLine("Bestätigung erforderlich — Undo danach verfügbar")),
                "confirm_unlock_all_self", ""));
        inv.setItem(34, h.buildButton(Material.BOOK, "§fHilfe",
                List.of(formatInfoLine("Anleitung zur Nutzung von ChestSecure")), "open_help", ""));

        int undoCount = plugin.undos().count(player.getUniqueId());
        if (undoCount > 0) {
            String undoLabel = undoCount == 1 ? "Letzte Aktion rückgängig"
                    : "Rückgängig (" + undoCount + " Schritte verfügbar)";
            inv.setItem(31, h.buildButton(Material.SLIME_BALL, "§f" + undoLabel,
                    List.of(formatInfoLine("Letztes Entsperren oder Transfer rückgängig machen"),
                            formatInfoLine(undoCount + " Snapshot(s) im Verlauf"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_undo_restore", ""));
        }

        if (player.hasPermission("chestsecure.admin"))
            inv.setItem(49, h.buildButton(Material.NETHER_STAR, "§6Admin-Panel",
                    List.of(formatInfoLine("Admin-Werkzeuge — alle Schlösser verwalten")),
                    "open_admin", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openHelp(Player player) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HELP, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("§6⚿ §lChestSecure – Hilfe"));
        holder.setInventory(inv);

        inv.setItem(10, h.buildInfoItem(Material.TRIPWIRE_HOOK, "§fSperren", List.of(
                "§71) Chest Key craften",
                "§72) Rechtsklick auf Kiste/Barrel/Shulker",
                "§73) Nur du (oder vertraute Spieler) können öffnen")));
        inv.setItem(12, h.buildInfoItem(Material.HOPPER, "§fSecure Hopper", List.of(
                "§7Secure Hopper mit Chest Key craften",
                "§7In Richtung eines gesperrten Behälters platzieren",
                "§7Nur Secure Hopper können Items bewegen")));
        inv.setItem(14, h.buildInfoItem(Material.LIME_DYE, "§aQuick Lock", List.of(
                "§7Wenn aktiv:",
                "§7- Rechtsklick mit LEERER HAND sperrt sofort",
                "§7  (Chest Key muss im Inventar sein)",
                "§7- Ideal für schnelles Einrichten der Basis")));
        inv.setItem(16, h.buildInfoItem(Material.RED_DYE, "§cQuick Remove", List.of(
                "§7Wenn aktiv:",
                "§7- Rechtsklick mit Chest Key entsperrt sofort",
                "§7- Kein Bestätigungsfenster")));
        inv.setItem(28, h.buildInfoItem(Material.SHIELD, "§fSchutz", List.of(
                "§7Schutz vor TNT, Creeper, Bett",
                "§7Wither/Dragon-Schaden",
                "§7Kolben & Feuer/Lava",
                "§7Normale Hopper können nicht durchgreifen")));
        inv.setItem(31, h.buildInfoItem(Material.PLAYER_HEAD, "§fVertrauensstufen", List.of(
                "§7VIEW: Nur Inhalt anzeigen",
                "§7STOCKER: Items nehmen/legen, nicht abbauen",
                "§7CO-OWNER: Gleicher Zugriff wie Besitzer")));

        inv.setItem(40, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openHoppersList(Player player, int page) {
        List<Location> all = plugin.locks().listSecureHoppers(player.getUniqueId());
        int pageSize = 45;
        int maxPage  = all.isEmpty() ? 0 : Math.max(0, (all.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HOPPERS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("§6Meine Secure Hopper §8(§f" + (page + 1)
                        + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inv);

        int start = page * pageSize;
        int end   = Math.min(all.size(), start + pageSize);
        int slot  = 0;
        for (int i = start; i < end; i++) {
            Location loc = all.get(i);
            if (loc.getWorld() == null) continue;
            String id = LockKey.fromLocation(loc).asId();
            inv.setItem(slot++, buildHopperListEntry(loc, id));
        }

        if (all.isEmpty())
            inv.setItem(22, h.buildInfoItem(Material.BARRIER, "§eKeine Secure Hopper vorhanden",
                    List.of("§7Einen Secure Hopper auf deinen", "§7gesperrten Behälter ausrichten.")));

        inv.setItem(45, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        if (page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + page), "hoppers_page", String.valueOf(page - 1)));
        if (page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (page + 2)), "hoppers_page", String.valueOf(page + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openRecipePreview(Player player, boolean showKeyRecipe) {
        ShapedRecipe recipe = showKeyRecipe
                ? plugin.recipes().chestKeyRecipe()
                : plugin.recipes().secureHopperRecipe();
        if (recipe == null) {
            player.sendMessage(Component.text(
                    plugin.msg().get("rezept-nicht-verfuegbar"), NamedTextColor.RED));
            mgr.openMain(player);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(
                showKeyRecipe ? ChestSecureHolder.GuiId.RECIPE_KEY
                              : ChestSecureHolder.GuiId.RECIPE_HOPPER, "");
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text(showKeyRecipe ? "§6Rezept: Chest Key" : "§6Rezept: Secure Hopper"));
        holder.setInventory(inv);

        int[] slots = {11, 12, 13, 20, 21, 22, 29, 30, 31};
        String[] rows = recipe.getShape();
        Map<Character, ItemStack> ingredients = new HashMap<>();

        for (Map.Entry<Character, RecipeChoice> entry : recipe.getChoiceMap().entrySet()) {
            RecipeChoice choice = entry.getValue();
            if (choice instanceof RecipeChoice.ExactChoice exact)
                ingredients.put(entry.getKey(), exact.getChoices().get(0));
            else if (choice instanceof RecipeChoice.MaterialChoice mat)
                ingredients.put(entry.getKey(), new ItemStack(mat.getChoices().get(0)));
        }

        int idx = 0;
        for (int row = 0; row < 3; row++) {
            String shapeRow = row < rows.length ? rows[row] : "   ";
            for (int col = 0; col < 3; col++) {
                char c = col < shapeRow.length() ? shapeRow.charAt(col) : ' ';
                ItemStack item = ingredients.get(c);
                inv.setItem(slots[idx++], item == null ? new ItemStack(Material.AIR) : item.clone());
            }
        }

        inv.setItem(24, recipe.getResult().clone());
        inv.setItem(40, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openStats(Player player) {
        LockStats s = plugin.locks().getLockStats(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.STATS, "");
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("§6Statistiken – §f" + player.getName()));
        holder.setInventory(inv);

        List<String> overview = new ArrayList<>();
        overview.add("§7Gesperrte Blöcke:       §f" + s.totalLocks());
        overview.add("§7Secure Hopper:          §f" + s.totalSecureHoppers());
        overview.add("§7Vertrauensbeziehungen:  §f" + s.totalTrustedRelations());
        if (s.oldestLockAt() > 0)
            overview.add("§7Ältestes Schloss: §f"
                    + DATE_FMT.format(Instant.ofEpochMilli(s.oldestLockAt())));
        if (s.newestLockAt() > 0)
            overview.add("§7Neuestes Schloss: §f"
                    + DATE_FMT.format(Instant.ofEpochMilli(s.newestLockAt())));

        inv.setItem(4, h.buildInfoItem(Material.NETHER_STAR,
                "§f" + player.getName() + "§7s Übersicht", overview));
        inv.setItem(10, h.buildInfoItem(Material.CHEST,              "§fKisten: §e"           + s.chestCount(),        List.of()));
        inv.setItem(11, h.buildInfoItem(Material.TRAPPED_CHEST,      "§fFallenkisten: §e"      + s.trappedChestCount(), List.of()));
        inv.setItem(12, h.buildInfoItem(Material.BARREL,             "§fFässer: §e"            + s.barrelCount(),       List.of()));
        inv.setItem(13, h.buildInfoItem(Material.PURPLE_SHULKER_BOX, "§fShulker-Boxen: §e"     + s.shulkerCount(),      List.of()));
        inv.setItem(14, h.buildInfoItem(Material.FURNACE,            "§fGesperrte Hopper: §e"  + s.hopperCount(),
                List.of(formatInfoLine("Direkt mit Chest Key gesperrt"))));
        inv.setItem(15, h.buildInfoItem(Material.HOPPER,             "§fSecure Hopper: §e"     + s.totalSecureHoppers(),
                List.of(formatInfoLine("Gecraftete Secure Hopper an einem Schloss"))));

        inv.setItem(22, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        h.fillEmptySlots(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openConfirm(Player player, String title, List<String> lines) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.CONFIRM, "");
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("§6Bestätigung: §f" + title));
        holder.setInventory(inv);

        inv.setItem(11, h.buildButton(Material.RED_STAINED_GLASS_PANE,
                "§cAbbrechen", List.of("§7Nein, abbrechen"), "confirm_no", ""));
        inv.setItem(15, h.buildButton(Material.LIME_STAINED_GLASS_PANE,
                "§aBestätigen", List.of("§aJa, fortfahren"), "confirm_yes", ""));

        int timeout = plugin.cfg().confirmTimeoutSeconds();
        List<String> withTimeout = new ArrayList<>(lines);
        withTimeout.add("");
        withTimeout.add("§e▶ Läuft ab in " + timeout + "s — schnell handeln!");
        inv.setItem(13, h.buildInfoItem(Material.PAPER, "§f" + title, withTimeout));
        h.fillEmptySlots(inv, Material.BLACK_STAINED_GLASS_PANE);

        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildHopperListEntry(Location loc, String id) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return h.buildButton(Material.HOPPER,
                "§f" + world + " §7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                List.of(formatHintLine("Aufheben durch Abbauen"),
                        formatInfoLine("Geschützt vor Explosionen und Hoppern")),
                "noop", id);
    }
}
