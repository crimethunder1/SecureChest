package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprachschluessel fuer Chest-Key-Verbrauch, Rezept-Anzahl und Cooldown-Meldung")
class ChestKeyConsumptionLanguageKeysTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final Map<String, Set<String>> TEXT_KEYS = Map.ofEntries(
            Map.entry("sperren-key-erforderlich", Set.of()),
            Map.entry("aktion-zu-schnell", Set.of()),
            Map.entry("chest-key-verbrauch-eingeschaltet", Set.of()),
            Map.entry("chest-key-verbrauch-ausgeschaltet", Set.of()),
            Map.entry("gui-admin-chest-key-consumption-label", Set.of()),
            Map.entry("gui-admin-chest-key-consumption-on", Set.of()),
            Map.entry("gui-admin-chest-key-consumption-off", Set.of()),
            Map.entry("rezept-fehler-anzahl", Set.of("{0}")),
            Map.entry("gui-recipe-edit-amount-name", Set.of("{0}")));
    private static final String AMOUNT_LORE_KEY = "gui-recipe-edit-amount-lore";

    private static YamlConfiguration load(String file) {
        return YamlConfiguration.loadConfiguration(LANGUAGE_FOLDER.resolve(file).toFile());
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    @Test
    @DisplayName("Jeder neue Text existiert in de.yml und en.yml, nicht leer, mit den erwarteten Platzhaltern")
    void textKeys() {
        for (String file : List.of("de.yml", "en.yml")) {
            YamlConfiguration language = load(file);
            for (Map.Entry<String, Set<String>> entry : TEXT_KEYS.entrySet()) {
                String value = language.getString(entry.getKey());
                assertNotNull(value, file + ": " + entry.getKey());
                assertFalse(value.isBlank(), file + " leer: " + entry.getKey());
                assertEquals(new TreeSet<>(entry.getValue()), placeholders(value), file + ": " + entry.getKey());
            }
        }
    }

    @Test
    @DisplayName("Die Bedienhinweise der Anzahl sind eine Liste mit {0}, {1} und {2} in beiden Sprachen")
    void amountLore() {
        for (String file : List.of("de.yml", "en.yml")) {
            List<String> lines = load(file).getStringList(AMOUNT_LORE_KEY);
            assertFalse(lines.isEmpty(), file);
            assertTrue(lines.stream().noneMatch(String::isBlank), file);
            assertEquals(Set.of("{0}", "{1}", "{2}"), placeholders(String.join(" ", lines)), file);
        }
    }

    @Test
    @DisplayName("Keine Gedankenstriche in den neuen Texten")
    void noDashes() {
        for (String file : List.of("de.yml", "en.yml")) {
            YamlConfiguration language = load(file);
            for (String key : TEXT_KEYS.keySet()) {
                String value = language.getString(key);
                assertFalse(value.contains("–") || value.contains("—"), file + ": " + key);
            }
            for (String line : language.getStringList(AMOUNT_LORE_KEY)) {
                assertFalse(line.contains("–") || line.contains("—"), file + ": " + line);
            }
        }
    }
}
