package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaCoordinateParser;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class AreaScreen {

    static final int PAGE_SIZE = 36;
    static final String RETURN_DESTINATION = "AREAS";

    private static final int FIRST_LIST_SLOT = 9;

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;
    private final GuiManager        mgr;

    AreaScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager) {
        this.plugin = plugin;
        this.h      = helper;
        this.mgr    = manager;
    }

    void openAreas(Player player, int page) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.sendMessage(Component.text(
                    plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            mgr.openMain(player);
            return;
        }

        List<Area> areas = plugin.areas().areas();
        int maxPage     = Math.max(0, (areas.size() - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, maxPage));

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.AREAS, "");
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-areas-title", currentPage + 1, maxPage + 1)));
        holder.setInventory(inv);

        boolean enabled = plugin.areas().isEnabled();
        inv.setItem(4, h.buildToggleButton(text("gui-areas-toggle-label"), enabled, "area_toggle",
                enabled ? text("gui-areas-toggle-on")
                        : text("gui-areas-toggle-off")));

        if (areas.isEmpty()) {
            inv.setItem(22, h.buildInfoItem(Material.MAP, text("gui-areas-empty-name"),
                    lines("gui-areas-empty-lore")));
        }

        int slot = FIRST_LIST_SLOT;
        int firstIndex = currentPage * PAGE_SIZE;
        int lastIndex  = Math.min(areas.size(), firstIndex + PAGE_SIZE);
        for (Area area : areas.subList(firstIndex, lastIndex)) {
            inv.setItem(slot++, buildAreaEntry(area));
        }

        inv.setItem(45, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-admin-lore"), "back_admin", ""));
        inv.setItem(49, h.buildButton(Material.EMERALD, text("gui-areas-create-name"),
                lines("gui-areas-create-lore"), "area_create", ""));

        if (currentPage > 0)
            inv.setItem(52, h.buildButton(Material.PAPER, text("gui-page-previous-name"),
                    lines("gui-page-lore", currentPage),
                    "areas_page", String.valueOf(currentPage - 1)));
        if (currentPage < maxPage)
            inv.setItem(53, h.buildButton(Material.PAPER, text("gui-page-next-name"),
                    lines("gui-page-lore", currentPage + 2),
                    "areas_page", String.valueOf(currentPage + 1)));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openChangeConfirm(Player player, AreaChangePreview preview) {
        AreaChange change = preview.change();
        List<String> confirmLines = new ArrayList<>();
        String title;
        switch (change) {
            case AreaChange.Enable _ -> {
                title = text("confirm-area-enable-title");
                confirmLines.add(text("confirm-area-enable-question"));
            }
            case AreaChange.Resize resize -> {
                title = text("confirm-area-resize-title");
                confirmLines.add(text("confirm-area-resize-question", resize.name()));
                confirmLines.add(text("confirm-area-resize-corners",
                        Math.min(resize.firstX(), resize.secondX()), Math.min(resize.firstZ(), resize.secondZ()),
                        Math.max(resize.firstX(), resize.secondX()), Math.max(resize.firstZ(), resize.secondZ())));
            }
            case AreaChange.Delete delete -> {
                title = text("confirm-area-delete-title");
                confirmLines.add(text("confirm-area-delete-question", delete.name()));
            }
            case AreaChange.Create _, AreaChange.Disable _ ->
                    throw new IllegalArgumentException("No confirmation dialog for area change " + change);
        }

        confirmLines.add("");
        confirmLines.addAll(lines("confirm-area-counts",
                preview.affectedLocks(), preview.affectedSecureHoppers(), preview.affectedOwners()));
        confirmLines.add("");
        if (change instanceof AreaChange.Enable) {
            confirmLines.addAll(lines("confirm-area-enable-warning"));
            if (plugin.areas().areas().isEmpty())
                confirmLines.addAll(lines("confirm-area-enable-no-areas"));
        } else {
            confirmLines.addAll(lines("confirm-area-removal-warning"));
        }
        confirmLines.add(text("confirm-area-recalculated"));

        long expiresAt = System.currentTimeMillis() + plugin.cfg().confirmTimeoutSeconds() * 1000L;
        plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                ConfirmType.AREA_CHANGE, "", RETURN_DESTINATION, expiresAt, change));
        mgr.openConfirm(player, title, confirmLines);
    }

    void promptName(Player player) {
        player.sendMessage(Component.text(
                text("chat-prompt-area-name"), NamedTextColor.YELLOW));
    }

    void promptCreateCoordinates(Player player, String name) {
        player.sendMessage(Component.text(
                text("chat-prompt-area-create-coordinates", name, player.getWorld().getName()),
                NamedTextColor.YELLOW));
    }

    void promptResizeCoordinates(Player player, Area area) {
        player.sendMessage(Component.text(
                text("chat-prompt-area-resize-coordinates", area.name(), area.worldName(),
                        area.minX(), area.minZ(), area.maxX(), area.maxZ()),
                NamedTextColor.YELLOW));
    }

    void sendValidationErrors(Player player, List<AreaValidationError> errors) {
        for (AreaValidationError error : errors) {
            player.sendMessage(Component.text(
                    text(validationErrorKey(error.code()), error.detail()), NamedTextColor.RED));
        }
    }

    void sendCoordinateError(Player player, AreaCoordinateParser.Rejected rejected) {
        player.sendMessage(Component.text(
                text(coordinateErrorKey(rejected.reason()), rejected.detail()),
                NamedTextColor.RED));
    }

    void sendResult(Player player, AreaChangeResult result) {
        if (!result.applied()) {
            sendValidationErrors(player, result.errors());
            return;
        }

        String headline = switch (result.change()) {
            case AreaChange.Enable _  -> text("wirkungsbereich-eingeschaltet");
            case AreaChange.Disable _ -> text("wirkungsbereich-ausgeschaltet");
            case AreaChange.Create create -> text("wirkungsbereich-areal-angelegt", create.name(), create.worldName(),
                    Math.min(create.firstX(), create.secondX()), Math.min(create.firstZ(), create.secondZ()),
                    Math.max(create.firstX(), create.secondX()), Math.max(create.firstZ(), create.secondZ()));
            case AreaChange.Resize resize -> text("wirkungsbereich-areal-geaendert", resize.name(),
                    Math.min(resize.firstX(), resize.secondX()), Math.min(resize.firstZ(), resize.secondZ()),
                    Math.max(resize.firstX(), resize.secondX()), Math.max(resize.firstZ(), resize.secondZ()));
            case AreaChange.Delete delete -> text("wirkungsbereich-areal-geloescht", delete.name());
        };
        player.sendMessage(Component.text(headline, NamedTextColor.GREEN));

        if (requiresConfirmation(result.change())) {
            player.sendMessage(Component.text(text("wirkungsbereich-ergebnis",
                    result.removedLocks(), result.removedSecureHoppers(), result.notifiedOwners()),
                    NamedTextColor.YELLOW));
        }
        if (result.saveError() != null) {
            player.sendMessage(Component.text(
                    text("wirkungsbereich-speichern-fehlgeschlagen", result.saveError()), NamedTextColor.RED));
        }
    }

    static boolean requiresConfirmation(AreaChange change) {
        return change instanceof AreaChange.Enable
                || change instanceof AreaChange.Resize
                || change instanceof AreaChange.Delete;
    }

    static String validationErrorKey(AreaValidationError.Code code) {
        return switch (code) {
            case NAME_INVALID       -> "wirkungsbereich-fehler-name-ungueltig";
            case NAME_ALREADY_USED  -> "wirkungsbereich-fehler-name-vergeben";
            case AREA_NOT_FOUND     -> "wirkungsbereich-fehler-areal-nicht-gefunden";
            case WORLD_NAME_MISSING -> "wirkungsbereich-fehler-welt-fehlt";
            case WORLD_NOT_FOUND    -> "wirkungsbereich-fehler-welt-nicht-gefunden";
        };
    }

    static String coordinateErrorKey(AreaCoordinateParser.Reason reason) {
        return switch (reason) {
            case WRONG_COUNT    -> "wirkungsbereich-koordinaten-anzahl";
            case NOT_AN_INTEGER -> "wirkungsbereich-koordinaten-keine-ganzzahl";
            case OUT_OF_RANGE   -> "wirkungsbereich-koordinaten-ausserhalb";
        };
    }

    private ItemStack buildAreaEntry(Area area) {
        long width = (long) area.maxX() - area.minX() + 1;
        long depth = (long) area.maxZ() - area.minZ() + 1;
        return h.buildButton(Material.FILLED_MAP, text("gui-areas-entry-name", area.name()),
                lines("gui-areas-entry-lore", area.worldName(),
                        area.minX(), area.minZ(), area.maxX(), area.maxZ(), width, depth),
                "area_entry", area.name());
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
