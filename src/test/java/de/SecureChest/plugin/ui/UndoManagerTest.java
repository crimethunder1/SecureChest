package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.lock.LockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UndoManager – Stack-Logik, TTL und Limits")
class UndoManagerTest {

    private static final UUID PLAYER  = UUID.randomUUID();
    private static final UUID WORLD   = UUID.randomUUID();
    private static final UUID OWNER   = UUID.randomUUID();

    /** TTL so weit in der Zukunft dass Snapshots nicht ablaufen. */
    private static final long FAR_FUTURE = System.currentTimeMillis() + 60_000L;
    /** TTL bereits abgelaufen. */
    private static final long EXPIRED    = System.currentTimeMillis() - 1L;

    private UndoManager mgr;

    @BeforeEach
    void setUp() {
        mgr = new UndoManager(3); // maxHistory = 3
    }

    private UndoSnapshot snap(long expiresAt) {
        LockKey key = new LockKey(WORLD, 0, 64, 0);
        return new UndoSnapshot(
                List.of(new UndoSnapshot.LockRow(key, "CHEST", OWNER, 0L)),
                List.of(),
                List.of(),
                System.currentTimeMillis(),
                expiresAt
        );
    }

    // ── Grundlegendes Verhalten ───────────────────────────────────────────────

    @Test
    @DisplayName("Frischer Manager hat keinen Undo-Eintrag")
    void leer_keinUndo() {
        assertFalse(mgr.hasUndo(PLAYER));
        assertEquals(0, mgr.count(PLAYER));
    }

    @Test
    @DisplayName("set() speichert einen Snapshot; hasUndo() gibt true zurück")
    void set_hasUndo() {
        mgr.set(PLAYER, snap(FAR_FUTURE));
        assertTrue(mgr.hasUndo(PLAYER));
    }

    @Test
    @DisplayName("consume() liefert den Snapshot und entfernt ihn")
    void consume_liefertUndEntfernt() {
        UndoSnapshot s = snap(FAR_FUTURE);
        mgr.set(PLAYER, s);

        Optional<UndoSnapshot> result = mgr.consume(PLAYER);
        assertTrue(result.isPresent());
        assertFalse(mgr.hasUndo(PLAYER), "Nach consume() sollte kein Undo mehr vorhanden sein");
    }

    @Test
    @DisplayName("get() liest Snapshot ohne ihn zu entfernen (peek)")
    void get_istPeek() {
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.get(PLAYER);                    // peek
        assertTrue(mgr.hasUndo(PLAYER));    // immer noch da
    }

    @Test
    @DisplayName("consume() auf leere Map gibt empty zurück")
    void consume_leer() {
        assertTrue(mgr.consume(PLAYER).isEmpty());
    }

    // ── TTL / Ablauf ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Abgelaufener Snapshot wird von hasUndo() ignoriert")
    void abgelaufen_hasUndoFalse() {
        mgr.set(PLAYER, snap(EXPIRED));
        assertFalse(mgr.hasUndo(PLAYER));
    }

    @Test
    @DisplayName("consume() überspringt abgelaufene Snapshots")
    void consume_abgelaufenLiefertEmpty() {
        mgr.set(PLAYER, snap(EXPIRED));
        assertTrue(mgr.consume(PLAYER).isEmpty());
    }

    @Test
    @DisplayName("Gültiger Snapshot nach abgelaufenem wird gefunden")
    void gueltigerNachAbgelaufenem() {
        UndoSnapshot fresh = snap(FAR_FUTURE);
        mgr.set(PLAYER, snap(EXPIRED));   // alt (wird unten auf Stack gelegt)
        mgr.set(PLAYER, fresh);           // neu (oben auf Stack)

        Optional<UndoSnapshot> result = mgr.consume(PLAYER);
        assertTrue(result.isPresent());
        // Der frische Snapshot ist oben – er wird zuerst geliefert
        assertEquals(fresh.createdAt(), result.get().createdAt());
    }

    // ── Stack-Limit ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Stack wächst nicht über maxHistory hinaus")
    void stackLimit_maxHistory3() {
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.set(PLAYER, snap(FAR_FUTURE)); // 4. → ältester fliegt raus
        assertEquals(3, mgr.count(PLAYER));
    }

    @Test
    @DisplayName("Neuester Snapshot ist nach set() oben auf dem Stack")
    void neusterObenAufStack() {
        UndoSnapshot first  = snap(FAR_FUTURE);
        UndoSnapshot second = snap(FAR_FUTURE + 1); // leicht unterschiedliche expiresAt
        mgr.set(PLAYER, first);
        mgr.set(PLAYER, second);

        Optional<UndoSnapshot> top = mgr.consume(PLAYER);
        assertTrue(top.isPresent());
        assertEquals(second.expiresAt(), top.get().expiresAt(), "Neuester Snapshot sollte oben liegen");
    }

    @Test
    @DisplayName("UndoManager mit maxHistory=1 speichert nur einen Eintrag")
    void maxHistory1() {
        UndoManager single = new UndoManager(1);
        single.set(PLAYER, snap(FAR_FUTURE));
        single.set(PLAYER, snap(FAR_FUTURE));
        assertEquals(1, single.count(PLAYER));
    }

    @Test
    @DisplayName("Negativer maxHistory-Wert wird auf 1 begrenzt")
    void maxHistoryNegativ() {
        UndoManager mgr2 = new UndoManager(-5);
        mgr2.set(PLAYER, snap(FAR_FUTURE));
        mgr2.set(PLAYER, snap(FAR_FUTURE));
        assertEquals(1, mgr2.count(PLAYER),
                "Negativer maxHistory-Wert sollte auf 1 begrenzt werden");
    }

    // ── clear() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clear() entfernt alle Snapshots des Spielers")
    void clear_leerIstDanach() {
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.set(PLAYER, snap(FAR_FUTURE));
        mgr.clear(PLAYER);
        assertFalse(mgr.hasUndo(PLAYER));
        assertEquals(0, mgr.count(PLAYER));
    }

    @Test
    @DisplayName("clear() auf nicht-vorhandenen Spieler ist fehlerfrei")
    void clear_nichtVorhanden() {
        assertDoesNotThrow(() -> mgr.clear(UUID.randomUUID()));
    }

    // ── Isolation zwischen Spielern ───────────────────────────────────────────

    @Test
    @DisplayName("Snapshots eines Spielers beeinflussen keinen anderen")
    void spielerIsolation() {
        UUID other = UUID.randomUUID();
        mgr.set(PLAYER, snap(FAR_FUTURE));

        assertFalse(mgr.hasUndo(other), "Anderer Spieler sollte keine Undos haben");
        assertEquals(0, mgr.count(other));
    }
}
