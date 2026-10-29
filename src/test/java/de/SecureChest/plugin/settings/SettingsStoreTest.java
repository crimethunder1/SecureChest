package de.SecureChest.plugin.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

@DisplayName("SettingsStore: settings.yml lesen und atomar schreiben")
class SettingsStoreTest {

    @TempDir
    Path folder;

    private final Logger logger = mock(Logger.class);

    private SettingsStore store() {
        return new SettingsStore(folder.resolve(SettingsStore.FILE_NAME), logger);
    }

    private List<String> folderEntries() throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("Fehlende Datei: nicht ueberschrieben, es wird keine Datei angelegt")
    void load_fehlendeDatei() throws IOException {
        SettingsStore.Contents contents = store().load();

        assertEquals(SettingsStore.Contents.EMPTY, contents);
        assertNull(contents.ownerNameDisplay());
        assertTrue(folderEntries().isEmpty());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Round-Trip: true kommt unveraendert zurueck")
    void roundTrip_true() throws IOException {
        store().save(new SettingsStore.Contents(true, null, null));

        assertEquals(Boolean.TRUE, store().load().ownerNameDisplay());
    }

    @Test
    @DisplayName("Round-Trip: false kommt unveraendert zurueck")
    void roundTrip_false() throws IOException {
        store().save(new SettingsStore.Contents(false, null, null));

        assertEquals(Boolean.FALSE, store().load().ownerNameDisplay());
    }

    @Test
    @DisplayName("Gespeicherte Datei enthaelt den Schluessel owner_name_display")
    void save_schreibtSchluessel() throws IOException {
        store().save(new SettingsStore.Contents(false, null, null));

        String written = Files.readString(folder.resolve(SettingsStore.FILE_NAME));
        assertTrue(written.contains(SettingsStore.KEY_OWNER_NAME_DISPLAY + ": false"), written);
    }

    @Test
    @DisplayName("Leere Datei gilt als nicht ueberschrieben und ist kein Lesefehler")
    void load_leereDatei() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "");
        SettingsStore store = store();

        assertEquals(SettingsStore.Contents.EMPTY, store.load());
        assertFalse(store.isUnreadableAtLoad());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Kaputtes YAML: Warnung, nicht ueberschrieben, Datei bleibt unangetastet und wird nicht ueberschrieben")
    void load_kaputtesYaml_dateiBleibtUnangetastet() throws IOException {
        Path file = folder.resolve(SettingsStore.FILE_NAME);
        String broken = "owner_name_display: [\n  : : :\n";
        Files.writeString(file, broken);
        SettingsStore store = store();

        SettingsStore.Contents contents = store.load();

        assertEquals(SettingsStore.Contents.EMPTY, contents);
        assertTrue(store.isUnreadableAtLoad());
        verify(logger).warning(anyString());
        IOException refused = assertThrows(IOException.class,
                () -> store.save(new SettingsStore.Contents(true, null, null)));
        assertTrue(refused.getMessage().contains(SettingsStore.FILE_NAME));
        assertEquals(broken, Files.readString(file));
        assertEquals(List.of(SettingsStore.FILE_NAME), folderEntries());
    }

    @Test
    @DisplayName("Oberste Ebene keine Map gilt als kein gueltiges YAML")
    void load_obersteEbeneListe() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "- a\n- b\n");
        SettingsStore store = store();

        assertEquals(SettingsStore.Contents.EMPTY, store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Unlesbare Datei (Ordner statt Datei) gilt als Lesefehler")
    void load_ordnerStattDatei() throws IOException {
        Files.createDirectory(folder.resolve(SettingsStore.FILE_NAME));
        SettingsStore store = store();

        assertEquals(SettingsStore.Contents.EMPTY, store.load());
        assertTrue(store.isUnreadableAtLoad());
    }

    @Test
    @DisplayName("Wert, der nicht true oder false ist, wird mit Warnung uebersprungen")
    void load_ungueltigerWert_uebersprungen() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "owner_name_display: vielleicht\n");
        SettingsStore store = store();

        assertNull(store.load().ownerNameDisplay());
        assertFalse(store.isUnreadableAtLoad());
        verify(logger).warning(anyString());
    }

    @Test
    @DisplayName("Unbekannter Eintrag wird mit Warnung uebersprungen, gueltiger Eintrag gilt")
    void load_unbekannterEintrag_uebersprungen() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME),
                "unbekannt: true\nowner_name_display: false\nzahl: 3\n");
        SettingsStore store = store();

        assertEquals(Boolean.FALSE, store.load().ownerNameDisplay());
        assertFalse(store.isUnreadableAtLoad());
        verify(logger, times(2)).warning(anyString());
    }

    @Test
    @DisplayName("Atomares Schreiben: vorhandene Datei wird vollstaendig ersetzt, keine Temp-Datei bleibt liegen")
    void save_ersetztDateiOhneTempReste() throws IOException {
        SettingsStore store = store();
        store.save(new SettingsStore.Contents(true, null, null));
        store.save(new SettingsStore.Contents(false, null, null));

        assertEquals(List.of(SettingsStore.FILE_NAME), folderEntries());
        assertEquals(Boolean.FALSE, store().load().ownerNameDisplay());
        assertFalse(Files.readString(folder.resolve(SettingsStore.FILE_NAME)).contains("true"));
    }

    @Test
    @DisplayName("Atomares Schreiben: legt fehlenden Ordner an")
    void save_legtOrdnerAn() throws IOException {
        Path nestedFile = folder.resolve("plugins").resolve("ChestSecure").resolve(SettingsStore.FILE_NAME);
        SettingsStore store = new SettingsStore(nestedFile, logger);

        store.save(new SettingsStore.Contents(true, null, null));

        assertTrue(Files.isRegularFile(nestedFile));
        assertEquals(Boolean.TRUE, new SettingsStore(nestedFile, logger).load().ownerNameDisplay());
    }

    @Test
    @DisplayName("Fehlgeschlagenes Verschieben: Ziel bleibt unveraendert, keine Temp-Datei bleibt liegen")
    void save_zielNichtErsetzbar_keineTempReste() throws IOException {
        Path target = folder.resolve(SettingsStore.FILE_NAME);
        Files.createDirectory(target);
        Files.writeString(target.resolve("inhalt.txt"), "bleibt");
        SettingsStore store = new SettingsStore(target, logger);

        assertThrows(IOException.class, () -> store.save(new SettingsStore.Contents(true, null, null)));

        assertEquals(List.of(SettingsStore.FILE_NAME), folderEntries());
        assertEquals("bleibt", Files.readString(target.resolve("inhalt.txt")));
    }

    @Test
    @DisplayName("Round-Trip mit chest_key_consumption: alle drei Schluessel kommen unveraendert zurueck")
    void roundTrip_dreiSchluessel() throws IOException {
        store().save(new SettingsStore.Contents(true, false, true));

        String written = Files.readString(folder.resolve(SettingsStore.FILE_NAME));
        assertTrue(written.contains(SettingsStore.KEY_CHEST_KEY_CONSUMPTION + ": true"), written);
        assertEquals(new SettingsStore.Contents(true, false, true), store().load());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("current laedt beim ersten Aufruf und liefert danach den zuletzt gespeicherten Stand")
    void current_laedtUndFolgtSave() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "chest_key_consumption: false\n");
        SettingsStore store = store();

        assertEquals(new SettingsStore.Contents(null, null, false), store.current());
        store.save(new SettingsStore.Contents(true, null, false));

        assertEquals(new SettingsStore.Contents(true, null, false), store.current());
    }

    @Test
    @DisplayName("current behaelt den gewuenschten Stand auch, wenn das Schreiben scheitert")
    void current_nachFehlgeschlagenemSpeichern() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "owner_name_display: [\n");
        SettingsStore store = store();
        store.load();

        assertThrows(IOException.class, () -> store.save(new SettingsStore.Contents(null, null, true)));

        assertEquals(new SettingsStore.Contents(null, null, true), store.current());
    }
}
