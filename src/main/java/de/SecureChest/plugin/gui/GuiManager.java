package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public final class GuiManager {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;

    public GuiManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------- Main ----------------

    public void openMain(Player p) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.MAIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("ChestSecure"));
        holder.setInventory(inv);

        int lockCount = plugin.locks().listLocks(p.getUniqueId()).size();
        int hopperCount = plugin.locks().listSecureHoppers(p.getUniqueId()).size();
        inv.setItem(10, button(Material.CHEST, "My Locked Blocks", List.of("View & manage your locks", "You have " + lockCount + " lock(s)"), "open_locks", ""));
        inv.setItem(12, button(Material.HOPPER, "My Secure Hoppers", List.of("View your secure hoppers", "You have " + hopperCount + " secure hopper(s)"), "open_hoppers", ""));
        inv.setItem(14, button(Material.TRIPWIRE_HOOK, "Recipe: Chest Key", List.of("Click to preview"), "open_recipe_key", ""));
        inv.setItem(16, button(Material.HOPPER, "Recipe: Secure Hopper", List.of("Click to preview"), "open_recipe_hopper", ""));

        boolean ql = plugin.locks().getQuickLock(p.getUniqueId());
        boolean qr = plugin.locks().getQuickRemove(p.getUniqueId());
        inv.setItem(28, toggle("Quick Lock", ql, "toggle_quicklock"));
        inv.setItem(30, toggle("Quick Remove", qr, "toggle_quickremove"));

        inv.setItem(32, button(Material.BARRIER, "Unlock ALL", List.of("Unlock all your locks & secure hoppers", "Confirm required"), "confirm_unlock_all_self", ""));

        inv.setItem(34, button(Material.BOOK, "Help", List.of("How to use ChestSecure"), "open_help", ""));

        if (plugin.locks().hasUndo(p)) {
            inv.setItem(31, button(Material.SLIME_BALL, "Undo last unlock", List.of("Restores your last unlock operation", "Confirm required"), "confirm_undo_restore", ""));
        }

        if (p.hasPermission("chestsecure.admin")) {
            inv.setItem(49, button(Material.NETHER_STAR, "Admin Panel", List.of("Admin features"), "open_admin", ""));
        }

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openHelp(Player p) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HELP, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("ChestSecure Help"));
        holder.setInventory(inv);

        inv.setItem(10, info(Material.TRIPWIRE_HOOK, "Locking", List.of(
                "1) Craft a Chest Key",
                "2) Right-click a chest/barrel/shulker to lock",
                "3) Only you (or trusted players) can open"
        )));

        inv.setItem(12, info(Material.HOPPER, "Secure Hopper", List.of(
                "Craft a Secure Hopper with a Chest Key",
                "Place it pointing into a locked container",
                "Only Secure Hoppers can move items in/out of locked containers"
        )));

        inv.setItem(14, info(Material.LIME_DYE, "Quick Lock", List.of(
                "If enabled:",
                "- Right-click with EMPTY HAND auto-locks",
                "  (a Chest Key must be somewhere in inventory)",
                "- Great for fast base setup"
        )));

        inv.setItem(16, info(Material.RED_DYE, "Quick Remove", List.of(
                "If enabled:",
                "- Right-click with Chest Key removes lock",
                "- Confirm GUIs are used for dangerous actions"
        )));

        inv.setItem(28, info(Material.SHIELD, "Protection", List.of(
                "Protected from TNT/Creeper/Bed",
                "Wither/Dragon damage",
                "Pistons & fire/lava damage",
                "Normal hoppers cannot bypass locked containers"
        )));

        inv.setItem(31, info(Material.PLAYER_HEAD, "Trust Levels", List.of(
                "VIEW: only view contents",
                "STOCKER: can take/put items, can't break",
                "CO-OWNER: same as owner for access (no breaking)"
        )));

        inv.setItem(40, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));
        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    // ---------------- Locks list ----------------

    public void openLocksList(Player p, int page) {
        UiStateStore.LockListState st = plugin.ui().locks(p.getUniqueId());
        st.page = Math.max(0, page);

        List<Location> locks = plugin.locks().listLocks(p.getUniqueId());
        List<LockView> views = new ArrayList<>(locks.size());
        for (Location loc : locks) {
            if (loc.getWorld() == null) continue;
            LockKey key = LockKey.fromLocation(loc);
            String lockId = key.asId();
            Material type = plugin.locks().getLockFromId(lockId).map(LockEntry::blockType).orElse(Material.CHEST);
            views.add(new LockView(lockId, loc, type));
        }

        // Filters
        if (st.world == UiStateStore.WorldFilter.CURRENT) {
            views.removeIf(v -> v.loc.getWorld() == null || !v.loc.getWorld().equals(p.getWorld()));
        }
        views.removeIf(v -> !matchesTypeFilter(v.type, st.type));

        // Sort
        views.sort(lockComparator(p, st.sort));

        int pageSize = 45;
        int maxPage = Math.max(0, (views.size() - 1) / pageSize);
        st.page = Math.max(0, Math.min(st.page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCKS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("My Locks (" + (st.page + 1) + "/" + (maxPage + 1) + ")"));
        holder.setInventory(inv);

        int start = st.page * pageSize;
        int end = Math.min(views.size(), start + pageSize);

        if (views.isEmpty()) {
            inv.setItem(22, info(Material.BARRIER, "No locks found",
                    List.of("You have no locked blocks", "matching the current filters.")));
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            LockView v = views.get(i);
            inv.setItem(slot++, lockItem(p, v));
        }

        // Controls
        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));

        inv.setItem(47, button(Material.HOPPER, "Sort: " + st.sort,
                List.of("Click to cycle"), "locks_cycle_sort", ""));
        inv.setItem(48, button(Material.COMPASS, "World: " + st.world,
                List.of("Click to cycle"), "locks_cycle_world", ""));
        inv.setItem(49, button(Material.ITEM_FRAME, "Type: " + st.type,
                List.of("Click to cycle"), "locks_cycle_type", ""));

        if (st.page > 0) inv.setItem(52, button(Material.PAPER, "Previous", List.of("Page " + st.page), "locks_page", String.valueOf(st.page - 1)));
        if (st.page < maxPage) inv.setItem(53, button(Material.PAPER, "Next", List.of("Page " + (st.page + 2)), "locks_page", String.valueOf(st.page + 1)));

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openLockManage(Player p, String lockId) {
        Optional<LockEntry> lockOpt = plugin.locks().getLockFromId(lockId);
        if (lockOpt.isEmpty()) {
            p.sendMessage(Component.text("Lock not found.", NamedTextColor.RED));
            openLocksList(p, 0);
            return;
        }
        LockEntry lock = lockOpt.get();
        Location loc = LockUtil.toLocation(lock.key());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCK_MANAGE, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Manage Lock"));
        holder.setInventory(inv);

        inv.setItem(10, info(lock.blockType().isItem() ? lock.blockType() : Material.CHEST,
                "Locked: " + lock.blockType().name(),
                List.of(
                        "Owner: " + nameOf(lock.owner()),
                        "World: " + (loc.getWorld() != null ? loc.getWorld().getName() : "?"),
                        "Coords: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                        "Created: " + DF.format(Instant.ofEpochMilli(lock.createdAt()))
                )));

        inv.setItem(12, button(Material.COMPASS, "Ping Location",
                List.of("Sets your compass to this lock"), "ping_lock", lockId));
        inv.setItem(13, button(Material.PAPER, "Copy Coords",
                List.of("Sends coords to chat"), "copy_lock", lockId));

        inv.setItem(16, button(Material.BARRIER, "Unlock",
                List.of("Unlock this lock", "Confirm required"), "confirm_unlock_one", lockId));

        inv.setItem(28, button(Material.PLAYER_HEAD, "Add trust (nearby)",
                List.of("Pick an online player nearby"), "open_trust_nearby", lockId));

        inv.setItem(30, button(Material.MAP, "Add VIEW (by name)",
                List.of("Type a player name in chat"), "add_trust_name", "VIEW|" + lockId));
        inv.setItem(31, button(Material.CHEST, "Add STOCKER (by name)",
                List.of("Type a player name in chat"), "add_trust_name", "STOCKER|" + lockId));
        inv.setItem(32, button(Material.DIAMOND, "Add CO_OWNER (by name)",
                List.of("Type a player name in chat"), "add_trust_name", "CO_OWNER|" + lockId));

        // Trusted list
        int slot = 36;
        List<Map.Entry<UUID, AccessLevel>> trustList = lock.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name()))
                .collect(Collectors.toList());

        // Show count above the trust list; warn if more than 9 (slot 35)
        int trustTotal = trustList.size();
        String trustCountLabel = trustTotal == 0 ? "No trusted players"
                : "Trusted: " + trustTotal + (trustTotal > 9 ? " (showing first 9)" : "");
        inv.setItem(35, info(Material.NAME_TAG, trustCountLabel, List.of()));

        for (var e : trustList) {
            if (slot >= 45) break;
            UUID uuid = e.getKey();
            AccessLevel lvl = e.getValue();
            inv.setItem(slot++, trustItem(uuid, lvl, lockId));
        }

        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to list"), "back_locks", ""));
        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openTrustNearby(Player p, String lockId) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_NEARBY, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Nearby Players"));
        holder.setInventory(inv);

        double radius = plugin.getConfig().getDouble("ui.trust_nearby_radius", 32.0);
        List<Player> nearby = p.getWorld().getPlayers().stream()
                .filter(o -> !o.getUniqueId().equals(p.getUniqueId()))
                .filter(o -> o.getLocation().distanceSquared(p.getLocation()) <= radius * radius)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (Player t : nearby) {
            if (slot >= 45) break;
            inv.setItem(slot++, head(t.getUniqueId(), t.getName(), List.of("Click to choose role"), "open_trust_role", lockId + "|" + t.getUniqueId()));
        }

        if (nearby.isEmpty()) {
            inv.setItem(22, info(Material.BARRIER, "No nearby players",
                    List.of("Move closer to a player", "or use 'Add trust (by name)'")));
        }

        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return"), "back_lock_manage", lockId));
        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openTrustRole(Player p, String lockId, UUID target) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_ROLE, lockId + "|" + target);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Pick Trust Role"));
        holder.setInventory(inv);

        String name = nameOf(target);
        inv.setItem(11, button(Material.MAP, "VIEW: " + name,
                List.of("Can view inventory", "Cannot take or place items"), "set_trust_role", "VIEW|" + lockId + "|" + target));
        inv.setItem(13, button(Material.CHEST, "STOCKER: " + name,
                List.of("Can take/put items", "Cannot break"), "set_trust_role", "STOCKER|" + lockId + "|" + target));
        inv.setItem(15, button(Material.DIAMOND, "CO_OWNER: " + name,
                List.of("Full access (no breaking)", "Cannot manage trust"), "set_trust_role", "CO_OWNER|" + lockId + "|" + target));

        inv.setItem(18, button(Material.ARROW, "Back", List.of("Return"), "back_trust_nearby", lockId));
        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    // ---------------- Secure hoppers ----------------

    public void openHoppersList(Player p, int page) {
        List<Location> hoppers = plugin.locks().listSecureHoppers(p.getUniqueId());
        int pageSize = 45;
        int maxPage = Math.max(0, (hoppers.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HOPPERS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("My Secure Hoppers (" + (page + 1) + "/" + (maxPage + 1) + ")"));
        holder.setInventory(inv);

        int start = page * pageSize;
        int end = Math.min(hoppers.size(), start + pageSize);

        int slot = 0;
        for (int i = start; i < end; i++) {
            Location loc = hoppers.get(i);
            if (loc.getWorld() == null) continue; // skip unloaded worlds (LockKey.fromLocation requires non-null world)
            String hopperId = LockKey.fromLocation(loc).asId();
            inv.setItem(slot++, hopperItem(loc, hopperId));
        }

        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));
        if (page > 0) inv.setItem(52, button(Material.PAPER, "Previous", List.of("Page " + page), "hoppers_page", String.valueOf(page - 1)));
        if (page < maxPage) inv.setItem(53, button(Material.PAPER, "Next", List.of("Page " + (page + 2)), "hoppers_page", String.valueOf(page + 1)));

        if (hoppers.isEmpty()) {
            inv.setItem(22, info(Material.BARRIER, "No secure hoppers",
                    List.of("Place a Secure Hopper facing", "your locked container to add one.")));
        }

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    // ---------------- Recipe preview ----------------

    public void openRecipePreview(Player p, boolean keyRecipe) {
        ShapedRecipe recipe = keyRecipe ? plugin.recipes().chestKeyRecipe() : plugin.recipes().secureHopperRecipe();
        if (recipe == null) {
            p.sendMessage(Component.text("Recipe not available.", NamedTextColor.RED));
            openMain(p);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(keyRecipe ? ChestSecureHolder.GuiId.RECIPE_KEY : ChestSecureHolder.GuiId.RECIPE_HOPPER, "");
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text(keyRecipe ? "Recipe: Chest Key" : "Recipe: Secure Hopper"));
        holder.setInventory(inv);

        int[] grid = {11, 12, 13, 20, 21, 22, 29, 30, 31};

        String[] shape = recipe.getShape();
        Map<Character, ItemStack> ingredients = new HashMap<>();

        // Use getChoiceMap() directly (Paper 1.21+ API) - handles ExactChoice correctly.
        for (Map.Entry<Character, RecipeChoice> me : recipe.getChoiceMap().entrySet()) {
            if (me.getValue() != null) {
                ingredients.put(me.getKey(), me.getValue().getItemStack());
            }
        }

        int idx = 0;
        for (int r = 0; r < 3; r++) {
            String row = (r < shape.length) ? shape[r] : "   ";
            for (int c = 0; c < 3; c++) {
                char ch = (c < row.length()) ? row.charAt(c) : ' ';
                ItemStack ing = ingredients.get(ch);
                inv.setItem(grid[idx++], ing == null ? new ItemStack(Material.AIR) : ing.clone());
            }
        }

        inv.setItem(24, recipe.getResult().clone());
        inv.setItem(40, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));

        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    // ---------------- Admin ----------------

    public void openAdmin(Player p) {
        if (!p.hasPermission("chestsecure.admin")) {
            p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            openMain(p);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("ChestSecure Admin"));
        holder.setInventory(inv);

        boolean inspect = plugin.ui().inspect(p.getUniqueId());

        inv.setItem(19, button(Material.NETHER_STAR, "Get Admin Key", List.of("Click to receive Admin Key"), "give_admin_key", ""));
        // "Book and Quill" was renamed in modern Minecraft versions; use WRITABLE_BOOK for the editable book.
        inv.setItem(21, button(Material.WRITABLE_BOOK, "Logs Book", List.of("Book & Quill (latest logs)"), "give_logs_book", ""));
        inv.setItem(23, button(Material.WRITTEN_BOOK, "Admin Report", List.of("Server overview (book)"), "give_admin_report", ""));
        inv.setItem(25, button(Material.PAPER, "Logs UI", List.of("Browse logs with filters", "Click a log: compass", "Shift-click: teleport (OP)"), "open_logs", ""));

        inv.setItem(28, toggle("Inspect Mode", inspect, "toggle_inspect"));
        inv.setItem(31, button(Material.BARRIER, "Global Unlock", List.of("Unlock all locks of a player", "Type player name in chat", "Confirm required"), "global_unlock", ""));

        inv.setItem(49, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openLogs(Player p, int page) {
        if (!p.hasPermission("chestsecure.admin")) {
            p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            openMain(p);
            return;
        }

        UiStateStore.LogsState st = plugin.ui().logs(p.getUniqueId());
        st.page = Math.max(0, page);

        String actionFilter = switch (st.action) {
            case UNAUTHORIZED_BREAK -> "UNAUTHORIZED_BREAK";
            case ADMIN_BREAK -> "ADMIN_BREAK";
            default -> "ALL";
        };
        long now = System.currentTimeMillis();
        long minTs = switch (st.time) {
            case H24 -> now - 24L * 60L * 60L * 1000L;
            case D7 -> now - 7L * 24L * 60L * 60L * 1000L;
            case D30 -> now - 30L * 24L * 60L * 60L * 1000L;
            case ALL -> 0L;
        };
        String actorLike = st.actorFilter == null ? "" : st.actorFilter;

        int pageSize = 45;
        int total = plugin.locks().countLogs(actionFilter, minTs, actorLike);
        int maxPage = Math.max(0, (total - 1) / pageSize);
        st.page = Math.max(0, Math.min(st.page, maxPage));

        int offset = st.page * pageSize;
        List<LogRecord> logs = plugin.locks().listLogs(actionFilter, minTs, actorLike, offset, pageSize);

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOGS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Logs (" + (st.page + 1) + "/" + (maxPage + 1) + ")"));
        holder.setInventory(inv);

        int slot = 0;
        for (LogRecord r : logs) {
            if (slot >= 45) break;
            inv.setItem(slot++, logItem(r));
        }

        // Controls
        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to admin panel"), "back_admin", ""));

        inv.setItem(47, button(Material.BOOK, "Action: " + st.action,
                List.of("Click to cycle"), "logs_cycle_action", ""));
        inv.setItem(48, button(Material.CLOCK, "Time: " + st.time,
                List.of("Click to cycle"), "logs_cycle_time", ""));
        inv.setItem(49, button(Material.PLAYER_HEAD, "Actor filter",
                List.of(actorLike.isEmpty() ? "(not set)" : actorLike, "Click to set (type name)", "Type 'clear' to reset"), "logs_set_actor", ""));

        if (st.page > 0) inv.setItem(52, button(Material.PAPER, "Previous", List.of("Page " + st.page), "logs_page", String.valueOf(st.page - 1)));
        if (st.page < maxPage) inv.setItem(53, button(Material.PAPER, "Next", List.of("Page " + (st.page + 2)), "logs_page", String.valueOf(st.page + 1)));

        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openConfirm(Player p, String title, List<String> lines) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.CONFIRM, "");
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Confirm: " + title));
        holder.setInventory(inv);

        inv.setItem(11, button(Material.RED_STAINED_GLASS_PANE, "Cancel", List.of("No"), "confirm_no", ""));
        inv.setItem(15, button(Material.LIME_STAINED_GLASS_PANE, "Confirm", List.of("Yes"), "confirm_yes", ""));

        int ttlSeconds = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
        List<String> linesWithHint = new ArrayList<>(lines);
        linesWithHint.add("");
        linesWithHint.add("\u25ba Expires in " + ttlSeconds + "s - act quickly!");
        inv.setItem(13, info(Material.PAPER, title, linesWithHint));
        fillGlass(inv, Material.BLACK_STAINED_GLASS_PANE);

        p.openInventory(inv);
        soundClick(p);
    }

    // ---------------- Item builders ----------------

    private static final class LockView {
        final String lockId;
        final Location loc;
        final Material type;

        private LockView(String lockId, Location loc, Material type) {
            this.lockId = lockId;
            this.loc = loc;
            this.type = type;
        }
    }

    private Comparator<LockView> lockComparator(Player p, UiStateStore.LockSort sort) {
        return switch (sort) {
            case WORLD -> Comparator
                    .comparing((LockView v) -> v.loc.getWorld() != null ? v.loc.getWorld().getName() : "")
                    .thenComparingInt(v -> v.loc.getBlockX())
                    .thenComparingInt(v -> v.loc.getBlockY())
                    .thenComparingInt(v -> v.loc.getBlockZ());
            case TYPE -> Comparator
                    .comparing((LockView v) -> v.type.name())
                    .thenComparingInt(v -> v.loc.getBlockX())
                    .thenComparingInt(v -> v.loc.getBlockY())
                    .thenComparingInt(v -> v.loc.getBlockZ());
            case RECENT -> Comparator
                    .comparingLong((LockView v) -> plugin.locks().getLockFromId(v.lockId).map(LockEntry::createdAt).orElse(0L))
                    .reversed();
            case DISTANCE -> Comparator.comparingDouble((LockView v) -> {
                if (v.loc.getWorld() == null || !v.loc.getWorld().equals(p.getWorld())) return Double.MAX_VALUE;
                return v.loc.distanceSquared(p.getLocation());
            });
        };
    }

    private boolean matchesTypeFilter(Material type, UiStateStore.LockTypeFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case CHESTS -> type == Material.CHEST || type == Material.TRAPPED_CHEST;
            case BARRELS -> type == Material.BARREL;
            case SHULKERS -> type.name().endsWith("SHULKER_BOX");
        };
    }

    private ItemStack lockItem(Player viewer, LockView v) {
        String world = v.loc.getWorld() != null ? v.loc.getWorld().getName() : "world";
        String coords = world + " " + v.loc.getBlockX() + " " + v.loc.getBlockY() + " " + v.loc.getBlockZ();

        List<String> lore = new ArrayList<>();
        if (v.loc.getWorld() != null && v.loc.getWorld().equals(viewer.getWorld())) {
            lore.add("Distance: " + Math.round(Math.sqrt(v.loc.distanceSquared(viewer.getLocation()))) + " blocks");
        } else {
            lore.add("Distance: (other world)");
        }
        plugin.locks().getLockFromId(v.lockId).ifPresent(le -> lore.add("Trusted: " + le.trusted().size()));
        lore.add("Click to manage");

        Material icon = v.type.isItem() ? v.type : Material.CHEST;
        return button(icon, coords, lore, "manage_lock", v.lockId);
    }

    private ItemStack trustItem(UUID uuid, AccessLevel lvl, String lockId) {
        String name = nameOf(uuid);
        return head(uuid, name,
                List.of("Level: " + lvl, "Click to remove (confirm)"),
                "confirm_remove_trust",
                lockId + "|" + uuid);
    }

    private ItemStack hopperItem(Location loc, String hopperId) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return button(Material.HOPPER,
                world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                List.of("Break it to pick it up", "(Protected like locks)"),
                "noop",
                hopperId);
    }

    private ItemStack logItem(LogRecord r) {
        String t = DF.format(Instant.ofEpochMilli(r.createdAt()));
        String name = r.action() + " | " + r.actorName();
        List<String> lore = new ArrayList<>();
        lore.add("Time: " + t);
        lore.add("World: " + r.worldName());
        lore.add("Coords: " + r.x() + " " + r.y() + " " + r.z());
        if (r.extra() != null && !r.extra().isBlank()) lore.add("Extra: " + r.extra());
        lore.add("");
        lore.add("Click: set compass");
        lore.add("Shift-click: teleport (OP) (confirm)");

        return button(Material.PAPER, name, lore, "log_entry", String.valueOf(r.id()));
    }

    private ItemStack toggle(String name, boolean on, String action) {
        // Use colored glass panes + Adventure API colors (legacy codes ignored by Component.text())
        Material indicator = on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        net.kyori.adventure.text.Component displayName = Component.text(name + " \u2014 ")
                .append(Component.text(on ? "ON" : "OFF",
                        on ? NamedTextColor.GREEN : NamedTextColor.RED));
        ItemStack it = new ItemStack(indicator);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(displayName);
        meta.lore(List.of(Component.text("Click to toggle")));
        meta.getPersistentDataContainer().set(plugin.keyGuiAction(), PersistentDataType.STRING, action);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack info(Material mat, String name, List<String> lore) {
        return button(mat, name, lore, "noop", "");
    }

    private ItemStack button(Material mat, String name, List<String> lore, String action, String context) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(Component::text).toList());
        meta.getPersistentDataContainer().set(plugin.keyGuiAction(), PersistentDataType.STRING, action);
        if (context != null && !context.isEmpty()) {
            meta.getPersistentDataContainer().set(plugin.keyGuiContext(), PersistentDataType.STRING, context);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack head(UUID uuid, String name, List<String> lore, String action, String context) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta base = it.getItemMeta();
        if (base instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.displayName(Component.text(name));
            meta.lore(lore.stream().map(Component::text).toList());
            meta.getPersistentDataContainer().set(plugin.keyGuiAction(), PersistentDataType.STRING, action);
            if (context != null && !context.isEmpty()) {
                meta.getPersistentDataContainer().set(plugin.keyGuiContext(), PersistentDataType.STRING, context);
            }
            it.setItemMeta(meta);
            return it;
        }
        return button(Material.PLAYER_HEAD, name, lore, action, context);
    }

    private void fillGlass(Inventory inv, Material mat) {
        ItemStack glass = new ItemStack(mat);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    private void soundClick(Player p) {
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    // Helper location builder without chunk loads
    private static final class LockUtil {
        private static Location toLocation(LockKey key) {
            // Return null-world Location for unloaded worlds; callers must handle null world gracefully.
            org.bukkit.World w = Bukkit.getWorld(key.worldUuid());
            return new Location(w, key.x(), key.y(), key.z());
        }
    }
}
