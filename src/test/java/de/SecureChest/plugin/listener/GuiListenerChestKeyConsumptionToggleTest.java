package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.SettingsStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Schalter fuer den Chest-Key-Verbrauch im Admin-Panel")
class GuiListenerChestKeyConsumptionToggleTest {

    @TempDir
    Path folder;

    private final AtomicBoolean configValue = new AtomicBoolean(false);
    private final Logger logger = mock(Logger.class);
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private GuiManager gui;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private Path settingsFile() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private ChestKeyConsumptionSetting useRealSetting() {
        ChestKeyConsumptionSetting setting = new ChestKeyConsumptionSetting(
                new SettingsStore(settingsFile(), logger), configValue::get, logger);
        when(plugin.chestKeyConsumption()).thenReturn(setting);
        return setting;
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Die Aktion des Schalters ist Admin-only und wird vom Admin-Panel erzeugt")
    void toggleActionIsAdminOnly() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_chest_key_consumption"));
        assertTrue(ScreenActionRecorder.adminScreenActions().contains("toggle_chest_key_consumption"));
    }

    @Test
    @DisplayName("Schalter ohne Admin-Permission beim Klick: nichts umgeschaltet, nichts gespeichert, Hauptmenue")
    void toggleWithoutPermission() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        ChestKeyConsumptionSetting setting = useRealSetting();

        listener.toggleChestKeyConsumption(player);

        assertFalse(setting.isEnabled());
        assertNull(setting.override());
        assertFalse(Files.exists(settingsFile()));
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
        verify(gui, never()).openAdmin(any());
    }

    @Test
    @DisplayName("Schalter aus nach an: wirkt sofort, in settings.yml gespeichert, Meldung, Admin-Panel")
    void toggleOffToOn() {
        ChestKeyConsumptionSetting setting = useRealSetting();

        listener.toggleChestKeyConsumption(player);

        assertTrue(setting.isEnabled());
        assertEquals(Boolean.TRUE, new SettingsStore(settingsFile(), logger).load().chestKeyConsumption());
        assertEquals(List.of("chest-key-verbrauch-eingeschaltet"), sentTexts());
        verify(gui).openAdmin(player);
    }

    @Test
    @DisplayName("Schalter an nach aus: Standard an aus der config.yml, der Klick schaltet aus")
    void toggleOnToOff() {
        configValue.set(true);
        ChestKeyConsumptionSetting setting = useRealSetting();

        listener.toggleChestKeyConsumption(player);

        assertFalse(setting.isEnabled());
        assertEquals(Boolean.FALSE, new SettingsStore(settingsFile(), logger).load().chestKeyConsumption());
        assertEquals(List.of("chest-key-verbrauch-ausgeschaltet"), sentTexts());
    }

    @Test
    @DisplayName("Vorhandene Schluessel in settings.yml bleiben beim Umschalten erhalten")
    void toggleKeepsOtherKeys() throws IOException {
        Files.writeString(settingsFile(), "owner_name_display: false\nowner_name_hide_when_covered: true\n");
        useRealSetting();

        listener.toggleChestKeyConsumption(player);

        YamlConfiguration stored = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(settingsFile())));
        assertFalse(stored.getBoolean("owner_name_display", true));
        assertTrue(stored.getBoolean("owner_name_hide_when_covered", false));
        assertTrue(stored.getBoolean("chest_key_consumption", false));
    }

    @Test
    @DisplayName("Speichern fehlgeschlagen: Warnung mit Grund, die Aenderung wirkt, die Datei bleibt unveraendert")
    void toggleSaveFailure() throws IOException {
        String broken = "chest_key_consumption: [\n";
        Files.writeString(settingsFile(), broken);
        ChestKeyConsumptionSetting setting = useRealSetting();

        listener.toggleChestKeyConsumption(player);

        assertTrue(setting.isEnabled());
        assertEquals(List.of("chest-key-verbrauch-eingeschaltet", "namensanzeige-speichern-fehlgeschlagen"),
                sentTexts());
        ArgumentCaptor<Object> reason = ArgumentCaptor.forClass(Object.class);
        verify(messages).get(eq("namensanzeige-speichern-fehlgeschlagen"), reason.capture());
        assertTrue(String.valueOf(reason.getValue()).contains(SettingsStore.FILE_NAME));
        assertEquals(broken, Files.readString(settingsFile()));
    }
}
