package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.ChestSecureHolder;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Rezept-Editor, Auswahl, Speichern und Zuruecksetzen")
class GuiListenerRecipeTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new HashMap<>();
    private MessageService messages;
    private UiStateStore ui;
    private RecipeRegistry recipes;
    private GuiManager gui;
    private ConfirmManager confirms;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        ui = new UiStateStore();
        when(plugin.ui()).thenReturn(ui);
        recipes = mock(RecipeRegistry.class);
        when(plugin.recipes()).thenReturn(recipes);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        when(plugin.pendingChat()).thenReturn(pendingChat);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> material != Material.WATER);
    }

    private RecipeEditorModel storeWorkingCopy(RecipeKind kind) {
        RecipeEditorModel model = new RecipeEditorModel(kind);
        ui.setRecipeEditor(playerUuid, model);
        return model;
    }

    private static ChestSecureHolder editorHolder(RecipeKind kind) {
        return new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPE_EDIT, kind.configKey());
    }

    private InventoryClickEvent bottomClick(InventoryHolder topHolder, ClickType click, Material material) {
        Inventory top = mock(Inventory.class);
        when(top.getHolder()).thenReturn(topHolder);
        Inventory bottom = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(bottom);
        when(event.getClick()).thenReturn(click);
        if (material != null) {
            ItemStack item = mock(ItemStack.class);
            when(item.getType()).thenReturn(material);
            when(event.getCurrentItem()).thenReturn(item);
        }
        return event;
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    private void storeResetConfirmation(long expiresAt) {
        confirms.set(playerUuid, new ConfirmRequest(ConfirmType.RECIPE_RESET,
                RecipeKind.CHEST_KEY.configKey(), "RECIPES", expiresAt));
    }

    private long inOneMinute() {
        return System.currentTimeMillis() + 60_000L;
    }

    @Test
    @DisplayName("Unteres Inventar mit Permission: gueltiges Item wird Auswahl, Event abgebrochen, Editor aktualisiert")
    void unteresInventar_gueltig_setztAuswahl() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        InventoryClickEvent event = bottomClick(editorHolder(RecipeKind.CHEST_KEY), ClickType.LEFT, Material.DIAMOND);

        listener.onInventoryClick(event);

        assertEquals(Material.DIAMOND, model.selection());
        verify(event).setCancelled(true);
        verify(gui).refreshRecipeEditor(player);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Unteres Inventar: Rechtsklick waehlt ebenfalls aus")
    void unteresInventar_rechtsklick_setztAuswahl() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);

        listener.onInventoryClick(bottomClick(editorHolder(RecipeKind.CHEST_KEY), ClickType.RIGHT,
                Material.GOLD_BLOCK));

        assertEquals(Material.GOLD_BLOCK, model.selection());
    }

    @Test
    @DisplayName("Unteres Inventar ohne Admin-Permission: keine Auswahl, Event abgebrochen, Hauptmenue")
    void unteresInventar_ohnePermission_nichts() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        InventoryClickEvent event = bottomClick(editorHolder(RecipeKind.CHEST_KEY), ClickType.LEFT, Material.DIAMOND);

        listener.onInventoryClick(event);

        assertNull(model.selection());
        verify(event).setCancelled(true);
        verify(gui, never()).refreshRecipeEditor(any());
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
    }

    @Test
    @DisplayName("Unteres Inventar: Material, das kein Item ist, wird mit Meldung abgelehnt")
    void unteresInventar_keinItem_abgelehnt() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        model.select(Material.STICK, material -> true);
        InventoryClickEvent event = bottomClick(editorHolder(RecipeKind.CHEST_KEY), ClickType.LEFT, Material.WATER);

        listener.onInventoryClick(event);

        assertEquals(Material.STICK, model.selection());
        verify(event).setCancelled(true);
        assertEquals(List.of("rezept-auswahl-ungueltig"), sentTexts());
        verify(messages).get("rezept-auswahl-ungueltig", "WATER");
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Unteres Inventar: Klick auf ein leeres Feld (Luft) tut nichts und meldet nichts")
    void unteresInventar_luft_nichts() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        InventoryClickEvent event = bottomClick(editorHolder(RecipeKind.CHEST_KEY), ClickType.LEFT, Material.AIR);

        listener.onInventoryClick(event);

        assertNull(model.selection());
        verify(event).setCancelled(true);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Unteres Inventar: Shift-Klick, Zahlentasten, Doppelklick, Drop und Mittelklick werden nur abgebrochen")
    void unteresInventar_andereKlicks_nurAbgebrochen() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        for (ClickType click : List.of(ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE,
                ClickType.SWAP_OFFHAND)) {
            InventoryClickEvent event = bottomClick(editorHolder(RecipeKind.CHEST_KEY), click, Material.DIAMOND);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            assertNull(model.selection(), click.name());
        }
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Oberes Inventar ist kein Plugin-GUI: nichts passiert, das Event bleibt unberuehrt")
    void obenKeinPluginGui_nichts() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        InventoryClickEvent event = bottomClick(null, ClickType.LEFT, Material.DIAMOND);

        listener.onInventoryClick(event);

        assertNull(model.selection());
        verify(event, never()).setCancelled(true);
        verify(event, never()).getCurrentItem();
    }

    @Test
    @DisplayName("Oberes Inventar ist ein anderes Plugin-GUI: wie bisher abgebrochen, der Auswahl-Weg greift nicht")
    void obenAnderesPluginGui_keineAuswahl() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        InventoryClickEvent event = bottomClick(new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN, ""),
                ClickType.LEFT, null);

        listener.onInventoryClick(event);

        assertNull(model.selection());
        verify(event).setCancelled(true);
        verify(event, never()).getClick();
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Ziehen im Editor wird abgebrochen, in anderen Inventaren nicht")
    void ziehen_imEditorAbgebrochen() {
        for (InventoryHolder holder : Arrays.asList(editorHolder(RecipeKind.CHEST_KEY),
                new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPES, ""), null)) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(holder);
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(top);
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getView()).thenReturn(view);

            listener.onInventoryDrag(event);

            boolean editor = holder instanceof ChestSecureHolder chestSecureHolder
                    && chestSecureHolder.id() == ChestSecureHolder.GuiId.RECIPE_EDIT;
            verify(event, editor ? atLeastOnce() : never()).setCancelled(true);
        }
    }

    @Test
    @DisplayName("Schliessen des Editors verwirft die Arbeitskopie, Schliessen der Uebersicht nicht")
    void schliessen_verwirftArbeitskopie() {
        storeWorkingCopy(RecipeKind.CHEST_KEY);

        listener.onInventoryClose(closeEvent(new ChestSecureHolder(ChestSecureHolder.GuiId.RECIPES, "")));
        assertNotNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));

        listener.onInventoryClose(closeEvent(editorHolder(RecipeKind.CHEST_KEY)));
        assertNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
    }

    private InventoryCloseEvent closeEvent(InventoryHolder holder) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(holder);
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        return event;
    }

    @Test
    @DisplayName("Quit-Aufraeumen verwirft die Arbeitskopie")
    void cleanup_verwirftArbeitskopie() {
        storeWorkingCopy(RecipeKind.CHEST_KEY);

        ui.cleanup(playerUuid);

        assertNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
    }

    @Test
    @DisplayName("Arbeitskopie des Chest Keys wird beim Verwerfen aller Arbeitskopien entfernt")
    void arbeitskopie_verwerfenAller() {
        storeWorkingCopy(RecipeKind.CHEST_KEY);

        assertNotNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
        ui.clearRecipeEditors();
        assertNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
    }

    @Test
    @DisplayName("Zelle: Linksklick setzt die Auswahl, Rechtsklick leert, jeweils mit Aktualisierung")
    void zelle_setzenUndLeeren() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, material -> true);

        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 4, ClickType.LEFT);
        assertEquals("DIAMOND", model.cell(4));

        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 4, ClickType.RIGHT);
        assertTrue(model.isEmpty(4));

        verify(gui, times(2)).refreshRecipeEditor(player);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Zelle: Linksklick ohne Auswahl zeigt einen Hinweis")
    void zelle_ohneAuswahl_hinweis() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);

        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 0, ClickType.LEFT);

        assertTrue(model.isEmpty(0));
        assertEquals(List.of("rezept-keine-auswahl"), sentTexts());
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Zelle: Umschalt+Linksklick setzt keinen Chest Key mehr, aendert nichts und meldet nichts")
    void zelle_umschaltLinksklick_nichts() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, material -> true);

        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 4, ClickType.SHIFT_LEFT);

        assertTrue(model.isEmpty(4));
        verify(player, never()).sendMessage(any(Component.class));
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Zelle: andere Klickarten und ungueltige Indizes aendern nichts")
    void zelle_andereKlicks_nichts() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, material -> true);

        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 4, ClickType.SHIFT_RIGHT);
        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 4, ClickType.NUMBER_KEY);
        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 9, ClickType.LEFT);
        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, -1, ClickType.LEFT);

        assertEquals(new RecipeEditorModel(RecipeKind.CHEST_KEY).toDefinition(), model.toDefinition());
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Fehlende Arbeitskopie: Hinweis und Rezeptuebersicht")
    void arbeitskopieFehlt_hinweis() {
        listener.editRecipeCell(player, RecipeKind.CHEST_KEY, 0, ClickType.LEFT);

        assertEquals(List.of("rezept-arbeitskopie-fehlt"), sentTexts());
        verify(gui).openRecipes(player);
    }

    @Test
    @DisplayName("Speichern ruft apply mit der Definition aus dem Raster auf")
    void speichern_ruftApplyMitDefinition() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, material -> true);
        model.placeSelection(0);
        model.select(Material.STICK, material -> true);
        model.placeSelection(4);
        model.select(Material.GOLD_INGOT, material -> true);
        model.placeSelection(8);
        model.setEnabled(false);
        when(recipes.apply(eq(RecipeKind.CHEST_KEY), any()))
                .thenReturn(new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), null));

        listener.saveRecipe(player, RecipeKind.CHEST_KEY);

        ArgumentCaptor<RecipeDefinition> captor = ArgumentCaptor.forClass(RecipeDefinition.class);
        verify(recipes).apply(eq(RecipeKind.CHEST_KEY), captor.capture());
        RecipeDefinition definition = captor.getValue();
        assertFalse(definition.enabled());
        assertEquals(List.of("A  ", " B ", "  C"), definition.shape());
        assertEquals(List.of("A", "B", "C"), List.copyOf(definition.ingredients().keySet()));
        assertEquals(Map.of("A", "DIAMOND", "B", "STICK", "C", "GOLD_INGOT"), definition.ingredients());
    }

    @Test
    @DisplayName("Speichern prueft die Admin-Permission erneut, ohne sie wird nichts angewendet")
    void speichern_ohnePermission_nichts() {
        storeWorkingCopy(RecipeKind.CHEST_KEY);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        listener.saveRecipe(player, RecipeKind.CHEST_KEY);

        verify(recipes, never()).apply(any(), any());
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
    }

    @Test
    @DisplayName("Speichern abgelehnt: Fehler gemeldet, Editor bleibt offen, Arbeitskopie bleibt erhalten")
    void speichern_abgelehnt_arbeitskopieBleibt() {
        RecipeEditorModel model = storeWorkingCopy(RecipeKind.CHEST_KEY);
        RecipeChangeResult result = new RecipeChangeResult(RecipeKind.CHEST_KEY, false,
                List.of(new RecipeValidationError(RecipeValidationError.Code.SHAPE_EMPTY, "empty")), null);
        when(recipes.apply(eq(RecipeKind.CHEST_KEY), any())).thenReturn(result);

        listener.saveRecipe(player, RecipeKind.CHEST_KEY);

        verify(gui).sendRecipeSaveResult(player, result);
        assertSame(model, ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
        verify(gui, never()).openRecipes(any());
        verify(gui, never()).openRecipeEditor(any(), any());
    }

    @Test
    @DisplayName("Speichern erfolgreich: Ergebnis gemeldet, Arbeitskopie verworfen, Uebersicht geoeffnet")
    void speichern_erfolgreich_arbeitskopieVerworfen() {
        storeWorkingCopy(RecipeKind.CHEST_KEY).setEnabled(false);
        RecipeChangeResult result = new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), "disk full");
        when(recipes.apply(eq(RecipeKind.CHEST_KEY), any())).thenReturn(result);

        listener.saveRecipe(player, RecipeKind.CHEST_KEY);

        verify(gui).sendRecipeSaveResult(player, result);
        assertNull(ui.recipeEditor(playerUuid, RecipeKind.CHEST_KEY));
        verify(gui).openRecipes(player);
    }

    @Test
    @DisplayName("Zuruecksetzen ohne Ueberschreibung: nur die Meldung, kein Dialog")
    void zuruecksetzen_bereitsStandard_nurMeldung() {
        when(recipes.isOverridden(RecipeKind.CHEST_KEY)).thenReturn(false);

        listener.requestRecipeReset(player, RecipeKind.CHEST_KEY);

        assertEquals(List.of("rezept-bereits-standard"), sentTexts());
        verify(messages).get("rezept-bereits-standard", "Chest Key");
        verify(gui, never()).openRecipeResetConfirm(any(), any());
        verify(recipes, never()).resetToDefault(any());
        assertTrue(confirms.get(playerUuid).isEmpty());
    }

    @Test
    @DisplayName("Zuruecksetzen mit Ueberschreibung: immer der Bestaetigungsdialog, noch nichts angewendet")
    void zuruecksetzen_ueberschrieben_dialog() {
        when(recipes.isOverridden(RecipeKind.CHEST_KEY)).thenReturn(true);

        listener.requestRecipeReset(player, RecipeKind.CHEST_KEY);

        verify(gui).openRecipeResetConfirm(player, RecipeKind.CHEST_KEY);
        verify(recipes, never()).resetToDefault(any());
    }

    @Test
    @DisplayName("Dialog Ja: resetToDefault, Ergebnis gemeldet, Uebersicht, keine allgemeine Erledigt-Meldung")
    void dialog_ja_wendetAn() {
        RecipeChangeResult result = new RecipeChangeResult(RecipeKind.CHEST_KEY, true, List.of(), null);
        when(recipes.resetToDefault(RecipeKind.CHEST_KEY)).thenReturn(result);
        storeResetConfirmation(inOneMinute());

        listener.handleConfirmAction(player, true);

        verify(recipes).resetToDefault(RecipeKind.CHEST_KEY);
        verify(gui).sendRecipeResetResult(player, result);
        verify(gui).openRecipes(player);
        verify(player, never()).sendMessage(any(Component.class));
        assertTrue(confirms.get(playerUuid).isEmpty());
    }

    @Test
    @DisplayName("Dialog Ja ohne Admin-Permission: nichts wird zurueckgesetzt")
    void dialog_ja_ohnePermission_nichts() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        storeResetConfirmation(inOneMinute());

        listener.handleConfirmAction(player, true);

        verify(recipes, never()).resetToDefault(any());
        verify(gui, never()).sendRecipeResetResult(any(), any());
        verify(gui).openRecipes(player);
    }

    @Test
    @DisplayName("Dialog Nein: nichts wird zurueckgesetzt, zurueck zur Uebersicht")
    void dialog_nein_nichts() {
        storeResetConfirmation(inOneMinute());

        listener.handleConfirmAction(player, false);

        verify(recipes, never()).resetToDefault(any());
        verify(gui).openRecipes(player);
        assertTrue(confirms.get(playerUuid).isEmpty());
    }

    @Test
    @DisplayName("Dialog abgelaufen: nichts wird zurueckgesetzt, Hinweis und Hauptmenue")
    void dialog_abgelaufen_nichts() {
        storeResetConfirmation(System.currentTimeMillis() - 1L);

        listener.handleConfirmAction(player, true);

        verify(recipes, never()).resetToDefault(any());
        assertEquals(List.of("gui-confirm-expired"), sentTexts());
        verify(gui).openMain(player);
    }
}
