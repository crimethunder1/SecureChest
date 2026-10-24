package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.recipe.RecipeChangeResult;
import de.SecureChest.plugin.recipe.RecipeDefinition;
import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.recipe.RecipeValidationError;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RecipeScreen: Rezeptuebersicht, Editor, Dialog und Chat-Meldungen")
class RecipeScreenTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");

    private final UUID playerUuid = UUID.randomUUID();
    private MessageService messages;
    private UiStateStore ui;
    private RecipeRegistry recipes;
    private Items items;
    private ConfirmManager confirms;
    private GuiHelper helper;
    private GuiManager manager;
    private Player player;
    private RecipeScreen screen;

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
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        PluginConfig config = mock(PluginConfig.class);
        when(config.confirmTimeoutSeconds()).thenReturn(30);
        when(plugin.cfg()).thenReturn(config);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        helper = mock(GuiHelper.class);
        manager = mock(GuiManager.class);
        screen = new RecipeScreen(plugin, helper, manager, material -> material != Material.WATER);
    }

    private void withInventories(Runnable screenOpening) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screenOpening.run();
        }
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    private static List<String> cells(RecipeEditorModel model) {
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < RecipeEditorModel.CELL_COUNT; index++) cells.add(model.cell(index));
        return cells;
    }

    @Test
    @DisplayName("Ohne Admin-Permission oeffnen sich weder Uebersicht noch Editor")
    void ohnePermission_keinScreen() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            screen.openRecipes(player);
            screen.openEditor(player, RecipeKind.CHEST_KEY);
            bukkit.verifyNoInteractions();
        }
        assertEquals(List.of("keine-berechtigung", "keine-berechtigung"), sentTexts());
        assertNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    @DisplayName("Uebersicht: nur der Chest Key auf Slot 21 mit Status, Quelle und Ergebnis-Item, Klick oeffnet den Editor")
    void uebersicht_nurChestKey() {
        ItemStack chestKeyItem = mock(ItemStack.class);
        ItemStack entry = mock(ItemStack.class);
        when(items.chestKey()).thenReturn(chestKeyItem);
        when(helper.buildItemButton(eq(chestKeyItem), eq("gui-recipes-entry-name"), any(),
                eq("recipe_entry"), eq("chest_key"))).thenReturn(entry);
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY))
                .thenReturn(new RecipeDefinition(false, List.of("D"), Map.of("D", "DIAMOND")));
        when(recipes.isOverridden(RecipeKind.CHEST_KEY)).thenReturn(true);
        Inventory overview = mock(Inventory.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), any(Component.class))).thenReturn(overview);
            screen.openRecipes(player);
        }

        assertArrayEquals(new int[] {21}, RecipeScreen.OVERVIEW_SLOTS);
        verify(overview).setItem(21, entry);
        verify(overview, never()).setItem(eq(23), any());
        verify(helper, times(1)).buildItemButton(any(), any(), any(), eq("recipe_entry"), any());
        verify(messages, times(1)).getList(eq("gui-recipes-entry-lore"), any(), any());
        verify(messages).getList("gui-recipes-entry-lore", "gui-recipes-state-disabled", "gui-recipes-source-override");
        verify(messages).get("gui-recipes-entry-name", "Chest Key");
        verify(messages, never()).get("gui-recipes-entry-name", "Secure Hopper");
        verify(helper).buildButton(eq(Material.ARROW), eq("gui-back-name"), any(), eq("back_admin"), eq(""));
    }

    @Test
    @DisplayName("Uebersicht: aktives Rezept zeigt aktiv")
    void uebersicht_aktiv() {
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY))
                .thenReturn(new RecipeDefinition(true, List.of("H"), Map.of("H", "HOPPER")));

        withInventories(() -> screen.openRecipes(player));

        verify(messages).getList("gui-recipes-entry-lore", "gui-recipes-state-active", "gui-recipes-source-config");
    }

    @Test
    @DisplayName("Editor: Arbeitskopie aus currentDefinition, sonst defaultDefinition, sonst leer")
    void editor_arbeitskopieAusDefinitionen() {
        RecipeDefinition current = new RecipeDefinition(true, List.of("D"), Map.of("D", "DIAMOND"));
        RecipeDefinition defaults = new RecipeDefinition(false, List.of("E"), Map.of("E", "EMERALD"));
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(current);
        when(recipes.defaultDefinition(RecipeKind.CHEST_KEY)).thenReturn(defaults);

        withInventories(() -> screen.openEditor(player, RecipeKind.CHEST_KEY));
        assertEquals("DIAMOND", ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY).cell(0));

        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(null);
        withInventories(() -> screen.openEditor(player, RecipeKind.CHEST_KEY));
        RecipeEditorModel fromDefaults = ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY);
        assertEquals("EMERALD", fromDefaults.cell(0));
        assertFalse(fromDefaults.enabled());

        when(recipes.defaultDefinition(RecipeKind.CHEST_KEY)).thenReturn(null);
        withInventories(() -> screen.openEditor(player, RecipeKind.CHEST_KEY));
        RecipeEditorModel empty = ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY);
        assertTrue(empty.enabled());
        assertEquals(new RecipeEditorModel(RecipeKind.CHEST_KEY).toDefinition(), empty.toDefinition());
        verify(messages, atLeastOnce()).get("gui-recipe-edit-title", "Chest Key");
    }

    @Test
    @DisplayName("Editor: Oeffnen aus der Uebersicht baut die Arbeitskopie immer neu auf")
    void editor_oeffnenVerwirftAlteArbeitskopie() {
        RecipeEditorModel old = new RecipeEditorModel(RecipeKind.CHEST_KEY);
        old.select(Material.STONE, material -> true);
        old.placeSelection(0);
        ui.setRecipeEditor(playerUuid, old);

        withInventories(() -> screen.openEditor(player, RecipeKind.CHEST_KEY));

        RecipeEditorModel fresh = ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY);
        assertNotSame(old, fresh);
        assertTrue(fresh.isEmpty(0));
        assertNull(fresh.selection());
    }

    @Test
    @DisplayName("Editor: Zellen mit Material, Nicht-Item und leer, CHEST_KEY als Zutat wird ein leeres Feld")
    void editor_zellen() {
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(new RecipeDefinition(true,
                List.of("WIK", "   ", "   "), Map.of("W", "WATER", "I", "IRON_BLOCK", "K", "CHEST_KEY")));

        withInventories(() -> screen.openEditor(player, RecipeKind.CHEST_KEY));

        verify(helper).buildButton(eq(Material.BARRIER), eq("gui-recipe-edit-cell-not-item-name"), any(),
                eq("recipe_cell"), eq("0"));
        verify(messages).get("gui-recipe-edit-cell-not-item-name", "WATER");
        verify(helper).buildButton(eq(Material.IRON_BLOCK), eq("gui-recipe-edit-cell-material-name"), any(),
                eq("recipe_cell"), eq("1"));
        verify(helper).buildButton(eq(Material.WHITE_STAINED_GLASS_PANE), eq("gui-recipe-edit-cell-empty-name"),
                any(), eq("recipe_cell"), eq("2"));
        verify(helper).buildButton(eq(Material.WHITE_STAINED_GLASS_PANE), eq("gui-recipe-edit-cell-empty-name"),
                any(), eq("recipe_cell"), eq("8"));
        verify(helper, never()).buildItemButton(any(), any(), any(), eq("recipe_cell"), any());
        verify(messages, atLeastOnce()).getList("gui-recipe-edit-cell-lore");
        verify(helper).buildInfoItem(eq(Material.STRUCTURE_VOID), eq("gui-recipe-edit-selection-none-name"), any());
        verify(helper).buildToggleButton("gui-recipe-edit-enabled-label", true, "recipe_toggle",
                "gui-recipe-edit-enabled-on");
    }

    @Test
    @DisplayName("Aktualisieren: rendert das offene Editor-Inventar aus der Arbeitskopie neu, ohne neues Inventar")
    void aktualisieren_imOffenenInventar() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, material -> true);
        ui.setRecipeEditor(playerUuid, model);
        Inventory open = openTopInventory(new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPE_EDIT, "chest_key"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            screen.refreshEditor(player);
            bukkit.verifyNoInteractions();
        }

        for (int slot : RecipeScreen.GRID_SLOTS) verify(open).setItem(eq(slot), any());
        verify(open).setItem(eq(RecipeScreen.SELECTION_SLOT), any());
        verify(helper).buildInfoItem(eq(Material.DIAMOND), eq("gui-recipe-edit-selection-name"), any());
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    @DisplayName("Aktualisieren: anderes offenes Inventar oder unbekannte Rezeptart wie secure_hopper aendern nichts")
    void aktualisieren_sonstNichts() {
        Inventory other = openTopInventory(new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPES, ""));
        ui.setRecipeEditor(playerUuid, new RecipeEditorModel(RecipeKind.CHEST_KEY));
        screen.refreshEditor(player);
        verify(other, never()).setItem(anyInt(), any());

        Inventory editor = openTopInventory(new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPE_EDIT, "secure_hopper"));
        screen.refreshEditor(player);
        verify(editor, never()).setItem(anyInt(), any());
    }

    private Inventory openTopInventory(ChestSecureHolder holder) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        return inventory;
    }

    @Test
    @DisplayName("Zuruecksetzen: Dialog mit Rezeptname, Hinweis auf recipes.yml und Warnung, Rueckweg RECIPES")
    @SuppressWarnings("unchecked")
    void dialog_zuruecksetzen() {
        screen.openResetConfirm(player, RecipeKind.CHEST_KEY);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(manager).openConfirm(eq(player), eq("confirm-recipe-reset-title"), captor.capture());
        assertTrue(captor.getValue().containsAll(List.of("confirm-recipe-reset-question",
                "confirm-recipe-reset-info", "confirm-recipe-reset-warning")));
        verify(messages).get("confirm-recipe-reset-question", "Chest Key");

        ConfirmRequest request = confirms.get(playerUuid).orElseThrow();
        assertEquals(ConfirmType.RECIPE_RESET, request.type());
        assertEquals("chest_key", request.data());
        assertEquals(RecipeScreen.RETURN_DESTINATION, request.returnTo());
        assertEquals("RECIPES", request.returnTo());
        assertNull(request.areaChange());
    }

    @Test
    @DisplayName("Speichern abgelehnt: Ueberschrift und je Fehler der uebersetzte Text mit dem Detail")
    void speichern_abgelehnt_meldungen() {
        screen.sendSaveResult(player, new RecipeChangeResult(RecipeKind.CHEST_KEY, false, List.of(
                new RecipeValidationError(RecipeValidationError.Code.SHAPE_EMPTY, "shape must contain"),
                new RecipeValidationError(RecipeValidationError.Code.REGISTRATION_FAILED, "not accepted")), null));

        assertEquals(List.of("rezept-nicht-gespeichert", "rezept-fehler-shape-leer", "rezept-fehler-registrierung"),
                sentTexts());
        verify(messages).get("rezept-nicht-gespeichert", "Chest Key");
        verify(messages).get("rezept-fehler-shape-leer", "shape must contain");
        verify(messages).get("rezept-fehler-registrierung", "not accepted");
    }

    @Test
    @DisplayName("Speichern erfolgreich: Erfolgsmeldung, bei saveError zusaetzlich die Warnung")
    void speichern_erfolgreich_meldungen() {
        screen.sendSaveResult(player, new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), null));
        screen.sendSaveResult(player, new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), "disk full"));

        assertEquals(List.of("rezept-gespeichert", "rezept-gespeichert", "rezept-speichern-fehlgeschlagen"),
                sentTexts());
        verify(messages, atLeastOnce()).get("rezept-gespeichert", "Chest Key");
        verify(messages).get("rezept-speichern-fehlgeschlagen", "disk full");
    }

    @Test
    @DisplayName("Zuruecksetzen: Ergebnis, Fehler der config.yml-Fassung und Speicherwarnung")
    void zuruecksetzen_meldungen() {
        screen.sendResetResult(player, new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), null));
        screen.sendResetResult(player, new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(
                new RecipeValidationError(RecipeValidationError.Code.INGREDIENT_MATERIAL_INVALID, "bad")), "locked"));

        assertEquals(List.of("rezept-zurueckgesetzt", "rezept-zurueckgesetzt", "rezept-zurueckgesetzt-fehler",
                "rezept-fehler-material-ungueltig", "rezept-speichern-fehlgeschlagen"), sentTexts());
        verify(messages).get("rezept-zurueckgesetzt-fehler", "Chest Key");
        verify(messages).get("rezept-fehler-material-ungueltig", "bad");
        verify(messages).get("rezept-speichern-fehlgeschlagen", "locked");
    }

    @Test
    @DisplayName("Jeder RecipeValidationError.Code hat einen eigenen Schluessel mit Platzhalter in de.yml und en.yml")
    void jederFehlercode_hatSprachschluessel() {
        Set<String> keys = new HashSet<>();
        for (RecipeValidationError.Code code : RecipeValidationError.Code.values()) {
            keys.add(RecipeScreen.validationErrorKey(code));
        }
        assertEquals(RecipeValidationError.Code.values().length, keys.size(), "Jeder Code braucht einen eigenen Schluessel");
        for (String language : List.of("de", "en")) {
            YamlConfiguration languageFile = YamlConfiguration.loadConfiguration(
                    LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
            for (String key : keys) {
                assertTrue(languageFile.isString(key), language + ".yml: Schluessel fehlt: " + key);
                assertFalse(languageFile.getString(key).isBlank(), language + ".yml: leerer Wert bei " + key);
                assertTrue(languageFile.getString(key).contains("{0}"), language + ".yml: Platzhalter fehlt bei " + key);
            }
        }
    }

    @Test
    @DisplayName("Slots: neun Rasterfelder, keine Ueberschneidung mit Ergebnis, Auswahl und Buttons")
    void slots_ohneUeberschneidung() {
        Set<Integer> slots = new HashSet<>();
        for (int slot : RecipeScreen.GRID_SLOTS) slots.add(slot);
        assertEquals(RecipeEditorModel.CELL_COUNT, slots.size());
        for (int slot : List.of(RecipeScreen.RESULT_SLOT, RecipeScreen.SELECTION_SLOT, RecipeScreen.BACK_SLOT,
                RecipeScreen.TOGGLE_SLOT, RecipeScreen.SAVE_SLOT, RecipeScreen.RESET_SLOT)) {
            assertTrue(slots.add(slot), "Slot doppelt belegt: " + slot);
            assertTrue(slot >= 0 && slot < 54);
        }
        assertEquals(RecipeKind.values().length, RecipeScreen.OVERVIEW_SLOTS.length);
    }
}
