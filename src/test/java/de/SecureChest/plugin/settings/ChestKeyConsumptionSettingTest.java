package de.SecureChest.plugin.settings;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ChestKeyConsumptionSetting: Standard aus der config.yml, Ueberschreibung in settings.yml")
class ChestKeyConsumptionSettingTest {

    @TempDir
    Path folder;

    private final AtomicBoolean configValue = new AtomicBoolean(false);
    private final Logger logger = mock(Logger.class);

    private Path file() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private SettingsStore store() {
        return new SettingsStore(file(), logger);
    }

    private ChestKeyConsumptionSetting setting(SettingsStore store) {
        return new ChestKeyConsumptionSetting(store, configValue::get, logger);
    }

    private YamlConfiguration storedYaml() throws IOException {
        return YamlConfiguration.loadConfiguration(new StringReader(Files.readString(file())));
    }

    @Test
    @DisplayName("Ohne settings.yml gilt der Wert aus der config.yml, Standard aus")
    void defaultFromConfig() {
        ChestKeyConsumptionSetting setting = setting(store());

        assertFalse(setting.isEnabled());
        assertNull(setting.override());
        configValue.set(true);
        assertTrue(setting.isEnabled());
    }

    @Test
    @DisplayName("Fehlender Eintrag in vorhandener settings.yml: Wert aus der config.yml, keine Warnung")
    void missingEntryUsesConfig() throws IOException {
        Files.writeString(file(), "owner_name_display: true\n");
        configValue.set(true);

        ChestKeyConsumptionSetting setting = setting(store());

        assertTrue(setting.isEnabled());
        assertNull(setting.override());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Eintrag in settings.yml hat Vorrang vor der config.yml")
    void overrideWins() throws IOException {
        Files.writeString(file(), "chest_key_consumption: true\n");

        ChestKeyConsumptionSetting setting = setting(store());

        assertTrue(setting.isEnabled());
        assertEquals(Boolean.TRUE, setting.override());
    }

    @Test
    @DisplayName("/cs reload: ohne Ueberschreibung folgt der Schalter der neuen config.yml, mit Ueberschreibung nicht")
    void reloadBehaviour() {
        ChestKeyConsumptionSetting withoutOverride = setting(store());
        configValue.set(true);
        assertTrue(withoutOverride.isEnabled());

        withoutOverride.set(false);
        configValue.set(true);
        assertFalse(withoutOverride.isEnabled());
    }

    @Test
    @DisplayName("set speichert chest_key_consumption und loggt die Aenderung")
    void setSaves() throws IOException {
        ChestKeyConsumptionSetting setting = setting(store());

        ChestKeyConsumptionSetting.ChangeResult result = setting.set(true);

        assertTrue(result.enabled());
        assertTrue(result.isSaved());
        assertTrue(storedYaml().getBoolean("chest_key_consumption", false));
        assertEquals(Boolean.TRUE, store().load().chestKeyConsumption());
        verify(logger).info("Chest Key consumption turned on for new locks.");
    }

    @Test
    @DisplayName("Die Schluessel der Namensanzeige bleiben beim Speichern des Verbrauchs erhalten")
    void keepsOwnerNameKeys() throws IOException {
        Files.writeString(file(), "owner_name_display: false\nowner_name_hide_when_covered: false\n");
        SettingsStore store = store();
        new OwnerNameDisplaySetting(store, () -> true, () -> true, logger);
        ChestKeyConsumptionSetting setting = setting(store);

        setting.set(true);

        YamlConfiguration stored = storedYaml();
        assertFalse(stored.getBoolean("owner_name_display", true));
        assertFalse(stored.getBoolean("owner_name_hide_when_covered", true));
        assertTrue(stored.getBoolean("chest_key_consumption", false));
    }

    @Test
    @DisplayName("Der Verbrauch bleibt beim Speichern der Namensanzeige und des Verstecken-Schalters erhalten")
    void ownerNameSavesKeepConsumption() throws IOException {
        Files.writeString(file(), "chest_key_consumption: true\n");
        SettingsStore store = store();
        OwnerNameDisplaySetting ownerNameDisplay = new OwnerNameDisplaySetting(store, () -> true, () -> true, logger);
        setting(store);

        ownerNameDisplay.set(false);
        ownerNameDisplay.setHideWhenCovered(false);

        YamlConfiguration stored = storedYaml();
        assertTrue(stored.getBoolean("chest_key_consumption", false));
        assertFalse(stored.getBoolean("owner_name_display", true));
        assertFalse(stored.getBoolean("owner_name_hide_when_covered", true));
    }

    @Test
    @DisplayName("Abwechselndes Speichern beider Einstellungen verliert keinen Wert")
    void alternatingSaves() throws IOException {
        SettingsStore store = store();
        OwnerNameDisplaySetting ownerNameDisplay = new OwnerNameDisplaySetting(store, () -> true, () -> true, logger);
        ChestKeyConsumptionSetting consumption = setting(store);

        consumption.set(true);
        ownerNameDisplay.set(false);
        consumption.set(false);
        ownerNameDisplay.setHideWhenCovered(false);

        assertEquals(new SettingsStore.Contents(false, false, false), store().load());
    }

    @Test
    @DisplayName("Reihenfolge der Erzeugung egal: Verbrauch zuerst geladen, Namensanzeige danach, nichts geht verloren")
    void constructionOrderIndependent() throws IOException {
        Files.writeString(file(), "chest_key_consumption: true\nowner_name_display: false\n");
        SettingsStore store = store();
        ChestKeyConsumptionSetting consumption = setting(store);
        OwnerNameDisplaySetting ownerNameDisplay = new OwnerNameDisplaySetting(store, () -> true, () -> true, logger);

        assertTrue(consumption.isEnabled());
        assertFalse(ownerNameDisplay.isEnabled());
        ownerNameDisplay.setHideWhenCovered(false);

        assertEquals(new SettingsStore.Contents(false, false, true), store().load());
    }

    @Test
    @DisplayName("Ungueltiger Wert in settings.yml: Warnung, Wert aus der config.yml")
    void invalidValueWarns() throws IOException {
        Files.writeString(file(), "chest_key_consumption: yes please\n");
        configValue.set(true);

        ChestKeyConsumptionSetting setting = setting(store());

        assertTrue(setting.isEnabled());
        assertNull(setting.override());
        verify(logger).warning(contains("'chest_key_consumption' is not true or false"));
    }

    @Test
    @DisplayName("Unlesbare settings.yml: die Aenderung wirkt, Speicherfehler mit Grund, Datei unveraendert")
    void unreadableFile() throws IOException {
        String broken = "chest_key_consumption: [\n";
        Files.writeString(file(), broken);
        ChestKeyConsumptionSetting setting = setting(store());

        ChestKeyConsumptionSetting.ChangeResult result = setting.set(true);

        assertTrue(setting.isEnabled());
        assertFalse(result.isSaved());
        assertTrue(result.saveError().contains(SettingsStore.FILE_NAME));
        assertEquals(broken, Files.readString(file()));
        verify(logger).warning(contains("The change stays active until the next restart."));
    }
}
