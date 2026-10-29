package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LockStats;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.ui.UndoManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MiscScreen: Rezeptvorschau fuer Spieler nur noch fuer den Chest Key")
class MiscScreenRecipePreviewTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final Set<String> actions = new TreeSet<>();
    private MessageService messages;
    private RecipeRegistry recipes;
    private GuiHelper helper;
    private GuiManager manager;
    private Player player;
    private MiscScreen screen;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        recipes = mock(RecipeRegistry.class);
        when(plugin.recipes()).thenReturn(recipes);
        LockService locks = mock(LockService.class);
        when(locks.getLockStats(playerUuid)).thenReturn(new LockStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L));
        when(plugin.locks()).thenReturn(locks);
        when(plugin.undos()).thenReturn(mock(UndoManager.class));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            actions.add(invocation.getArgument(3));
            return null;
        });
        manager = mock(GuiManager.class);
        screen = new MiscScreen(plugin, helper, manager);
    }

    private Inventory openWith(Runnable screenOpening, ArgumentCaptor<InventoryHolder> holders,
                               ArgumentCaptor<Component> titles) {
        Inventory inventory = mock(Inventory.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(holders.capture(), anyInt(), titles.capture()))
                    .thenReturn(inventory);
            screenOpening.run();
        }
        return inventory;
    }

    @Test
    @DisplayName("Hauptmenue: Vorschau-Button fuer den Chest Key auf Slot 28, kein Button fuer den Secure Hopper")
    void hauptmenue_nurChestKeyVorschau() {
        Inventory inventory = openWith(() -> screen.openMain(player),
                ArgumentCaptor.forClass(InventoryHolder.class), ArgumentCaptor.forClass(Component.class));

        assertTrue(actions.contains("open_recipe_key"), "Vorschau-Button fuer den Chest Key fehlt: " + actions);
        assertFalse(actions.contains("open_recipe_hopper"), "Vorschau-Button fuer den Secure Hopper: " + actions);
        verify(helper).buildButton(eq(Material.TRIPWIRE_HOOK), eq("gui-main-recipe-key-name"), any(),
                eq("open_recipe_key"), eq(""));
        verify(helper).buildButton(eq(Material.HOPPER), eq("gui-main-hoppers-name"), any(),
                eq("open_hoppers"), eq(""));
        verify(messages, never()).get("gui-main-recipe-hopper-name");
        verify(inventory, never()).setItem(eq(30), any());
    }

    @Test
    @DisplayName("Vorschau: zeigt das Chest-Key-Rezept mit GuiId RECIPE_KEY und Titel des Chest Keys")
    void vorschau_chestKey() {
        ItemStack result = mock(ItemStack.class);
        when(result.clone()).thenReturn(result);
        RecipeChoice.ExactChoice choice = mock(RecipeChoice.ExactChoice.class);
        when(choice.getChoices()).thenReturn(List.of(result));
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        when(recipe.getShape()).thenReturn(new String[] {"DDD", "DDD", "DDD"});
        when(recipe.getChoiceMap()).thenReturn(Map.of('D', choice));
        when(recipe.getResult()).thenReturn(result);
        when(recipes.chestKeyRecipe()).thenReturn(recipe);
        ArgumentCaptor<InventoryHolder> holders = ArgumentCaptor.forClass(InventoryHolder.class);
        ArgumentCaptor<Component> titles = ArgumentCaptor.forClass(Component.class);

        Inventory inventory = openWith(() -> screen.openRecipePreview(player), holders, titles);

        assertEquals(ChestSecureHolder.GuiId.RECIPE_KEY, ((ChestSecureHolder) holders.getValue()).id());
        assertEquals("gui-recipe-key-title", ((TextComponent) titles.getValue()).content());
        verify(inventory).setItem(24, result);
        verify(player).openInventory(inventory);
    }

    @Test
    @DisplayName("Vorschau ohne aktives Chest-Key-Rezept: Hinweis und Hauptmenue, kein Inventar")
    void vorschau_ohneRezept() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            screen.openRecipePreview(player);
            bukkit.verifyNoInteractions();
        }

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("rezept-nicht-verfuegbar", ((TextComponent) captor.getValue()).content());
        verify(manager).openMain(player);
    }

    @Test
    @DisplayName("GuiId kennt RECIPE_KEY, aber kein RECIPE_HOPPER mehr")
    void guiId_ohneRecipeHopper() {
        assertEquals(ChestSecureHolder.GuiId.RECIPE_KEY, ChestSecureHolder.GuiId.valueOf("RECIPE_KEY"));
        assertThrows(IllegalArgumentException.class, () -> ChestSecureHolder.GuiId.valueOf("RECIPE_HOPPER"));
    }
}
