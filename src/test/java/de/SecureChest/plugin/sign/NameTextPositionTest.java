package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameTextPosition: Mittelpunkt und Hoehe des Namenstextes")
class NameTextPositionTest {

    private final UUID worldUuid = new UUID(0L, 1L);

    private LockKey at(int x, int y, int z) {
        return new LockKey(worldUuid, x, y, z);
    }

    @Test
    @DisplayName("Ein Feld: Blockmitte in x und z, Hoehe 1.35 ueber dem Block wie bisher")
    void singleField() {
        NameTextPosition position = NameTextPosition.above(List.of(at(5, 64, 7)));

        assertEquals(worldUuid, position.worldUuid());
        assertEquals(5.5, position.x(), 0.0);
        assertEquals(65.35, position.y(), 1e-9);
        assertEquals(7.5, position.z(), 0.0);
        assertEquals(1.35, NameTextPosition.HEIGHT_ABOVE_BLOCK, 0.0);
    }

    @Test
    @DisplayName("Zwei Felder: genau zwischen den Feldern")
    void twoFields() {
        NameTextPosition position = NameTextPosition.above(List.of(at(5, 64, 7), at(6, 64, 7)));

        assertEquals(6.0, position.x(), 0.0);
        assertEquals(7.5, position.z(), 0.0);
    }

    @Test
    @DisplayName("L-Form: Durchschnitt der Feldmitten")
    void lShape() {
        NameTextPosition position = NameTextPosition.above(List.of(at(0, 10, 0), at(1, 10, 0), at(0, 10, 1)));

        assertEquals(1.0 / 3 + 0.5, position.x(), 1e-9);
        assertEquals(1.0 / 3 + 0.5, position.z(), 1e-9);
        assertEquals(11.35, position.y(), 1e-9);
    }

    @Test
    @DisplayName("Negative Koordinaten und negative Hoehe")
    void negativeCoordinates() {
        NameTextPosition position = NameTextPosition.above(List.of(at(-1, -20, -16), at(-2, -20, -16)));

        assertEquals(-1.0, position.x(), 0.0);
        assertEquals(-15.5, position.z(), 0.0);
        assertEquals(-18.65, position.y(), 1e-9);
    }

    @ParameterizedTest(name = "Felder x {0} bis {1}: Chunk {2}")
    @CsvSource({
            "15, 15, 0",
            "15, 16, 1",
            "16, 16, 1",
            "-1, -1, -1",
            "-1, 0, 0",
            "-17, -17, -2",
            "-17, -16, -1"
    })
    @DisplayName("Chunk des Textes nach dem Mittelpunkt")
    void chunkOfText(int fromX, int toX, int expectedChunk) {
        NameTextPosition alongX = NameTextPosition.above(List.of(at(fromX, 64, 0), at(toX, 64, 0)).stream().distinct().toList());
        NameTextPosition alongZ = NameTextPosition.above(List.of(at(0, 64, fromX), at(0, 64, toX)).stream().distinct().toList());

        assertEquals(expectedChunk, alongX.chunkX());
        assertEquals(0, alongX.chunkZ());
        assertEquals(expectedChunk, alongZ.chunkZ());
        assertEquals(0, alongZ.chunkX());
    }

    @Test
    @DisplayName("Felder in verschiedenen Welten, auf verschiedenen Hoehen oder keine Felder werden abgelehnt")
    void invalidInput() {
        LockKey otherWorld = new LockKey(new UUID(0L, 2L), 1, 64, 0);

        assertThrows(IllegalArgumentException.class, () -> NameTextPosition.above(List.of(at(0, 64, 0), otherWorld)));
        assertThrows(IllegalArgumentException.class, () -> NameTextPosition.above(List.of(at(0, 64, 0), at(1, 65, 0))));
        assertThrows(IllegalArgumentException.class, () -> NameTextPosition.above(List.of()));
    }
}
