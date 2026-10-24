package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.recipe.RecipeChangeResult;
import de.SecureChest.plugin.recipe.RecipeDefinition;
import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.recipe.RecipeValidationError;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class RecipeScreen {

    static final String RETURN_DESTINATION = "RECIPES";
    static final int[] OVERVIEW_SLOTS = {21};
    static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    static final int RESULT_SLOT = 23;
    static final int SELECTION_SLOT = 25;
    static final int BACK_SLOT = 45;
    static final int TOGGLE_SLOT = 47;
    static final int SAVE_SLOT = 49;
    static final int RESET_SLOT = 51;

    private final ChestSecurePlugin   plugin;
    private final GuiHelper           h;
    private final GuiManager          mgr;
    private final Predicate<Material> itemMaterial;

    RecipeScreen(ChestSecurePlugin plugin, GuiHelper helper, GuiManager manager, Predicate<Material> itemMaterial) {
        this.plugin       = plugin;
        this.h            = helper;
        this.mgr          = manager;
        this.itemMaterial = itemMaterial;
    }

    void openRecipes(Player player) {
        if (!requireAdmin(player)) return;

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPES, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(text("gui-recipes-title")));
        holder.setInventory(inv);

        RecipeKind[] kinds = RecipeKind.values();
        for (int index = 0; index < kinds.length; index++) {
            inv.setItem(OVERVIEW_SLOTS[index], buildOverviewEntry(kinds[index]));
        }

        inv.setItem(BACK_SLOT, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-admin-lore"), "back_admin", ""));

        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void openEditor(Player player, RecipeKind kind) {
        if (!requireAdmin(player)) return;

        RecipeRegistry recipes = plugin.recipes();
        RecipeEditorModel model = RecipeEditorModel.fromDefinitions(kind,
                recipes.currentDefinition(kind), recipes.defaultDefinition(kind));
        plugin.ui().setRecipeEditor(player.getUniqueId(), model);

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPE_EDIT, kind.configKey());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-recipe-edit-title", kind.displayName())));
        holder.setInventory(inv);

        renderEditor(inv, model);
        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    void refreshEditor(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!(inv.getHolder() instanceof ChestSecureHolder holder)
                || holder.id() != ChestSecureHolder.GuiId.RECIPE_EDIT) return;
        RecipeKind kind = RecipeKind.fromConfigKey(holder.context());
        RecipeEditorModel model = kind == null ? null : plugin.ui().recipeEditor(player.getUniqueId(), kind);
        if (model == null) return;

        renderEditor(inv, model);
        h.playClickSound(player);
    }

    void openResetConfirm(Player player, RecipeKind kind) {
        List<String> confirmLines = new ArrayList<>();
        confirmLines.add(text("confirm-recipe-reset-question", kind.displayName()));
        confirmLines.add("");
        confirmLines.addAll(lines("confirm-recipe-reset-info"));
        confirmLines.add("");
        confirmLines.add(text("confirm-recipe-reset-warning"));

        long expiresAt = System.currentTimeMillis() + plugin.cfg().confirmTimeoutSeconds() * 1000L;
        plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                ConfirmType.RECIPE_RESET, kind.configKey(), RETURN_DESTINATION, expiresAt));
        mgr.openConfirm(player, text("confirm-recipe-reset-title"), confirmLines);
    }

    void sendSaveResult(Player player, RecipeChangeResult result) {
        String recipeName = result.kind().displayName();
        if (!result.applied()) {
            player.sendMessage(Component.text(text("rezept-nicht-gespeichert", recipeName), NamedTextColor.RED));
            sendValidationErrors(player, result.errors());
            return;
        }
        player.sendMessage(Component.text(text("rezept-gespeichert", recipeName), NamedTextColor.GREEN));
        sendSaveError(player, result);
    }

    void sendResetResult(Player player, RecipeChangeResult result) {
        String recipeName = result.kind().displayName();
        player.sendMessage(Component.text(text("rezept-zurueckgesetzt", recipeName), NamedTextColor.GREEN));
        if (!result.errors().isEmpty()) {
            player.sendMessage(Component.text(text("rezept-zurueckgesetzt-fehler", recipeName), NamedTextColor.RED));
            sendValidationErrors(player, result.errors());
        }
        sendSaveError(player, result);
    }

    void sendValidationErrors(Player player, List<RecipeValidationError> errors) {
        for (RecipeValidationError error : errors) {
            player.sendMessage(Component.text(
                    text(validationErrorKey(error.code()), error.detail()), NamedTextColor.RED));
        }
    }

    static String validationErrorKey(RecipeValidationError.Code code) {
        return switch (code) {
            case SHAPE_ROW_COUNT                  -> "rezept-fehler-shape-zeilenzahl";
            case SHAPE_ROW_LENGTH                 -> "rezept-fehler-shape-zeilenlaenge";
            case SHAPE_ROWS_UNEQUAL               -> "rezept-fehler-shape-ungleich";
            case SHAPE_EMPTY                      -> "rezept-fehler-shape-leer";
            case INGREDIENT_SYMBOL_LENGTH         -> "rezept-fehler-zeichen-laenge";
            case INGREDIENT_SYMBOL_SPACE          -> "rezept-fehler-zeichen-leerzeichen";
            case INGREDIENT_SYMBOL_NOT_IN_SHAPE   -> "rezept-fehler-zeichen-nicht-in-shape";
            case INGREDIENT_MATERIAL_INVALID      -> "rezept-fehler-material-ungueltig";
            case INGREDIENT_MATERIAL_AIR          -> "rezept-fehler-material-luft";
            case REGISTRATION_FAILED              -> "rezept-fehler-registrierung";
        };
    }

    private void sendSaveError(Player player, RecipeChangeResult result) {
        if (result.saveError() != null) {
            player.sendMessage(Component.text(
                    text("rezept-speichern-fehlgeschlagen", result.saveError()), NamedTextColor.RED));
        }
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission(Permissions.ADMIN)) return true;
        player.sendMessage(Component.text(text("keine-berechtigung"), NamedTextColor.RED));
        mgr.openMain(player);
        return false;
    }

    private ItemStack buildOverviewEntry(RecipeKind kind) {
        RecipeDefinition current = plugin.recipes().currentDefinition(kind);
        String state = current == null ? text("gui-recipes-state-none")
                : current.enabled() ? text("gui-recipes-state-active")
                : text("gui-recipes-state-disabled");
        String source = plugin.recipes().isOverridden(kind) ? text("gui-recipes-source-override")
                : text("gui-recipes-source-config");
        return h.buildItemButton(resultItem(kind), text("gui-recipes-entry-name", kind.displayName()),
                lines("gui-recipes-entry-lore", state, source), "recipe_entry", kind.configKey());
    }

    private void renderEditor(Inventory inv, RecipeEditorModel model) {
        for (int index = 0; index < RecipeEditorModel.CELL_COUNT; index++) {
            inv.setItem(GRID_SLOTS[index], buildCell(model, index));
        }
        inv.setItem(RESULT_SLOT, resultItem(model.kind()));
        inv.setItem(SELECTION_SLOT, buildSelection(model));

        inv.setItem(BACK_SLOT, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-recipes-lore"), "back_recipes", ""));
        inv.setItem(TOGGLE_SLOT, h.buildToggleButton(text("gui-recipe-edit-enabled-label"), model.enabled(),
                "recipe_toggle", model.enabled() ? text("gui-recipe-edit-enabled-on")
                                                 : text("gui-recipe-edit-enabled-off")));
        inv.setItem(SAVE_SLOT, h.buildButton(Material.EMERALD, text("gui-recipe-edit-save-name"),
                lines("gui-recipe-edit-save-lore"), "recipe_save", ""));
        inv.setItem(RESET_SLOT, h.buildButton(Material.BARRIER, text("gui-recipe-edit-reset-name"),
                lines("gui-recipe-edit-reset-lore"), "recipe_reset", ""));
    }

    private ItemStack buildCell(RecipeEditorModel model, int index) {
        List<String> lore = lines("gui-recipe-edit-cell-lore");
        String context = String.valueOf(index);
        Material material = model.material(index);
        if (material == null) {
            return h.buildButton(Material.WHITE_STAINED_GLASS_PANE, text("gui-recipe-edit-cell-empty-name"),
                    lore, "recipe_cell", context);
        }
        if (!itemMaterial.test(material)) {
            return h.buildButton(Material.BARRIER, text("gui-recipe-edit-cell-not-item-name", material.name()),
                    lore, "recipe_cell", context);
        }
        return h.buildButton(material, text("gui-recipe-edit-cell-material-name", material.name()),
                lore, "recipe_cell", context);
    }

    private ItemStack buildSelection(RecipeEditorModel model) {
        Material selection = model.selection();
        if (selection == null) {
            return h.buildInfoItem(Material.STRUCTURE_VOID, text("gui-recipe-edit-selection-none-name"),
                    lines("gui-recipe-edit-selection-lore"));
        }
        return h.buildInfoItem(selection, text("gui-recipe-edit-selection-name", selection.name()),
                lines("gui-recipe-edit-selection-lore"));
    }

    private ItemStack resultItem(RecipeKind kind) {
        return switch (kind) {
            case CHEST_KEY -> plugin.items().chestKey();
        };
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
