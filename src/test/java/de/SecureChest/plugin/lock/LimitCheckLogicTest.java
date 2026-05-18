package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Limit-Check – Max-Locks und Max-Hoppers pro Spieler")
class LimitCheckLogicTest {

    private boolean canCreateLock(int maxLocks, long currentOwned) {
        if (maxLocks <= 0) return true;
        return currentOwned < maxLocks;
    }

    @Test
    @DisplayName("maxLocks=0 bedeutet kein Limit – immer erlaubt")
    void keinLimit_immerErlaubt() {
        assertTrue(canCreateLock(0, 0));
        assertTrue(canCreateLock(0, 1000));
        assertTrue(canCreateLock(0, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("Negativer Limit-Wert wird wie 0 behandelt (kein Limit)")
    void negativLimit_keinLimit() {
        assertTrue(canCreateLock(-1, 999));
        assertTrue(canCreateLock(-100, 999));
    }

    @ParameterizedTest(name = "max={0}, owned={1} → erlaubt={2}")
    @CsvSource({
        "10, 0,  true",
        "10, 9,  true",
        "10, 10, false",
        "10, 11, false",
        "1,  0,  true",
        "1,  1,  false",
    })
    @DisplayName("Limit-Prüfung für verschiedene Werte")
    void limitPruefung(int max, long owned, boolean expected) {
        assertEquals(expected, canCreateLock(max, owned),
                String.format("max=%d, owned=%d sollte %b sein", max, owned, expected));
    }

    @Test
    @DisplayName("Genau am Limit = gesperrt")
    void genauAmLimit_gesperrt() {
        assertFalse(canCreateLock(100, 100));
    }

    @Test
    @DisplayName("Einer unter Limit = erlaubt")
    void einerUnterLimit_erlaubt() {
        assertTrue(canCreateLock(100, 99));
    }

    @Test
    @DisplayName("Limit=1 und 0 vorhanden = erlaubt (Erster Lock)")
    void ersterLock_erlaubt() {
        assertTrue(canCreateLock(1, 0));
    }

    @Test
    @DisplayName("Limit=1 und 1 vorhanden = gesperrt (zweiter Lock)")
    void zweiterLock_gesperrt() {
        assertFalse(canCreateLock(1, 1));
    }
}
