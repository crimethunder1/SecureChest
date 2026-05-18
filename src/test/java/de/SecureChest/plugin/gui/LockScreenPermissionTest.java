package de.SecureChest.plugin.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockScreen – Manage-Permission (SEC-08)")
class LockScreenPermissionTest {

    @Test
    @DisplayName("Owner darf die Manage-View sehen")
    void owner_darf_sehen() {
        assertTrue(LockScreen.canSeeLockManage(true, false, false));
    }

    @Test
    @DisplayName("Admin darf die Manage-View sehen, auch ohne Owner/Trust")
    void admin_darf_sehen() {
        assertTrue(LockScreen.canSeeLockManage(false, true, false));
    }

    @Test
    @DisplayName("Trusted Player darf die Manage-View sehen (read-only)")
    void trusted_darf_sehen() {
        assertTrue(LockScreen.canSeeLockManage(false, false, true));
    }

    @Test
    @DisplayName("Fremder ohne Trust, ohne Admin darf die Manage-View NICHT sehen")
    void fremder_darf_nicht_sehen() {
        assertFalse(LockScreen.canSeeLockManage(false, false, false));
    }

    @Test
    @DisplayName("Owner darf das Schloss verwalten")
    void owner_darf_verwalten() {
        assertTrue(LockScreen.canManageLock(true, false));
    }

    @Test
    @DisplayName("Admin darf das Schloss verwalten")
    void admin_darf_verwalten() {
        assertTrue(LockScreen.canManageLock(false, true));
    }

    @Test
    @DisplayName("Trusted Player darf das Schloss NICHT verwalten (nur read-only)")
    void trusted_darf_nicht_verwalten() {
        assertFalse(LockScreen.canManageLock(false, false));
    }

    @Test
    @DisplayName("canSee impliziert NICHT canManage (Trust nur Read-Access)")
    void canSee_impliziert_nicht_canManage() {
        boolean isOwner = false, isAdmin = false, isTrusted = true;
        assertTrue(LockScreen.canSeeLockManage(isOwner, isAdmin, isTrusted));
        assertFalse(LockScreen.canManageLock(isOwner, isAdmin));
    }
}
