package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import de.SecureChest.plugin.settings.SettingsStore;
import de.SecureChest.plugin.sign.SignManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Schalter fuer die Besitzernamen im Admin-Panel")
class GuiListenerOwnerNameDisplayTest {

    @TempDir
    Path folder;

    private final AtomicBoolean configValue = new AtomicBoolean(true);
    private final Logger logger = mock(Logger.class);
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private GuiManager gui;
    private SignManager signs;
    private Player player;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        signs = mock(SignManager.class);
        when(plugin.signs()).thenReturn(signs);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private Path settingsFile() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private OwnerNameDisplaySetting useSetting() {
        OwnerNameDisplaySetting setting = new OwnerNameDisplaySetting(
                new SettingsStore(settingsFile(), logger), configValue::get, () -> true, logger);
        when(plugin.ownerNameDisplay()).thenReturn(setting);
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
    @DisplayName("Ohne Admin-Permission: nichts umgeschaltet, nichts gespeichert, SignManager unberuehrt, Hauptmenue")
    void toggle_ohnePermission_nichts() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertTrue(setting.isEnabled());
        assertNull(setting.override());
        assertFalse(Files.exists(settingsFile()));
        verifyNoInteractions(signs);
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
        verify(gui, never()).openAdmin(any());
    }

    @Test
    @DisplayName("An nach aus: entfernt alle Texte, speichert false, Admin-Panel wird neu geoeffnet")
    void toggle_anNachAus() {
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertFalse(setting.isEnabled());
        verify(signs).removeAllSigns();
        verify(signs, never()).spawnAllSigns();
        assertEquals(Boolean.FALSE, new SettingsStore(settingsFile(), logger).load().ownerNameDisplay());
        assertEquals(List.of("namensanzeige-ausgeschaltet"), sentTexts());
        verify(gui).openAdmin(player);
    }

    @Test
    @DisplayName("Aus nach an: erzeugt alle Texte neu und speichert true")
    void toggle_ausNachAn() throws IOException {
        Files.writeString(settingsFile(), "owner_name_display: false\n");
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertTrue(setting.isEnabled());
        verify(signs).spawnAllSigns();
        verify(signs, never()).removeAllSigns();
        assertEquals(Boolean.TRUE, new SettingsStore(settingsFile(), logger).load().ownerNameDisplay());
        assertEquals(List.of("namensanzeige-eingeschaltet"), sentTexts());
        verify(gui).openAdmin(player);
    }

    @Test
    @DisplayName("Ohne Eintrag in settings.yml gilt signs.enabled false, der Klick schaltet an")
    void toggle_configFalse_schaltetAn() {
        configValue.set(false);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertTrue(setting.isEnabled());
        verify(signs).spawnAllSigns();
        assertEquals(Boolean.TRUE, new SettingsStore(settingsFile(), logger).load().ownerNameDisplay());
    }

    @Test
    @DisplayName("Zweimal klicken: aus und wieder an, beide Male gespeichert")
    void toggle_zweimal() {
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);
        listener.toggleOwnerNameDisplay(player);

        assertTrue(setting.isEnabled());
        verify(signs).removeAllSigns();
        verify(signs).spawnAllSigns();
        assertEquals(Boolean.TRUE, new SettingsStore(settingsFile(), logger).load().ownerNameDisplay());
    }

    @Test
    @DisplayName("Speichern fehlgeschlagen: Aenderung wirkt, deutliche Warnung mit Grund, Datei unangetastet")
    void toggle_speichernFehlgeschlagen_warnung() throws IOException {
        String broken = "owner_name_display: [\n";
        Files.writeString(settingsFile(), broken);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertFalse(setting.isEnabled());
        verify(signs).removeAllSigns();
        assertEquals(List.of("namensanzeige-ausgeschaltet", "namensanzeige-speichern-fehlgeschlagen"), sentTexts());
        ArgumentCaptor<Object> reason = ArgumentCaptor.forClass(Object.class);
        verify(messages).get(eq("namensanzeige-speichern-fehlgeschlagen"), reason.capture());
        assertTrue(String.valueOf(reason.getValue()).contains(SettingsStore.FILE_NAME));
        assertEquals(broken, Files.readString(settingsFile()));
        verify(gui).openAdmin(player);
    }

    @Test
    @DisplayName("Speichern erfolgreich: keine Warnung")
    void toggle_gespeichert_keineWarnung() {
        useSetting();

        listener.toggleOwnerNameDisplay(player);

        assertFalse(sentTexts().contains("namensanzeige-speichern-fehlgeschlagen"));
    }
}
