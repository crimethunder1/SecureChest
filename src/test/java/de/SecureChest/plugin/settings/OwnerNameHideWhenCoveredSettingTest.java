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

@DisplayName("Admin-Schalter Namen verstecken, wenn ueberdeckt: settings.yml und signs.hide_when_covered")
class OwnerNameHideWhenCoveredSettingTest {

    @TempDir
    Path folder;

    private final Logger logger = mock(Logger.class);
    private final AtomicBoolean displayConfigValue = new AtomicBoolean(true);
    private final AtomicBoolean hideConfigValue = new AtomicBoolean(true);

    private Path file() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private OwnerNameDisplaySetting setting() {
        return new OwnerNameDisplaySetting(new SettingsStore(file(), logger), displayConfigValue::get,
                hideConfigValue::get, logger);
    }

    @Test
    @DisplayName("Ohne Eintrag in settings.yml gilt signs.hide_when_covered, Standard an")
    void missingEntryUsesConfigValue() {
        OwnerNameDisplaySetting setting = setting();

        assertNull(setting.hideWhenCoveredOverride());
        assertTrue(setting.isHideWhenCoveredEnabled());
        hideConfigValue.set(false);
        assertFalse(setting.isHideWhenCoveredEnabled());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Ohne Eintrag folgt der Schalter einem neu geladenen signs.hide_when_covered sofort (/cs reload)")
    void missingEntryFollowsReload() {
        hideConfigValue.set(false);
        OwnerNameDisplaySetting setting = setting();

        assertFalse(setting.isHideWhenCoveredEnabled());
        hideConfigValue.set(true);
        assertTrue(setting.isHideWhenCoveredEnabled());
    }

    @Test
    @DisplayName("Eintrag owner_name_hide_when_covered in settings.yml hat Vorrang vor der config.yml")
    void overrideWins() throws IOException {
        Files.writeString(file(), "owner_name_hide_when_covered: false\n");
        OwnerNameDisplaySetting setting = setting();

        assertEquals(Boolean.FALSE, setting.hideWhenCoveredOverride());
        assertFalse(setting.isHideWhenCoveredEnabled());
        assertTrue(setting.isEnabled());
        assertNull(setting.override());

        Files.writeString(file(), "owner_name_hide_when_covered: true\n");
        hideConfigValue.set(false);
        assertTrue(setting().isHideWhenCoveredEnabled());
    }

    @Test
    @DisplayName("Umschalten wirkt sofort, wird gespeichert und laesst den Schalter der Namensanzeige unberuehrt")
    void setHideWhenCoveredSavesBothKeys() throws IOException {
        Files.writeString(file(), "owner_name_display: false\n");
        OwnerNameDisplaySetting setting = setting();

        OwnerNameDisplaySetting.ChangeResult result = setting.setHideWhenCovered(false);

        assertFalse(result.enabled());
        assertTrue(result.isSaved());
        assertFalse(setting.isHideWhenCoveredEnabled());
        assertFalse(setting.isEnabled());
        SettingsStore.Contents stored = new SettingsStore(file(), logger).load();
        assertEquals(Boolean.FALSE, stored.ownerNameDisplay());
        assertEquals(Boolean.FALSE, stored.ownerNameHideWhenCovered());
        assertTrue(Files.readString(file()).contains(SettingsStore.KEY_OWNER_NAME_HIDE_WHEN_COVERED + ": false"));
        assertEquals("owner_name_hide_when_covered", SettingsStore.KEY_OWNER_NAME_HIDE_WHEN_COVERED);
    }

    @Test
    @DisplayName("Umschalten der Namensanzeige behaelt einen gespeicherten Schalter fuer das Verstecken")
    void setDisplayKeepsHideKey() throws IOException {
        Files.writeString(file(), "owner_name_hide_when_covered: false\n");
        OwnerNameDisplaySetting setting = setting();

        setting.set(false);

        SettingsStore.Contents stored = new SettingsStore(file(), logger).load();
        assertEquals(Boolean.FALSE, stored.ownerNameDisplay());
        assertEquals(Boolean.FALSE, stored.ownerNameHideWhenCovered());
        assertFalse(setting().isHideWhenCoveredEnabled());
    }

    @Test
    @DisplayName("Ungueltiger Wert wird mit Warnung uebersprungen, es gilt die config.yml")
    void invalidValueSkipped() throws IOException {
        Files.writeString(file(), "owner_name_hide_when_covered: vielleicht\n");
        hideConfigValue.set(false);

        OwnerNameDisplaySetting setting = setting();

        assertNull(setting.hideWhenCoveredOverride());
        assertFalse(setting.isHideWhenCoveredEnabled());
        verify(logger).warning(anyString());
    }

    @Test
    @DisplayName("Speichern fehlgeschlagen: Wert gilt trotzdem, Fehler wird gemeldet, Datei bleibt unangetastet")
    void saveFailureKeepsValue() throws IOException {
        String broken = "owner_name_hide_when_covered: [\n";
        Files.writeString(file(), broken);
        OwnerNameDisplaySetting setting = setting();

        OwnerNameDisplaySetting.ChangeResult result = setting.setHideWhenCovered(false);

        assertFalse(result.isSaved());
        assertTrue(result.saveError().contains(SettingsStore.FILE_NAME));
        assertFalse(setting.isHideWhenCoveredEnabled());
        assertEquals(broken, Files.readString(file()));
    }

    @Test
    @DisplayName("SettingsStore: beide Schluessel im Round-Trip, fehlender Schluessel bleibt leer")
    void storeRoundTrip() throws IOException {
        SettingsStore store = new SettingsStore(file(), logger);

        store.save(new SettingsStore.Contents(null, true, null));
        assertEquals(new SettingsStore.Contents(null, true, null), new SettingsStore(file(), logger).load());
        assertFalse(Files.readString(file()).contains(SettingsStore.KEY_OWNER_NAME_DISPLAY));

        store.save(new SettingsStore.Contents(true, false, null));
        assertEquals(new SettingsStore.Contents(true, false, null), new SettingsStore(file(), logger).load());
        verify(logger, never()).warning(anyString());
    }
}
