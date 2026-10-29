package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("LockScreen – Manage-Permission (SEC-08)")
class LockScreenPermissionTest {

    private GuiHelper helper;
    private GuiManager manager;
    private LockScreen screen;
    private String lockId;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        LockService locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        helper = mock(GuiHelper.class);
        manager = mock(GuiManager.class);
        screen = new LockScreen(plugin, helper, manager);

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(new LockEntry(key, Material.CHEST, UUID.randomUUID(), 0L)));
    }

    private Player foreignPlayer(boolean operator) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(operator);
        return player;
    }

    private void assertRejected(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("keine-berechtigung", ((TextComponent) captor.getValue()).content());
        verify(player, never()).openInventory(any(Inventory.class));
        verify(manager).openLocksList(player, 0);
        verifyNoInteractions(helper);
    }

    @Test
    @DisplayName("Owner darf die Manage-View sehen")
    void owner_darf_sehen() {
        assertTrue(LockScreen.canSeeLockManage(true, false));
    }

    @Test
    @DisplayName("Trusted Player darf die Manage-View sehen (read-only)")
    void trusted_darf_sehen() {
        assertTrue(LockScreen.canSeeLockManage(false, true));
    }

    @Test
    @DisplayName("Fremder ohne Trust darf die Manage-View NICHT sehen")
    void fremder_darf_nicht_sehen() {
        assertFalse(LockScreen.canSeeLockManage(false, false));
    }

    @Test
    @DisplayName("Owner darf das Schloss verwalten")
    void owner_darf_verwalten() {
        assertTrue(LockScreen.canManageLock(true));
    }

    @Test
    @DisplayName("Nicht-Besitzer darf das Schloss NICHT verwalten, auch nicht als Trusted Player")
    void nichtBesitzer_darf_nicht_verwalten() {
        assertFalse(LockScreen.canManageLock(false));
    }

    @Test
    @DisplayName("canSee impliziert NICHT canManage (Trust nur Read-Access)")
    void canSee_impliziert_nicht_canManage() {
        boolean isOwner = false, isTrusted = true;
        assertTrue(LockScreen.canSeeLockManage(isOwner, isTrusted));
        assertFalse(LockScreen.canManageLock(isOwner));
    }

    @Test
    @DisplayName("Operator auf fremder Sperre: Menue oeffnet sich nicht, Meldung keine-berechtigung, zurueck zur Liste")
    void operator_fremdeSperre_menueOeffnetNicht() {
        Player operator = foreignPlayer(true);

        screen.openLockManage(operator, lockId);

        assertRejected(operator);
    }

    @Test
    @DisplayName("Fremder ohne Operator-Rechte auf fremder Sperre: dieselbe Ablehnung wie beim Operator")
    void fremder_fremdeSperre_menueOeffnetNicht() {
        Player stranger = foreignPlayer(false);

        screen.openLockManage(stranger, lockId);

        assertRejected(stranger);
    }
}
