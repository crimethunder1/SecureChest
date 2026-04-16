package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testet die Limit-Check-Logik fuer Max-Locks/Hopper pro Spieler.
 * Da die Logik direkt in SqlLockService eingebettet ist, wird sie hier
 * als reine Entscheidungslogik ohne Bukkit-Abhaengigkeit repliziert.
 */
@DisplayName("Limit-Check – Max-Locks und Max-Hoppers pro Spieler")
class LimitCheckLogicTest {

    /**
     * Repliziert die Entscheidungslogik aus SqlLockService.lock():
     * Darf ein neuer Lock erstellt werden?
     */
    private boolean canCreateLock(int maxLocks, long currentOwned) {
        if (maxLocks <= 0) return true;            // 0 = kein Limit
        return currentOwned < maxLocks;
    }

    // ── Kein Limit (0) ────────────────────────────────────────────────────────

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

    // ── Mit Limit ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "max={0}, owned={1} → erlaubt={2}")
    @CsvSource({
        "10, 0,  true",   // leer
        "10, 9,  true",   // einer unter Limit
        "10, 10, false",  // genau am Limit
        "10, 11, false",  // über Limit
        "1,  0,  true",   // Limit=1, noch keiner
        "1,  1,  false",  // Limit=1, schon einer
    })
    @DisplayName("Limit-Prüfung für verschiedene Werte")
    void limitPruefung(int max, long owned, boolean expected) {
        assertEquals(expected, canCreateLock(max, owned),
                String.format("max=%d, owned=%d sollte %b sein", max, owned, expected));
    }

    // ── Grenzwerte ────────────────────────────────────────────────────────────

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
