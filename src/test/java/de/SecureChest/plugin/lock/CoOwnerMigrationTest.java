package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CO_OWNER → STOCKER Migration (Verhalten)")
class CoOwnerMigrationTest {

    @Test
    @DisplayName("fromString(\"CO_OWNER\") liefert null nach Enum-Removal")
    void coOwner_nichtMehrParsbar() {
        assertNull(AccessLevel.fromString("CO_OWNER"));
    }

    @Test
    @DisplayName("AccessLevel.values() enthaelt nur noch VIEW und STOCKER")
    void enum_hat_nur_zwei_werte() {
        assertEquals(2, AccessLevel.values().length);
        assertEquals(AccessLevel.VIEW, AccessLevel.values()[0]);
        assertEquals(AccessLevel.STOCKER, AccessLevel.values()[1]);
    }

    @Test
    @DisplayName("STOCKER ist der Ziel-Level fuer migrierte CO_OWNER-Eintraege")
    void stocker_istMigrationsZiel() {
        assertEquals("STOCKER", AccessLevel.STOCKER.name());
    }
}
