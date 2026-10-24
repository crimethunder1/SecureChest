package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Klick auf den Button Chest Key im Admin-Panel")
class GuiListenerChestKeyTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private Items items;
    private GuiManager gui;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        when(plugin.pendingChat()).thenReturn(pendingChat);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        items = mock(Items.class);
        when(plugin.items()).thenReturn(items);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Die Aktion give_chest_key ist Admin-only")
    void aktion_adminOnly() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("give_chest_key"));
    }

    @Test
    @DisplayName("Ohne Admin-Permission: keine Eingabe, kein Prompt, GUI nicht geschlossen, keine Keys")
    void ohnePermission_nichts() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        listener.startChestKeyAmountInput(player);

        assertFalse(pendingChat.containsKey(playerUuid));
        verify(player, never()).closeInventory();
        verify(gui, never()).promptChestKeyAmount(any());
        verifyNoInteractions(items);
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
    }

    @Test
    @DisplayName("Mit Permission: GUI geschlossen, Eintrag ChestKeyAmount in pendingChat, Prompt")
    void mitPermission_startetEingabe() {
        listener.startChestKeyAmountInput(player);

        assertEquals(new PendingChatAction.ChestKeyAmount(), pendingChat.get(playerUuid));
        verify(player).closeInventory();
        verify(gui).promptChestKeyAmount(player);
        verify(gui, never()).openAdmin(any());
        verifyNoInteractions(items);
    }

    @Test
    @DisplayName("Das GUI wird vor dem Eintrag geschlossen, sonst wuerde das Schliessen die Eingabe sofort abbrechen")
    void schliessen_vorEintrag() {
        doAnswer(invocation -> {
            assertFalse(pendingChat.containsKey(playerUuid), "Eintrag existiert schon beim Schliessen");
            return null;
        }).when(player).closeInventory();

        listener.startChestKeyAmountInput(player);

        InOrder order = inOrder(player, gui);
        order.verify(player).closeInventory();
        order.verify(gui).promptChestKeyAmount(player);
        assertTrue(pendingChat.containsKey(playerUuid));
    }
}
