package de.SecureChest.plugin.area;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Area: Geometrie und Namensregeln")
class AreaTest {

    @Test
    @DisplayName("Ecken werden normalisiert, egal in welcher Reihenfolge sie kommen")
    void normalized_vertauschteEcken() {
        Area area = Area.normalized("spawn", "world", 10, -5, -20, 30);

        assertEquals(-20, area.minX());
        assertEquals(-5, area.minZ());
        assertEquals(10, area.maxX());
        assertEquals(30, area.maxZ());
    }

    @Test
    @DisplayName("Grenzen sind inklusive, ein Block daneben liegt ausserhalb")
    void contains_grenzenInklusive() {
        Area area = Area.normalized("spawn", "world", 0, 0, 10, 20);

        assertTrue(area.contains("world", 0, 0));
        assertTrue(area.contains("world", 10, 20));
        assertTrue(area.contains("world", 0, 20));
        assertTrue(area.contains("world", 10, 0));
        assertFalse(area.contains("world", -1, 0));
        assertFalse(area.contains("world", 11, 0));
        assertFalse(area.contains("world", 0, -1));
        assertFalse(area.contains("world", 0, 21));
    }

    @Test
    @DisplayName("Negative Koordinaten funktionieren inklusive der Grenzen")
    void contains_negativeKoordinaten() {
        Area area = Area.normalized("unten", "world", -100, -50, -10, -1);

        assertTrue(area.contains("world", -100, -50));
        assertTrue(area.contains("world", -10, -1));
        assertTrue(area.contains("world", -55, -25));
        assertFalse(area.contains("world", -101, -25));
        assertFalse(area.contains("world", -9, -25));
        assertFalse(area.contains("world", -55, 0));
    }

    @Test
    @DisplayName("Ein Areal aus einem einzigen Block enthaelt genau diesen Block")
    void contains_einzelnerBlock() {
        Area area = Area.normalized("punkt", "world", 5, 5, 5, 5);

        assertTrue(area.contains("world", 5, 5));
        assertFalse(area.contains("world", 5, 6));
        assertFalse(area.contains("world", 4, 5));
    }

    @Test
    @DisplayName("Andere Welt liegt nie im Areal, Weltnamen sind exakt")
    void contains_andereWelt() {
        Area area = Area.normalized("spawn", "world", 0, 0, 10, 10);

        assertFalse(area.contains("world_nether", 5, 5));
        assertFalse(area.contains("World", 5, 5));
        assertFalse(area.contains(null, 5, 5));
    }

    @Test
    @DisplayName("withCorners behaelt Name und Welt und normalisiert die neuen Ecken")
    void withCorners_behaeltNameUndWelt() {
        Area resized = Area.normalized("spawn", "world", 0, 0, 10, 10).withCorners(4, 8, -4, -8);

        assertEquals(new Area("spawn", "world", -4, -8, 4, 8), resized);
    }

    @Test
    @DisplayName("Konstruktor lehnt nicht normalisierte Ecken ab")
    void konstruktor_nichtNormalisiert() {
        assertThrows(IllegalArgumentException.class, () -> new Area("spawn", "world", 10, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Area("spawn", "world", 0, 10, 10, 0));
    }

    @Test
    @DisplayName("Konstruktor lehnt ungueltigen Namen und leere Welt ab")
    void konstruktor_ungueltigeWerte() {
        assertThrows(IllegalArgumentException.class, () -> new Area("mit leerzeichen", "world", 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Area("spawn", " ", 0, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> new Area("spawn", null, 0, 0, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "Spawn", "spawn_1", "markt-platz", "ABC_def-123", "_", "-",
            "abcdefghijklmnopqrstuvwxyz012345"})
    @DisplayName("Gueltige Namen: 1 bis 32 Zeichen aus Buchstaben, Ziffern, Unterstrich, Bindestrich")
    void isValidName_gueltig(String name) {
        assertTrue(Area.isValidName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "mit leerzeichen", "punkt.name", "doppel:punkt", "umlaut_\u00e4",
            "abcdefghijklmnopqrstuvwxyz0123456", "slash/name", "stern*"})
    @DisplayName("Ungueltige Namen werden abgelehnt")
    void isValidName_ungueltig(String name) {
        assertFalse(Area.isValidName(name));
    }

    @Test
    @DisplayName("null ist kein gueltiger Name")
    void isValidName_null() {
        assertFalse(Area.isValidName(null));
    }

    @Test
    @DisplayName("nameKey ignoriert Gross- und Kleinschreibung")
    void nameKey_ohneGrossKlein() {
        assertEquals(Area.nameKey("Spawn"), Area.nameKey("sPAWN"));
    }
}
