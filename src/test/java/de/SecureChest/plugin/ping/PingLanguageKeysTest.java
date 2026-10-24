package de.SecureChest.plugin.ping;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Chest Locator: Sprachschluessel in de.yml und en.yml")
class PingLanguageKeysTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");

    private static final List<String> NEW_KEYS = List.of(
            "ping-navigation-relative", "ping-navigation-relative-above", "ping-navigation-relative-below",
            "ping-direction-ahead", "ping-direction-ahead-right", "ping-direction-right",
            "ping-direction-behind-right", "ping-direction-behind", "ping-direction-behind-left",
            "ping-direction-left", "ping-direction-ahead-left",
            "ping-stopped", "ping-timed-out", "ping-world-unloaded",
            "gui-lock-manage-ping-stop-name", "gui-lock-manage-ping-stop-lore");

    private static final List<String> REMOVED_KEYS = List.of(
            "ping-navigation",
            "ping-direction-north", "ping-direction-north-east", "ping-direction-east",
            "ping-direction-south-east", "ping-direction-south", "ping-direction-south-west",
            "ping-direction-west", "ping-direction-north-west");

    private static YamlConfiguration load(String language) {
        return YamlConfiguration.loadConfiguration(LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
    }

    @Test
    @DisplayName("Alle neuen Schluessel stehen in beiden Dateien und sind nicht leer")
    void newKeysExistInBothFiles() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = load(language);
            for (String key : NEW_KEYS) {
                assertTrue(messages.contains(key), language + ".yml: fehlender Schluessel " + key);
                List<String> values = messages.isList(key)
                        ? messages.getStringList(key)
                        : List.of(String.valueOf(messages.getString(key)));
                for (String value : values) {
                    assertFalse(value.isBlank(), language + ".yml: leerer Wert bei " + key);
                }
            }
        }
    }

    @Test
    @DisplayName("Jeder Sektor hat einen Richtungstext mit Pfeil in beiden Dateien")
    void everySectorHasLabel() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = load(language);
            for (PingDirection.Sector sector : PingDirection.Sector.values()) {
                String key = PingManager.directionLabelKey(sector);
                String value = messages.getString(key);
                assertNotNull(value, language + ".yml: fehlender Schluessel " + key);
                assertTrue(value.matches("^[←-↙] .+"), language + ".yml: kein Pfeil vorne bei " + key);
            }
        }
    }

    @Test
    @DisplayName("Hoehenhinweise nutzen Pfeil nach oben und unten und den Platzhalter fuer die Hoehe")
    void heightHintsUseArrowsAndPlaceholder() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = load(language);
            assertTrue(messages.getString("ping-navigation-relative-above").contains("↑ {2}"), language);
            assertTrue(messages.getString("ping-navigation-relative-below").contains("↓ {2}"), language);
        }
    }

    @Test
    @DisplayName("Absolute Himmelsrichtungen und ping-navigation sind entfernt")
    void removedKeysAreGone() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = load(language);
            for (String key : REMOVED_KEYS) {
                assertFalse(messages.contains(key), language + ".yml: Schluessel sollte entfernt sein: " + key);
            }
        }
    }
}
