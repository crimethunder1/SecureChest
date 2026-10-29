package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprachdateien: Schluessel fuer verdeckte Namen und den Spielerschalter")
class OwnerNameVisibilityLanguageKeysTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    private static final Map<String, Integer> NEW_KEYS_WITH_PLACEHOLDERS = Map.ofEntries(
            Map.entry("gui-admin-owner-name-hide-when-covered-label", 0),
            Map.entry("gui-admin-owner-name-hide-when-covered-on", 0),
            Map.entry("gui-admin-owner-name-hide-when-covered-off", 0),
            Map.entry("verdeckte-namen-verstecken-eingeschaltet", 0),
            Map.entry("verdeckte-namen-verstecken-ausgeschaltet", 0),
            Map.entry("gui-settings-show-owner-name-label", 0),
            Map.entry("gui-settings-show-owner-name-on", 0),
            Map.entry("gui-settings-show-owner-name-off", 0));

    private static YamlConfiguration load(String language) {
        return YamlConfiguration.loadConfiguration(LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
    }

    private static String value(YamlConfiguration file, String key) {
        if (file.isList(key)) return String.join("\n", file.getStringList(key));
        String value = file.getString(key);
        return value == null ? "" : value;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en"})
    @DisplayName("Jeder neue Schluessel existiert, ist nicht leer, ohne Platzhalter und ohne Gedankenstriche")
    void newKeys(String language) {
        YamlConfiguration file = load(language);

        NEW_KEYS_WITH_PLACEHOLDERS.forEach((key, placeholders) -> {
            String text = value(file, key);
            assertFalse(text.isBlank(), language + ".yml: fehlender oder leerer Schluessel " + key);
            assertEquals(placeholders, placeholders(text).size(), language + ".yml: Platzhalter bei " + key);
            assertTrue(text.chars().noneMatch(character -> character == 0x2013 || character == 0x2014),
                    language + ".yml: Gedankenstrich bei " + key);
        });
    }

    @Test
    @DisplayName("Deutsch und Englisch haben bei jedem neuen Schluessel dieselben Platzhalter")
    void samePlaceholdersInBothLanguages() {
        YamlConfiguration german = load("de");
        YamlConfiguration english = load("en");

        for (String key : NEW_KEYS_WITH_PLACEHOLDERS.keySet()) {
            assertEquals(placeholders(value(german, key)), placeholders(value(english, key)), key);
        }
    }

    @Test
    @DisplayName("Die Warnung beim Speichern wird weiter benutzt und hat in beiden Sprachen den Platzhalter {0}")
    void reusedSaveWarning() {
        for (String language : List.of("de", "en")) {
            assertEquals(Set.of("{0}"), placeholders(value(load(language), "namensanzeige-speichern-fehlgeschlagen")),
                    language);
        }
    }
}
