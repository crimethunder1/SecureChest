package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Einstellungen: Spielerschalter fuer den Namen ueber den eigenen Sperren")
class MiscScreenShowOwnerNameTest {

    private static final int SHOW_OWNER_NAME_SLOT = 22;

    private final UUID playerUuid = UUID.randomUUID();
    private final ItemStack toggleItem = mock(ItemStack.class);
    private LockService locks;
    private GuiHelper helper;
    private MiscScreen screen;
    private Player player;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);

        helper = mock(GuiHelper.class);
        when(helper.buildToggleButton(any(), anyBoolean(), eq("toggle_show_owner_name"), any())).thenReturn(toggleItem);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        screen = new MiscScreen(plugin, helper, mock(GuiManager.class));
    }

    private void openSettings() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> {
                        inventory = mock(Inventory.class);
                        return inventory;
                    });
            screen.openSettings(player);
        }
    }

    @Test
    @DisplayName("Der Schalter liegt in Slot 22 zwischen Quick Lock und Quick Remove")
    void toggleInSlot22() {
        when(locks.getShowOwnerName(playerUuid)).thenReturn(true);

        openSettings();

        verify(inventory).setItem(SHOW_OWNER_NAME_SLOT, toggleItem);
        verify(inventory, times(1)).setItem(eq(SHOW_OWNER_NAME_SLOT), any());
        verify(helper).buildToggleButton(eq("gui-settings-quick-lock-label"), anyBoolean(), eq("toggle_quicklock"), any());
        verify(helper).buildToggleButton(eq("gui-settings-quick-remove-label"), anyBoolean(), eq("toggle_quickremove"), any());
    }

    @Test
    @DisplayName("Schalter an: Wert des Spielers, Beschreibung fuer an")
    void toggleOn() {
        when(locks.getShowOwnerName(playerUuid)).thenReturn(true);

        openSettings();

        verify(helper).buildToggleButton("gui-settings-show-owner-name-label", true,
                "toggle_show_owner_name", "gui-settings-show-owner-name-on");
    }

    @Test
    @DisplayName("Schalter aus: Wert des Spielers, Beschreibung fuer aus")
    void toggleOff() {
        when(locks.getShowOwnerName(playerUuid)).thenReturn(false);

        openSettings();

        verify(helper).buildToggleButton("gui-settings-show-owner-name-label", false,
                "toggle_show_owner_name", "gui-settings-show-owner-name-off");
    }
}
