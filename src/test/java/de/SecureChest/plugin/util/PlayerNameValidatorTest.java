package de.SecureChest.plugin.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerNameValidator – Spielername-Validierung (REF-01 / SEC-10)")
class PlayerNameValidatorTest {

    @ParameterizedTest(name = "Gueltig: \"{0}\"")
    @ValueSource(strings = {
        "Steve",
        "Alex",
        "Player123",
        "abc",
        "Unter_Strich",
        "A1B2C3D4E5F6G7H",
        "ABCDEFGHIJKLMNOP"
    })
    @DisplayName("Gueltige Spielernamen werden akzeptiert")
    void gueltigeNamen_akzeptiert(String name) {
        assertTrue(PlayerNameValidator.isValid(name), "Sollte gueltig sein: " + name);
    }

    @ParameterizedTest(name = "Ungueltig: \"{0}\"")
    @ValueSource(strings = {
        "ab",
        "ABCDEFGHIJKLMNOPQ",
        "Spieler Name",
        "spieler@server",
        "00000000-0000-0000-0000-000000000001",
        "spieler;DROP TABLE locks;--",
        "",
        "  ",
        "abc!def"
    })
    @DisplayName("Ungueltige Spielernamen werden abgelehnt")
    void ungueltigeNamen_abgelehnt(String name) {
        assertFalse(PlayerNameValidator.isValid(name), "Sollte ungueltig sein: " + name);
    }

    @Test
    @DisplayName("null wird abgelehnt (NPE-sicher, im Gegensatz zur frueheren Implementation)")
    void nullName_abgelehnt() {
        assertDoesNotThrow(() -> PlayerNameValidator.isValid(null));
        assertFalse(PlayerNameValidator.isValid(null));
    }

    @ParameterizedTest(name = "Grenzwert: \"{0}\"")
    @ValueSource(strings = {
        "abc",
        "ABCDEFGHIJKLMNOP"
    })
    @DisplayName("Grenzwerte 3 und 16 Zeichen sind gueltig")
    void grenzwerte_3und16_gueltig(String name) {
        assertTrue(PlayerNameValidator.isValid(name));
    }

    @Test
    @DisplayName("Underscore ist erlaubt, andere Sonderzeichen nicht")
    void underscore_erlaubt_anderes_nicht() {
        assertTrue(PlayerNameValidator.isValid("a_b"));
        assertFalse(PlayerNameValidator.isValid("a-b"));
        assertFalse(PlayerNameValidator.isValid("a.b"));
        assertFalse(PlayerNameValidator.isValid("a+b"));
    }

    @Test
    @DisplayName("Unicode-Letters werden nicht erlaubt (nur ASCII-Alphanumerisch + Underscore)")
    void unicodeLetters_abgelehnt() {
        assertFalse(PlayerNameValidator.isValid("Müller"),
                "Mojang-Spielernamen sind ASCII-only; Unicode-Letters muessen abgelehnt werden");
        assertFalse(PlayerNameValidator.isValid("玩家A"));
    }
}
