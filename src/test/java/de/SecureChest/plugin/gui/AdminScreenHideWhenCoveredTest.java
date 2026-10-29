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

@DisplayName("AdminScreen: Schalter Namen verstecken, wenn ueberdeckt")
class AdminScreenHideWhenCoveredTest {

    private static final int HIDE_WHEN_COVERED_SLOT = 41;
    private static final int RESERVED_SLOT = 42;
    private static final Set<Integer> PREVIOUSLY_OCCUPIED_SLOTS = Set.of(19, 21, 23, 25, 28, 31, 34, 37, 40, 43, 49);

    private final ItemStack toggleItem = mock(ItemStack.class);
    private OwnerNameDisplaySetting ownerNameDisplay;
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
        ownerNameDisplay = mock(OwnerNameDisplaySetting.class);
        when(plugin.ownerNameDisplay()).thenReturn(ownerNameDisplay);
        when(plugin.chestKeyConsumption()).thenReturn(mock(ChestKeyConsumptionSetting.class));

        helper = mock(GuiHelper.class);
        when(helper.buildToggleButton(any(), anyBoolean(), eq("toggle_owner_name_hide_when_covered"), any()))
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
    @DisplayName("Der Schalter liegt in Slot 41, frei von den vorhandenen Buttons, Slot 42 in derselben Reihe gehoert nicht zu ihm")
    void toggleInFreeSlot() {
        assertFalse(PREVIOUSLY_OCCUPIED_SLOTS.contains(HIDE_WHEN_COVERED_SLOT));
        assertFalse(PREVIOUSLY_OCCUPIED_SLOTS.contains(RESERVED_SLOT));
        assertEquals(HIDE_WHEN_COVERED_SLOT / 9, RESERVED_SLOT / 9);
        when(ownerNameDisplay.isHideWhenCoveredEnabled()).thenReturn(true);

        openAdmin();

        verify(inventory).setItem(HIDE_WHEN_COVERED_SLOT, toggleItem);
        verify(inventory, times(1)).setItem(eq(HIDE_WHEN_COVERED_SLOT), any());
        verify(inventory, never()).setItem(RESERVED_SLOT, toggleItem);
    }

    @Test
    @DisplayName("Schalter an: Beschreibung fuer an")
    void toggleOn() {
        when(ownerNameDisplay.isHideWhenCoveredEnabled()).thenReturn(true);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-owner-name-hide-when-covered-label", true,
                "toggle_owner_name_hide_when_covered", "gui-admin-owner-name-hide-when-covered-on");
    }

    @Test
    @DisplayName("Schalter aus: Beschreibung fuer aus")
    void toggleOff() {
        when(ownerNameDisplay.isHideWhenCoveredEnabled()).thenReturn(false);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-owner-name-hide-when-covered-label", false,
                "toggle_owner_name_hide_when_covered", "gui-admin-owner-name-hide-when-covered-off");
    }

    @Test
    @DisplayName("Ohne Admin-Permission wird das Admin-Panel nicht aufgebaut und der Schalter nicht gelesen")
    void withoutPermissionNoToggle() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        openAdmin();

        assertNull(inventory);
        verify(ownerNameDisplay, never()).isHideWhenCoveredEnabled();
    }
}
