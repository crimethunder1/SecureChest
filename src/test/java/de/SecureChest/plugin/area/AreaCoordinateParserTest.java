package de.SecureChest.plugin.area;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AreaCoordinateParser: vier Ganzzahlen x1 z1 x2 z2")
class AreaCoordinateParserTest {

    private static AreaCoordinateParser.Coordinates accepted(String input) {
        AreaCoordinateParser.Result result = AreaCoordinateParser.parse(input);
        return assertInstanceOf(AreaCoordinateParser.Coordinates.class, result, "Sollte gueltig sein: " + input);
    }

    private static AreaCoordinateParser.Rejected rejected(String input) {
        AreaCoordinateParser.Result result = AreaCoordinateParser.parse(input);
        return assertInstanceOf(AreaCoordinateParser.Rejected.class, result, "Sollte ungueltig sein: " + input);
    }

    @Test
    @DisplayName("Vier positive Ganzzahlen werden in Eingabereihenfolge uebernommen")
    void gueltig_positiv() {
        assertEquals(new AreaCoordinateParser.Coordinates(10, 20, 30, 40), accepted("10 20 30 40"));
    }

    @Test
    @DisplayName("Negative Zahlen mit fuehrendem Minus sind erlaubt")
    void gueltig_negativ() {
        assertEquals(new AreaCoordinateParser.Coordinates(-10, -20, 30, -40), accepted("-10 -20 30 -40"));
    }

    @Test
    @DisplayName("Ecken werden nicht normalisiert, das macht der AreaService")
    void gueltig_nichtNormalisiert() {
        assertEquals(new AreaCoordinateParser.Coordinates(50, 60, -50, -60), accepted("50 60 -50 -60"));
    }

    @Test
    @DisplayName("Null, minus null und fuehrende Nullen sind Ganzzahlen")
    void gueltig_nullen() {
        assertEquals(new AreaCoordinateParser.Coordinates(0, 0, 7, -7), accepted("0 -0 007 -007"));
    }

    @Test
    @DisplayName("Beliebig viele Leerzeichen zwischen und um die Zahlen")
    void gueltig_leerzeichen() {
        assertEquals(new AreaCoordinateParser.Coordinates(1, 2, 3, 4), accepted("   1     2  3        4   "));
    }

    @Test
    @DisplayName("Grenzwerte von int werden akzeptiert")
    void gueltig_intGrenzen() {
        assertEquals(new AreaCoordinateParser.Coordinates(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 0),
                accepted("2147483647 -2147483648 0 0"));
    }

    @ParameterizedTest(name = "\"{0}\" hat {1} Zahlen")
    @CsvSource(delimiter = '|', value = {
            "1 2 3|3",
            "1|1",
            "1 2 3 4 5|5",
            "1 2 3 4 5 6 7 8|8",
            "1,2,3,4|1"
    })
    @DisplayName("Zu wenige oder zu viele Zahlen ergeben WRONG_COUNT mit der Anzahl")
    void falscheAnzahl(String input, String count) {
        AreaCoordinateParser.Rejected result = rejected(input);
        assertEquals(AreaCoordinateParser.Reason.WRONG_COUNT, result.reason());
        assertEquals(count, result.detail());
    }

    @ParameterizedTest(name = "Leer: \"{0}\"")
    @ValueSource(strings = {"", " ", "      "})
    @DisplayName("Leere Eingabe ergibt WRONG_COUNT mit 0")
    void leer(String input) {
        AreaCoordinateParser.Rejected result = rejected(input);
        assertEquals(AreaCoordinateParser.Reason.WRONG_COUNT, result.reason());
        assertEquals("0", result.detail());
    }

    @Test
    @DisplayName("null ergibt WRONG_COUNT mit 0")
    void nullEingabe() {
        AreaCoordinateParser.Rejected result = rejected(null);
        assertEquals(AreaCoordinateParser.Reason.WRONG_COUNT, result.reason());
        assertEquals("0", result.detail());
    }

    @Test
    @DisplayName("Die Anzahl wird vor dem Inhalt geprueft")
    void anzahlVorInhalt() {
        assertEquals(AreaCoordinateParser.Reason.WRONG_COUNT, rejected("1.5 2 3").reason());
    }

    @ParameterizedTest(name = "\"{0}\" scheitert an \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "1.5 2 3 4|1.5",
            "1 2 3 4.0|4.0",
            "1,5 2 3 4|1,5",
            "1, 2, 3, 4|1,",
            "~ ~ ~ ~|~",
            "~5 0 0 0|~5",
            "0 ~-5 0 0|~-5",
            "a b c d|a",
            "x1 z1 x2 z2|x1",
            "10 20 30 vierzig|vierzig",
            "+1 2 3 4|+1",
            "- 1 2 3|-",
            "--1 2 3 4|--1",
            "1e3 2 3 4|1e3",
            "0x10 2 3 4|0x10"
    })
    @DisplayName("Kommazahlen, Tilden, Text und Vorzeichenfehler ergeben NOT_AN_INTEGER mit dem Wert")
    void keineGanzzahl(String input, String token) {
        AreaCoordinateParser.Rejected result = rejected(input);
        assertEquals(AreaCoordinateParser.Reason.NOT_AN_INTEGER, result.reason());
        assertEquals(token, result.detail());
    }

    @Test
    @DisplayName("Nicht-ASCII-Ziffern werden abgelehnt, obwohl Integer.parseInt sie lesen koennte")
    void nichtAsciiZiffern() {
        AreaCoordinateParser.Rejected result = rejected(Character.toString(0x0661) + " 2 3 4");
        assertEquals(AreaCoordinateParser.Reason.NOT_AN_INTEGER, result.reason());
    }

    @ParameterizedTest(name = "\"{0}\" liegt ausserhalb bei \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "2147483648 0 0 0|2147483648",
            "0 -2147483649 0 0|-2147483649",
            "0 0 99999999999999999999 0|99999999999999999999",
            "0 0 0 -99999999999999999999|-99999999999999999999"
    })
    @DisplayName("Werte ausserhalb von int ergeben OUT_OF_RANGE mit dem Wert")
    void intUeberlauf(String input, String token) {
        AreaCoordinateParser.Rejected result = rejected(input);
        assertEquals(AreaCoordinateParser.Reason.OUT_OF_RANGE, result.reason());
        assertEquals(token, result.detail());
    }

    @Test
    @DisplayName("Der erste fehlerhafte Wert bestimmt den Grund")
    void ersterFehlerZaehlt() {
        AreaCoordinateParser.Rejected result = rejected("2147483648 abc 0 0");
        assertEquals(AreaCoordinateParser.Reason.OUT_OF_RANGE, result.reason());
        assertEquals("2147483648", result.detail());
    }
}
