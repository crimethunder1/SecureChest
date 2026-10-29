package de.SecureChest.plugin;

import de.SecureChest.plugin.ui.UiStateStore;
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

@DisplayName("Sprachschluessel fuer die neuen sperrbaren Bloecke")
class NewLockableBlocksLanguageKeysTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final Map<String, Set<String>> NEW_KEYS = Map.ofEntries(
            Map.entry("gui-stats-furnaces", Set.of("{0}")),
            Map.entry("gui-stats-blast-furnaces", Set.of("{0}")),
            Map.entry("gui-stats-smokers", Set.of("{0}")),
            Map.entry("gui-stats-droppers", Set.of("{0}")),
            Map.entry("gui-stats-dispensers", Set.of("{0}")),
            Map.entry("gui-stats-brewing-stands", Set.of("{0}")),
            Map.entry("gui-stats-crafters", Set.of("{0}")),
            Map.entry("enum-lock-type-filter-furnaces", Set.of()),
            Map.entry("enum-lock-type-filter-droppers-and-dispensers", Set.of()),
            Map.entry("enum-lock-type-filter-brewing-stands", Set.of()),
            Map.entry("enum-lock-type-filter-crafters", Set.of()));

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
    @DisplayName("Jeder neue Schluessel existiert in de.yml und en.yml, nicht leer, mit den erwarteten Platzhaltern")
    void newKeys() {
        for (String file : List.of("de.yml", "en.yml")) {
            YamlConfiguration language = load(file);
            for (Map.Entry<String, Set<String>> entry : NEW_KEYS.entrySet()) {
                String value = language.getString(entry.getKey());
                assertNotNull(value, file + ": " + entry.getKey());
                assertFalse(value.isBlank(), file + " leer: " + entry.getKey());
                assertEquals(new TreeSet<>(entry.getValue()), placeholders(value), file + ": " + entry.getKey());
                assertFalse(value.contains("–") || value.contains("—"), file + ": " + entry.getKey());
            }
        }
    }

    @Test
    @DisplayName("Jeder Typfilter der Sperrenliste hat einen Text in beiden Sprachen")
    void everyTypeFilterHasLabel() {
        for (String file : List.of("de.yml", "en.yml")) {
            YamlConfiguration language = load(file);
            for (UiStateStore.LockTypeFilter filter : UiStateStore.LockTypeFilter.values()) {
                String value = language.getString(filter.labelKey());
                assertNotNull(value, file + ": " + filter.labelKey());
                assertFalse(value.isBlank(), file + " leer: " + filter.labelKey());
            }
        }
    }
}
