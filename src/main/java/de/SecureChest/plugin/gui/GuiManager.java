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
import org.bukkit.Sound;
import org.bukkit.World;
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

    

    public void openMain(Player p) {
        // UX FIX: getLockStats iterates the cache once instead of two separate passes
        de.SecureChest.plugin.lock.LockStats stats = plugin.locks().getLockStats(p.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.MAIN, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("ChestSecure"));
        holder.setInventory(inv);

        inv.setItem(10, button(Material.CHEST, "My Locked Blocks",
                List.of(inf("View and manage your locks"), inf(stats.totalLocks() + " lock(s) owned")),
                "open_locks", ""));
        inv.setItem(11, button(Material.WRITABLE_BOOK, "My Statistics",
                List.of(inf("Overview of all your locks")),
                "open_stats", ""));
        inv.setItem(12, button(Material.HOPPER, "My Secure Hoppers",
                List.of(inf("View your secure hoppers"), inf(stats.totalSecureHoppers() + " secure hopper(s)")),
                "open_hoppers", ""));
        inv.setItem(14, button(Material.TRIPWIRE_HOOK, "Recipe: Chest Key",
                List.of(inf("Click to preview crafting recipe")), "open_recipe_key", ""));
        inv.setItem(16, button(Material.HOPPER, "Recipe: Secure Hopper",
                List.of(inf("Click to preview crafting recipe")), "open_recipe_hopper", ""));

        boolean ql = plugin.locks().getQuickLock(p.getUniqueId());
        boolean qr = plugin.locks().getQuickRemove(p.getUniqueId());
        inv.setItem(28, toggle("Quick Lock", ql, "toggle_quicklock",
                ql ? "Right-click any container with empty hand to instantly lock."
                   : "Right-click a block with a Chest Key to lock."));
        inv.setItem(30, toggle("Quick Remove", qr, "toggle_quickremove",
                qr ? "Right-click your locked block with a Chest Key to remove."
                   : "Use /cs to unlock via the management GUI."));

        inv.setItem(32, button(Material.BARRIER, "Unlock ALL",
                List.of(warn("Removes all your locks and secure hoppers"), hint("Requires confirmation — undo available afterwards")),
                "confirm_unlock_all_self", ""));
        inv.setItem(34, button(Material.BOOK, "Help",
                List.of(inf("How to use ChestSecure")), "open_help", ""));

        int undoCount = plugin.undos().count(p.getUniqueId());
        if (undoCount > 0) {
            String undoLabel = undoCount == 1 ? "Undo last action"
                    : "Undo (" + undoCount + " steps available)";
            inv.setItem(31, button(Material.SLIME_BALL, undoLabel,
                    List.of(inf("Restore the most recent unlock or transfer"), inf(undoCount + " snapshot(s) in history"), hint("Requires confirmation")),
                    "confirm_undo_restore", ""));
        }

        if (p.hasPermission("chestsecure.admin")) {
            inv.setItem(49, button(Material.NETHER_STAR, "Admin Panel",
                    List.of(inf("Admin tools — manage all locks")), "open_admin", ""));
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

    

    public void openLocksList(Player p, int page) {
        UiStateStore.LockListState st = plugin.ui().locks(p.getUniqueId());
        st.page = Math.max(0, page);

        List<Location> locks = plugin.locks().listLocks(p.getUniqueId());
        List<LockView> views = new ArrayList<>(locks.size());
        for (Location loc : locks) {
            if (loc.getWorld() == null) continue;
            LockKey key = LockKey.fromLocation(loc);
            String lockId = key.asId();
            LockEntry le = plugin.locks().getLockFromId(lockId).orElse(null);
            Material type = le != null ? le.blockType() : Material.CHEST;
            int trustedCount = le != null ? le.trusted().size() : 0;
            long createdAt   = le != null ? le.createdAt() : 0L;
            views.add(new LockView(lockId, loc, type, trustedCount, createdAt));
        }

        
        if (st.world == UiStateStore.WorldFilter.CURRENT) {
            views.removeIf(v -> v.loc.getWorld() == null || !v.loc.getWorld().equals(p.getWorld()));
        }
        views.removeIf(v -> !matchesTypeFilter(v.type, st.type));

        
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
            boolean hasAnyLocks = !plugin.locks().listLocks(p.getUniqueId()).isEmpty();
            if (hasAnyLocks) {
                // Locks exist but filtered out
                inv.setItem(22, info(Material.COMPASS, "No locks match current filters",
                        List.of(inf("Try changing World or Type filters below."),
                                action("Reset filters: set World=ALL, Type=ALL"))));
            } else {
                // Player has no locks at all
                inv.setItem(22, info(Material.CHEST, "No locked blocks yet",
                        List.of(inf("Craft a Chest Key and right-click a chest,"),
                                inf("barrel, hopper, or shulker box to lock it."),
                                hint("See the recipe in the main menu."))));
            }
        }

        int slot = 0;
        for (int i = start; i < end; i++) {
            LockView v = views.get(i);
            inv.setItem(slot++, lockItem(p, v));
        }

        
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
        Location loc = GuiLockUtil.toLocation(lock.key());
        boolean isOwner = lock.owner().equals(p.getUniqueId());
        boolean isAdmin = p.hasPermission("chestsecure.admin");
        boolean canManage = isOwner || isAdmin;

        String blockName = lock.blockType().name().replace('_', ' ');
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCK_MANAGE, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Manage " + blockName + (isOwner ? " (yours)" : "")));
        holder.setInventory(inv);

        String ownerTag = isOwner ? " (you)" : "";
        inv.setItem(10, info(lock.blockType().isItem() ? lock.blockType() : Material.CHEST,
                lock.blockType().name().replace('_', ' ') + (isOwner ? " §a(yours)" : " §7owned by " + nameOf(lock.owner())),
                List.of(
                        inf("World: " + (loc.getWorld() != null ? loc.getWorld().getName() : "?")),
                        inf("Coords: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()),
                        inf("Locked: " + DF.format(Instant.ofEpochMilli(lock.createdAt())))
                )));

        inv.setItem(12, button(Material.COMPASS, "Ping Location",
                List.of(inf("Sets your compass target to this block")), "ping_lock", lockId));
        inv.setItem(13, button(Material.PAPER, "Copy Coords",
                List.of(inf("Prints coordinates to chat"), action("Click to copy")), "copy_lock", lockId));

        // UX FIX U1: only show destructive/management buttons to owner or admin
        if (canManage) {
            inv.setItem(16, button(Material.BARRIER, "Remove Lock",
                    List.of(warn("Permanently removes this lock"), hint("Requires confirmation — undo available")), "confirm_unlock_one", lockId));

            inv.setItem(15, button(Material.ENDER_PEARL, "Transfer Ownership",
                    List.of(inf("Hand this lock to another player"), warn("Trust list will be reset"), hint("Undo available afterwards")),
                    "transfer_lock_name", lockId));

            // UX FIX U10: use human-readable role labels
            inv.setItem(28, button(Material.PLAYER_HEAD, "Add Trusted Player (nearby)",
                    List.of(inf("Pick from online players within range")), "open_trust_nearby", lockId));
            inv.setItem(30, button(Material.MAP, "Add Viewer (by name)",
                    List.of(inf("Can open and view contents"), inf("Cannot take or place items"), action("Type their name in chat")), "add_trust_name", "VIEW|" + lockId));
            inv.setItem(31, button(Material.CHEST, "Add Stocker (by name)",
                    List.of(inf("Can take and put items"), inf("Cannot break the block"), action("Type their name in chat")), "add_trust_name", "STOCKER|" + lockId));
            inv.setItem(32, button(Material.DIAMOND, "Add Co-owner (by name)",
                    List.of(inf("Full access — same as owner"), inf("Cannot break the block"), action("Type their name in chat")), "add_trust_name", "CO_OWNER|" + lockId));
        }

        int slot = 36;
        List<Map.Entry<UUID, AccessLevel>> trustList = lock.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name()))
                .collect(Collectors.toList());

        int trustTotal = trustList.size();
        String trustCountLabel = trustTotal == 0 ? "No trusted players"
                : trustTotal + " trusted player" + (trustTotal == 1 ? "" : "s") + (trustTotal > 9 ? " (first 9 shown)" : "");
        inv.setItem(35, info(Material.NAME_TAG, trustCountLabel, List.of()));

        for (var e : trustList) {
            if (slot >= 45) break;
            UUID uuid = e.getKey();
            AccessLevel lvl = e.getValue();
            inv.setItem(slot++, trustItem(uuid, lvl, lockId, canManage));
        }

        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to lock list"), "back_locks", ""));
        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openTrustNearby(Player p, String lockId) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_NEARBY, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Nearby Players — Add Trust"));
        holder.setInventory(inv);

        // Build a map of existing trust for this lock so we can show current roles
        java.util.Map<UUID, AccessLevel> existingTrust = plugin.locks().getLockFromId(lockId)
                .map(le -> new java.util.HashMap<>(le.trusted()))
                .orElse(new java.util.HashMap<>());

        double radius = plugin.getConfig().getDouble("ui.trust_nearby_radius", 32.0);
        List<Player> nearby = p.getWorld().getPlayers().stream()
                .filter(o -> !o.getUniqueId().equals(p.getUniqueId()))
                .filter(o -> o.getLocation().distanceSquared(p.getLocation()) <= radius * radius)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (Player t : nearby) {
            if (slot >= 45) break;
            AccessLevel current = existingTrust.get(t.getUniqueId());
            List<String> lore = new ArrayList<>();
            if (current != null) {
                String roleLabel = switch (current) {
                    case VIEW     -> "Viewer";
                    case STOCKER  -> "Stocker";
                    case CO_OWNER -> "Co-owner";
                };
                lore.add(inf("Already trusted as: " + roleLabel));
                lore.add(action("Click to change or remove role"));
            } else {
                lore.add(action("Click to assign a trust role"));
            }
            inv.setItem(slot++, head(t.getUniqueId(), t.getName(), lore,
                    "open_trust_role", lockId + "|" + t.getUniqueId()));
        }

        if (nearby.isEmpty()) {
            inv.setItem(22, info(Material.BARRIER, "No players nearby",
                    List.of(inf("No other players within " + (int) radius + " blocks."),
                            hint("Move closer, or use 'Add by name' in the lock menu."))));
        }

        inv.setItem(45, button(Material.ARROW, "Back", List.of("Return to lock management"), "back_lock_manage", lockId));
        fillGlass(inv, Material.GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    public void openTrustRole(Player p, String lockId, UUID target) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_ROLE, lockId + "|" + target);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Choose Trust Role"));
        holder.setInventory(inv);

        String name = nameOf(target);
        AccessLevel current = plugin.locks().getLockFromId(lockId)
                .map(le -> le.trusted().get(target)).orElse(null);

        String currentDesc = current == null ? inf("Not currently trusted")
                : inf("Currently: " + switch (current) {
                    case VIEW     -> "Viewer";
                    case STOCKER  -> "Stocker";
                    case CO_OWNER -> "Co-owner";
                });

        inv.setItem(11, button(Material.MAP, "Viewer: " + name,
                List.of(inf("Can open and view contents"), inf("Cannot take or place items"),
                        currentDesc, action(current == AccessLevel.VIEW ? "Click to reapply" : "Click to assign")),
                "set_trust_role", "VIEW|" + lockId + "|" + target));
        inv.setItem(13, button(Material.CHEST, "Stocker: " + name,
                List.of(inf("Can take and put items"), inf("Cannot break the block"),
                        currentDesc, action(current == AccessLevel.STOCKER ? "Click to reapply" : "Click to assign")),
                "set_trust_role", "STOCKER|" + lockId + "|" + target));
        inv.setItem(15, button(Material.DIAMOND, "Co-owner: " + name,
                List.of(inf("Full access — same as owner"), inf("Cannot break the block"),
                        currentDesc, action(current == AccessLevel.CO_OWNER ? "Click to reapply" : "Click to assign")),
                "set_trust_role", "CO_OWNER|" + lockId + "|" + target));

        // Show a "Remove Access" button if the player is currently trusted
        if (current != null) {
            inv.setItem(26, button(Material.BARRIER, "Remove Access: " + name,
                    List.of(warn("Remove all access for this player"), hint("Requires confirmation")),
                    "confirm_remove_trust", lockId + "|" + target));
        }

        inv.setItem(18, button(Material.ARROW, "Back", List.of("Return to nearby players"), "back_trust_nearby", lockId));
        fillGlass(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    

    public void openHoppersList(Player p, int page) {
        List<Location> hoppers = plugin.locks().listSecureHoppers(p.getUniqueId());
        int pageSize = 45;
        int maxPage = hoppers.isEmpty() ? 0 : Math.max(0, (hoppers.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HOPPERS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("My Secure Hoppers (" + (page + 1) + "/" + (maxPage + 1) + ")"));
        holder.setInventory(inv);

        int start = page * pageSize;
        int end = Math.min(hoppers.size(), start + pageSize);

        int slot = 0;
        for (int i = start; i < end; i++) {
            Location loc = hoppers.get(i);
            if (loc.getWorld() == null) continue; 
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

    

    // -------------------------------------------------------------------------
    // FEATURE: Lock Statistics screen
    // -------------------------------------------------------------------------

    public void openStats(Player p) {
        de.SecureChest.plugin.lock.LockStats st = plugin.locks().getLockStats(p.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.STATS, "");
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Lock Statistics"));
        holder.setInventory(inv);

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add("Total locks: " + st.totalLocks());
        overviewLore.add("Secure Hoppers: " + st.totalSecureHoppers());
        overviewLore.add("Trust relations: " + st.totalTrustedRelations());
        if (st.oldestLockAt() > 0) {
            overviewLore.add("Oldest lock: " + DF.format(java.time.Instant.ofEpochMilli(st.oldestLockAt())));
        }
        if (st.newestLockAt() > 0) {
            overviewLore.add("Newest lock: " + DF.format(java.time.Instant.ofEpochMilli(st.newestLockAt())));
        }
        inv.setItem(4, info(Material.NETHER_STAR, p.getName() + "'s Lock Overview", overviewLore));

        // UX FIX U7: different icons so Hopper (locked) and Secure Hopper are distinct
        inv.setItem(10, info(Material.CHEST,               "Chests: "          + st.chestCount(),        List.of()));
        inv.setItem(11, info(Material.TRAPPED_CHEST,       "Trapped Chests: "  + st.trappedChestCount(), List.of()));
        inv.setItem(12, info(Material.BARREL,              "Barrels: "         + st.barrelCount(),       List.of()));
        inv.setItem(13, info(Material.PURPLE_SHULKER_BOX,  "Shulker Boxes: "   + st.shulkerCount(),      List.of()));
        inv.setItem(14, info(Material.FURNACE,             "Locked Hoppers: "  + st.hopperCount(),       List.of(inf("Hoppers locked directly with a Chest Key"))));
        inv.setItem(15, info(Material.HOPPER,              "Secure Hoppers: "  + st.totalSecureHoppers(),List.of(inf("Crafted Secure Hopper items bound to a lock"))));

        inv.setItem(22, button(Material.ARROW, "Back", List.of("Return to main menu"), "back_main", ""));
        fillGlass(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        p.openInventory(inv);
        soundClick(p);
    }

    // -------------------------------------------------------------------------

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
        
        inv.setItem(21, button(Material.WRITTEN_BOOK, "Activity Log Book", List.of(inf("Latest log entries printed in a book")), "give_logs_book", ""));
        inv.setItem(23, button(Material.WRITTEN_BOOK, "Server Report", List.of(inf("Full server lock overview in a book")), "give_admin_report", ""));
        inv.setItem(25, button(Material.PAPER, "Browse Logs", List.of(inf("Filter and search activity logs"), inf("Click → compass  |  Shift+Click → teleport (OP)")), "open_logs", ""));

        inv.setItem(28, toggle("Inspect Mode", inspect, "toggle_inspect",
                inspect ? "Sneak + right-click any container to view its lock info."
                        : "Enable to sneak-click containers for lock details."));
        inv.setItem(31, button(Material.BARRIER, "Global Unlock", List.of(warn("Remove ALL locks of a specific player"), hint("Type player name in chat — requires confirmation")), "global_unlock", ""));

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

    

    private static final class LockView {
        final String lockId;
        final Location loc;
        final Material type;
        final int trusted;
        final long createdAt; // B6 FIX: store here so RECENT sort needs no cache lookups

        private LockView(String lockId, Location loc, Material type, int trusted, long createdAt) {
            this.lockId    = lockId;
            this.loc       = loc;
            this.type      = type;
            this.trusted   = trusted;
            this.createdAt = createdAt;
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
            // B6 FIX: createdAt is now stored in LockView — no getLockFromId calls during sort
            case RECENT -> Comparator.comparingLong((LockView v) -> v.createdAt).reversed();
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
        String world  = v.loc.getWorld() != null ? v.loc.getWorld().getName() : "?";
        // UX5: show block type as the item display name, coords in lore
        String typeName = v.type.name().replace('_', ' ');

        List<String> lore = new ArrayList<>();
        lore.add("§7" + world + "  " + v.loc.getBlockX() + " " + v.loc.getBlockY() + " " + v.loc.getBlockZ());
        if (v.loc.getWorld() != null && v.loc.getWorld().equals(viewer.getWorld())) {
            long dist = Math.round(Math.sqrt(v.loc.distanceSquared(viewer.getLocation())));
            lore.add("§8Distance: " + dist + " blocks");
        } else {
            lore.add("§8Other world");
        }
        if (v.trusted > 0) lore.add("§8Trusted players: " + v.trusted);
        lore.add("§eClick to manage");

        Material icon = v.type.isItem() ? v.type : Material.CHEST;
        return button(icon, typeName, lore, "manage_lock", v.lockId);
    }

    private ItemStack trustItem(UUID uuid, AccessLevel lvl, String lockId, boolean canManage) {
        String name = nameOf(uuid);
        // UX FIX U10: human-readable role label
        String roleLabel = switch (lvl) {
            case VIEW     -> "Viewer";
            case STOCKER  -> "Stocker";
            case CO_OWNER -> "Co-owner";
        };
        if (canManage) {
            return head(uuid, name,
                    List.of(inf("Role: " + roleLabel), warn("Click to revoke access"), hint("Requires confirmation")),
                    "confirm_remove_trust",
                    lockId + "|" + uuid);
        } else {
            return head(uuid, name,
                    List.of(inf("Role: " + roleLabel)),
                    "noop", "");
        }
    }

    private ItemStack hopperItem(Location loc, String hopperId) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return button(Material.HOPPER,
                world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                List.of(hint("Break to pick it up"), inf("Protected from explosions and hoppers")),
                "noop",
                hopperId);
    }

    private ItemStack logItem(LogRecord r) {
        String t = DF.format(Instant.ofEpochMilli(r.createdAt()));
        String name = r.action() + " | " + r.actorName();
        List<String> lore = new ArrayList<>();
        lore.add(inf("Time: " + t));
        lore.add(inf("World: " + r.worldName() + "  " + r.x() + " " + r.y() + " " + r.z()));
        if (r.extra() != null && !r.extra().isBlank()) lore.add(inf("Note: " + r.extra()));
        lore.add(action("Click → compass"));
        lore.add(action("Shift+Click → teleport (OP, confirm)"));

        return button(Material.PAPER, name, lore, "log_entry", String.valueOf(r.id()));
    }

    private ItemStack toggle(String name, boolean on, String action) {
        return toggle(name, on, action, null);
    }

    private ItemStack toggle(String name, boolean on, String action, String description) {
        Material indicator = on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        net.kyori.adventure.text.Component displayName = Component.text(name + " \u2014 ")
                .append(Component.text(on ? "ON" : "OFF",
                        on ? NamedTextColor.GREEN : NamedTextColor.RED));
        ItemStack it = new ItemStack(indicator);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(displayName);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            lore.add(Component.text(inf(description)));
        }
        lore.add(Component.text(action("Click to toggle")));
        meta.lore(lore);
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
            // getOfflinePlayer is non-blocking here: it only accesses Bukkit's name cache
            // (same as getOfflinePlayerIfCached). The SkullMeta uses it for skin texture
            // resolution, which happens asynchronously by the client.
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(uuid.toString());
            if (op == null) op = Bukkit.getOfflinePlayer(uuid); // fallback to cache-only fetch
            meta.setOwningPlayer(op);
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
        // B3 FIX: Bukkit.getOfflinePlayer(UUID) can do a blocking disk lookup for unknown UUIDs.
        // Use the non-blocking cache variant; fall back to a short UUID prefix.
        org.bukkit.entity.Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (cached != null && cached.getName() != null) return cached.getName();
        return uuid.toString().substring(0, 8) + "…";
    }

    // ── Lore colour helpers ──────────────────────────────────────────────────
    /** Dim gray — passive info text (coords, dates, counts). */
    private static String inf(String s) { return "§8" + s; }
    /** Yellow — advisory hints ("Requires confirmation", "Undo available"). */
    private static String hint(String s) { return "§e" + s; }
    /** Aqua — actionable prompt ("Click to …"). */
    private static String action(String s) { return "§b" + s; }
    /** Red — destructive warning. */
    private static String warn(String s) { return "§c" + s; }

    private void soundClick(Player p) {
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    
    private static final class GuiLockUtil {
        private static Location toLocation(LockKey key) {
            
            org.bukkit.World w = Bukkit.getWorld(key.worldUuid());
            return new Location(w, key.x(), key.y(), key.z());
        }
    }
}
