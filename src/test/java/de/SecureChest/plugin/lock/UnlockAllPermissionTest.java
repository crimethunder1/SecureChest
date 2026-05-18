package de.SecureChest.plugin.lock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SqlLockService.isUnlockAllAllowed – Permission-Check (SEC-02)")
class UnlockAllPermissionTest {

    @Test
    @DisplayName("Spieler darf eigene Locks via unlockAll entfernen, auch ohne Admin")
    void eigene_locks_erlaubt() {
        UUID actor = UUID.randomUUID();
        assertTrue(SqlLockService.isUnlockAllAllowed(actor, actor, false));
    }

    @Test
    @DisplayName("Spieler darf fremde Locks NICHT entfernen ohne Admin-Permission")
    void fremde_locks_ohne_admin_verboten() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertFalse(SqlLockService.isUnlockAllAllowed(actor, other, false));
    }

    @Test
    @DisplayName("Admin darf fremde Locks entfernen")
    void admin_darf_fremde_locks() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(SqlLockService.isUnlockAllAllowed(actor, other, true));
    }

    @Test
    @DisplayName("Admin darf auch eigene Locks via unlockAll entfernen")
    void admin_darf_eigene_locks() {
        UUID actor = UUID.randomUUID();
        assertTrue(SqlLockService.isUnlockAllAllowed(actor, actor, true));
    }

    @Test
    @DisplayName("Eigene Locks haben Vorrang vor Admin-Flag (auch wenn isAdmin=false)")
    void selbst_immer_erlaubt_unabhaengig_von_admin() {
        UUID actor = UUID.randomUUID();
        assertTrue(SqlLockService.isUnlockAllAllowed(actor, actor, false));
        assertTrue(SqlLockService.isUnlockAllAllowed(actor, actor, true));
    }
}
