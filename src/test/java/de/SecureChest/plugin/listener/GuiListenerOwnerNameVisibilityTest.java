package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.LockService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Admin-Schalter fuer verdeckte Namen und Spielerschalter fuer den eigenen Namen")
class GuiListenerOwnerNameVisibilityTest {

    @TempDir
    Path folder;

    private final AtomicBoolean hideConfigValue = new AtomicBoolean(true);
    private final Logger logger = mock(Logger.class);
    private final UUID playerUuid = UUID.randomUUID();
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private GuiManager gui;
    private SignManager signs;
    private LockService locks;
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
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    private Path settingsFile() {
        return folder.resolve(SettingsStore.FILE_NAME);
    }

    private OwnerNameDisplaySetting useSetting() {
        OwnerNameDisplaySetting setting = new OwnerNameDisplaySetting(
                new SettingsStore(settingsFile(), logger), () -> true, hideConfigValue::get, logger);
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
    @DisplayName("Die Aktion des Admin-Schalters ist Admin-only und wird vom Admin-Panel erzeugt")
    void hideActionIsAdminOnly() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_owner_name_hide_when_covered"));
        assertTrue(ScreenActionRecorder.adminScreenActions().contains("toggle_owner_name_hide_when_covered"));
    }

    @Test
    @DisplayName("Admin-Schalter ohne Admin-Permission beim Klick: nichts umgeschaltet, nichts gespeichert, Hauptmenue")
    void hideToggleWithoutPermission() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameHideWhenCovered(player);

        assertTrue(setting.isHideWhenCoveredEnabled());
        assertNull(setting.hideWhenCoveredOverride());
        assertFalse(Files.exists(settingsFile()));
        verifyNoInteractions(signs);
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(gui).openMain(player);
        verify(gui, never()).openAdmin(any());
    }

    @Test
    @DisplayName("Admin-Schalter an nach aus: gespeichert, alle Gruppen neu berechnet, Meldung, Admin-Panel")
    void hideToggleOnToOff() {
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameHideWhenCovered(player);

        assertFalse(setting.isHideWhenCoveredEnabled());
        assertTrue(setting.isEnabled());
        verify(signs).refreshAllGroups();
        verify(signs, never()).removeAllSigns();
        verify(signs, never()).spawnAllSigns();
        SettingsStore.Contents stored = new SettingsStore(settingsFile(), logger).load();
        assertEquals(Boolean.FALSE, stored.ownerNameHideWhenCovered());
        assertNull(stored.ownerNameDisplay());
        assertEquals(List.of("verdeckte-namen-verstecken-ausgeschaltet"), sentTexts());
        verify(gui).openAdmin(player);
    }

    @Test
    @DisplayName("Admin-Schalter aus nach an: Standard aus der config.yml aus, der Klick schaltet an")
    void hideToggleOffToOn() {
        hideConfigValue.set(false);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameHideWhenCovered(player);

        assertTrue(setting.isHideWhenCoveredEnabled());
        verify(signs).refreshAllGroups();
        assertEquals(Boolean.TRUE, new SettingsStore(settingsFile(), logger).load().ownerNameHideWhenCovered());
        assertEquals(List.of("verdeckte-namen-verstecken-eingeschaltet"), sentTexts());
    }

    @Test
    @DisplayName("Admin-Schalter: Speichern fehlgeschlagen ergibt die Warnung mit Grund, die Aenderung wirkt")
    void hideToggleSaveFailure() throws IOException {
        String broken = "owner_name_hide_when_covered: [\n";
        Files.writeString(settingsFile(), broken);
        OwnerNameDisplaySetting setting = useSetting();

        listener.toggleOwnerNameHideWhenCovered(player);

        assertFalse(setting.isHideWhenCoveredEnabled());
        verify(signs).refreshAllGroups();
        assertEquals(List.of("verdeckte-namen-verstecken-ausgeschaltet", "namensanzeige-speichern-fehlgeschlagen"),
                sentTexts());
        ArgumentCaptor<Object> reason = ArgumentCaptor.forClass(Object.class);
        verify(messages).get(eq("namensanzeige-speichern-fehlgeschlagen"), reason.capture());
        assertTrue(String.valueOf(reason.getValue()).contains(SettingsStore.FILE_NAME));
        assertEquals(broken, Files.readString(settingsFile()));
    }

    @Test
    @DisplayName("Die Aktion des Spielerschalters ist keine Admin-Aktion")
    void ownerActionIsNotAdminOnly() {
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_show_owner_name"));
    }

    @Test
    @DisplayName("Spielerschalter an nach aus: schreibt den eigenen Wert und oeffnet die Einstellungen, auch ohne Admin-Permission")
    void ownerToggleOnToOff() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        when(locks.getShowOwnerName(playerUuid)).thenReturn(true);

        listener.toggleShowOwnerName(player);

        verify(locks).setShowOwnerName(playerUuid, false);
        verify(gui).openSettings(player);
        verify(player, never()).sendMessage(any(Component.class));
        verify(gui, never()).openMain(any());
    }

    @Test
    @DisplayName("Spielerschalter aus nach an")
    void ownerToggleOffToOn() {
        when(locks.getShowOwnerName(playerUuid)).thenReturn(false);

        listener.toggleShowOwnerName(player);

        verify(locks).setShowOwnerName(playerUuid, true);
        verify(locks, never()).setShowOwnerName(any(), eq(false));
        verify(locks, never()).setQuickLock(any(), anyBoolean());
        verify(gui).openSettings(player);
    }
}
