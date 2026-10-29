package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.recipe.RecipeDefinition;
import de.SecureChest.plugin.recipe.RecipeKind;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class ScreenActionRecorder {

    private ScreenActionRecorder() {}

    public static MessageService keyEchoingMessages() {
        return mock(MessageService.class, invocation -> switch (invocation.getMethod().getName()) {
            case "get" -> invocation.getArgument(0);
            case "getList" -> List.of((String) invocation.getArgument(0));
            default -> null;
        });
    }

    public static Set<String> areaScreenActions() {
        List<Area> areas = new ArrayList<>();
        for (int index = 0; index <= AreaScreen.PAGE_SIZE; index++) {
            areas.add(new Area("area" + index, "world", 0, 0, 10, 10));
        }
        Set<String> actions = new TreeSet<>();
        ChestSecurePlugin plugin = pluginWithAreas(areas);
        AreaScreen screen = new AreaScreen(plugin, recordingHelper(actions), mock(GuiManager.class));
        Player player = adminPlayer();
        withInventories(() -> {
            screen.openAreas(player, 0);
            screen.openAreas(player, 1);
        });
        return actions;
    }

    public static Set<String> adminScreenActions() {
        Set<String> actions = new TreeSet<>();
        ChestSecurePlugin plugin = pluginWithAreas(List.of());
        AdminScreen screen = new AdminScreen(plugin, recordingHelper(actions), mock(GuiManager.class));
        Player player = adminPlayer();
        withInventories(() -> screen.openAdmin(player));
        return actions;
    }

    public static Set<String> recipeScreenActions() {
        Set<String> actions = new TreeSet<>();
        ChestSecurePlugin plugin = pluginWithAreas(List.of());
        RecipeRegistry recipes = mock(RecipeRegistry.class);
        when(recipes.currentDefinition(RecipeKind.CHEST_KEY)).thenReturn(new RecipeDefinition(true,
                List.of("NDN", "DHD", "NDN"),
                Map.of("N", "NETHERITE_INGOT", "D", "DIAMOND", "H", "TRIPWIRE_HOOK")));
        when(plugin.recipes()).thenReturn(recipes);
        Items items = mock(Items.class);
        when(items.chestKey()).thenAnswer(invocation -> mock(ItemStack.class));
        when(plugin.items()).thenReturn(items);
        RecipeScreen screen = new RecipeScreen(plugin, recordingHelper(actions), mock(GuiManager.class),
                material -> true);
        Player player = adminPlayer();
        withInventories(() -> {
            screen.openRecipes(player);
            for (RecipeKind kind : RecipeKind.values()) screen.openEditor(player, kind);
        });
        return actions;
    }

    public static Set<String> adminLockScreenActions() {
        Set<String> actions = new TreeSet<>();
        ChestSecurePlugin plugin = pluginWithAreas(List.of());
        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        LockEntry entry = mock(LockEntry.class);
        when(entry.key()).thenReturn(key);
        when(entry.blockType()).thenReturn(Material.CHEST);
        when(entry.owner()).thenReturn(UUID.randomUUID());
        when(entry.trusted()).thenReturn(Map.of(UUID.randomUUID(), AccessLevel.VIEW,
                UUID.randomUUID(), AccessLevel.STOCKER));
        LockService locks = mock(LockService.class);
        when(locks.getLockFromId(key.asId())).thenReturn(Optional.of(entry));
        when(plugin.locks()).thenReturn(locks);
        AdminLockScreen screen = new AdminLockScreen(plugin, recordingHelper(actions));
        Player player = adminPlayer();
        withInventories(() -> screen.openLockManage(player, key.asId()));
        return actions;
    }

    private static ChestSecurePlugin pluginWithAreas(List<Area> areas) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        AreaService areaService = mock(AreaService.class);
        when(areaService.areas()).thenReturn(areas);
        when(plugin.areas()).thenReturn(areaService);
        when(plugin.ownerNameDisplay()).thenReturn(mock(OwnerNameDisplaySetting.class));
        when(plugin.chestKeyConsumption()).thenReturn(mock(ChestKeyConsumptionSetting.class));
        UiStateStore uiStateStore = new UiStateStore();
        when(plugin.ui()).thenReturn(uiStateStore);
        return plugin;
    }

    private static Player adminPlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        return player;
    }

    private static GuiHelper recordingHelper(Set<String> actions) {
        GuiHelper helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            actions.add(invocation.getArgument(3));
            return null;
        });
        when(helper.buildToggleButton(any(), anyBoolean(), anyString(), any())).thenAnswer(invocation -> {
            actions.add(invocation.getArgument(2));
            return null;
        });
        when(helper.buildPlayerHeadButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            actions.add(invocation.getArgument(3));
            return null;
        });
        when(helper.buildItemButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            actions.add(invocation.getArgument(3));
            return null;
        });
        return helper;
    }

    private static void withInventories(Runnable screenOpening) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screenOpening.run();
        }
    }
}
