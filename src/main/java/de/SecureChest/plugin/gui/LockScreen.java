package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.ui.UiStateStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class LockScreen {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;
    private final GuiManager        mgr;

    LockScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager) {
        this.plugin = plugin;
        this.h      = helper;
        this.mgr    = manager;
    }

    void openLocksList(Player player, int page) {
        UiStateStore.LockListState state = plugin.ui().locks(player.getUniqueId());
        state.page = Math.max(0, page);

        List<Location> all = plugin.locks().listLocks(player.getUniqueId());
        List<LockView> views = new ArrayList<>(all.size());
        for (Location loc : all) {
            if (loc.getWorld() == null) continue;
            LockKey key     = LockKey.fromLocation(loc);
            String  id      = key.asId();
            LockEntry entry = plugin.locks().getLockFromId(id).orElse(null);
            views.add(new LockView(id, loc,
                    entry != null ? entry.blockType()       : Material.CHEST,
                    entry != null ? entry.trusted().size()  : 0,
                    entry != null ? entry.createdAt()       : 0L));
        }

        if (state.world == UiStateStore.WorldFilter.CURRENT)
            views.removeIf(v -> v.location().getWorld() == null
                    || !v.location().getWorld().equals(player.getWorld()));
        views.removeIf(v -> !matchesTypeFilter(v.blockType(), state.type));
        views.sort(buildLockComparator(player, state.sort));

        int pageSize = 45;
        int maxPage  = Math.max(0, (views.size() - 1) / pageSize);
        state.page   = Math.max(0, Math.min(state.page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCKS_LIST, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-lock-list-title", state.page + 1, maxPage + 1)));
        holder.setInventory(inv);

        if (views.isEmpty()) {
            boolean hasAny = plugin.locks().countLocks(player.getUniqueId()) > 0;
            if (hasAny) {
                inv.setItem(22, h.buildInfoItem(Material.COMPASS, text("gui-lock-list-filter-empty-name"),
                        lines("gui-lock-list-filter-empty-lore")));
            } else {
                inv.setItem(22, h.buildInfoItem(Material.CHEST, text("gui-lock-list-empty-name"),
                        lines("gui-lock-list-empty-lore")));
            }
        }

        int start = state.page * pageSize;
        int end   = Math.min(views.size(), start + pageSize);
        for (int i = start; i < end; i++)
            inv.setItem(i - start, buildLockListEntry(player, views.get(i)));

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-main-lore"), "back_main", ""));
        inv.setItem(47, h.buildButton(Material.HOPPER, text("gui-lock-list-sort-name", text(state.sort.labelKey())),
                lines("gui-cycle-lore"), "locks_cycle_sort", ""));
        inv.setItem(48, h.buildButton(Material.COMPASS, text("gui-lock-list-world-name", text(state.world.labelKey())),
                lines("gui-cycle-lore"), "locks_cycle_world", ""));
        inv.setItem(49, h.buildButton(Material.ITEM_FRAME, text("gui-lock-list-type-name", text(state.type.labelKey())),
                lines("gui-cycle-lore"), "locks_cycle_type", ""));

        if (state.page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, text("gui-page-previous-name"),
                    lines("gui-page-lore", state.page), "locks_page", String.valueOf(state.page - 1)));
        if (state.page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, text("gui-page-next-name"),
                    lines("gui-page-lore", state.page + 2), "locks_page", String.valueOf(state.page + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    static boolean canSeeLockManage(boolean isOwner, boolean isTrusted) {
        return isOwner || isTrusted;
    }

    static boolean canManageLock(boolean isOwner) {
        return isOwner;
    }

    void openLockManage(Player player, String lockId) {
        LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
        if (lockEntry == null) {
            player.sendMessage(Component.text(
                    plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
            mgr.openLocksList(player, 0);
            return;
        }

        boolean isOwner   = lockEntry.owner().equals(player.getUniqueId());
        boolean isTrusted = lockEntry.trusted().containsKey(player.getUniqueId());
        if (!canSeeLockManage(isOwner, isTrusted)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openLocksList(player, 0);
            return;
        }

        boolean canManage = canManageLock(isOwner);

        String blockName  = lockEntry.blockType().name().replace('_', ' ');
        Location lockLoc  = new Location(Bukkit.getWorld(lockEntry.key().worldUuid()),
                lockEntry.key().x(), lockEntry.key().y(), lockEntry.key().z());
        String worldName  = lockLoc.getWorld() != null ? lockLoc.getWorld().getName() : "?";
        String infoName   = isOwner
                ? text("gui-lock-manage-info-name-own", blockName)
                : text("gui-lock-manage-info-name-other", blockName, h.resolvePlayerName(lockEntry.owner()));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCK_MANAGE, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-lock-manage-title", blockName)));
        holder.setInventory(inv);

        inv.setItem(10, h.buildInfoItem(
                lockEntry.blockType().isItem() ? lockEntry.blockType() : Material.CHEST,
                infoName,
                lines("gui-lock-manage-info-lore", worldName,
                        lockLoc.getBlockX(), lockLoc.getBlockY(), lockLoc.getBlockZ(),
                        DATE_FMT.format(Instant.ofEpochMilli(lockEntry.createdAt())))));

        inv.setItem(13, buildPingButton(player, lockEntry, lockId));

        if (canManage) {
            inv.setItem(16, h.buildButton(Material.BARRIER, text("gui-lock-manage-remove-name"),
                    lines("gui-lock-manage-remove-lore"),
                    "confirm_unlock_one", lockId));
            inv.setItem(15, h.buildButton(Material.ENDER_PEARL, text("gui-lock-manage-transfer-name"),
                    lines("gui-lock-manage-transfer-lore"),
                    "transfer_lock_name", lockId));
            inv.setItem(29, h.buildButton(Material.PLAYER_HEAD, text("gui-lock-manage-trust-nearby-name"),
                    lines("gui-lock-manage-trust-nearby-lore"),
                    "open_trust_nearby", lockId));
            inv.setItem(31, h.buildButton(Material.SPYGLASS, text("gui-lock-manage-add-viewer-name"),
                    lines("gui-lock-manage-add-viewer-lore"),
                    "add_trust_name", "VIEW|" + lockId));
            inv.setItem(33, h.buildButton(Material.CHEST, text("gui-lock-manage-add-stocker-name"),
                    lines("gui-lock-manage-add-stocker-lore"),
                    "add_trust_name", "STOCKER|" + lockId));
        }

        List<Map.Entry<UUID, AccessLevel>> trustList = lockEntry.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name())).toList();
        int trustCount = trustList.size();
        String countLabel = trustCount == 0 ? text("gui-lock-manage-trusted-none")
                : trustCount == 1 ? text("gui-lock-manage-trusted-one", trustCount)
                : trustCount > 9  ? text("gui-lock-manage-trusted-many-limited", trustCount)
                : text("gui-lock-manage-trusted-many", trustCount);
        inv.setItem(35, h.buildInfoItem(Material.NAME_TAG, countLabel, List.of()));

        int trustSlot = 36;
        for (var e : trustList) {
            if (trustSlot >= 45) break;
            inv.setItem(trustSlot++, buildTrustListEntry(e.getKey(), e.getValue(), lockId, canManage));
        }

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-lock-list-lore"), "back_locks", ""));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    ItemStack buildPingButton(Player player, LockEntry lockEntry, String lockId) {
        boolean pingActive = plugin.ping().isPinging(player.getUniqueId(), lockEntry.key());
        return h.buildButton(Material.RECOVERY_COMPASS,
                text(pingActive ? "gui-lock-manage-ping-stop-name" : "gui-lock-manage-ping-name"),
                lines(pingActive ? "gui-lock-manage-ping-stop-lore" : "gui-lock-manage-ping-lore"),
                "ping_lock", lockId);
    }

    void openTrustNearby(Player player, String lockId) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_NEARBY, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-trust-nearby-title")));
        holder.setInventory(inv);

        Map<UUID, AccessLevel> existing = plugin.locks().getLockFromId(lockId)
                .map(e -> new HashMap<>(e.trusted()))
                .orElse(new HashMap<>());

        double radius = plugin.cfg().trustNearbyRadius();
        List<Player> nearby = player.getWorld().getPlayers().stream()
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= radius * radius)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (Player p : nearby) {
            if (slot >= 45) break;
            AccessLevel cur = existing.get(p.getUniqueId());
            List<String> lore = cur != null
                    ? lines("gui-trust-nearby-trusted-lore", buildAccessLevelLabel(cur))
                    : lines("gui-trust-nearby-untrusted-lore");
            inv.setItem(slot++, h.buildPlayerHeadButton(p.getUniqueId(), p.getName(), lore,
                    "open_trust_role", lockId + "|" + p.getUniqueId()));
        }

        if (nearby.isEmpty())
            inv.setItem(22, h.buildInfoItem(Material.BARRIER, text("gui-trust-nearby-empty-name"),
                    lines("gui-trust-nearby-empty-lore", (int) radius)));

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-lock-manage-lore"), "back_lock_manage", lockId));
        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openTrustRole(Player player, String lockId, UUID targetUuid) {
        ChestSecureHolder holder = new ChestSecureHolder(
                ChestSecureHolder.GuiId.TRUST_ROLE, lockId + "|" + targetUuid);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(text("gui-trust-role-title")));
        holder.setInventory(inv);

        String name = h.resolvePlayerName(targetUuid);
        AccessLevel cur = plugin.locks().getLockFromId(lockId)
                .map(e -> e.trusted().get(targetUuid)).orElse(null);
        String statusLine = cur == null ? text("gui-trust-role-status-none")
                : text("gui-trust-role-status-current", buildAccessLevelLabel(cur));

        inv.setItem(11, h.buildButton(Material.SPYGLASS, text("gui-trust-role-viewer-name", name),
                lines("gui-trust-role-viewer-lore", statusLine),
                "set_trust_role", "VIEW|" + lockId + "|" + targetUuid));
        inv.setItem(15, h.buildButton(Material.CHEST, text("gui-trust-role-stocker-name", name),
                lines("gui-trust-role-stocker-lore", statusLine),
                "set_trust_role", "STOCKER|" + lockId + "|" + targetUuid));

        if (cur != null)
            inv.setItem(26, h.buildButton(Material.BARRIER,
                    text("gui-trust-role-remove-name", name),
                    lines("gui-trust-role-remove-lore"),
                    "confirm_remove_trust", lockId + "|" + targetUuid));

        inv.setItem(18, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-trust-nearby-lore"), "back_trust_nearby", lockId));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private record LockView(String lockId, Location location, Material blockType,
                            int trustedCount, long createdAt) {}

    private ItemStack buildLockListEntry(Player viewer, LockView v) {
        String world = v.location().getWorld() != null ? v.location().getWorld().getName() : "?";
        List<String> lore = new ArrayList<>();
        lore.add(text("gui-lock-list-entry-location", world,
                v.location().getBlockX(), v.location().getBlockY(), v.location().getBlockZ()));
        if (v.location().getWorld() != null && v.location().getWorld().equals(viewer.getWorld()))
            lore.add(text("gui-lock-list-entry-distance",
                    Math.round(Math.sqrt(v.location().distanceSquared(viewer.getLocation())))));
        else
            lore.add(text("gui-lock-list-entry-other-world"));
        if (v.trustedCount() > 0)
            lore.add(text("gui-lock-list-entry-trusted", v.trustedCount()));
        lore.add(text("gui-lock-list-entry-click"));
        Material icon = v.blockType().isItem() ? v.blockType() : Material.CHEST;
        return h.buildButton(icon, text("gui-lock-list-entry-name", v.blockType().name().replace('_', ' ')),
                lore, "manage_lock", v.lockId());
    }

    private ItemStack buildTrustListEntry(UUID uuid, AccessLevel level,
                                          String lockId, boolean canManage) {
        String name  = h.resolvePlayerName(uuid);
        String role  = buildAccessLevelLabel(level);
        if (canManage)
            return h.buildPlayerHeadButton(uuid, name,
                    lines("gui-lock-manage-trusted-entry-manage-lore", role),
                    "confirm_remove_trust", lockId + "|" + uuid);
        return h.buildPlayerHeadButton(uuid, name,
                lines("gui-lock-manage-trusted-entry-lore", role), "noop", "");
    }

    private Comparator<LockView> buildLockComparator(Player player, UiStateStore.LockSort sort) {
        return switch (sort) {
            case WORLD -> Comparator
                    .comparing((LockView v) -> v.location().getWorld() != null
                            ? v.location().getWorld().getName() : "")
                    .thenComparingInt(v -> v.location().getBlockX())
                    .thenComparingInt(v -> v.location().getBlockY())
                    .thenComparingInt(v -> v.location().getBlockZ());
            case TYPE -> Comparator
                    .comparing((LockView v) -> v.blockType().name())
                    .thenComparingInt(v -> v.location().getBlockX());
            case RECENT -> Comparator
                    .comparingLong((LockView v) -> v.createdAt()).reversed();
            case DISTANCE -> Comparator.comparingDouble((LockView v) -> {
                if (v.location().getWorld() == null
                        || !v.location().getWorld().equals(player.getWorld()))
                    return Double.MAX_VALUE;
                return v.location().distanceSquared(player.getLocation());
            });
        };
    }

    static boolean matchesTypeFilter(Material type, UiStateStore.LockTypeFilter filter) {
        return switch (filter) {
            case ALL                     -> true;
            case CHESTS                  -> LockUtil.isChestFamily(type);
            case BARRELS                 -> type == Material.BARREL;
            case SHULKERS                -> type.name().endsWith("SHULKER_BOX");
            case FURNACES                -> type == Material.FURNACE || type == Material.BLAST_FURNACE
                                            || type == Material.SMOKER;
            case DROPPERS_AND_DISPENSERS -> type == Material.DROPPER || type == Material.DISPENSER;
            case BREWING_STANDS          -> type == Material.BREWING_STAND;
            case CRAFTERS                -> type == Material.CRAFTER;
        };
    }

    private String buildAccessLevelLabel(AccessLevel level) {
        return switch (level) {
            case VIEW     -> text("gui-role-viewer");
            case STOCKER  -> text("gui-role-stocker");
        };
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
