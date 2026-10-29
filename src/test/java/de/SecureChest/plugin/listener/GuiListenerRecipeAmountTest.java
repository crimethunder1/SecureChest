package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Anzahl im Rezept-Editor")
class GuiListenerRecipeAmountTest {

    private final UUID playerUuid = UUID.randomUUID();
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private UiStateStore ui;
    private GuiManager gui;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        ui = new UiStateStore();
        when(plugin.ui()).thenReturn(ui);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private RecipeEditorModel storeWorkingCopy() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);
        ui.setRecipeEditor(playerUuid, model);
        return model;
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Die Aktion des Anzahl-Buttons ist Admin-only und wird vom Rezept-Editor erzeugt")
    void amountActionIsAdminOnly() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_amount"));
        assertTrue(ScreenActionRecorder.recipeScreenActions().contains("recipe_amount"));
    }

    @Test
    @DisplayName("Linksklick plus 1, Rechtsklick minus 1, Shift-Links plus 8, Shift-Rechts minus 8, Editor aktualisiert")
    void clicksChangeAmount() {
        RecipeEditorModel model = storeWorkingCopy();

        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.LEFT);
        assertEquals(2, model.amount());
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.SHIFT_LEFT);
        assertEquals(10, model.amount());
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.RIGHT);
        assertEquals(9, model.amount());
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.SHIFT_RIGHT);
        assertEquals(1, model.amount());

        verify(gui, org.mockito.Mockito.times(4)).refreshRecipeEditor(player);
    }

    @Test
    @DisplayName("Grenzen: nicht unter 1 und nicht ueber 64")
    void clicksRespectBounds() {
        RecipeEditorModel model = storeWorkingCopy();

        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.RIGHT);
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.SHIFT_RIGHT);
        assertEquals(1, model.amount());

        for (int click = 0; click < 9; click++) {
            listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.SHIFT_LEFT);
        }
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.LEFT);
        assertEquals(64, model.amount());
    }

    @Test
    @DisplayName("Andere Klickarten (Mitte, Doppelklick, Q) aendern nichts")
    void otherClicksIgnored() {
        RecipeEditorModel model = storeWorkingCopy();

        for (ClickType click : List.of(ClickType.MIDDLE, ClickType.DOUBLE_CLICK, ClickType.DROP,
                ClickType.NUMBER_KEY)) {
            listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, click);
        }

        assertEquals(1, model.amount());
        verify(gui, never()).refreshRecipeEditor(any());
    }

    @Test
    @DisplayName("Ohne Arbeitskopie: Hinweis und Rezeptuebersicht")
    void withoutWorkingCopy() {
        listener.changeRecipeAmount(player, RecipeKind.CHEST_KEY, ClickType.LEFT);

        assertEquals(List.of("rezept-arbeitskopie-fehlt"), sentTexts());
        verify(gui).openRecipes(player);
    }
}
