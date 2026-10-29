package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccessLevel – fromString Parsing")
class AccessLevelTest {

    @ParameterizedTest(name = "\"{0}\" → VIEW")
    @ValueSource(strings = {"VIEW", "view", "View", "  VIEW  "})
    @DisplayName("VIEW wird case-insensitiv und mit Whitespace erkannt")
    void fromString_view(String input) {
        assertEquals(AccessLevel.VIEW, AccessLevel.fromString(input));
    }

    @ParameterizedTest(name = "\"{0}\" → STOCKER")
    @ValueSource(strings = {"STOCKER", "stocker", "Stocker"})
    @DisplayName("STOCKER wird case-insensitiv erkannt")
    void fromString_stocker(String input) {
        assertEquals(AccessLevel.STOCKER, AccessLevel.fromString(input));
    }

    @ParameterizedTest(name = "ungültig: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "OWNER", "NONE", "co-owner", "0", "CO_OWNER", "co_owner"})
    @DisplayName("Ungültige Strings liefern null (CO_OWNER entfernt, Migration setzt sie auf STOCKER)")
    void fromString_ungueltig(String input) {
        assertNull(AccessLevel.fromString(input));
    }

    @Test
    @DisplayName("name() liefert den kanonischen Datenbank-String zurück")
    void name_kannonischForDb() {
        assertEquals("VIEW",     AccessLevel.VIEW.name());
        assertEquals("STOCKER",  AccessLevel.STOCKER.name());
    }

    @Test
    @DisplayName("fromString(level.name()) ist immer ein valider Round-Trip")
    void roundTrip() {
        for (AccessLevel level : AccessLevel.values()) {
            assertEquals(level, AccessLevel.fromString(level.name()),
                    "Round-Trip fehlgeschlagen für " + level);
        }
    }
}
