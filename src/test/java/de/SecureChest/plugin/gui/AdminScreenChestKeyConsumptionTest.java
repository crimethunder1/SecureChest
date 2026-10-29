package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminScreen: Schalter Chest Key verbrauchen")
class AdminScreenChestKeyConsumptionTest {

    private static final int CONSUMPTION_SLOT = 42;
    private static final Set<Integer> PREVIOUSLY_OCCUPIED_SLOTS =
            Set.of(19, 21, 23, 25, 28, 31, 34, 37, 40, 41, 43, 49);

    private final ItemStack toggleItem = mock(ItemStack.class);
    private ChestKeyConsumptionSetting consumption;
    private GuiHelper helper;
    private AdminScreen screen;
    private Player player;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        when(plugin.ui()).thenReturn(new UiStateStore());
        AreaService areaService = mock(AreaService.class);
        when(areaService.areas()).thenReturn(List.of());
        when(plugin.areas()).thenReturn(areaService);
        when(plugin.ownerNameDisplay()).thenReturn(mock(OwnerNameDisplaySetting.class));
        consumption = mock(ChestKeyConsumptionSetting.class);
        when(plugin.chestKeyConsumption()).thenReturn(consumption);

        helper = mock(GuiHelper.class);
        when(helper.buildToggleButton(any(), anyBoolean(), eq("toggle_chest_key_consumption"), any()))
                .thenReturn(toggleItem);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        screen = new AdminScreen(plugin, helper, mock(GuiManager.class));
    }

    private void openAdmin() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> {
                        inventory = mock(Inventory.class);
                        return inventory;
                    });
            screen.openAdmin(player);
        }
    }

    @Test
    @DisplayName("Der Schalter liegt in Slot 42, frei von den vorhandenen Buttons, neben dem Schalter in Slot 41")
    void toggleInSlot42() {
        assertFalse(PREVIOUSLY_OCCUPIED_SLOTS.contains(CONSUMPTION_SLOT));
        assertTrue(PREVIOUSLY_OCCUPIED_SLOTS.contains(CONSUMPTION_SLOT - 1));

        openAdmin();

        verify(inventory).setItem(CONSUMPTION_SLOT, toggleItem);
        verify(inventory, times(1)).setItem(eq(CONSUMPTION_SLOT), any());
        for (int slot : PREVIOUSLY_OCCUPIED_SLOTS) {
            verify(inventory, never()).setItem(slot, toggleItem);
        }
    }

    @Test
    @DisplayName("Schalter an: Beschreibung fuer an")
    void toggleOn() {
        when(consumption.isEnabled()).thenReturn(true);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-chest-key-consumption-label", true,
                "toggle_chest_key_consumption", "gui-admin-chest-key-consumption-on");
    }

    @Test
    @DisplayName("Schalter aus (Standard): Beschreibung fuer aus")
    void toggleOff() {
        when(consumption.isEnabled()).thenReturn(false);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-chest-key-consumption-label", false,
                "toggle_chest_key_consumption", "gui-admin-chest-key-consumption-off");
    }

    @Test
    @DisplayName("Ohne Admin-Permission wird das Admin-Panel nicht aufgebaut und der Schalter nicht gelesen")
    void withoutPermissionNoToggle() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        openAdmin();

        assertNull(inventory);
        verify(consumption, never()).isEnabled();
    }
}
