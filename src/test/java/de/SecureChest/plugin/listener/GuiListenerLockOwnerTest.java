package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Aktionen des Verwaltungsmenues pruefen den Besitzer erneut, ohne Admin-Ausnahme")
class GuiListenerLockOwnerTest {

    private final UUID ownerUuid = UUID.randomUUID();
    private final UUID targetUuid = UUID.randomUUID();
    private LockService locks;
    private GuiManager gui;
    private ConfirmManager confirms;
    private GuiListener listener;
    private String lockId;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        when(plugin.ui()).thenReturn(new UiStateStore());
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(new LockEntry(key, Material.CHEST, ownerUuid, 0L)));
        when(locks.unlockByIdWithUndo(any(), anyString())).thenReturn(true);
        when(locks.removeTrustById(any(), anyString(), any())).thenReturn(true);
        when(locks.transferLock(any(), anyString(), any())).thenReturn(true);
        when(locks.setTrustById(any(), anyString(), any(), any())).thenReturn(true);
        when(locks.unlockAllWithUndo(any(), any())).thenReturn(true);
        when(locks.restoreUndo(any())).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private Player player(UUID uuid, boolean operator) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(operator);
        return player;
    }

    private Player foreignOperator() {
        return player(UUID.randomUUID(), true);
    }

    private void confirm(Player player, ConfirmType type, String data, String returnTo) {
        confirms.set(player.getUniqueId(), new ConfirmRequest(type, data, returnTo, Long.MAX_VALUE));
        listener.handleConfirmAction(player, true);
    }

    private List<String> sentTexts(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Entsperren per ID (UNLOCK_ONE): fremder Operator wird abgelehnt, Dienst wird nicht aufgerufen")
    void unlockOne_fremderOperator_abgelehnt() {
        Player operator = foreignOperator();

        confirm(operator, ConfirmType.UNLOCK_ONE, lockId, "LOCK_MANAGE|" + lockId);

        verify(locks, never()).unlockByIdWithUndo(any(), anyString());
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts(operator));
        verify(gui).openLockManage(operator, lockId);
    }

    @Test
    @DisplayName("Vertrauen entfernen per ID (REMOVE_TRUST): fremder Operator wird abgelehnt")
    void removeTrust_fremderOperator_abgelehnt() {
        Player operator = foreignOperator();

        confirm(operator, ConfirmType.REMOVE_TRUST, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);

        verify(locks, never()).removeTrustById(any(), anyString(), any());
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts(operator));
    }

    @Test
    @DisplayName("Uebertragen (TRANSFER_LOCK): fremder Operator wird abgelehnt")
    void transfer_fremderOperator_abgelehnt() {
        Player operator = foreignOperator();

        confirm(operator, ConfirmType.TRANSFER_LOCK, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);

        verify(locks, never()).transferLock(any(), anyString(), any());
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts(operator));
    }

    @Test
    @DisplayName("Vertrauen setzen per ID (set_trust_role, Klick auf Kopf): fremder Operator wird abgelehnt")
    void setTrustRole_fremderOperator_abgelehnt() {
        Player operator = foreignOperator();

        listener.setTrustRole(operator, "STOCKER|" + lockId + "|" + targetUuid);

        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        assertEquals(List.of("trust-vergeben-fehlgeschlagen"), sentTexts(operator));
        verify(gui).openLockManage(operator, lockId);
    }

    @Test
    @DisplayName("Besitzer mit Operator-Rechten: alle Aktionen per ID rufen den Dienst wie bisher auf")
    void besitzerMitOperator_erlaubt() {
        Player owner = player(ownerUuid, true);

        confirm(owner, ConfirmType.UNLOCK_ONE, lockId, "LOCK_MANAGE|" + lockId);
        confirm(owner, ConfirmType.REMOVE_TRUST, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);
        confirm(owner, ConfirmType.TRANSFER_LOCK, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);
        listener.setTrustRole(owner, "VIEW|" + lockId + "|" + targetUuid);

        verify(locks).unlockByIdWithUndo(owner, lockId);
        verify(locks).removeTrustById(owner, lockId, targetUuid);
        verify(locks).transferLock(owner, lockId, targetUuid);
        verify(locks).setTrustById(owner, lockId, targetUuid, AccessLevel.VIEW);
        assertEquals(List.of("gui-erledigt", "gui-erledigt", "gui-erledigt", "gui-vertrauen-aktualisiert"),
                sentTexts(owner));
    }

    @Test
    @DisplayName("Besitzer ohne Operator-Rechte: alle Aktionen per ID rufen den Dienst wie bisher auf")
    void besitzerOhneOperator_erlaubt() {
        Player owner = player(ownerUuid, false);

        confirm(owner, ConfirmType.UNLOCK_ONE, lockId, "LOCK_MANAGE|" + lockId);
        confirm(owner, ConfirmType.REMOVE_TRUST, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);
        confirm(owner, ConfirmType.TRANSFER_LOCK, lockId + "|" + targetUuid, "LOCK_MANAGE|" + lockId);
        listener.setTrustRole(owner, "STOCKER|" + lockId + "|" + targetUuid);

        verify(locks).unlockByIdWithUndo(owner, lockId);
        verify(locks).removeTrustById(owner, lockId, targetUuid);
        verify(locks).transferLock(owner, lockId, targetUuid);
        verify(locks).setTrustById(owner, lockId, targetUuid, AccessLevel.STOCKER);
    }

    @Test
    @DisplayName("Sperre nicht mehr vorhanden: Entsperren geht wie bisher an den Dienst, der das selbst behandelt")
    void sperreFehlt_dienstEntscheidetWieBisher() {
        Player operator = foreignOperator();
        String missingLockId = new LockKey(UUID.randomUUID(), 0, 0, 0).asId();

        confirm(operator, ConfirmType.UNLOCK_ONE, missingLockId, "LOCKS");

        verify(locks).unlockByIdWithUndo(operator, missingLockId);
    }

    @Test
    @DisplayName("Admin-Panel: globales Entsperren eines fremden Spielers durch einen Operator bleibt unveraendert")
    void globalesEntsperren_unveraendert() {
        Player operator = foreignOperator();

        confirm(operator, ConfirmType.GLOBAL_UNLOCK, ownerUuid.toString(), "ADMIN");

        verify(locks).unlockAllWithUndo(operator, ownerUuid);
        assertEquals(List.of("gui-erledigt"), sentTexts(operator));
        verify(gui).openAdmin(operator);
    }

    @Test
    @DisplayName("Rueckgaengig aus dem Hauptmenue (UNDO_RESTORE) bleibt unveraendert und geht direkt an den Dienst")
    void rueckgaengig_unveraendert() {
        Player operator = foreignOperator();

        confirm(operator, ConfirmType.UNDO_RESTORE, "", "MAIN");

        verify(locks).restoreUndo(operator);
        assertEquals(List.of("gui-erledigt"), sentTexts(operator));
    }
}
