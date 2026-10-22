package de.SecureChest.plugin.area;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("AreaStore: areas.yml lesen und atomar schreiben")
class AreaStoreTest {

    @TempDir
    Path folder;

    private final Logger logger = mock(Logger.class);

    private AreaStore store() {
        return new AreaStore(folder.resolve(AreaStore.FILE_NAME), logger);
    }

    private List<String> folderEntries() throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("Fehlende Datei: Schalter aus, keine Areale, es wird keine Datei angelegt")
    void load_fehlendeDatei() throws IOException {
        AreaStore.Contents contents = store().load();

        assertEquals(AreaStore.Contents.EMPTY, contents);
        assertTrue(folderEntries().isEmpty());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Round-Trip: Schalter und Areale kommen unveraendert zurueck")
    void roundTrip() throws IOException {
        List<Area> areas = List.of(
                Area.normalized("Spawn", "world", -10, -20, 30, 40),
                Area.normalized("markt-1", "world_nether", 5, 5, 5, 5),
                Area.normalized("yes", "world", 0, 0, 1, 1));
        store().save(new AreaStore.Contents(true, areas));

        AreaStore.Contents loaded = store().load();

        assertTrue(loaded.enabled());
        assertEquals(areas, loaded.areas());
    }

    @Test
    @DisplayName("Leere Datei gilt als leer und ist kein Lesefehler")
    void load_leereDatei() throws IOException {
        Files.writeString(folder.resolve(AreaStore.FILE_NAME), "");
        AreaStore store = store();

        assertEquals(AreaStore.Contents.EMPTY, store.load());
        assertFalse(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Kaputtes YAML: Warnung, leer, Datei bleibt unangetastet und wird nicht ueberschrieben")
    void load_kaputtesYaml_dateiBleibtUnangetastet() throws IOException {
        Path file = folder.resolve(AreaStore.FILE_NAME);
        String broken = "enabled: true\nareas: [\n  - name: spawn\n    world: : :\n";
        Files.writeString(file, broken);
        AreaStore store = store();

        AreaStore.Contents contents = store.load();

        assertEquals(AreaStore.Contents.EMPTY, contents);
        assertTrue(store.isUnreadableAtLoad());
        verify(logger).warning(anyString());
        IOException refused = assertThrows(IOException.class,
                () -> store.save(new AreaStore.Contents(true, List.of())));
        assertTrue(refused.getMessage().contains(AreaStore.FILE_NAME));
        assertEquals(broken, Files.readString(file));
        assertEquals(List.of(AreaStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("Oberste Ebene keine Map gilt als kein gueltiges YAML")
    void load_obersteEbeneListe() throws IOException {
        Files.writeString(folder.resolve(AreaStore.FILE_NAME), "- a\n- b\n");
        AreaStore store = store();

        assertEquals(AreaStore.Contents.EMPTY, store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Unlesbare Datei (Ordner statt Datei) gilt als Lesefehler")
    void load_ordnerStattDatei() throws IOException {
        Files.createDirectory(folder.resolve(AreaStore.FILE_NAME));
        AreaStore store = store();

        assertEquals(AreaStore.Contents.EMPTY, store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Ungueltige und doppelte Eintraege werden mit Warnung uebersprungen, Ecken normalisiert")
    void load_ungueltigeEintraege() throws IOException {
        String yaml = String.join("\n",
                "enabled: true",
                "areas:",
                "- name: gut",
                "  world: world",
                "  min_x: 10",
                "  min_z: 10",
                "  max_x: -10",
                "  max_z: -10",
                "- name: kein name",
                "  world: world",
                "  min_x: 0",
                "  min_z: 0",
                "  max_x: 1",
                "  max_z: 1",
                "- name: kommazahl",
                "  world: world",
                "  min_x: 0.5",
                "  min_z: 0",
                "  max_x: 1",
                "  max_z: 1",
                "- name: ohnewelt",
                "  min_x: 0",
                "  min_z: 0",
                "  max_x: 1",
                "  max_z: 1",
                "- name: GUT",
                "  world: world",
                "  min_x: 0",
                "  min_z: 0",
                "  max_x: 1",
                "  max_z: 1",
                "");
        Files.writeString(folder.resolve(AreaStore.FILE_NAME), yaml);
        AreaStore store = store();

        AreaStore.Contents contents = store.load();

        assertTrue(contents.enabled());
        assertEquals(List.of(new Area("gut", "world", -10, -10, 10, 10)), contents.areas());
        assertFalse(store.isUnreadableAtLoad());
        verify(logger, times(4)).warning(anyString());
    }

    @Test
    @DisplayName("Atomares Schreiben: vorhandene Datei wird vollstaendig ersetzt, keine Temp-Datei bleibt liegen")
    void save_ersetztDateiOhneTempReste() throws IOException {
        AreaStore store = store();
        store.save(new AreaStore.Contents(true, List.of(Area.normalized("alt", "world", 0, 0, 100, 100))));
        store.save(new AreaStore.Contents(false, List.of(Area.normalized("neu", "world", 1, 2, 3, 4))));

        assertEquals(List.of(AreaStore.FILE_NAME), folderEntries());
        AreaStore.Contents loaded = store().load();
        assertFalse(loaded.enabled());
        assertEquals(List.of(new Area("neu", "world", 1, 2, 3, 4)), loaded.areas());
        String written = Files.readString(folder.resolve(AreaStore.FILE_NAME), StandardCharsets.UTF_8);
        assertFalse(written.contains("alt"));
    }

    @Test
    @DisplayName("Atomares Schreiben: legt fehlenden Ordner an")
    void save_legtOrdnerAn() throws IOException {
        Path nestedFile = folder.resolve("plugins").resolve("ChestSecure").resolve(AreaStore.FILE_NAME);
        AreaStore store = new AreaStore(nestedFile, logger);

        store.save(new AreaStore.Contents(true, List.of()));

        assertTrue(Files.isRegularFile(nestedFile));
        assertTrue(new AreaStore(nestedFile, logger).load().enabled());
    }

    @Test
    @DisplayName("Fehlgeschlagenes Verschieben: Ziel bleibt unveraendert, keine Temp-Datei bleibt liegen")
    void save_zielNichtErsetzbar_keineTempReste() throws IOException {
        Path target = folder.resolve(AreaStore.FILE_NAME);
        Files.createDirectory(target);
        Files.writeString(target.resolve("inhalt.txt"), "bleibt");
        AreaStore store = new AreaStore(target, logger);

        assertThrows(IOException.class, () -> store.save(new AreaStore.Contents(true, List.of())));

        assertEquals(List.of(AreaStore.FILE_NAME), folderEntries());
        assertEquals("bleibt", Files.readString(target.resolve("inhalt.txt")));
    }
}
