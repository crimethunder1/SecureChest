package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.AreaService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminScreen: Schalter fuer die Besitzernamen")
class AdminScreenOwnerNameDisplayTest {

    private static final int OWNER_NAME_DISPLAY_SLOT = 40;
    private static final Set<Integer> OCCUPIED_SLOTS = Set.of(19, 21, 23, 25, 28, 31, 34, 37, 49);

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

        helper = mock(GuiHelper.class);
        when(helper.buildToggleButton(any(), anyBoolean(), eq("toggle_owner_name_display"), any()))
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
    @DisplayName("Der Schalter liegt in einem freien Slot und ist nicht von anderen Buttons belegt")
    void schalter_freierSlot() {
        assertFalse(OCCUPIED_SLOTS.contains(OWNER_NAME_DISPLAY_SLOT));
        when(ownerNameDisplay.isEnabled()).thenReturn(true);

        openAdmin();

        verify(inventory).setItem(OWNER_NAME_DISPLAY_SLOT, toggleItem);
        verify(inventory, times(1)).setItem(eq(OWNER_NAME_DISPLAY_SLOT), any());
    }

    @Test
    @DisplayName("Anzeige aktiv: Schalter steht auf an, mit der Beschreibung fuer an")
    void schalter_an() {
        when(ownerNameDisplay.isEnabled()).thenReturn(true);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-owner-name-display-label", true,
                "toggle_owner_name_display", "gui-admin-owner-name-display-on");
    }

    @Test
    @DisplayName("Anzeige aus: Schalter steht auf aus, mit der Beschreibung fuer aus")
    void schalter_aus() {
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        openAdmin();

        verify(helper).buildToggleButton("gui-admin-owner-name-display-label", false,
                "toggle_owner_name_display", "gui-admin-owner-name-display-off");
    }

    @Test
    @DisplayName("Ohne Admin-Permission wird das Admin-Panel nicht aufgebaut")
    void ohnePermission_keinSchalter() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        openAdmin();

        assertNull(inventory);
        verify(helper, never()).buildToggleButton(any(), anyBoolean(), anyString(), any());
        verify(ownerNameDisplay, never()).isEnabled();
    }
}
