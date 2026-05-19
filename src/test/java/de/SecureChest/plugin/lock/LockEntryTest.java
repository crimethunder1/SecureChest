package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("LockEntry – Trust-Logik und Modell-Integritaet")
class LockEntryTest {

    private static final UUID WORLD  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER  = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    private LockEntry entry;

    @BeforeEach
    void setUp() {
        LockKey key = new LockKey(WORLD, 0, 64, 0);

        org.bukkit.Material mat = mock(org.bukkit.Material.class);
        entry = new LockEntry(key, mat, OWNER, System.currentTimeMillis());
    }

    @Test
    @DisplayName("Neuer LockEntry hat keine vertrauten Spieler")
    void neuErstellterEntry_keineTrusted() {
        assertTrue(entry.trusted().isEmpty());
    }

    @Test
    @DisplayName("owner() gibt den gesetzten Besitzer zurück")
    void owner_korrekt() {
        assertEquals(OWNER, entry.owner());
    }

    @Test
    @DisplayName("put() fügt einen Trusted-Eintrag hinzu")
    void trusted_put_einEintrag() {
        entry.trustedMutable().put(USER_A, AccessLevel.VIEW);
        assertEquals(1, entry.trusted().size());
        assertEquals(AccessLevel.VIEW, entry.trusted().get(USER_A));
    }

    @Test
    @DisplayName("Mehrere Spieler können mit unterschiedlichen Levels vertraut sein")
    void trusted_mehrereEintraege() {
        entry.trustedMutable().put(USER_A, AccessLevel.VIEW);
        entry.trustedMutable().put(USER_B, AccessLevel.STOCKER);
        assertEquals(AccessLevel.VIEW,    entry.trusted().get(USER_A));
        assertEquals(AccessLevel.STOCKER, entry.trusted().get(USER_B));
    }

    @Test
    @DisplayName("Trust-Level kann überschrieben werden (VIEW → STOCKER)")
    void trusted_levelUeberschreiben() {
        entry.trustedMutable().put(USER_A, AccessLevel.VIEW);
        entry.trustedMutable().put(USER_A, AccessLevel.STOCKER);
        assertEquals(AccessLevel.STOCKER, entry.trusted().get(USER_A));
        assertEquals(1, entry.trusted().size(), "Kein doppelter Eintrag erwartet");
    }

    @Test
    @DisplayName("remove() löscht den Trust-Eintrag")
    void trusted_remove() {
        entry.trustedMutable().put(USER_A, AccessLevel.VIEW);
        entry.trustedMutable().remove(USER_A);
        assertFalse(entry.trusted().containsKey(USER_A));
        assertTrue(entry.trusted().isEmpty());
    }

    @Test
    @DisplayName("remove() eines nicht existierenden Eintrags ist fehlerfrei")
    void trusted_removeNichtVorhanden() {
        assertDoesNotThrow(() -> entry.trustedMutable().remove(USER_A));
    }

    @Test
    @DisplayName("Besitzer ist nicht im trusted()-Map eingetragen (Sonderbehandlung in Service)")
    void owner_nichtInTrustedMap() {

        assertFalse(entry.trusted().containsKey(OWNER),
                "Besitzer sollte nicht in der trusted-Map stehen");
    }

    @Test
    @DisplayName("trusted() liefert eine unveränderliche Sicht – put() wirft UnsupportedOperationException")
    void trusted_externalView_istImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> entry.trusted().put(USER_A, AccessLevel.VIEW),
                "External callers dürfen die Trust-Map nicht mutieren");
    }

    @Test
    @DisplayName("trustedMutable()-Mutation ist über trusted() sichtbar (gleiche Daten, andere View)")
    void trustedMutable_aenderung_sichtbarInView() {
        entry.trustedMutable().put(USER_A, AccessLevel.STOCKER);
        assertEquals(AccessLevel.STOCKER, entry.trusted().get(USER_A));
    }
}
