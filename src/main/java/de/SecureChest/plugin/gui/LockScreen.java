package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
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
import static de.SecureChest.plugin.gui.GuiHelper.*;

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
                Component.text("§6Meine Schlösser §8(§f" + (state.page + 1)
                        + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inv);

        if (views.isEmpty()) {
            boolean hasAny = plugin.locks().countLocks(player.getUniqueId()) > 0;
            if (hasAny) {
                inv.setItem(22, h.buildInfoItem(Material.COMPASS, "§eKeine Schlösser für aktive Filter",
                        List.of(formatInfoLine("Versuche Welt- oder Typ-Filter zu ändern."),
                                formatActionLine("Filter zurücksetzen: Welt=ALLE, Typ=ALLE"))));
            } else {
                inv.setItem(22, h.buildInfoItem(Material.CHEST, "§eNoch keine gesperrten Blöcke",
                        List.of(formatInfoLine("Chest Key craften und Rechtsklick auf Kiste/Barrel/Shulker."),
                                formatHintLine("Rezept im Hauptmenü ansehen."))));
            }
        }

        int start = state.page * pageSize;
        int end   = Math.min(views.size(), start + pageSize);
        for (int i = start; i < end; i++)
            inv.setItem(i - start, buildLockListEntry(player, views.get(i)));

        inv.setItem(45, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        inv.setItem(47, h.buildButton(Material.HOPPER, "§fSortierung: §e" + state.sort,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_sort", ""));
        inv.setItem(48, h.buildButton(Material.COMPASS, "§fWelt: §e" + state.world,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_world", ""));
        inv.setItem(49, h.buildButton(Material.ITEM_FRAME, "§fTyp: §e" + state.type,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_type", ""));

        if (state.page > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + state.page), "locks_page", String.valueOf(state.page - 1)));
        if (state.page < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (state.page + 2)), "locks_page", String.valueOf(state.page + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    static boolean canSeeLockManage(boolean isOwner, boolean isAdmin, boolean isTrusted) {
        return isOwner || isAdmin || isTrusted;
    }

    static boolean canManageLock(boolean isOwner, boolean isAdmin) {
        return isOwner || isAdmin;
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
        boolean isAdmin   = player.hasPermission(Permissions.ADMIN);
        boolean isTrusted = lockEntry.trusted().containsKey(player.getUniqueId());
        if (!canSeeLockManage(isOwner, isAdmin, isTrusted)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openLocksList(player, 0);
            return;
        }

        boolean canManage = canManageLock(isOwner, isAdmin);

        String blockName  = lockEntry.blockType().name().replace('_', ' ');
        Location lockLoc  = new Location(Bukkit.getWorld(lockEntry.key().worldUuid()),
                lockEntry.key().x(), lockEntry.key().y(), lockEntry.key().z());
        String worldName  = lockLoc.getWorld() != null ? lockLoc.getWorld().getName() : "?";
        String ownerLabel = isOwner ? " §a(deins)"
                : " §7– Besitzer: §f" + h.resolvePlayerName(lockEntry.owner());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCK_MANAGE, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("§6Schloss verwalten §8– §f" + blockName));
        holder.setInventory(inv);

        inv.setItem(10, h.buildInfoItem(
                lockEntry.blockType().isItem() ? lockEntry.blockType() : Material.CHEST,
                "§f" + blockName + ownerLabel,
                List.of(formatInfoLine("Welt:     §f" + worldName),
                        formatInfoLine("Position: §f" + lockLoc.getBlockX()
                                + " " + lockLoc.getBlockY() + " " + lockLoc.getBlockZ()),
                        formatInfoLine("Gesperrt: §f"
                                + DATE_FMT.format(Instant.ofEpochMilli(lockEntry.createdAt()))))));

        inv.setItem(12, h.buildButton(Material.COMPASS, "§fStorage anpingen",
                List.of(formatInfoLine("Richtungsanzeige über der Hotbar aktivieren")),
                "ping_lock", lockId));
        inv.setItem(13, h.buildButton(Material.PAPER, "§fKoordinaten kopieren",
                List.of(formatInfoLine("Koordinaten in den Chat schreiben"),
                        formatActionLine("Klicken zum Kopieren")), "copy_lock", lockId));

        if (canManage) {
            inv.setItem(16, h.buildButton(Material.BARRIER, "§cSchloss entfernen",
                    List.of(formatWarningLine("Entfernt dieses Schloss dauerhaft"),
                            formatHintLine("Bestätigung erforderlich — Undo verfügbar")),
                    "confirm_unlock_one", lockId));
            inv.setItem(15, h.buildButton(Material.ENDER_PEARL, "§fEigentum übertragen",
                    List.of(formatInfoLine("An anderen Spieler übergeben"),
                            formatWarningLine("Vertrauensliste wird zurückgesetzt"),
                            formatHintLine("Undo danach verfügbar")),
                    "transfer_lock_name", lockId));
            inv.setItem(29, h.buildButton(Material.PLAYER_HEAD, "§fSpieler in der Nähe vertrauen",
                    List.of(formatInfoLine("Vertrauen aus Online-Spielern in der Nähe wählen")),
                    "open_trust_nearby", lockId));
            inv.setItem(31, h.buildButton(Material.SPYGLASS, "§fViewer per Name hinzufügen",
                    List.of(formatInfoLine("Kann Inhalt anzeigen, nichts bewegen"),
                            formatActionLine("Name im Chat eingeben")),
                    "add_trust_name", "VIEW|" + lockId));
            inv.setItem(33, h.buildButton(Material.CHEST, "§fStocker per Name hinzufügen",
                    List.of(formatInfoLine("Kann Items nehmen und legen, nichts abbauen"),
                            formatActionLine("Name im Chat eingeben")),
                    "add_trust_name", "STOCKER|" + lockId));
        }

        List<Map.Entry<UUID, AccessLevel>> trustList = lockEntry.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name())).toList();
        int trustCount = trustList.size();
        String countLabel = trustCount == 0 ? "§7Keine vertrauten Spieler"
                : "§f" + trustCount + " vertraute" + (trustCount == 1 ? "r Spieler" : " Spieler")
                  + (trustCount > 9 ? " §8(erste 9 angezeigt)" : "");
        inv.setItem(35, h.buildInfoItem(Material.NAME_TAG, countLabel, List.of()));

        int trustSlot = 36;
        for (var e : trustList) {
            if (trustSlot >= 45) break;
            inv.setItem(trustSlot++, buildTrustListEntry(e.getKey(), e.getValue(), lockId, canManage));
        }

        inv.setItem(45, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Schlossliste"), "back_locks", ""));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openTrustNearby(Player player, String lockId) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_NEARBY, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("§6Spieler in der Nähe – Vertrauen vergeben"));
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
                    ? List.of(formatInfoLine("Bereits vertraut als: §f" + buildAccessLevelLabel(cur)),
                              formatActionLine("Klicken um Rolle zu ändern oder zu entfernen"))
                    : List.of(formatActionLine("Klicken um Vertrauensrolle zuzuweisen"));
            inv.setItem(slot++, h.buildPlayerHeadButton(p.getUniqueId(), p.getName(), lore,
                    "open_trust_role", lockId + "|" + p.getUniqueId()));
        }

        if (nearby.isEmpty())
            inv.setItem(22, h.buildInfoItem(Material.BARRIER, "§eKeine Spieler in der Nähe",
                    List.of(formatInfoLine("Keine anderen Spieler innerhalb von " + (int) radius + " Blöcken."),
                            formatHintLine("Näher herangehen oder 'per Name hinzufügen' verwenden."))));

        inv.setItem(45, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Schlossverwaltung"), "back_lock_manage", lockId));
        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openTrustRole(Player player, String lockId, UUID targetUuid) {
        ChestSecureHolder holder = new ChestSecureHolder(
                ChestSecureHolder.GuiId.TRUST_ROLE, lockId + "|" + targetUuid);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("§6Vertrauensrolle wählen"));
        holder.setInventory(inv);

        String name = h.resolvePlayerName(targetUuid);
        AccessLevel cur = plugin.locks().getLockFromId(lockId)
                .map(e -> e.trusted().get(targetUuid)).orElse(null);
        String statusLine = cur == null ? formatInfoLine("Aktuell nicht vertraut")
                : formatInfoLine("Aktuell: §f" + buildAccessLevelLabel(cur));

        inv.setItem(11, h.buildButton(Material.SPYGLASS, "§fViewer: §e" + name,
                List.of(formatInfoLine("Kann Inhalt anzeigen, nichts bewegen"),
                        statusLine, formatActionLine("Klicken zum Vergeben")),
                "set_trust_role", "VIEW|" + lockId + "|" + targetUuid));
        inv.setItem(15, h.buildButton(Material.CHEST, "§fStocker: §e" + name,
                List.of(formatInfoLine("Kann Items nehmen und legen, nichts abbauen"),
                        statusLine, formatActionLine("Klicken zum Vergeben")),
                "set_trust_role", "STOCKER|" + lockId + "|" + targetUuid));

        if (cur != null)
            inv.setItem(26, h.buildButton(Material.BARRIER,
                    "§cZugriff entfernen: §e" + name,
                    List.of(formatWarningLine("Entfernt alle Zugriffsrechte"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_remove_trust", lockId + "|" + targetUuid));

        inv.setItem(18, h.buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Spielerauswahl"), "back_trust_nearby", lockId));
        h.fillEmptySlots(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private record LockView(String lockId, Location location, Material blockType,
                            int trustedCount, long createdAt) {}

    private ItemStack buildLockListEntry(Player viewer, LockView v) {
        String world = v.location().getWorld() != null ? v.location().getWorld().getName() : "?";
        List<String> lore = new ArrayList<>();
        lore.add("§7" + world + "  §f" + v.location().getBlockX()
                + " " + v.location().getBlockY() + " " + v.location().getBlockZ());
        if (v.location().getWorld() != null && v.location().getWorld().equals(viewer.getWorld()))
            lore.add("§8Entfernung: §f"
                    + Math.round(Math.sqrt(v.location().distanceSquared(viewer.getLocation()))) + " Blöcke");
        else
            lore.add("§8Andere Welt");
        if (v.trustedCount() > 0)
            lore.add("§8Vertraute Spieler: §f" + v.trustedCount());
        lore.add(formatActionLine("Klicken zum Verwalten"));
        Material icon = v.blockType().isItem() ? v.blockType() : Material.CHEST;
        return h.buildButton(icon, "§f" + v.blockType().name().replace('_', ' '),
                lore, "manage_lock", v.lockId());
    }

    private ItemStack buildTrustListEntry(UUID uuid, AccessLevel level,
                                          String lockId, boolean canManage) {
        String name  = h.resolvePlayerName(uuid);
        String role  = buildAccessLevelLabel(level);
        if (canManage)
            return h.buildPlayerHeadButton(uuid, name,
                    List.of(formatInfoLine("Rolle: §f" + role),
                            formatWarningLine("Klicken um Zugriff zu entziehen"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_remove_trust", lockId + "|" + uuid);
        return h.buildPlayerHeadButton(uuid, name,
                List.of(formatInfoLine("Rolle: §f" + role)), "noop", "");
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

    private boolean matchesTypeFilter(Material type, UiStateStore.LockTypeFilter filter) {
        return switch (filter) {
            case ALL      -> true;
            case CHESTS   -> type == Material.CHEST || type == Material.TRAPPED_CHEST;
            case BARRELS  -> type == Material.BARREL;
            case SHULKERS -> type.name().endsWith("SHULKER_BOX");
        };
    }

    static String buildAccessLevelLabel(AccessLevel level) {
        return switch (level) {
            case VIEW     -> "Viewer";
            case STOCKER  -> "Stocker";
        };
    }
}
