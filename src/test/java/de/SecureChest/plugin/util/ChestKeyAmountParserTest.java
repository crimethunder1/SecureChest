package de.SecureChest.plugin.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChestKeyAmountParser: Anzahl der Chest Keys aus dem Chat")
class ChestKeyAmountParserTest {

    private static final int STACK_LIMIT = 64;

    private static int accepted(String input) {
        ChestKeyAmountParser.Result result = ChestKeyAmountParser.parse(input, STACK_LIMIT);
        return assertInstanceOf(ChestKeyAmountParser.Amount.class, result, "Sollte gueltig sein: '" + input + "'")
                .value();
    }

    private static ChestKeyAmountParser.Rejected rejected(String input) {
        ChestKeyAmountParser.Result result = ChestKeyAmountParser.parse(input, STACK_LIMIT);
        return assertInstanceOf(ChestKeyAmountParser.Rejected.class, result, "Sollte ungueltig sein: '" + input + "'");
    }

    @Test
    @DisplayName("Untergrenze 1 ist erlaubt")
    void anzahlEins() {
        assertEquals(1, accepted("1"));
    }

    @Test
    @DisplayName("Obergrenze 64 ist erlaubt")
    void anzahlVierundsechzig() {
        assertEquals(64, accepted("64"));
    }

    @Test
    @DisplayName("Zahl zwischen den Grenzen wird uebernommen")
    void anzahlDazwischen() {
        assertEquals(12, accepted("12"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-64", "65", "1.5", "1,5", "abc", "+5", "5x", "zwei",
            "99999999999", "2147483648", "-2147483649"})
    @DisplayName("Ungueltige Eingabe wird mit dem Text abgelehnt")
    void ungueltig(String input) {
        assertEquals(input, rejected(input).detail());
    }

    @Test
    @DisplayName("Leerzeichen am Anfang und Ende werden ignoriert")
    void leerzeichenAussen() {
        assertEquals(5, accepted("  5 "));
        assertEquals(5, accepted("\t5\t"));
    }

    @Test
    @DisplayName("Leerzeichen innerhalb der Eingabe machen sie ungueltig, Detail ohne Rand")
    void leerzeichenInnen() {
        assertEquals("1 2", rejected(" 1 2 ").detail());
        assertEquals("- 5", rejected("- 5").detail());
    }

    @Test
    @DisplayName("Leere Eingabe, nur Leerzeichen oder null werden abgelehnt")
    void leer() {
        assertEquals("", rejected("").detail());
        assertEquals("", rejected("   ").detail());
        assertEquals("", rejected(null).detail());
    }

    @Test
    @DisplayName("Obergrenze ist die Stapelgroesse, hoechstens 64")
    void obergrenze() {
        assertEquals(1, ChestKeyAmountParser.upperLimit(1));
        assertEquals(16, ChestKeyAmountParser.upperLimit(16));
        assertEquals(64, ChestKeyAmountParser.upperLimit(64));
        assertEquals(64, ChestKeyAmountParser.upperLimit(99));
    }

    @Test
    @DisplayName("Eine kleinere Stapelgroesse begrenzt die Anzahl")
    void kleinereStapelgroesse() {
        assertEquals(new ChestKeyAmountParser.Amount(16), ChestKeyAmountParser.parse("16", 16));
        assertEquals(new ChestKeyAmountParser.Rejected("17"), ChestKeyAmountParser.parse("17", 16));
    }
}
