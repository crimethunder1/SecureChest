package de.SecureChest.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TrustChatListener – Spielername-Validierung")
class TrustChatValidationTest {

    private static boolean isValidPlayerName(String name) {
        return name.length() >= 3 && name.length() <= 16
                && name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    @ParameterizedTest(name = "Gültig: \"{0}\"")
    @ValueSource(strings = {
        "Steve",
        "Alex",
        "Player123",
        "abc",
        "Unter_Strich",
        "A1B2C3D4E5F6G7H",
        "ABCDEFGHIJKLMNOP"
    })
    @DisplayName("Gültige Spielernamen werden akzeptiert")
    void valid(String name) {
        assertTrue(isValidPlayerName(name), "Sollte gültig sein: " + name);
    }

    @ParameterizedTest(name = "Ungültig: \"{0}\"")
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
    @DisplayName("Ungültige Spielernamen werden abgelehnt")
    void invalid(String name) {
        assertFalse(isValidPlayerName(name), "Sollte ungültig sein: " + name);
    }

    @ParameterizedTest(name = "Grenzwert: \"{0}\"")
    @ValueSource(strings = {
        "abc",
        "ABCDEFGHIJKLMNOP"
    })
    @DisplayName("Grenzwerte 3 und 16 Zeichen sind gültig")
    void grenzwerte_gueltig(String name) {
        assertTrue(isValidPlayerName(name));
    }
}
