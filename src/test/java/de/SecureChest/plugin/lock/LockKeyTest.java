package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockKey – Serialisierung und Parsing")
class LockKeyTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ── asId / fromId Round-Trip ──────────────────────────────────────────────

    @Test
    @DisplayName("asId erzeugt lesbaren String mit vier Segmenten")
    void asId_hatVierSegmente() {
        LockKey key = new LockKey(WORLD, 10, 64, -5);
        String id   = key.asId();
        String[] parts = id.split(":");
        assertEquals(4, parts.length, "asId() muss genau 4 ':'-getrennte Segmente liefern");
    }

    @Test
    @DisplayName("fromId rekonstruiert identischen LockKey")
    void fromId_roundTrip() {
        LockKey original   = new LockKey(WORLD, 100, -64, 999);
        LockKey parsed     = LockKey.fromId(original.asId());
        assertEquals(original.worldUuid(), parsed.worldUuid());
        assertEquals(original.x(), parsed.x());
        assertEquals(original.y(), parsed.y());
        assertEquals(original.z(), parsed.z());
    }

    @ParameterizedTest(name = "Koordinaten ({0},{1},{2})")
    @CsvSource({
        "0, 0, 0",
        "2147483647, 2147483647, 2147483647",
        "-2147483648, -2147483648, -2147483648",
        "-1, 64, 1000000"
    })
    @DisplayName("Round-Trip mit Grenzwert-Koordinaten")
    void fromId_grenzwerte(int x, int y, int z) {
        LockKey key    = new LockKey(WORLD, x, y, z);
        LockKey parsed = LockKey.fromId(key.asId());
        assertEquals(x, parsed.x());
        assertEquals(y, parsed.y());
        assertEquals(z, parsed.z());
    }

    @Test
    @DisplayName("fromId mit ungültigem String wirft Exception")
    void fromId_ungueltigerString_wurfException() {
        assertThrows(Exception.class, () -> LockKey.fromId("nicht:gueltig"));
        assertThrows(Exception.class, () -> LockKey.fromId(""));
        assertThrows(Exception.class, () -> LockKey.fromId("a:b:c:kein-int"));
    }

    // ── Gleichheit ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Zwei LockKeys mit gleichen Werten haben gleiches asId()")
    void asId_zweimalGleicherKey_gleicheId() {
        LockKey a = new LockKey(WORLD, 1, 2, 3);
        LockKey b = new LockKey(WORLD, 1, 2, 3);
        assertEquals(a.asId(), b.asId());
    }

    @Test
    @DisplayName("Unterschiedliche Koordinaten ergeben unterschiedliche IDs")
    void asId_unterschiedlicheKoords_unterschiedlicheId() {
        LockKey a = new LockKey(WORLD, 1, 2, 3);
        LockKey b = new LockKey(WORLD, 3, 2, 1);
        assertNotEquals(a.asId(), b.asId());
    }
}
