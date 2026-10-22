package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Bestaetigung einer Wirkungsbereich-Aenderung")
class GuiListenerAreaConfirmTest {

    private final UUID playerUuid = UUID.randomUUID();
    private AreaService areaService;
    private LockService locks;
    private GuiManager gui;
    private ConfirmManager confirms;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        areaService = mock(AreaService.class);
        when(plugin.areas()).thenReturn(areaService);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin);
    }

    private void storeAreaConfirmation(AreaChange change, long expiresAt) {
        confirms.set(playerUuid, new ConfirmRequest(ConfirmType.AREA_CHANGE, "", "AREAS", expiresAt, change));
    }

    private long inOneMinute() {
        return System.currentTimeMillis() + 60_000L;
    }

    @Test
    @DisplayName("Ja: apply mit der gespeicherten Aenderung, Ergebnis gemeldet, zurueck zum Wirkungsbereich-Screen")
    void ja_wendetAn() {
        AreaChange.Delete delete = new AreaChange.Delete("Spawn");
        AreaChangeResult result = new AreaChangeResult(delete, true, List.of(), 5, 1, 2, null);
        when(areaService.apply(delete)).thenReturn(result);
        storeAreaConfirmation(delete, inOneMinute());

        listener.handleConfirmAction(player, true);

        verify(areaService).apply(delete);
        verify(gui).sendAreaResult(player, result);
        verify(gui).openAreas(player, 0);
        verify(player, never()).sendMessage(any(Component.class));
        assertTrue(confirms.get(playerUuid).isEmpty());
    }

    @Test
    @DisplayName("Ja ohne Admin-Permission: nichts wird angewendet")
    void ja_ohnePermission_nichts() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        storeAreaConfirmation(new AreaChange.Enable(), inOneMinute());

        listener.handleConfirmAction(player, true);

        verify(areaService, never()).apply(any());
        verify(gui, never()).sendAreaResult(any(), any());
        verify(gui).openAreas(player, 0);
    }

    @Test
    @DisplayName("Nein: nichts wird angewendet, zurueck zum Wirkungsbereich-Screen")
    void nein_nichts() {
        storeAreaConfirmation(new AreaChange.Resize("Spawn", 0, 0, 1, 1), inOneMinute());

        listener.handleConfirmAction(player, false);

        verify(areaService, never()).apply(any());
        verify(gui).openAreas(player, 0);
        assertTrue(confirms.get(playerUuid).isEmpty());
    }

    @Test
    @DisplayName("Zeitueberschreitung: nichts wird angewendet, Hinweis wie bei den anderen Bestaetigungen")
    void abgelaufen_nichts() {
        storeAreaConfirmation(new AreaChange.Enable(), System.currentTimeMillis() - 1L);

        listener.handleConfirmAction(player, true);

        verify(areaService, never()).apply(any());
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("gui-confirm-expired", ((TextComponent) captor.getValue()).content());
        verify(gui).openMain(player);
    }

    @Test
    @DisplayName("Andere Bestaetigungen melden weiterhin Erledigt")
    void andereBestaetigung_unveraendert() {
        when(locks.restoreUndo(player)).thenReturn(true);
        confirms.set(playerUuid, new ConfirmRequest(ConfirmType.UNDO_RESTORE, "", "MAIN", inOneMinute()));

        listener.handleConfirmAction(player, true);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("gui-erledigt", ((TextComponent) captor.getValue()).content());
        verify(gui).openMain(player);
    }

    @Test
    @DisplayName("Bestehende Bestaetigungen tragen keine Wirkungsbereich-Aenderung")
    void vierArgumentKonstruktor_ohneAreaChange() {
        assertNull(new ConfirmRequest(ConfirmType.UNLOCK_ONE, "id", "MAIN", 0L).areaChange());
    }
}
