package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprachdateien – de.yml und en.yml sind deckungsgleich")
class LanguageFilesParityTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    private static YamlConfiguration load(String language) {
        Path languageFile = LANGUAGE_FOLDER.resolve(language + ".yml");
        assertTrue(Files.isRegularFile(languageFile), "Erwartet: " + languageFile.toAbsolutePath());
        return YamlConfiguration.loadConfiguration(languageFile.toFile());
    }

    private static List<String> values(YamlConfiguration messages, String key) {
        if (messages.isList(key)) return messages.getStringList(key);
        List<String> single = new ArrayList<>();
        single.add(messages.getString(key));
        return single;
    }

    private static Set<String> placeholders(List<String> values) {
        Set<String> found = new TreeSet<>();
        for (String value : values) {
            Matcher matcher = PLACEHOLDER.matcher(value);
            while (matcher.find()) found.add(matcher.group());
        }
        return found;
    }

    @Test
    @DisplayName("Beide Dateien haben exakt dieselbe Schluesselmenge")
    void sameKeySet() {
        Set<String> germanKeys  = new TreeSet<>(load("de").getKeys(false));
        Set<String> englishKeys = new TreeSet<>(load("en").getKeys(false));

        assertFalse(germanKeys.isEmpty(), "de.yml darf nicht leer sein");
        assertEquals(germanKeys, englishKeys, "de.yml und en.yml muessen dieselben Schluessel haben");
    }

    @Test
    @DisplayName("Pro Schluessel stehen in beiden Sprachen dieselben Platzhalter")
    void samePlaceholdersPerKey() {
        YamlConfiguration german  = load("de");
        YamlConfiguration english = load("en");

        for (String key : german.getKeys(false)) {
            if (!english.contains(key)) continue;
            assertEquals(placeholders(values(german, key)), placeholders(values(english, key)),
                    "Platzhalter weichen ab bei: " + key);
        }
    }

    @Test
    @DisplayName("Kein Schluessel hat einen leeren Wert")
    void noEmptyValues() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = load(language);
            for (String key : messages.getKeys(false)) {
                List<String> values = values(messages, key);
                assertFalse(values.isEmpty(), language + ".yml: leere Liste bei " + key);
                for (String value : values) {
                    assertNotNull(value, language + ".yml: kein Text bei " + key);
                    assertFalse(value.isBlank(), language + ".yml: leerer Wert bei " + key);
                }
            }
        }
    }
}
