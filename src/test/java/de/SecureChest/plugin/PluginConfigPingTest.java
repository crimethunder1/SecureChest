package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginConfig: Einstellungen des Chest Locators unter ping")
class PluginConfigPingTest {

    private static PluginConfig from(String yaml) {
        return new PluginConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    @DisplayName("Standardwerte: Zeitlimit 300, Nachlaufzeit 15, Leuchten an")
    void defaults() {
        PluginConfig config = from("");

        assertEquals(300, config.pingTimeoutSeconds());
        assertEquals(15, config.pingArrivedSeconds());
        assertTrue(config.pingHighlightEnabled());
    }

    @Test
    @DisplayName("Eigene Werte werden geladen")
    void customValues() {
        PluginConfig config = from("ping:\n  timeout_seconds: 60\n  arrived_seconds: 5\n  highlight_enabled: false\n");

        assertEquals(60, config.pingTimeoutSeconds());
        assertEquals(5, config.pingArrivedSeconds());
        assertFalse(config.pingHighlightEnabled());
    }

    @Test
    @DisplayName("0 ist gueltig: kein Zeitlimit beziehungsweise sofortiges Ende nach der Ankunft")
    void zeroIsAccepted() {
        PluginConfig config = from("ping:\n  timeout_seconds: 0\n  arrived_seconds: 0\n");

        assertEquals(0, config.pingTimeoutSeconds());
        assertEquals(0, config.pingArrivedSeconds());
    }

    @ParameterizedTest(name = "timeout_seconds: {0}")
    @ValueSource(strings = {"-1", "-300", "abc", "1.5", "\"\"", "99999999999", "[1, 2]", "true"})
    @DisplayName("Ungueltiges Zeitlimit faellt auf 300 zurueck")
    void invalidTimeoutFallsBack(String value) {
        assertEquals(300, from("ping:\n  timeout_seconds: " + value + "\n").pingTimeoutSeconds());
    }

    @ParameterizedTest(name = "arrived_seconds: {0}")
    @ValueSource(strings = {"-1", "-15", "abc", "2.5", "\"\"", "99999999999", "[1, 2]", "false"})
    @DisplayName("Ungueltige Nachlaufzeit faellt auf 15 zurueck")
    void invalidArrivedFallsBack(String value) {
        assertEquals(15, from("ping:\n  arrived_seconds: " + value + "\n").pingArrivedSeconds());
    }

    @ParameterizedTest(name = "highlight_enabled: {0}")
    @ValueSource(strings = {"abc", "1", "\"\"", "[true]"})
    @DisplayName("Ungueltiger Wert fuer highlight_enabled faellt auf true zurueck")
    void invalidHighlightFallsBack(String value) {
        assertTrue(from("ping:\n  highlight_enabled: " + value + "\n").pingHighlightEnabled());
    }

    @Test
    @DisplayName("Ein ungueltiger Wert laesst die anderen ping-Werte unberuehrt")
    void invalidValueDoesNotAffectOthers() {
        PluginConfig config = from("ping:\n  timeout_seconds: -5\n  arrived_seconds: 30\n  highlight_enabled: false\n");

        assertEquals(300, config.pingTimeoutSeconds());
        assertEquals(30, config.pingArrivedSeconds());
        assertFalse(config.pingHighlightEnabled());
    }
}
