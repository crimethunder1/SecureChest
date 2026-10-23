package de.SecureChest.plugin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GiveKeyArgumentParser: /cs givekey [Spieler] [Anzahl]")
class GiveKeyArgumentParserTest {

    private static final int STACK_LIMIT = 64;

    private static GiveKeyArgumentParser.Request accepted(String... arguments) {
        GiveKeyArgumentParser.Result result = GiveKeyArgumentParser.parse(arguments, STACK_LIMIT);
        return assertInstanceOf(GiveKeyArgumentParser.Request.class, result, "Sollte gueltig sein: " + String.join(" ", arguments));
    }

    private static GiveKeyArgumentParser.Rejected rejected(String... arguments) {
        GiveKeyArgumentParser.Result result = GiveKeyArgumentParser.parse(arguments, STACK_LIMIT);
        return assertInstanceOf(GiveKeyArgumentParser.Rejected.class, result, "Sollte ungueltig sein: " + String.join(" ", arguments));
    }

    @Test
    @DisplayName("Ohne Argument: kein Spieler, ein Chest Key")
    void keinArgument() {
        GiveKeyArgumentParser.Request request = accepted();
        assertNull(request.playerName());
        assertFalse(request.hasPlayerName());
        assertEquals(1, request.amount());
    }

    @Test
    @DisplayName("Nur Spieler: dieser Spieler, ein Chest Key")
    void nurSpieler() {
        GiveKeyArgumentParser.Request request = accepted("Steve");
        assertEquals("Steve", request.playerName());
        assertTrue(request.hasPlayerName());
        assertEquals(1, request.amount());
    }

    @Test
    @DisplayName("Spieler und Anzahl werden uebernommen")
    void spielerUndAnzahl() {
        assertEquals(new GiveKeyArgumentParser.Request("Steve", 12), accepted("Steve", "12"));
    }

    @Test
    @DisplayName("Untergrenze 1 ist erlaubt")
    void anzahlEins() {
        assertEquals(1, accepted("Steve", "1").amount());
    }

    @Test
    @DisplayName("Obergrenze 64 ist erlaubt")
    void anzahlVierundsechzig() {
        assertEquals(64, accepted("Steve", "64").amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65", "-1", "-64", "1.5", "1,5", "abc", "", "+5", "5x", "99999999999"})
    @DisplayName("Ungueltige Anzahl wird mit dem Originaltext abgelehnt")
    void anzahlUngueltig(String amount) {
        GiveKeyArgumentParser.Rejected result = rejected("Steve", amount);
        assertEquals(GiveKeyArgumentParser.Reason.INVALID_AMOUNT, result.reason());
        assertEquals(amount, result.detail());
    }

    @Test
    @DisplayName("Mehr als zwei Argumente werden abgelehnt")
    void zuVieleArgumente() {
        GiveKeyArgumentParser.Rejected result = rejected("Steve", "5", "extra");
        assertEquals(GiveKeyArgumentParser.Reason.TOO_MANY_ARGUMENTS, result.reason());
        assertEquals("3", result.detail());
    }

    @Test
    @DisplayName("Obergrenze ist die Stapelgroesse, hoechstens 64")
    void obergrenze() {
        assertEquals(16, GiveKeyArgumentParser.upperLimit(16));
        assertEquals(64, GiveKeyArgumentParser.upperLimit(64));
        assertEquals(64, GiveKeyArgumentParser.upperLimit(99));
    }

    @Test
    @DisplayName("Eine kleinere Stapelgroesse begrenzt die Anzahl")
    void kleinereStapelgroesse() {
        assertInstanceOf(GiveKeyArgumentParser.Request.class, GiveKeyArgumentParser.parse(new String[] {"Steve", "16"}, 16));
        GiveKeyArgumentParser.Result result = GiveKeyArgumentParser.parse(new String[] {"Steve", "17"}, 16);
        assertEquals(new GiveKeyArgumentParser.Rejected(GiveKeyArgumentParser.Reason.INVALID_AMOUNT, "17"), result);
    }
}
