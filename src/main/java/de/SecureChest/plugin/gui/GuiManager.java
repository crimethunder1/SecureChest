package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

public final class GuiManager {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String LOG_SEPARATOR_LINE = "§8────────────────────────────";

    private final ChestSecurePlugin plugin;

    public GuiManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        de.SecureChest.plugin.lock.LockStats lockStats = plugin.locks().getLockStats(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.MAIN, "");
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("§6⚿ §lChestSecure"));
        holder.setInventory(inventory);

        boolean quickLockEnabled   = plugin.locks().getQuickLock(player.getUniqueId());
        boolean quickRemoveEnabled = plugin.locks().getQuickRemove(player.getUniqueId());

        inventory.setItem(10, buildButton(Material.CHEST, "§fMeine gesperrten Blöcke",
                List.of(formatInfoLine("Schlösser anzeigen und verwalten"),
                        formatInfoLine(lockStats.totalLocks() + " Schloss/Schlösser vorhanden")),
                "open_locks", ""));
        inventory.setItem(11, buildButton(Material.WRITABLE_BOOK, "§fMeine Statistiken",
                List.of(formatInfoLine("Übersicht über alle deine Schlösser")),
                "open_stats", ""));
        inventory.setItem(12, buildButton(Material.HOPPER, "§fMeine Secure Hopper",
                List.of(formatInfoLine("Secure Hopper anzeigen"),
                        formatInfoLine(lockStats.totalSecureHoppers() + " Secure Hopper vorhanden")),
                "open_hoppers", ""));

        inventory.setItem(14, buildButton(Material.TRIPWIRE_HOOK, "§fRezept: Chest Key",
                List.of(formatInfoLine("Crafting-Rezept anzeigen")), "open_recipe_key", ""));
        inventory.setItem(16, buildButton(Material.HOPPER, "§fRezept: Secure Hopper",
                List.of(formatInfoLine("Crafting-Rezept anzeigen")), "open_recipe_hopper", ""));

        inventory.setItem(28, buildToggleButton("Quick Lock", quickLockEnabled, "toggle_quicklock",
                quickLockEnabled
                        ? "Rechtsklick mit leerer Hand sperrt sofort."
                        : "Rechtsklick mit Chest Key öffnet Bestätigungsfenster."));
        inventory.setItem(30, buildToggleButton("Quick Remove", quickRemoveEnabled, "toggle_quickremove",
                quickRemoveEnabled
                        ? "Rechtsklick mit Chest Key entsperrt sofort."
                        : "Rechtsklick mit Chest Key öffnet Bestätigungsfenster."));

        inventory.setItem(32, buildButton(Material.BARRIER, "§cAlle Schlösser entfernen",
                List.of(formatWarningLine("Entfernt alle deine Schlösser und Secure Hopper"),
                        formatHintLine("Bestätigung erforderlich — Undo danach verfügbar")),
                "confirm_unlock_all_self", ""));
        inventory.setItem(34, buildButton(Material.BOOK, "§fHilfe",
                List.of(formatInfoLine("Anleitung zur Nutzung von ChestSecure")), "open_help", ""));

        int undoCount = plugin.undos().count(player.getUniqueId());
        if (undoCount > 0) {
            String undoLabel = undoCount == 1 ? "Letzte Aktion rückgängig"
                    : "Rückgängig (" + undoCount + " Schritte verfügbar)";
            inventory.setItem(31, buildButton(Material.SLIME_BALL, "§f" + undoLabel,
                    List.of(formatInfoLine("Letztes Entsperren oder Transfer rückgängig machen"),
                            formatInfoLine(undoCount + " Snapshot(s) im Verlauf"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_undo_restore", ""));
        }

        if (player.hasPermission("chestsecure.admin")) {
            inventory.setItem(49, buildButton(Material.NETHER_STAR, "§6Admin-Panel",
                    List.of(formatInfoLine("Admin-Werkzeuge — alle Schlösser verwalten")), "open_admin", ""));
        }

        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openHelp(Player player) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HELP, "");
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6⚿ §lChestSecure – Hilfe"));
        holder.setInventory(inventory);

        inventory.setItem(10, buildInfoItem(Material.TRIPWIRE_HOOK, "§fSperren", List.of(
                "§71) Chest Key craften",
                "§72) Rechtsklick auf Kiste/Barrel/Shulker",
                "§73) Nur du (oder vertraute Spieler) können öffnen"
        )));
        inventory.setItem(12, buildInfoItem(Material.HOPPER, "§fSecure Hopper", List.of(
                "§7Secure Hopper mit Chest Key craften",
                "§7In Richtung eines gesperrten Behälters platzieren",
                "§7Nur Secure Hopper können Items bewegen"
        )));
        inventory.setItem(14, buildInfoItem(Material.LIME_DYE, "§aQuick Lock", List.of(
                "§7Wenn aktiv:",
                "§7- Rechtsklick mit LEERER HAND sperrt sofort",
                "§7  (Chest Key muss im Inventar sein)",
                "§7- Ideal für schnelles Einrichten der Basis"
        )));
        inventory.setItem(16, buildInfoItem(Material.RED_DYE, "§cQuick Remove", List.of(
                "§7Wenn aktiv:",
                "§7- Rechtsklick mit Chest Key entsperrt sofort",
                "§7- Kein Bestätigungsfenster"
        )));
        inventory.setItem(28, buildInfoItem(Material.SHIELD, "§fSchutz", List.of(
                "§7Schutz vor TNT, Creeper, Bett",
                "§7Wither/Dragon-Schaden",
                "§7Kolben & Feuer/Lava",
                "§7Normale Hopper können nicht durchgreifen"
        )));
        inventory.setItem(31, buildInfoItem(Material.PLAYER_HEAD, "§fVertrauensstufen", List.of(
                "§7VIEWER: Nur Inhalt anzeigen",
                "§7STOCKER: Items nehmen/legen, nicht abbauen",
                "§7CO-OWNER: Gleicher Zugriff wie Besitzer"
        )));

        inventory.setItem(40, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        fillEmptySlotsWithGlassPane(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openLocksList(Player player, int page) {
        UiStateStore.LockListState lockListState = plugin.ui().locks(player.getUniqueId());
        lockListState.page = Math.max(0, page);

        List<Location> allLockLocations = plugin.locks().listLocks(player.getUniqueId());
        List<LockView> lockViews = new ArrayList<>(allLockLocations.size());
        for (Location location : allLockLocations) {
            if (location.getWorld() == null) continue;
            LockKey lockKey    = LockKey.fromLocation(location);
            String lockId      = lockKey.asId();
            LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
            Material blockType = lockEntry != null ? lockEntry.blockType() : Material.CHEST;
            int trustedCount   = lockEntry != null ? lockEntry.trusted().size() : 0;
            long createdAt     = lockEntry != null ? lockEntry.createdAt() : 0L;
            lockViews.add(new LockView(lockId, location, blockType, trustedCount, createdAt));
        }

        if (lockListState.world == UiStateStore.WorldFilter.CURRENT) {
            lockViews.removeIf(lockView -> lockView.location().getWorld() == null
                    || !lockView.location().getWorld().equals(player.getWorld()));
        }
        lockViews.removeIf(lockView -> !matchesTypeFilter(lockView.blockType(), lockListState.type));
        lockViews.sort(buildLockComparator(player, lockListState.sort));

        int pageSize = 45;
        int maxPage  = Math.max(0, (lockViews.size() - 1) / pageSize);
        lockListState.page = Math.max(0, Math.min(lockListState.page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCKS_LIST, "");
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6Meine Schlösser §8(§f" + (lockListState.page + 1)
                        + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inventory);

        int startIndex = lockListState.page * pageSize;
        int endIndex   = Math.min(lockViews.size(), startIndex + pageSize);

        if (lockViews.isEmpty()) {
            boolean hasAnyLocks = !plugin.locks().listLocks(player.getUniqueId()).isEmpty();
            if (hasAnyLocks) {
                inventory.setItem(22, buildInfoItem(Material.COMPASS,
                        "§eKeine Schlösser für aktive Filter",
                        List.of(formatInfoLine("Versuche Welt- oder Typ-Filter zu ändern."),
                                formatActionLine("Filter zurücksetzen: Welt=ALLE, Typ=ALLE"))));
            } else {
                inventory.setItem(22, buildInfoItem(Material.CHEST,
                        "§eNoch keine gesperrten Blöcke",
                        List.of(formatInfoLine("Chest Key craften und Rechtsklick auf eine Kiste,"),
                                formatInfoLine("Barrel, Hopper oder Shulker Box."),
                                formatHintLine("Rezept im Hauptmenü ansehen."))));
            }
        }

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            inventory.setItem(slot++, buildLockListEntry(player, lockViews.get(i)));
        }

        inventory.setItem(45, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        inventory.setItem(47, buildButton(Material.HOPPER, "§fSortierung: §e" + lockListState.sort,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_sort", ""));
        inventory.setItem(48, buildButton(Material.COMPASS, "§fWelt: §e" + lockListState.world,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_world", ""));
        inventory.setItem(49, buildButton(Material.ITEM_FRAME, "§fTyp: §e" + lockListState.type,
                List.of("§7Klicken zum Wechseln"), "locks_cycle_type", ""));

        if (lockListState.page > 0)
            inventory.setItem(52, buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + lockListState.page),
                    "locks_page", String.valueOf(lockListState.page - 1)));
        if (lockListState.page < maxPage)
            inventory.setItem(53, buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (lockListState.page + 2)),
                    "locks_page", String.valueOf(lockListState.page + 1)));

        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openLockManage(Player player, String lockId) {
        Optional<LockEntry> lockEntryOptional = plugin.locks().getLockFromId(lockId);
        if (lockEntryOptional.isEmpty()) {
            player.sendMessage(Component.text(plugin.msg().get("schloss-nicht-gefunden"), NamedTextColor.RED));
            openLocksList(player, 0);
            return;
        }
        LockEntry lockEntry    = lockEntryOptional.get();
        Location lockLocation  = GuiLockUtil.toLocation(lockEntry.key());
        boolean isOwner        = lockEntry.owner().equals(player.getUniqueId());
        boolean isAdmin        = player.hasPermission("chestsecure.admin");
        boolean canManage      = isOwner || isAdmin;

        String blockDisplayName = lockEntry.blockType().name().replace('_', ' ');
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOCK_MANAGE, lockId);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6Schloss verwalten §8– §f" + blockDisplayName));
        holder.setInventory(inventory);

        String ownerLabel = isOwner
                ? " §a(deins)"
                : " §7– Besitzer: §f" + resolvePlayerName(lockEntry.owner());
        String worldName  = lockLocation.getWorld() != null ? lockLocation.getWorld().getName() : "?";

        inventory.setItem(10, buildInfoItem(
                lockEntry.blockType().isItem() ? lockEntry.blockType() : Material.CHEST,
                "§f" + blockDisplayName + ownerLabel,
                List.of(
                        formatInfoLine("Welt:     §f" + worldName),
                        formatInfoLine("Position: §f" + lockLocation.getBlockX()
                                + " " + lockLocation.getBlockY() + " " + lockLocation.getBlockZ()),
                        formatInfoLine("Gesperrt: §f"
                                + DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(lockEntry.createdAt())))
                )));

        inventory.setItem(12, buildButton(Material.COMPASS, "§fStorage anpingen",
                List.of(formatInfoLine("Richtungsanzeige über der Hotbar aktivieren")),
                "ping_lock", lockId));
        inventory.setItem(13, buildButton(Material.PAPER, "§fKoordinaten kopieren",
                List.of(formatInfoLine("Koordinaten in den Chat schreiben"),
                        formatActionLine("Klicken zum Kopieren")), "copy_lock", lockId));

        if (canManage) {
            inventory.setItem(16, buildButton(Material.BARRIER, "§cSchloss entfernen",
                    List.of(formatWarningLine("Entfernt dieses Schloss dauerhaft"),
                            formatHintLine("Bestätigung erforderlich — Undo verfügbar")),
                    "confirm_unlock_one", lockId));
            inventory.setItem(15, buildButton(Material.ENDER_PEARL, "§fEigentum übertragen",
                    List.of(formatInfoLine("Dieses Schloss an einen anderen Spieler übergeben"),
                            formatWarningLine("Vertrauensliste wird dabei zurückgesetzt"),
                            formatHintLine("Undo danach verfügbar")),
                    "transfer_lock_name", lockId));
            inventory.setItem(28, buildButton(Material.PLAYER_HEAD, "§fSpieler in der Nähe hinzufügen",
                    List.of(formatInfoLine("Vertrauen aus Online-Spielern in der Nähe wählen")),
                    "open_trust_nearby", lockId));
            inventory.setItem(30, buildButton(Material.MAP, "§fViewer hinzufügen §8(per Name)",
                    List.of(formatInfoLine("Kann Inhalt anzeigen"),
                            formatInfoLine("Kann keine Items nehmen oder legen"),
                            formatActionLine("Name im Chat eingeben")),
                    "add_trust_name", "VIEW|" + lockId));
            inventory.setItem(31, buildButton(Material.CHEST, "§fStocker hinzufügen §8(per Name)",
                    List.of(formatInfoLine("Kann Items nehmen und legen"),
                            formatInfoLine("Kann Block nicht abbauen"),
                            formatActionLine("Name im Chat eingeben")),
                    "add_trust_name", "STOCKER|" + lockId));
            inventory.setItem(32, buildButton(Material.DIAMOND, "§fCo-Owner hinzufügen §8(per Name)",
                    List.of(formatInfoLine("Voller Zugriff — wie Besitzer"),
                            formatInfoLine("Kann Block nicht abbauen"),
                            formatActionLine("Name im Chat eingeben")),
                    "add_trust_name", "CO_OWNER|" + lockId));
        }

        int trustSlot = 36;
        List<Map.Entry<UUID, AccessLevel>> trustList = lockEntry.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name()))
                .toList();

        int trustPlayerCount = trustList.size();
        String trustCountLabel = trustPlayerCount == 0
                ? "§7Keine vertrauten Spieler"
                : "§f" + trustPlayerCount + " vertraute"
                + (trustPlayerCount == 1 ? "r Spieler" : " Spieler")
                + (trustPlayerCount > 9 ? " §8(erste 9 angezeigt)" : "");
        inventory.setItem(35, buildInfoItem(Material.NAME_TAG, trustCountLabel, List.of()));

        for (var trustEntry : trustList) {
            if (trustSlot >= 45) break;
            inventory.setItem(trustSlot++,
                    buildTrustListEntry(trustEntry.getKey(), trustEntry.getValue(), lockId, canManage));
        }

        inventory.setItem(45, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Schlossliste"), "back_locks", ""));
        fillEmptySlotsWithGlassPane(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openTrustNearby(Player player, String lockId) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.TRUST_NEARBY, lockId);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6Spieler in der Nähe – Vertrauen vergeben"));
        holder.setInventory(inventory);

        java.util.Map<UUID, AccessLevel> existingTrustMap = plugin.locks().getLockFromId(lockId)
                .map(lockEntry -> new java.util.HashMap<>(lockEntry.trusted()))
                .orElse(new java.util.HashMap<>());

        double nearbyRadius = plugin.cfg().trustNearbyRadius();
        List<Player> nearbyPlayers = player.getWorld().getPlayers().stream()
                .filter(otherPlayer -> !otherPlayer.getUniqueId().equals(player.getUniqueId()))
                .filter(otherPlayer -> otherPlayer.getLocation().distanceSquared(player.getLocation())
                        <= nearbyRadius * nearbyRadius)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (Player nearbyPlayer : nearbyPlayers) {
            if (slot >= 45) break;
            AccessLevel currentLevel = existingTrustMap.get(nearbyPlayer.getUniqueId());
            List<String> lore = new ArrayList<>();
            if (currentLevel != null) {
                lore.add(formatInfoLine("Bereits vertraut als: §f" + buildAccessLevelLabel(currentLevel)));
                lore.add(formatActionLine("Klicken um Rolle zu ändern oder zu entfernen"));
            } else {
                lore.add(formatActionLine("Klicken um Vertrauensrolle zuzuweisen"));
            }
            inventory.setItem(slot++, buildPlayerHeadButton(
                    nearbyPlayer.getUniqueId(), nearbyPlayer.getName(), lore,
                    "open_trust_role", lockId + "|" + nearbyPlayer.getUniqueId()));
        }

        if (nearbyPlayers.isEmpty()) {
            inventory.setItem(22, buildInfoItem(Material.BARRIER, "§eKeine Spieler in der Nähe",
                    List.of(formatInfoLine("Keine anderen Spieler innerhalb von "
                                    + (int) nearbyRadius + " Blöcken."),
                            formatHintLine("Näher herangehen oder 'per Name hinzufügen' verwenden."))));
        }

        inventory.setItem(45, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Schlossverwaltung"), "back_lock_manage", lockId));
        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openTrustRole(Player player, String lockId, UUID targetPlayerUuid) {
        ChestSecureHolder holder = new ChestSecureHolder(
                ChestSecureHolder.GuiId.TRUST_ROLE, lockId + "|" + targetPlayerUuid);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("§6Vertrauensrolle wählen"));
        holder.setInventory(inventory);

        String targetPlayerName = resolvePlayerName(targetPlayerUuid);
        AccessLevel currentLevel = plugin.locks().getLockFromId(lockId)
                .map(lockEntry -> lockEntry.trusted().get(targetPlayerUuid)).orElse(null);

        String currentStatusLine = currentLevel == null
                ? formatInfoLine("Aktuell nicht vertraut")
                : formatInfoLine("Aktuell: §f" + buildAccessLevelLabel(currentLevel));

        inventory.setItem(11, buildButton(Material.MAP, "§fViewer: §e" + targetPlayerName,
                List.of(formatInfoLine("Kann Inhalt anzeigen"),
                        formatInfoLine("Kann keine Items nehmen oder legen"),
                        currentStatusLine,
                        formatActionLine(currentLevel == AccessLevel.VIEW
                                ? "Klicken zum erneuten Vergeben" : "Klicken zum Vergeben")),
                "set_trust_role", "VIEW|" + lockId + "|" + targetPlayerUuid));
        inventory.setItem(13, buildButton(Material.CHEST, "§fStocker: §e" + targetPlayerName,
                List.of(formatInfoLine("Kann Items nehmen und legen"),
                        formatInfoLine("Kann Block nicht abbauen"),
                        currentStatusLine,
                        formatActionLine(currentLevel == AccessLevel.STOCKER
                                ? "Klicken zum erneuten Vergeben" : "Klicken zum Vergeben")),
                "set_trust_role", "STOCKER|" + lockId + "|" + targetPlayerUuid));
        inventory.setItem(15, buildButton(Material.DIAMOND, "§fCo-Owner: §e" + targetPlayerName,
                List.of(formatInfoLine("Voller Zugriff — wie Besitzer"),
                        formatInfoLine("Kann Block nicht abbauen"),
                        currentStatusLine,
                        formatActionLine(currentLevel == AccessLevel.CO_OWNER
                                ? "Klicken zum erneuten Vergeben" : "Klicken zum Vergeben")),
                "set_trust_role", "CO_OWNER|" + lockId + "|" + targetPlayerUuid));

        if (currentLevel != null) {
            inventory.setItem(26, buildButton(Material.BARRIER,
                    "§cZugriff entfernen: §e" + targetPlayerName,
                    List.of(formatWarningLine("Entfernt alle Zugriffsrechte dieses Spielers"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_remove_trust", lockId + "|" + targetPlayerUuid));
        }

        inventory.setItem(18, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zur Spielerauswahl"), "back_trust_nearby", lockId));
        fillEmptySlotsWithGlassPane(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openHoppersList(Player player, int page) {
        List<Location> hopperLocations = plugin.locks().listSecureHoppers(player.getUniqueId());
        int pageSize = 45;
        int maxPage  = hopperLocations.isEmpty() ? 0 : Math.max(0, (hopperLocations.size() - 1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.HOPPERS_LIST, "");
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6Meine Secure Hopper §8(§f" + (page + 1) + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inventory);

        int startIndex = page * pageSize;
        int endIndex   = Math.min(hopperLocations.size(), startIndex + pageSize);

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Location location = hopperLocations.get(i);
            if (location.getWorld() == null) continue;
            String hopperId = LockKey.fromLocation(location).asId();
            inventory.setItem(slot++, buildHopperListEntry(location, hopperId));
        }

        inventory.setItem(45, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        if (page > 0)
            inventory.setItem(52, buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + page), "hoppers_page", String.valueOf(page - 1)));
        if (page < maxPage)
            inventory.setItem(53, buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (page + 2)), "hoppers_page", String.valueOf(page + 1)));

        if (hopperLocations.isEmpty()) {
            inventory.setItem(22, buildInfoItem(Material.BARRIER, "§eKeine Secure Hopper vorhanden",
                    List.of("§7Einen Secure Hopper auf deinen", "§7gesperrten Behälter ausrichten.")));
        }

        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openRecipePreview(Player player, boolean showKeyRecipe) {
        ShapedRecipe recipe = showKeyRecipe
                ? plugin.recipes().chestKeyRecipe()
                : plugin.recipes().secureHopperRecipe();
        if (recipe == null) {
            player.sendMessage(Component.text(plugin.msg().get("rezept-nicht-verfuegbar"), NamedTextColor.RED));
            openMain(player);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(
                showKeyRecipe ? ChestSecureHolder.GuiId.RECIPE_KEY : ChestSecureHolder.GuiId.RECIPE_HOPPER, "");
        Inventory inventory = Bukkit.createInventory(holder, 45,
                Component.text(showKeyRecipe ? "§6Rezept: Chest Key" : "§6Rezept: Secure Hopper"));
        holder.setInventory(inventory);

        int[] recipeGridSlots = {11, 12, 13, 20, 21, 22, 29, 30, 31};
        String[] shapeRows = recipe.getShape();
        Map<Character, ItemStack> ingredientMap = new HashMap<>();

        for (Map.Entry<Character, RecipeChoice> choiceEntry : recipe.getChoiceMap().entrySet()) {
            RecipeChoice choice = choiceEntry.getValue();
            if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
                ingredientMap.put(choiceEntry.getKey(), exactChoice.getChoices().get(0));
            } else if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
                ingredientMap.put(choiceEntry.getKey(), new ItemStack(materialChoice.getChoices().get(0)));
            }
        }

        int gridIndex = 0;
        for (int row = 0; row < 3; row++) {
            String shapeRow = (row < shapeRows.length) ? shapeRows[row] : "   ";
            for (int column = 0; column < 3; column++) {
                char shapeChar = (column < shapeRow.length()) ? shapeRow.charAt(column) : ' ';
                ItemStack ingredient = ingredientMap.get(shapeChar);
                inventory.setItem(recipeGridSlots[gridIndex++],
                        ingredient == null ? new ItemStack(Material.AIR) : ingredient.clone());
            }
        }

        inventory.setItem(24, recipe.getResult().clone());
        inventory.setItem(40, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        fillEmptySlotsWithGlassPane(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openStats(Player player) {
        de.SecureChest.plugin.lock.LockStats lockStats = plugin.locks().getLockStats(player.getUniqueId());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.STATS, "");
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("§6Statistiken – §f" + player.getName()));
        holder.setInventory(inventory);

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add("§7Gesperrte Blöcke:       §f" + lockStats.totalLocks());
        overviewLore.add("§7Secure Hopper:          §f" + lockStats.totalSecureHoppers());
        overviewLore.add("§7Vertrauensbeziehungen:  §f" + lockStats.totalTrustedRelations());
        if (lockStats.oldestLockAt() > 0) {
            overviewLore.add("§7Ältestes Schloss: §f"
                    + DATE_TIME_FORMATTER.format(java.time.Instant.ofEpochMilli(lockStats.oldestLockAt())));
        }
        if (lockStats.newestLockAt() > 0) {
            overviewLore.add("§7Neuestes Schloss: §f"
                    + DATE_TIME_FORMATTER.format(java.time.Instant.ofEpochMilli(lockStats.newestLockAt())));
        }
        inventory.setItem(4, buildInfoItem(Material.NETHER_STAR,
                "§f" + player.getName() + "§7s Übersicht", overviewLore));

        inventory.setItem(10, buildInfoItem(Material.CHEST,              "§fKisten: §e"            + lockStats.chestCount(),         List.of()));
        inventory.setItem(11, buildInfoItem(Material.TRAPPED_CHEST,      "§fFallenkisten: §e"       + lockStats.trappedChestCount(),  List.of()));
        inventory.setItem(12, buildInfoItem(Material.BARREL,             "§fFässer: §e"             + lockStats.barrelCount(),        List.of()));
        inventory.setItem(13, buildInfoItem(Material.PURPLE_SHULKER_BOX, "§fShulker-Boxen: §e"      + lockStats.shulkerCount(),       List.of()));
        inventory.setItem(14, buildInfoItem(Material.FURNACE,            "§fGesperrte Hopper: §e"   + lockStats.hopperCount(),
                List.of(formatInfoLine("Direkt mit Chest Key gesperrt"))));
        inventory.setItem(15, buildInfoItem(Material.HOPPER,             "§fSecure Hopper: §e"      + lockStats.totalSecureHoppers(),
                List.of(formatInfoLine("Gecraftete Secure Hopper an einem Schloss"))));

        inventory.setItem(22, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));
        fillEmptySlotsWithGlassPane(inventory, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission("chestsecure.admin")) {
            player.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            openMain(player);
            return;
        }

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN, "");
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6⚿ §lChestSecure Admin"));
        holder.setInventory(inventory);

        boolean inspectModeEnabled = plugin.ui().inspect(player.getUniqueId());

        inventory.setItem(19, buildButton(Material.NETHER_STAR, "§fAdmin-Key erhalten",
                List.of("§7Klicken um Admin-Key zu empfangen"), "give_admin_key", ""));
        inventory.setItem(21, buildButton(Material.WRITTEN_BOOK, "§fAktivitätslog als Buch",
                List.of(formatInfoLine("Neueste Log-Einträge als Buch")), "give_logs_book", ""));
        inventory.setItem(23, buildButton(Material.WRITTEN_BOOK, "§fServer-Bericht",
                List.of(formatInfoLine("Vollständige Server-Schlossübersicht als Buch")),
                "give_admin_report", ""));
        inventory.setItem(25, buildButton(Material.PAPER, "§fLogs durchsuchen",
                List.of(formatInfoLine("Aktivitätslogs filtern und durchsuchen"),
                        formatInfoLine("Klick → Kompass  |  Shift+Klick → Teleport (OP)")),
                "open_logs", ""));
        inventory.setItem(28, buildToggleButton("Inspect-Modus", inspectModeEnabled, "toggle_inspect",
                inspectModeEnabled
                        ? "Schleichen + Rechtsklick zeigt Schloss-Informationen."
                        : "Aktivieren um Behälter per Schleich-Klick zu untersuchen."));
        inventory.setItem(31, buildButton(Material.BARRIER, "§cGlobal entsperren",
                List.of(formatWarningLine("Alle Schlösser eines bestimmten Spielers entfernen"),
                        formatHintLine("Spielername im Chat — Bestätigung erforderlich")),
                "global_unlock", ""));
        inventory.setItem(49, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Hauptmenü"), "back_main", ""));

        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openLogs(Player player, int page) {
        if (!player.hasPermission("chestsecure.admin")) {
            player.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            openMain(player);
            return;
        }

        UiStateStore.LogsState logsState = plugin.ui().logs(player.getUniqueId());
        logsState.page = Math.max(0, page);

        String actionFilterString = logsState.action.databaseValue();
        long minimumTimestamp     = logsState.time.minTimestamp();
        String actorNameFilter = logsState.actorFilter == null ? "" : logsState.actorFilter;

        int pageSize  = 45;
        int totalLogs = plugin.locks().countLogs(actionFilterString, minimumTimestamp, actorNameFilter);
        int maxPage   = Math.max(0, (totalLogs - 1) / pageSize);
        logsState.page = Math.max(0, Math.min(logsState.page, maxPage));

        int pageOffset = logsState.page * pageSize;
        List<LogRecord> logRecords = plugin.locks().listLogs(
                actionFilterString, minimumTimestamp, actorNameFilter, pageOffset, pageSize);

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.LOGS_LIST, "");
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("§6Aktivitätslogs §8(§f" + (logsState.page + 1)
                        + "§8/§f" + (maxPage + 1) + "§8)"));
        holder.setInventory(inventory);

        int slot = 0;
        for (LogRecord logRecord : logRecords) {
            if (slot >= 45) break;
            inventory.setItem(slot++, buildLogListEntry(logRecord));
        }

        inventory.setItem(45, buildButton(Material.ARROW, "§fZurück",
                List.of("§7Zurück zum Admin-Panel"), "back_admin", ""));

        String actionFilterDisplayLabel = logsState.action.label();
        String timeFilterDisplayLabel   = logsState.time.label();

        inventory.setItem(47, buildButton(Material.BOOK,
                "§fAktion: " + actionFilterDisplayLabel,
                List.of("§7Klicken zum Wechseln"), "logs_cycle_action", ""));
        inventory.setItem(48, buildButton(Material.CLOCK,
                "§fZeitraum: §e" + timeFilterDisplayLabel,
                List.of("§7Klicken zum Wechseln"), "logs_cycle_time", ""));
        inventory.setItem(49, buildButton(Material.PLAYER_HEAD, "§fSpielerfilter",
                List.of(actorNameFilter.isEmpty() ? "§8(nicht gesetzt)" : "§f" + actorNameFilter,
                        "§7Klicken zum Setzen (Name tippen)",
                        "§7'clear' zum Zurücksetzen"), "logs_set_actor", ""));

        if (logsState.page > 0)
            inventory.setItem(52, buildButton(Material.PAPER, "§fVorherige Seite",
                    List.of("§7Seite " + logsState.page),
                    "logs_page", String.valueOf(logsState.page - 1)));
        if (logsState.page < maxPage)
            inventory.setItem(53, buildButton(Material.PAPER, "§fNächste Seite",
                    List.of("§7Seite " + (logsState.page + 2)),
                    "logs_page", String.valueOf(logsState.page + 1)));

        fillEmptySlotsWithGlassPane(inventory, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    public void openConfirm(Player player, String title, List<String> lines) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.CONFIRM, "");
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("§6Bestätigung: §f" + title));
        holder.setInventory(inventory);

        inventory.setItem(11, buildButton(Material.RED_STAINED_GLASS_PANE,
                "§cAbbrechen", List.of("§7Nein, abbrechen"), "confirm_no", ""));
        inventory.setItem(15, buildButton(Material.LIME_STAINED_GLASS_PANE,
                "§aBestätigen", List.of("§aJa, fortfahren"), "confirm_yes", ""));

        int timeoutSeconds = plugin.cfg().confirmTimeoutSeconds();
        List<String> linesWithTimeout = new ArrayList<>(lines);
        linesWithTimeout.add("");
        linesWithTimeout.add("§e▶ Läuft ab in " + timeoutSeconds + "s — schnell handeln!");
        inventory.setItem(13, buildInfoItem(Material.PAPER, "§f" + title, linesWithTimeout));
        fillEmptySlotsWithGlassPane(inventory, Material.BLACK_STAINED_GLASS_PANE);

        player.openInventory(inventory);
        playMenuClickSound(player);
    }

    private record LockView(String lockId, Location location, Material blockType,
                            int trustedCount, long createdAt) {}

    private Comparator<LockView> buildLockComparator(Player player, UiStateStore.LockSort sortMode) {
        return switch (sortMode) {
            case WORLD -> Comparator
                    .comparing((LockView lockView) ->
                            lockView.location().getWorld() != null
                                    ? lockView.location().getWorld().getName() : "")
                    .thenComparingInt(lockView -> lockView.location().getBlockX())
                    .thenComparingInt(lockView -> lockView.location().getBlockY())
                    .thenComparingInt(lockView -> lockView.location().getBlockZ());
            case TYPE -> Comparator
                    .comparing((LockView lockView) -> lockView.blockType().name())
                    .thenComparingInt(lockView -> lockView.location().getBlockX())
                    .thenComparingInt(lockView -> lockView.location().getBlockY())
                    .thenComparingInt(lockView -> lockView.location().getBlockZ());
            case RECENT -> Comparator
                    .comparingLong((LockView lockView) -> lockView.createdAt()).reversed();
            case DISTANCE -> Comparator.comparingDouble((LockView lockView) -> {
                if (lockView.location().getWorld() == null
                        || !lockView.location().getWorld().equals(player.getWorld()))
                    return Double.MAX_VALUE;
                return lockView.location().distanceSquared(player.getLocation());
            });
        };
    }

    private boolean matchesTypeFilter(Material blockType, UiStateStore.LockTypeFilter typeFilter) {
        return switch (typeFilter) {
            case ALL      -> true;
            case CHESTS   -> blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST;
            case BARRELS  -> blockType == Material.BARREL;
            case SHULKERS -> blockType.name().endsWith("SHULKER_BOX");
        };
    }

    private ItemStack buildLockListEntry(Player viewer, LockView lockView) {
        String worldName = lockView.location().getWorld() != null
                ? lockView.location().getWorld().getName() : "?";
        String blockDisplayName = lockView.blockType().name().replace('_', ' ');

        List<String> lore = new ArrayList<>();
        lore.add("§7" + worldName + "  §f"
                + lockView.location().getBlockX() + " "
                + lockView.location().getBlockY() + " "
                + lockView.location().getBlockZ());
        if (lockView.location().getWorld() != null
                && lockView.location().getWorld().equals(viewer.getWorld())) {
            long distanceBlocks = Math.round(
                    Math.sqrt(lockView.location().distanceSquared(viewer.getLocation())));
            lore.add("§8Entfernung: §f" + distanceBlocks + " Blöcke");
        } else {
            lore.add("§8Andere Welt");
        }
        if (lockView.trustedCount() > 0)
            lore.add("§8Vertraute Spieler: §f" + lockView.trustedCount());
        lore.add(formatActionLine("Klicken zum Verwalten"));

        Material iconMaterial = lockView.blockType().isItem() ? lockView.blockType() : Material.CHEST;
        return buildButton(iconMaterial, "§f" + blockDisplayName, lore, "manage_lock", lockView.lockId());
    }

    private ItemStack buildTrustListEntry(UUID playerUuid, AccessLevel accessLevel,
                                          String lockId, boolean canManage) {
        String playerName = resolvePlayerName(playerUuid);
        String roleLabel  = buildAccessLevelLabel(accessLevel);

        if (canManage) {
            return buildPlayerHeadButton(playerUuid, playerName,
                    List.of(formatInfoLine("Rolle: §f" + roleLabel),
                            formatWarningLine("Klicken um Zugriff zu entziehen"),
                            formatHintLine("Bestätigung erforderlich")),
                    "confirm_remove_trust", lockId + "|" + playerUuid);
        }
        return buildPlayerHeadButton(playerUuid, playerName,
                List.of(formatInfoLine("Rolle: §f" + roleLabel)),
                "noop", "");
    }

    private ItemStack buildHopperListEntry(Location location, String hopperId) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "?";
        return buildButton(Material.HOPPER,
                "§f" + worldName + " §7"
                        + location.getBlockX() + " "
                        + location.getBlockY() + " "
                        + location.getBlockZ(),
                List.of(formatHintLine("Aufheben durch Abbauen"),
                        formatInfoLine("Geschützt vor Explosionen und Hoppern")),
                "noop", hopperId);
    }

    private ItemStack buildLogListEntry(LogRecord record) {
        Material iconMaterial = switch (record.action()) {
            case "UNAUTHORIZED_BREAK" -> Material.RED_CONCRETE;
            case "ADMIN_BREAK"        -> Material.ORANGE_CONCRETE;
            case "LOCK"               -> Material.LIME_CONCRETE;
            case "UNLOCK"             -> Material.YELLOW_CONCRETE;
            default                   -> Material.PAPER;
        };

        String actionColorCode = switch (record.action()) {
            case "UNAUTHORIZED_BREAK" -> "§c";
            case "ADMIN_BREAK"        -> "§6";
            case "LOCK"               -> "§a";
            case "UNLOCK"             -> "§e";
            default                   -> "§7";
        };

        String actionLabel = switch (record.action()) {
            case "UNAUTHORIZED_BREAK" -> "Unerlaubter Abbauversuch";
            case "ADMIN_BREAK"        -> "Admin-Abbau";
            case "LOCK"               -> "Gesperrt";
            case "UNLOCK"             -> "Entsperrt";
            default                   -> record.action();
        };

        String relativeTimeLabel = buildRelativeTimeLabel(record.createdAt());
        String absoluteTimeLabel = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(record.createdAt()));

        String entryTitle = actionColorCode + actionLabel
                + "  §8│  §f" + record.actorName()
                + "  §8│  §7" + relativeTimeLabel;

        List<String> lore = new ArrayList<>();
        lore.add(LOG_SEPARATOR_LINE);
        lore.add(formatInfoLine("Spieler:  §f" + record.actorName()));
        lore.add(formatInfoLine("Aktion:   " + actionColorCode + actionLabel));
        lore.add(formatInfoLine("Zeit:     §f" + absoluteTimeLabel));
        lore.add(formatInfoLine("          §7(" + relativeTimeLabel + ")"));
        lore.add(formatInfoLine("Ort:      §f" + record.worldName()
                + "  " + record.x() + " " + record.y() + " " + record.z()));
        if (record.extra() != null && !record.extra().isBlank()) {
            lore.add(formatInfoLine("Hinweis:  §f" + record.extra()));
        }
        lore.add(LOG_SEPARATOR_LINE);
        lore.add(formatActionLine("Klick → Kompass setzen"));
        lore.add(formatActionLine("Shift+Klick → Teleport (OP, Bestätigung)"));

        return buildButton(iconMaterial, entryTitle, lore, "log_entry", String.valueOf(record.id()));
    }

    private String buildRelativeTimeLabel(long timestampMillis) {
        long ageMinutes = (System.currentTimeMillis() - timestampMillis) / 60_000L;
        if (ageMinutes < 1)  return "Gerade eben";
        if (ageMinutes < 60) return "vor " + ageMinutes + " Min";
        long ageHours = ageMinutes / 60;
        if (ageHours < 24)   return "vor " + ageHours + " Std";
        long ageDays = ageHours / 24;
        return "vor " + ageDays + (ageDays == 1 ? " Tag" : " Tagen");
    }

    private String buildAccessLevelLabel(AccessLevel accessLevel) {
        return switch (accessLevel) {
            case VIEW     -> "Viewer";
            case STOCKER  -> "Stocker";
            case CO_OWNER -> "Co-Owner";
        };
    }

    private ItemStack buildToggleButton(String label, boolean isEnabled,
                                        String action, String description) {
        Material glassPaneMaterial = isEnabled
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE;
        net.kyori.adventure.text.Component displayName = Component.text(label + " — ")
                .append(Component.text(isEnabled ? "AN" : "AUS",
                        isEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        ItemStack itemStack = new ItemStack(glassPaneMaterial);
        ItemMeta itemMeta   = itemStack.getItemMeta();
        itemMeta.displayName(displayName);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            lore.add(Component.text(formatInfoLine(description)));
        }
        lore.add(Component.text(formatActionLine("Klicken zum Umschalten")));
        itemMeta.lore(lore);
        itemMeta.getPersistentDataContainer().set(
                plugin.keyGuiAction(), PersistentDataType.STRING, action);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack buildInfoItem(Material material, String name, List<String> lore) {
        return buildButton(material, name, lore, "noop", "");
    }

    private ItemStack buildButton(Material material, String name, List<String> lore,
                                  String action, String context) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta   = itemStack.getItemMeta();
        itemMeta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty())
            itemMeta.lore(lore.stream().map(Component::text).toList());
        itemMeta.getPersistentDataContainer().set(
                plugin.keyGuiAction(), PersistentDataType.STRING, action);
        if (context != null && !context.isEmpty()) {
            itemMeta.getPersistentDataContainer().set(
                    plugin.keyGuiContext(), PersistentDataType.STRING, context);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack buildPlayerHeadButton(UUID playerUuid, String displayName,
                                            List<String> lore, String action, String context) {
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta   = itemStack.getItemMeta();
        if (baseMeta instanceof SkullMeta skullMeta) {
            // Nur gecachte Spieler-Daten verwenden – getOfflinePlayer(uuid) kann
            // den Main-Thread blockieren (I/O / Mojang-API). Der Skull-Skin
            // bleibt leer wenn der Spieler nicht bekannt ist; das ist akzeptabel.
            org.bukkit.OfflinePlayer offlinePlayer =
                    Bukkit.getOfflinePlayerIfCached(playerUuid.toString());
            if (offlinePlayer == null) {
                org.bukkit.entity.Player online = Bukkit.getPlayer(playerUuid);
                if (online != null) offlinePlayer = online;
            }
            if (offlinePlayer != null) skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.displayName(Component.text(displayName));
            skullMeta.lore(lore.stream().map(Component::text).toList());
            skullMeta.getPersistentDataContainer().set(
                    plugin.keyGuiAction(), PersistentDataType.STRING, action);
            if (context != null && !context.isEmpty()) {
                skullMeta.getPersistentDataContainer().set(
                        plugin.keyGuiContext(), PersistentDataType.STRING, context);
            }
            itemStack.setItemMeta(skullMeta);
            return itemStack;
        }
        return buildButton(Material.PLAYER_HEAD, displayName, lore, action, context);
    }

    private void fillEmptySlotsWithGlassPane(Inventory inventory, Material glassPaneMaterial) {
        ItemStack glassPane = new ItemStack(glassPaneMaterial);
        ItemMeta glassMeta  = glassPane.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glassPane.setItemMeta(glassMeta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, glassPane);
        }
    }

    private String resolvePlayerName(UUID playerUuid) {
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null) return onlinePlayer.getName();
        org.bukkit.OfflinePlayer cachedPlayer =
                Bukkit.getOfflinePlayerIfCached(playerUuid.toString());
        if (cachedPlayer != null && cachedPlayer.getName() != null) return cachedPlayer.getName();
        return playerUuid.toString().substring(0, 8) + "…";
    }

    private static String formatInfoLine(String text)    { return "§8" + text; }
    private static String formatHintLine(String text)    { return "§e" + text; }
    private static String formatActionLine(String text)  { return "§b" + text; }
    private static String formatWarningLine(String text) { return "§c" + text; }

    private void playMenuClickSound(Player player) {
        if (plugin.cfg().soundsEnabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    private static final class GuiLockUtil {
        private static Location toLocation(LockKey lockKey) {
            org.bukkit.World world = Bukkit.getWorld(lockKey.worldUuid());
            return new Location(world, lockKey.x(), lockKey.y(), lockKey.z());
        }
    }
}