package de.SecureChest.plugin.recipe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("RecipeStore: recipes.yml lesen und atomar schreiben")
class RecipeStoreTest {

    @TempDir
    Path folder;

    private final Logger logger = mock(Logger.class);

    private static final RecipeDefinition CHEST_KEY_DEFINITION = new RecipeDefinition(true,
            List.of("DDD", "DHD", "DDD"), orderedIngredients("H", "TRIPWIRE_HOOK", "D", "DIAMOND"));
    private static final RecipeDefinition SECURE_HOPPER_DEFINITION = new RecipeDefinition(true,
            List.of(" I ", "HKH", "   "), orderedIngredients("I", "IRON_BLOCK", "H", "HOPPER", "K", "CHEST_KEY"));

    private static Map<String, String> orderedIngredients(String... symbolsAndMaterials) {
        Map<String, String> ingredients = new LinkedHashMap<>();
        for (int index = 0; index < symbolsAndMaterials.length; index += 2) {
            ingredients.put(symbolsAndMaterials[index], symbolsAndMaterials[index + 1]);
        }
        return ingredients;
    }

    private Path file() {
        return folder.resolve(RecipeStore.FILE_NAME);
    }

    private RecipeStore store() {
        return new RecipeStore(file(), logger);
    }

    private List<String> folderEntries() throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static Map<RecipeKind, RecipeDefinition> overrides(RecipeDefinition chestKey, RecipeDefinition secureHopper) {
        Map<RecipeKind, RecipeDefinition> overrides = new EnumMap<>(RecipeKind.class);
        if (chestKey != null) overrides.put(RecipeKind.CHEST_KEY, chestKey);
        if (secureHopper != null) overrides.put(RecipeKind.SECURE_HOPPER, secureHopper);
        return overrides;
    }

    @Test
    @DisplayName("Fehlende Datei: keine Ueberschreibungen, es wird keine Datei angelegt")
    void loadMissingFile() throws IOException {
        RecipeStore store = store();

        assertEquals(Map.of(), store.load());
        assertFalse(store.isUnreadableAtLoad());
        assertTrue(folderEntries().isEmpty());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Round-Trip: beide Rezepte kommen unveraendert zurueck, inklusive Leerzeichen und Zutaten-Reihenfolge")
    void roundTrip() throws IOException {
        store().save(overrides(CHEST_KEY_DEFINITION, SECURE_HOPPER_DEFINITION));

        Map<RecipeKind, RecipeDefinition> loaded = store().load();

        assertEquals(Map.of(RecipeKind.CHEST_KEY, CHEST_KEY_DEFINITION,
                RecipeKind.SECURE_HOPPER, SECURE_HOPPER_DEFINITION), loaded);
        assertEquals(List.of("I", "H", "K"),
                List.copyOf(loaded.get(RecipeKind.SECURE_HOPPER).ingredients().keySet()));
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Round-Trip: abgeschaltetes Rezept und leere Zutaten bleiben erhalten")
    void roundTripDisabledWithoutIngredients() throws IOException {
        RecipeDefinition disabled = new RecipeDefinition(false, List.of("A", "A", "A"), Map.of());
        store().save(overrides(disabled, null));

        assertEquals(Map.of(RecipeKind.CHEST_KEY, disabled), store().load());
    }

    @Test
    @DisplayName("Gespeicherte Datei hat denselben Aufbau wie der Abschnitt recipes der config.yml")
    void savedFileHasConfigLayout() throws IOException {
        store().save(overrides(CHEST_KEY_DEFINITION, null));

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(file(), StandardCharsets.UTF_8)));

        assertEquals(List.of("chest_key"), List.copyOf(yaml.getKeys(false)));
        assertTrue(yaml.getBoolean("chest_key.enabled"));
        assertEquals(List.of("DDD", "DHD", "DDD"), yaml.getStringList("chest_key.shape"));
        assertEquals("DIAMOND", yaml.getString("chest_key.ingredients.D"));
        assertEquals("TRIPWIRE_HOOK", yaml.getString("chest_key.ingredients.H"));
    }

    @Test
    @DisplayName("Ungueltiger, aber lesbarer Inhalt wird unveraendert geladen, die Pruefung macht die Registry")
    void loadKeepsReadableButInvalidDefinitions() throws IOException {
        Files.writeString(file(), String.join("\n",
                "chest_key:",
                "  shape:",
                "  - \"DD\"",
                "  ingredients:",
                "    X: \"DIAMOND_X\"",
                ""));

        Map<RecipeKind, RecipeDefinition> loaded = store().load();

        assertEquals(Map.of(RecipeKind.CHEST_KEY,
                new RecipeDefinition(true, List.of("DD"), Map.of("X", "DIAMOND_X"))), loaded);
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Leere Datei gilt als leer und ist kein Lesefehler")
    void loadEmptyFile() throws IOException {
        Files.writeString(file(), "");
        RecipeStore store = store();

        assertEquals(Map.of(), store.load());
        assertFalse(store.isUnreadableAtLoad());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Kaputtes YAML: Warnung, leer, Datei bleibt unangetastet und wird weder gespeichert noch bereinigt")
    void loadBrokenYamlLeavesFileUntouched() throws IOException {
        String broken = "chest_key:\n  shape: [\n  - \"DDD\"\n    enabled: : :\n";
        Files.writeString(file(), broken);
        RecipeStore store = store();

        Map<RecipeKind, RecipeDefinition> loaded = store.load();

        assertEquals(Map.of(), loaded);
        assertTrue(store.isUnreadableAtLoad());
        verify(logger).warning(anyString());
        IOException refusedSave = assertThrows(IOException.class,
                () -> store.save(overrides(CHEST_KEY_DEFINITION, null)));
        assertTrue(refusedSave.getMessage().contains(RecipeStore.FILE_NAME));
        IOException refusedRemove = assertThrows(IOException.class, () -> store.remove(RecipeKind.CHEST_KEY));
        assertTrue(refusedRemove.getMessage().contains(RecipeStore.FILE_NAME));
        assertEquals(broken, Files.readString(file()));
        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("Oberste Ebene keine Map gilt als Lesefehler")
    void loadTopLevelListIsUnreadable() throws IOException {
        Files.writeString(file(), "- a\n- b\n");
        RecipeStore store = store();

        assertEquals(Map.of(), store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Unlesbare Datei (Ordner statt Datei) gilt als Lesefehler")
    void loadDirectoryInsteadOfFileIsUnreadable() throws IOException {
        Files.createDirectory(file());
        RecipeStore store = store();

        assertEquals(Map.of(), store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Erfolgreiches Laden nach einem Lesefehler hebt die Sperre wieder auf")
    void successfulLoadClearsUnreadableFlag() throws IOException {
        Files.writeString(file(), "- a\n");
        RecipeStore store = store();
        store.load();
        Files.writeString(file(), "");

        store.load();

        assertFalse(store.isUnreadableAtLoad());
        store.save(overrides(CHEST_KEY_DEFINITION, null));
        assertEquals(Map.of(RecipeKind.CHEST_KEY, CHEST_KEY_DEFINITION), store().load());
    }

    @Test
    @DisplayName("Unbekannte Eintraege werden mit Warnung uebersprungen, bekannte bleiben erhalten")
    void loadSkipsUnknownEntries() throws IOException {
        Files.writeString(file(), String.join("\n",
                "golden_key:",
                "  shape: [\"G\", \"G\", \"G\"]",
                "  ingredients:",
                "    G: \"GOLD_INGOT\"",
                "Chest_Key:",
                "  shape: [\"D\", \"D\", \"D\"]",
                "secure_hopper:",
                "  shape: [\"H\", \"H\", \"H\"]",
                "  ingredients:",
                "    H: \"HOPPER\"",
                ""));
        RecipeStore store = store();

        Map<RecipeKind, RecipeDefinition> loaded = store.load();

        assertEquals(Map.of(RecipeKind.SECURE_HOPPER,
                new RecipeDefinition(true, List.of("H", "H", "H"), Map.of("H", "HOPPER"))), loaded);
        assertFalse(store.isUnreadableAtLoad());
        verify(logger, times(2)).warning(anyString());
    }

    @Test
    @DisplayName("Nicht lesbare Eintraege werden mit Warnung uebersprungen")
    void loadSkipsUnreadableEntries() throws IOException {
        List<String> unreadableChestKeyEntries = List.of(
                "chest_key: \"DDD\"",
                "chest_key:\n  shape: \"DDD\"",
                "chest_key:\n  shape:\n  - \"DDD\"\n  - 123\n  - \"DDD\"",
                "chest_key:\n  shape: [\"D\", \"D\", \"D\"]\n  ingredients: \"DIAMOND\"",
                "chest_key:\n  shape: [\"D\", \"D\", \"D\"]\n  ingredients:\n    D: 5",
                "chest_key:\n  shape: [\"D\", \"D\", \"D\"]\n  ingredients:\n    D:\n      nested: \"DIAMOND\"",
                "chest_key:\n  enabled: \"maybe\"\n  shape: [\"D\", \"D\", \"D\"]");
        String validSecureHopper = "\nsecure_hopper:\n  shape: [\"H\", \"H\", \"H\"]\n  ingredients:\n    H: \"HOPPER\"\n";

        for (String entry : unreadableChestKeyEntries) {
            Files.writeString(file(), entry + validSecureHopper);
            Logger entryLogger = mock(Logger.class);
            RecipeStore store = new RecipeStore(file(), entryLogger);

            Map<RecipeKind, RecipeDefinition> loaded = store.load();

            assertEquals(List.of(RecipeKind.SECURE_HOPPER), List.copyOf(loaded.keySet()), entry);
            assertFalse(store.isUnreadableAtLoad(), entry);
            verify(entryLogger).warning(anyString());
        }
    }

    @Test
    @DisplayName("Fehlende Felder: enabled ist wahr, Shape und Zutaten sind leer")
    void loadAppliesDefaultsForMissingFields() throws IOException {
        Files.writeString(file(), "chest_key:\n  enabled: true\n");

        assertEquals(Map.of(RecipeKind.CHEST_KEY, new RecipeDefinition(true, List.of(), Map.of())), store().load());
        Files.writeString(file(), "chest_key:\n  shape: [\"D\", \"D\", \"D\"]\n");
        assertTrue(store().load().get(RecipeKind.CHEST_KEY).enabled());
    }

    @Test
    @DisplayName("Geladene Ueberschreibungen sind unveraenderlich")
    void loadedOverridesAreUnmodifiable() throws IOException {
        store().save(overrides(CHEST_KEY_DEFINITION, null));

        Map<RecipeKind, RecipeDefinition> loaded = store().load();

        assertThrows(UnsupportedOperationException.class, () -> loaded.remove(RecipeKind.CHEST_KEY));
    }

    @Test
    @DisplayName("Atomares Schreiben: vorhandene Datei wird vollstaendig ersetzt, keine Temp-Datei bleibt liegen")
    void saveReplacesFileWithoutTemporaryLeftovers() throws IOException {
        RecipeStore store = store();
        store.save(overrides(CHEST_KEY_DEFINITION, SECURE_HOPPER_DEFINITION));
        RecipeDefinition replacement = new RecipeDefinition(true, List.of("G", "G", "G"), Map.of("G", "GOLD_BLOCK"));
        store.save(overrides(replacement, null));

        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
        assertEquals(Map.of(RecipeKind.CHEST_KEY, replacement), store().load());
        String written = Files.readString(file(), StandardCharsets.UTF_8);
        assertFalse(written.contains("secure_hopper"));
        assertFalse(written.contains("TRIPWIRE_HOOK"));
    }

    @Test
    @DisplayName("Speichern ohne Ueberschreibungen schreibt eine leere Datei")
    void saveWithoutOverridesWritesEmptyFile() throws IOException {
        store().save(overrides(CHEST_KEY_DEFINITION, null));

        store().save(Map.of());

        assertEquals(Map.of(), store().load());
        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("Atomares Schreiben: legt fehlenden Ordner an")
    void saveCreatesMissingFolder() throws IOException {
        Path nestedFile = folder.resolve("plugins").resolve("ChestSecure").resolve(RecipeStore.FILE_NAME);
        RecipeStore store = new RecipeStore(nestedFile, logger);

        store.save(overrides(CHEST_KEY_DEFINITION, null));

        assertTrue(Files.isRegularFile(nestedFile));
        assertEquals(Map.of(RecipeKind.CHEST_KEY, CHEST_KEY_DEFINITION), new RecipeStore(nestedFile, logger).load());
    }

    @Test
    @DisplayName("Fehlgeschlagenes Verschieben: Ziel bleibt unveraendert, keine Temp-Datei bleibt liegen")
    void saveFailingMoveLeavesNoTemporaryFile() throws IOException {
        Files.createDirectory(file());
        Files.writeString(file().resolve("inhalt.txt"), "bleibt");
        RecipeStore store = store();

        assertThrows(IOException.class, () -> store.save(overrides(CHEST_KEY_DEFINITION, null)));

        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
        assertEquals("bleibt", Files.readString(file().resolve("inhalt.txt")));
    }

    @Test
    @DisplayName("Entfernen: nur der eine Eintrag verschwindet, der andere bleibt, keine Temp-Datei")
    void removeDeletesOnlyOneEntry() throws IOException {
        RecipeStore store = store();
        store.save(overrides(CHEST_KEY_DEFINITION, SECURE_HOPPER_DEFINITION));

        store.remove(RecipeKind.CHEST_KEY);

        assertEquals(Map.of(RecipeKind.SECURE_HOPPER, SECURE_HOPPER_DEFINITION), store().load());
        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("Entfernen des letzten Eintrags hinterlaesst eine leere, lesbare Datei")
    void removeLastEntryLeavesEmptyFile() throws IOException {
        RecipeStore store = store();
        store.save(overrides(CHEST_KEY_DEFINITION, null));

        store.remove(RecipeKind.CHEST_KEY);

        RecipeStore reloaded = store();
        assertEquals(Map.of(), reloaded.load());
        assertFalse(reloaded.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Entfernen eines fehlenden Eintrags laesst die Datei unveraendert")
    void removeMissingEntryLeavesFileUnchanged() throws IOException {
        String content = "# eigene Notiz\nsecure_hopper:\n  shape: [\"H\", \"H\", \"H\"]\n";
        Files.writeString(file(), content);
        RecipeStore store = store();
        store.load();

        store.remove(RecipeKind.CHEST_KEY);

        assertEquals(content, Files.readString(file()));
    }

    @Test
    @DisplayName("Entfernen ohne Datei legt keine Datei an")
    void removeWithoutFileCreatesNothing() throws IOException {
        store().remove(RecipeKind.SECURE_HOPPER);

        assertTrue(folderEntries().isEmpty());
    }

    @Test
    @DisplayName("Entfernen entfernt auch einen nicht lesbaren Eintrag")
    void removeDeletesUnreadableEntry() throws IOException {
        Files.writeString(file(), "chest_key: \"kaputt\"\nsecure_hopper:\n  shape: [\"H\", \"H\", \"H\"]\n");
        RecipeStore store = store();
        store.load();

        store.remove(RecipeKind.CHEST_KEY);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(file(), StandardCharsets.UTF_8)));
        assertEquals(List.of("secure_hopper"), List.copyOf(yaml.getKeys(false)));
    }

    @Test
    @DisplayName("Entfernen verweigert das Ueberschreiben, wenn die Datei inzwischen kaputt ist")
    void removeRefusesFileBrokenAfterLoad() throws IOException {
        RecipeStore store = store();
        store.save(overrides(CHEST_KEY_DEFINITION, null));
        String broken = "chest_key: [\n  : :\n";
        Files.writeString(file(), broken);

        assertThrows(IOException.class, () -> store.remove(RecipeKind.CHEST_KEY));

        assertEquals(broken, Files.readString(file()));
        assertEquals(List.of(RecipeStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("readDefinition: fehlender Abschnitt ergibt null, Werte werden wie bisher aus der config.yml gelesen")
    void readDefinitionFromConfigSection() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(String.join("\n",
                "recipes:",
                "  chest_key:",
                "    enabled: false",
                "    shape:",
                "      - \"NDN\"",
                "      - \"DHD\"",
                "      - \"NDN\"",
                "    ingredients:",
                "      N: \"NETHERITE_INGOT\"",
                "      D: \"DIAMOND\"",
                "")));

        assertNull(RecipeStore.readDefinition(null));
        assertNull(RecipeStore.readDefinition(config.getConfigurationSection("recipes.secure_hopper")));
        assertEquals(new RecipeDefinition(false, List.of("NDN", "DHD", "NDN"),
                        Map.of("N", "NETHERITE_INGOT", "D", "DIAMOND")),
                RecipeStore.readDefinition(config.getConfigurationSection("recipes.chest_key")));
    }
}
