package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LockStats;
import de.SecureChest.plugin.ui.UiStateStore.LockTypeFilter;
import de.SecureChest.plugin.ui.UndoManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Oberflaeche: Typfilter, Symbole und Statistik fuer die neuen sperrbaren Bloecke")
class NewLockableBlocksScreenTest {

    private static final Map<LockTypeFilter, Set<Material>> FILTER_MEMBERS = Map.of(
            LockTypeFilter.CHESTS, EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST),
            LockTypeFilter.BARRELS, EnumSet.of(Material.BARREL),
            LockTypeFilter.SHULKERS, EnumSet.of(Material.SHULKER_BOX, Material.PURPLE_SHULKER_BOX),
            LockTypeFilter.FURNACES, EnumSet.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER),
            LockTypeFilter.DROPPERS_AND_DISPENSERS, EnumSet.of(Material.DROPPER, Material.DISPENSER),
            LockTypeFilter.BREWING_STANDS, EnumSet.of(Material.BREWING_STAND),
            LockTypeFilter.CRAFTERS, EnumSet.of(Material.CRAFTER));

    private static final List<Material> ALL_LOCKABLE_SAMPLES = List.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER, Material.SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.DROPPER, Material.DISPENSER, Material.BREWING_STAND, Material.CRAFTER);

    @Test
    @DisplayName("Jeder Filter trifft genau seine Typen, Alle trifft alles, der Trichter nur Alle")
    void filterMembership() {
        for (Material type : ALL_LOCKABLE_SAMPLES) {
            assertTrue(LockScreen.matchesTypeFilter(type, LockTypeFilter.ALL), type.name());
            for (Map.Entry<LockTypeFilter, Set<Material>> entry : FILTER_MEMBERS.entrySet()) {
                assertEquals(entry.getValue().contains(type), LockScreen.matchesTypeFilter(type, entry.getKey()),
                        entry.getKey() + " / " + type);
            }
        }
        assertEquals(LockTypeFilter.values().length - 1, FILTER_MEMBERS.size(), "jeder Filter ausser Alle ist geprueft");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Kupferkisten jeder Stufe gehoeren zum Filter Kisten und zu Alle, zu keinem anderen Filter")
    void copperChestsBelongToChestFilter(Material type) {
        assertTrue(LockScreen.matchesTypeFilter(type, LockTypeFilter.ALL));
        assertTrue(LockScreen.matchesTypeFilter(type, LockTypeFilter.CHESTS));
        for (LockTypeFilter filter : FILTER_MEMBERS.keySet()) {
            if (filter != LockTypeFilter.CHESTS) assertFalse(LockScreen.matchesTypeFilter(type, filter), filter.name());
        }
    }

    @Test
    @DisplayName("Filter-Reihenfolge: bisherige zuerst, dann die neuen, danach wieder Alle")
    void filterCycle() {
        List<LockTypeFilter> expected = List.of(LockTypeFilter.ALL, LockTypeFilter.CHESTS, LockTypeFilter.BARRELS,
                LockTypeFilter.SHULKERS, LockTypeFilter.FURNACES, LockTypeFilter.DROPPERS_AND_DISPENSERS,
                LockTypeFilter.BREWING_STANDS, LockTypeFilter.CRAFTERS);
        LockTypeFilter filter = LockTypeFilter.ALL;
        for (LockTypeFilter step : expected) {
            assertEquals(step, filter);
            filter = filter.next();
        }
        assertEquals(LockTypeFilter.ALL, filter);
        assertEquals("enum-lock-type-filter-furnaces", LockTypeFilter.FURNACES.labelKey());
        assertEquals("enum-lock-type-filter-droppers-and-dispensers", LockTypeFilter.DROPPERS_AND_DISPENSERS.labelKey());
        assertEquals("enum-lock-type-filter-brewing-stands", LockTypeFilter.BREWING_STANDS.labelKey());
        assertEquals("enum-lock-type-filter-crafters", LockTypeFilter.CRAFTERS.labelKey());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Admin-Verwaltungsmenue: Symbol ist das Material der Sperre, Titel und Info nennen den Typ")
    void adminLockScreenIcon(Material type) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        LockService locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        LockKey key = new LockKey(UUID.randomUUID(), 1, 64, 2);
        LockEntry entry = new LockEntry(key, type, UUID.randomUUID(), 0L);
        when(locks.getLockFromId(key.asId())).thenReturn(Optional.of(entry));
        GuiHelper helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenReturn(mock(ItemStack.class));
        when(helper.buildInfoItem(any(), any(), any())).thenReturn(mock(ItemStack.class));
        Player admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);
        AdminLockScreen screen = new AdminLockScreen(plugin, helper);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(mock(Inventory.class));
            screen.openLockManage(admin, key.asId());
        }

        verify(helper).buildInfoItem(eq(type), eq("gui-admin-lock-manage-info-name"), any());
        verify(admin).openInventory(any(Inventory.class));
    }

    @Test
    @DisplayName("Statistik: neue Typen in der dritten Reihe mit eigenem Symbol und Zaehler, Zurueck darunter")
    void statisticsScreen() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        when(plugin.undos()).thenReturn(mock(UndoManager.class));
        LockService locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        UUID playerUuid = UUID.randomUUID();
        when(locks.getLockStats(playerUuid)).thenReturn(
                new LockStats(28, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0L, 0L));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alice");

        Map<ItemStack, Material> materials = new HashMap<>();
        Map<ItemStack, String> names = new HashMap<>();
        GuiHelper helper = mock(GuiHelper.class);
        when(helper.buildInfoItem(any(), any(), any())).thenAnswer(invocation -> {
            ItemStack stack = mock(ItemStack.class);
            materials.put(stack, invocation.getArgument(0));
            names.put(stack, invocation.getArgument(1));
            return stack;
        });
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            ItemStack stack = mock(ItemStack.class);
            materials.put(stack, invocation.getArgument(0));
            names.put(stack, invocation.getArgument(3));
            return stack;
        });
        Map<Integer, ItemStack> slots = new HashMap<>();
        Inventory inventory = mock(Inventory.class);
        doAnswer(invocation -> {
            slots.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(inventory).setItem(anyInt(), any());
        ArgumentCaptor<Integer> sizes = ArgumentCaptor.forClass(Integer.class);
        MiscScreen screen = new MiscScreen(plugin, helper, mock(GuiManager.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), sizes.capture(), any(Component.class)))
                    .thenReturn(inventory);
            screen.openStats(player);
        }

        assertEquals(36, sizes.getValue());
        Map<Integer, Material> expectedMaterials = Map.ofEntries(
                Map.entry(10, Material.CHEST), Map.entry(11, Material.TRAPPED_CHEST), Map.entry(12, Material.BARREL),
                Map.entry(13, Material.PURPLE_SHULKER_BOX), Map.entry(14, Material.HOPPER), Map.entry(15, Material.HOPPER),
                Map.entry(19, Material.FURNACE), Map.entry(20, Material.BLAST_FURNACE), Map.entry(21, Material.SMOKER),
                Map.entry(22, Material.DROPPER), Map.entry(23, Material.DISPENSER), Map.entry(24, Material.BREWING_STAND),
                Map.entry(25, Material.CRAFTER), Map.entry(31, Material.ARROW));
        for (Map.Entry<Integer, Material> expected : expectedMaterials.entrySet()) {
            ItemStack stack = slots.get(expected.getKey());
            assertNotNull(stack, "Slot " + expected.getKey());
            assertEquals(expected.getValue(), materials.get(stack), "Slot " + expected.getKey());
        }
        Map<Integer, String> expectedNames = Map.of(
                19, "gui-stats-furnaces", 20, "gui-stats-blast-furnaces", 21, "gui-stats-smokers",
                22, "gui-stats-droppers", 23, "gui-stats-dispensers", 24, "gui-stats-brewing-stands",
                25, "gui-stats-crafters", 31, "back_main");
        for (Map.Entry<Integer, String> expected : expectedNames.entrySet()) {
            assertEquals(expected.getValue(), names.get(slots.get(expected.getKey())), "Slot " + expected.getKey());
        }
        verify(plugin.msg()).get("gui-stats-furnaces", 6);
        verify(plugin.msg()).get("gui-stats-blast-furnaces", 7);
        verify(plugin.msg()).get("gui-stats-smokers", 8);
        verify(plugin.msg()).get("gui-stats-droppers", 9);
        verify(plugin.msg()).get("gui-stats-dispensers", 10);
        verify(plugin.msg()).get("gui-stats-brewing-stands", 11);
        verify(plugin.msg()).get("gui-stats-crafters", 12);
    }
}
