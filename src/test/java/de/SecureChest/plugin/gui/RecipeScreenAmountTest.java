package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.recipe.RecipeDefinition;
import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RecipeScreen: Button fuer die Anzahl und Ergebnis-Item mit Anzahl")
class RecipeScreenAmountTest {

    private final UUID playerUuid = UUID.randomUUID();
    private MessageService messages;
    private UiStateStore ui;
    private RecipeRegistry recipes;
    private Items items;
    private GuiHelper helper;
    private Player player;
    private RecipeScreen screen;
    private Inventory editor;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        ui = new UiStateStore();
        when(plugin.ui()).thenReturn(ui);
        recipes = mock(RecipeRegistry.class);
        when(plugin.recipes()).thenReturn(recipes);
        items = mock(Items.class);
        when(plugin.items()).thenReturn(items);
        when(plugin.cfg()).thenReturn(mock(PluginConfig.class));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        helper = mock(GuiHelper.class);
        screen = new RecipeScreen(plugin, helper, mock(GuiManager.class), material -> true);
    }

    private void openEditor() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), any(Component.class))).thenAnswer(invocation -> {
                editor = mock(Inventory.class);
                return editor;
            });
            screen.openEditor(player, RecipeKind.CHEST_KEY);
        }
    }

    @Test
    @DisplayName("Editor: Button in Slot 32 mit Aktion recipe_amount, Anzahl im Namen und Bedienhinweisen")
    void amountButton() {
        ItemStack button = mock(ItemStack.class);
        when(helper.buildButton(eq(Material.PAPER), eq("gui-recipe-edit-amount-name"), any(),
                eq("recipe_amount"), eq(""))).thenReturn(button);
        when(items.chestKey()).thenAnswer(invocation -> mock(ItemStack.class));
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(new RecipeDefinition(true,
                List.of("D"), Map.of("D", "DIAMOND"), 9));

        openEditor();

        verify(editor).setItem(RecipeScreen.AMOUNT_SLOT, button);
        verify(messages).get("gui-recipe-edit-amount-name", 9);
        verify(messages).getList("gui-recipe-edit-amount-lore", RecipeEditorModel.LARGE_AMOUNT_STEP,
                RecipeDefinition.MINIMUM_AMOUNT, RecipeDefinition.MAXIMUM_AMOUNT);
        assertEquals(9, ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY).amount());
    }

    @Test
    @DisplayName("Editor: das Ergebnis-Item in Slot 23 zeigt die Anzahl der Arbeitskopie")
    void resultShowsAmount() {
        ItemStack result = mock(ItemStack.class);
        when(items.chestKey()).thenReturn(result);
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(new RecipeDefinition(true,
                List.of("D"), Map.of("D", "DIAMOND"), 64));

        openEditor();

        verify(result).setAmount(64);
        verify(editor).setItem(RecipeScreen.RESULT_SLOT, result);
    }

    @Test
    @DisplayName("Aktualisieren nach einem Klick zeigt die neue Anzahl")
    void refreshShowsNewAmount() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);
        model.changeAmount(4);
        ui.setRecipeEditor(playerUuid, model);
        Inventory open = mock(Inventory.class);
        when(open.getHolder()).thenReturn(new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPE_EDIT, "chest_key"));
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(open);
        when(player.getOpenInventory()).thenReturn(view);
        ItemStack result = mock(ItemStack.class);
        when(items.chestKey()).thenReturn(result);

        screen.refreshEditor(player);

        verify(result).setAmount(5);
        verify(messages).get("gui-recipe-edit-amount-name", 5);
        verify(open).setItem(eq(RecipeScreen.AMOUNT_SLOT), any());
    }

    @Test
    @DisplayName("Slot 32 ueberschneidet sich nicht mit Raster, Ergebnis, Auswahl und Buttons")
    void amountSlotFree() {
        Set<Integer> slots = new HashSet<>();
        for (int slot : RecipeScreen.GRID_SLOTS) slots.add(slot);
        slots.addAll(List.of(RecipeScreen.RESULT_SLOT, RecipeScreen.SELECTION_SLOT, RecipeScreen.BACK_SLOT,
                RecipeScreen.TOGGLE_SLOT, RecipeScreen.SAVE_SLOT, RecipeScreen.RESET_SLOT));

        assertEquals(32, RecipeScreen.AMOUNT_SLOT);
        assertFalse(slots.contains(RecipeScreen.AMOUNT_SLOT));
        assertEquals(RecipeScreen.RESULT_SLOT + 9, RecipeScreen.AMOUNT_SLOT);
    }

    @Test
    @DisplayName("Fehlercode AMOUNT_OUT_OF_RANGE hat den Sprachschluessel rezept-fehler-anzahl")
    void amountErrorKey() {
        assertEquals("rezept-fehler-anzahl",
                RecipeScreen.validationErrorKey(de.SecureChest.plugin.recipe.RecipeValidationError.Code.AMOUNT_OUT_OF_RANGE));
    }
}
