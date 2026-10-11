package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockStats;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.MAIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(text("gui-main-title")));
        holder.setInventory(inv);

        inv.setItem(10, h.buildButton(Material.CHEST, text("gui-main-locks-name"),
                lines("gui-main-locks-lore", stats.totalLocks()),
                "open_locks", ""));
        inv.setItem(12, h.buildButton(Material.BOOK, text("gui-main-stats-name"),
                lines("gui-main-stats-lore"),
                "open_stats", ""));
        inv.setItem(14, h.buildButton(Material.HOPPER, text("gui-main-hoppers-name"),
                lines("gui-main-hoppers-lore", stats.totalSecureHoppers()),
                "open_hoppers", ""));
        inv.setItem(16, h.buildButton(Material.KNOWLEDGE_BOOK, text("gui-main-help-name"),
                lines("gui-main-help-lore"), "open_help", ""));

        inv.setItem(28, h.buildButton(Material.TRIPWIRE_HOOK, text("gui-main-recipe-key-name"),
                lines("gui-main-recipe-lore"), "open_recipe_key", ""));
        inv.setItem(30, h.buildButton(Material.HOPPER, text("gui-main-recipe-hopper-name"),
                lines("gui-main-recipe-lore"), "open_recipe_hopper", ""));

        int undoCount = plugin.undos().count(player.getUniqueId());
        if (undoCount > 0) {
            String undoLabel = undoCount == 1 ? text("gui-main-undo-name-single")
                    : text("gui-main-undo-name-multiple", undoCount);
            inv.setItem(32, h.buildButton(Material.ECHO_SHARD, undoLabel,
                    lines("gui-main-undo-lore", undoCount),
                    "confirm_undo_restore", ""));
        }

        inv.setItem(45, h.buildButton(Material.COMPARATOR, text("gui-main-settings-name"),
                lines("gui-main-settings-lore"),
                "open_settings", ""));

        if (player.hasPermission(Permissions.ADMIN))
            inv.setItem(53, h.buildButton(Material.NETHER_STAR, text("gui-main-admin-name"),
                    lines("gui-main-admin-lore"),
                    "open_admin", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openSettings(Player player) {
        boolean qlOn  = plugin.locks().getQuickLock(player.getUniqueId());
        boolean qrOn  = plugin.locks().getQuickRemove(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.SETTINGS, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(text("gui-settings-title")));
        holder.setInventory(inv);

        inv.setItem(20, h.buildToggleButton(text("gui-settings-quick-lock-label"), qlOn, "toggle_quicklock",
                qlOn ? text("gui-settings-quick-lock-on")
                     : text("gui-settings-quick-lock-off")));
        inv.setItem(24, h.buildToggleButton(text("gui-settings-quick-remove-label"), qrOn, "toggle_quickremove",
                qrOn ? text("gui-settings-quick-remove-on")
                     : text("gui-settings-quick-remove-off")));

        inv.setItem(40, h.buildButton(Material.BARRIER, text("gui-settings-unlock-all-name"),
                lines("gui-settings-unlock-all-lore"),
                "confirm_unlock_all_self", ""));

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openHelp(Player player) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HELP, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-help-title")));
        holder.setInventory(inv);

        inv.setItem(10, h.buildInfoItem(Material.TRIPWIRE_HOOK, text("gui-help-lock-name"),
                lines("gui-help-lock-lore")));
        inv.setItem(12, h.buildInfoItem(Material.HOPPER, text("gui-help-hopper-name"),
                lines("gui-help-hopper-lore")));
        inv.setItem(14, h.buildInfoItem(Material.LIME_DYE, text("gui-help-quick-lock-name"),
                lines("gui-help-quick-lock-lore")));
        inv.setItem(16, h.buildInfoItem(Material.RED_DYE, text("gui-help-quick-remove-name"),
                lines("gui-help-quick-remove-lore")));
        inv.setItem(28, h.buildInfoItem(Material.SHIELD, text("gui-help-protection-name"),
                lines("gui-help-protection-lore")));
        inv.setItem(31, h.buildInfoItem(Material.PLAYER_HEAD, text("gui-help-trust-levels-name"),
                lines("gui-help-trust-levels-lore")));

        inv.setItem(40, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));
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
                Component.text(text("gui-hoppers-title", page + 1, maxPage + 1)));
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
            inv.setItem(22, h.buildInfoItem(Material.BARRIER, text("gui-hoppers-empty-name"),
                    lines("gui-hoppers-empty-lore")));

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));
        if (page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, text("gui-page-previous-name"),
                    lines("gui-page-lore", page), "hoppers_page", String.valueOf(page - 1)));
        if (page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, text("gui-page-next-name"),
                    lines("gui-page-lore", page + 2), "hoppers_page", String.valueOf(page + 1)));

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
                Component.text(showKeyRecipe ? text("gui-recipe-key-title") : text("gui-recipe-hopper-title")));
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
        inv.setItem(40, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openStats(Player player) {
        LockStats s = plugin.locks().getLockStats(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.STATS, "");
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(text("gui-stats-title", player.getName())));
        holder.setInventory(inv);

        List<String> overview = new ArrayList<>();
        overview.add(text("gui-stats-locks", s.totalLocks()));
        overview.add(text("gui-stats-hoppers", s.totalSecureHoppers()));
        overview.add(text("gui-stats-trusted", s.totalTrustedRelations()));
        if (s.oldestLockAt() > 0)
            overview.add(text("gui-stats-oldest",
                    DATE_FMT.format(Instant.ofEpochMilli(s.oldestLockAt()))));
        if (s.newestLockAt() > 0)
            overview.add(text("gui-stats-newest",
                    DATE_FMT.format(Instant.ofEpochMilli(s.newestLockAt()))));

        inv.setItem(4, h.buildInfoItem(Material.NETHER_STAR,
                text("gui-stats-overview-name", player.getName()), overview));
        inv.setItem(10, h.buildInfoItem(Material.CHEST,              text("gui-stats-chests",         s.chestCount()),        List.of()));
        inv.setItem(11, h.buildInfoItem(Material.TRAPPED_CHEST,      text("gui-stats-trapped-chests", s.trappedChestCount()), List.of()));
        inv.setItem(12, h.buildInfoItem(Material.BARREL,             text("gui-stats-barrels",        s.barrelCount()),       List.of()));
        inv.setItem(13, h.buildInfoItem(Material.PURPLE_SHULKER_BOX, text("gui-stats-shulkers",       s.shulkerCount()),      List.of()));
        inv.setItem(14, h.buildInfoItem(Material.FURNACE,            text("gui-stats-locked-hoppers", s.hopperCount()),
                lines("gui-stats-locked-hoppers-lore")));
        inv.setItem(15, h.buildInfoItem(Material.HOPPER,             text("gui-stats-secure-hoppers", s.totalSecureHoppers()),
                lines("gui-stats-secure-hoppers-lore")));

        inv.setItem(22, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));
        h.fillEmptySlots(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openConfirm(Player player, String title, List<String> lines) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.CONFIRM, "");
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(text("gui-confirm-title", title)));
        holder.setInventory(inv);

        inv.setItem(11, h.buildButton(Material.RED_STAINED_GLASS_PANE,
                text("gui-confirm-cancel-name"), lines("gui-confirm-cancel-lore"), "confirm_no", ""));
        inv.setItem(15, h.buildButton(Material.LIME_STAINED_GLASS_PANE,
                text("gui-confirm-accept-name"), lines("gui-confirm-accept-lore"), "confirm_yes", ""));

        int timeout = plugin.cfg().confirmTimeoutSeconds();
        List<String> withTimeout = new ArrayList<>(lines);
        withTimeout.add("");
        withTimeout.add(text("gui-confirm-expires", timeout));
        inv.setItem(13, h.buildInfoItem(Material.PAPER, text("gui-confirm-info-name", title), withTimeout));
        h.fillEmptySlots(inv, Material.BLACK_STAINED_GLASS_PANE);

        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildHopperListEntry(Location loc, String id) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return h.buildButton(Material.HOPPER,
                text("gui-hoppers-entry-name", world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()),
                lines("gui-hoppers-entry-lore"),
                "noop", id);
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
