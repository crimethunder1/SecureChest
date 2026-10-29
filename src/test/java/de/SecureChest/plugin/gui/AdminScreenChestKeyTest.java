package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminScreen: Button Chest Key")
class AdminScreenChestKeyTest {

    private static final int CHEST_KEY_SLOT = 43;
    private static final Set<Integer> OCCUPIED_SLOTS = Set.of(19, 21, 23, 25, 28, 31, 34, 37, 40, 49);

    private final ItemStack chestKeyButton = mock(ItemStack.class);
    private MessageService messages;
    private ItemStack chestKey;
    private GuiHelper helper;
    private AdminScreen screen;
    private Player player;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        when(plugin.ui()).thenReturn(new UiStateStore());
        AreaService areaService = mock(AreaService.class);
        when(areaService.areas()).thenReturn(List.of());
        when(plugin.areas()).thenReturn(areaService);
        when(plugin.ownerNameDisplay()).thenReturn(mock(OwnerNameDisplaySetting.class));
        when(plugin.chestKeyConsumption()).thenReturn(mock(ChestKeyConsumptionSetting.class));
        Items items = mock(Items.class);
        chestKey = mock(ItemStack.class);
        when(chestKey.getMaxStackSize()).thenReturn(64);
        when(items.chestKey()).thenReturn(chestKey);
        when(plugin.items()).thenReturn(items);

        helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), eq("give_chest_key"), any())).thenReturn(chestKeyButton);

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
    @DisplayName("Der Button liegt in einem freien Slot, der von keinem anderen Button belegt ist")
    void button_freierSlot() {
        assertFalse(OCCUPIED_SLOTS.contains(CHEST_KEY_SLOT));

        openAdmin();

        verify(inventory).setItem(CHEST_KEY_SLOT, chestKeyButton);
        verify(inventory, times(1)).setItem(eq(CHEST_KEY_SLOT), any());
        for (int slot : OCCUPIED_SLOTS) verify(inventory, never()).setItem(slot, chestKeyButton);
    }

    @Test
    @DisplayName("Der Button hat Material, Texte und die Aktion give_chest_key")
    void button_aktionUndTexte() {
        openAdmin();

        verify(helper).buildButton(Material.TRIPWIRE_HOOK, "gui-admin-chest-key-name",
                List.of("gui-admin-chest-key-lore"), "give_chest_key", "");
        assertTrue(ScreenActionRecorder.adminScreenActions().contains("give_chest_key"));
    }

    @Test
    @DisplayName("Ohne Admin-Permission wird das Admin-Panel und damit der Button nicht aufgebaut")
    void ohnePermission_keinButton() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        openAdmin();

        assertNull(inventory);
        verify(helper, never()).buildButton(any(), any(), any(), eq("give_chest_key"), any());
    }

    @Test
    @DisplayName("Prompt nennt die Obergrenze aus der Stapelgroesse, gelb")
    void prompt_obergrenze() {
        when(chestKey.getMaxStackSize()).thenReturn(16);

        screen.promptChestKeyAmount(player);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        TextComponent prompt = (TextComponent) captor.getValue();
        assertEquals("chat-prompt-chest-key-amount", prompt.content());
        assertEquals(NamedTextColor.YELLOW, prompt.color());
        verify(messages).get("chat-prompt-chest-key-amount", 16);
    }

    @Test
    @DisplayName("Prompt bei Stapelgroesse ueber 64 nennt 64")
    void prompt_hoechstens64() {
        when(chestKey.getMaxStackSize()).thenReturn(99);

        screen.promptChestKeyAmount(player);

        verify(messages).get("chat-prompt-chest-key-amount", 64);
        verify(messages, never()).get(anyString(), eq(99));
    }
}
