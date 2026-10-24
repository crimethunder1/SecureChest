package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.ping.PingManager;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Ping aus dem LockScreen und aus dem Admin-Log starten und stoppen")
class GuiListenerPingTest {

    private MockedStatic<Bukkit> bukkit;
    private PingManager ping;
    private LockService locks;
    private GuiManager gui;
    private Player player;
    private UUID worldId;
    private LockKey lockKey;
    private String lockId;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        ping = mock(PingManager.class);
        when(plugin.ping()).thenReturn(ping);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        when(plugin.ui()).thenReturn(new UiStateStore());

        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        worldId = UUID.randomUUID();
        World world = mock(World.class);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        lockKey = new LockKey(worldId, 10, 64, -20);
        lockId = lockKey.asId();
        when(locks.getLockFromId(lockId))
                .thenReturn(Optional.of(new LockEntry(lockKey, Material.CHEST, playerId, 0L)));
        listener = new GuiListener(plugin, material -> true);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    private LogRecord logRecord(UUID logWorldId) {
        return new LogRecord(7L, 0L, "UNAUTHORIZED_BREAK", UUID.randomUUID(), "Actor", "world",
                logWorldId, 10, 64, -20, null);
    }

    @Test
    @DisplayName("ping_lock ohne laufenden Ping: startet den Ping mit dem LockKey der Sperre und schliesst das GUI")
    void pingLockStarts() {
        listener.pingLock(player, lockId);

        verify(ping).startPing(player, lockKey);
        verify(ping, never()).stopPing(any());
        assertEquals(List.of("gui-ping-started"), sentTexts());
        verify(player).closeInventory();
    }

    @Test
    @DisplayName("ping_lock bei laufendem Ping zu dieser Sperre: beendet den Ping und zeigt die Verwaltung neu")
    void pingLockStopsRunningPing() {
        when(ping.isPinging(player.getUniqueId(), lockKey)).thenReturn(true);

        listener.pingLock(player, lockId);

        verify(ping).stopPing(player);
        verify(ping, never()).startPing(any(), any());
        assertEquals(List.of("ping-stopped"), sentTexts());
        verify(gui).openLockManage(player, lockId);
        verify(player, never()).closeInventory();
    }

    @Test
    @DisplayName("ping_lock bei laufendem Ping zu einer anderen Sperre: startet den neuen Ping")
    void pingLockWithOtherPingStartsNew() {
        when(ping.isPinging(player.getUniqueId(), new LockKey(worldId, 0, 0, 0))).thenReturn(true);

        listener.pingLock(player, lockId);

        verify(ping).startPing(player, lockKey);
        verify(ping, never()).stopPing(any());
    }

    @Test
    @DisplayName("ping_lock mit nicht geladener Welt: Meldung, kein Ping")
    void pingLockWorldNotLoaded() {
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(null);

        listener.pingLock(player, lockId);

        verify(ping, never()).startPing(any(), any());
        assertEquals(List.of("gui-ping-world-not-loaded"), sentTexts());
        verify(gui).openLockManage(player, lockId);
    }

    @Test
    @DisplayName("ping_lock fuer eine entfernte Sperre: schloss-nicht-gefunden, kein Ping")
    void pingLockMissingLock() {
        when(locks.getLockFromId(anyString())).thenReturn(Optional.empty());

        listener.pingLock(player, lockId);

        verify(ping, never()).startPing(any(), any());
        verify(ping, never()).stopPing(any());
        assertEquals(List.of("schloss-nicht-gefunden"), sentTexts());
        verify(gui).openLocksList(player, 0);
    }

    @Test
    @DisplayName("log_entry ohne laufenden Ping: startet den Ping zur Log-Position und schliesst das GUI")
    void logEntryStarts() {
        listener.pingLogEntry(player, logRecord(worldId));

        verify(ping).startPing(player, lockKey);
        assertEquals(List.of("gui-ping-started-log"), sentTexts());
        verify(player).closeInventory();
    }

    @Test
    @DisplayName("log_entry erneut geklickt: beendet den Ping und zeigt den Log-Browser neu")
    void logEntryStopsRunningPing() {
        when(ping.isPinging(player.getUniqueId(), lockKey)).thenReturn(true);

        listener.pingLogEntry(player, logRecord(worldId));

        verify(ping).stopPing(player);
        verify(ping, never()).startPing(any(), any());
        assertEquals(List.of("ping-stopped"), sentTexts());
        verify(gui).openLogs(player, 0);
        verify(player, never()).closeInventory();
    }

    @Test
    @DisplayName("log_entry mit nicht geladener Welt: Meldung, kein Ping")
    void logEntryWorldNotLoaded() {
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(null);

        listener.pingLogEntry(player, logRecord(worldId));

        verify(ping, never()).startPing(any(), any());
        assertEquals(List.of("gui-log-world-not-loaded"), sentTexts());
        verify(gui).openLogs(player, 0);
    }

    @Test
    @DisplayName("log_entry ohne Welt-UUID: Meldung, kein Ping, keine Exception")
    void logEntryWithoutWorldUuid() {
        assertDoesNotThrow(() -> listener.pingLogEntry(player, logRecord(null)));

        verify(ping, never()).startPing(any(), any());
        verify(ping, never()).isPinging(any(), any());
        assertEquals(List.of("gui-log-world-not-loaded"), sentTexts());
        verify(gui).openLogs(player, 0);
    }
}
