package de.SecureChest.plugin.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OwnerNameDisplaySetting: wirksamer Wert aus settings.yml oder signs.enabled")
class OwnerNameDisplaySettingTest {

    @TempDir
    Path folder;

    private final Logger logger = mock(Logger.class);
    private final AtomicBoolean configValue = new AtomicBoolean(true);

    private Path file() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private OwnerNameDisplaySetting setting() {
        return new OwnerNameDisplaySetting(new SettingsStore(file(), logger), configValue::get, logger);
    }

    private void writeOverride(boolean value) throws IOException {
        Files.writeString(file(), "owner_name_display: " + value + "\n");
    }

    @Test
    @DisplayName("Reine Logik: Ueberschreibung gilt vor der config.yml, ohne Ueberschreibung gilt die config.yml")
    void effectiveValue_matrix() {
        assertTrue(OwnerNameDisplaySetting.effectiveValue(true, false));
        assertTrue(OwnerNameDisplaySetting.effectiveValue(true, true));
        assertFalse(OwnerNameDisplaySetting.effectiveValue(false, true));
        assertFalse(OwnerNameDisplaySetting.effectiveValue(false, false));
        assertTrue(OwnerNameDisplaySetting.effectiveValue(null, true));
        assertFalse(OwnerNameDisplaySetting.effectiveValue(null, false));
    }

    @Test
    @DisplayName("Ueberschreibung true gilt, auch wenn signs.enabled false ist")
    void ueberschreibungTrue_configFalse() throws IOException {
        writeOverride(true);
        configValue.set(false);

        OwnerNameDisplaySetting setting = setting();

        assertEquals(Boolean.TRUE, setting.override());
        assertTrue(setting.isEnabled());
    }

    @Test
    @DisplayName("Ueberschreibung false gilt, auch wenn signs.enabled true ist")
    void ueberschreibungFalse_configTrue() throws IOException {
        writeOverride(false);
        configValue.set(true);

        OwnerNameDisplaySetting setting = setting();

        assertEquals(Boolean.FALSE, setting.override());
        assertFalse(setting.isEnabled());
    }

    @Test
    @DisplayName("Keine Ueberschreibung, signs.enabled true: Anzeige aktiv")
    void keineUeberschreibung_configTrue() {
        configValue.set(true);

        OwnerNameDisplaySetting setting = setting();

        assertNull(setting.override());
        assertTrue(setting.isEnabled());
    }

    @Test
    @DisplayName("Keine Ueberschreibung, signs.enabled false: Anzeige aus")
    void keineUeberschreibung_configFalse() {
        configValue.set(false);

        OwnerNameDisplaySetting setting = setting();

        assertNull(setting.override());
        assertFalse(setting.isEnabled());
    }

    @Test
    @DisplayName("Keine Ueberschreibung: ein spaeter neu geladenes signs.enabled gilt sofort")
    void keineUeberschreibung_folgtConfig() {
        OwnerNameDisplaySetting setting = setting();

        configValue.set(false);
        assertFalse(setting.isEnabled());
        configValue.set(true);
        assertTrue(setting.isEnabled());
    }

    @Test
    @DisplayName("Mit Ueberschreibung: ein geaendertes signs.enabled aendert nichts")
    void ueberschreibung_ignoriertConfig() throws IOException {
        writeOverride(false);
        OwnerNameDisplaySetting setting = setting();

        configValue.set(true);

        assertFalse(setting.isEnabled());
    }

    @Test
    @DisplayName("Umschalten wirkt sofort und wird in settings.yml gespeichert")
    void set_wirktSofortUndSpeichert() {
        OwnerNameDisplaySetting setting = setting();

        OwnerNameDisplaySetting.ChangeResult result = setting.set(false);

        assertFalse(result.enabled());
        assertTrue(result.isSaved());
        assertNull(result.saveError());
        assertFalse(setting.isEnabled());
        assertEquals(Boolean.FALSE, new SettingsStore(file(), logger).load().ownerNameDisplay());
        assertFalse(setting().isEnabled());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Speichern fehlgeschlagen: Wert gilt trotzdem, Fehler wird gemeldet, Datei bleibt unangetastet")
    void set_speichernFehlgeschlagen() throws IOException {
        String broken = "owner_name_display: [\n";
        Files.writeString(file(), broken);
        configValue.set(true);
        OwnerNameDisplaySetting setting = setting();

        OwnerNameDisplaySetting.ChangeResult result = setting.set(false);

        assertFalse(result.isSaved());
        assertNotNull(result.saveError());
        assertTrue(result.saveError().contains(SettingsStore.FILE_NAME));
        assertFalse(setting.isEnabled());
        assertEquals(broken, Files.readString(file()));
    }
}
