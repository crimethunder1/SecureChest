package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.lock.LockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PingManager: Lebenszyklus eines Pings mit Actionbar, Zeitlimit und Ende")
class PingManagerLifecycleTest {

    private PingTestFixture fixture;
    private Player player;

    @BeforeEach
    void setUp() {
        fixture = new PingTestFixture();
        player = fixture.player();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private void start() {
        fixture.manager.startPing(player, fixture.target);
    }

    private void moveNearTarget() {
        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y,
                PingTestFixture.TARGET_Z + 0.5 - 3.0, 0.0f);
    }

    @Test
    @DisplayName("Start: Timer mit 2 Ticks Verzoegerung und 10 Ticks Takt, Ping ist dem Ziel zugeordnet")
    void startSchedulesTimerAndRegistersTarget() {
        start();

        verify(fixture.scheduler).runTaskTimer(eq(fixture.plugin), any(Runnable.class),
                eq(PingManager.INITIAL_DELAY_TICKS), eq(PingManager.ACTION_BAR_UPDATE_INTERVAL_TICKS));
        assertEquals(1, fixture.scheduledCount());
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertFalse(fixture.manager.isPinging(player.getUniqueId(),
                new LockKey(fixture.worldId, 0, 64, 0)));
    }

    @Test
    @DisplayName("Takt: Actionbar mit relativer Richtung geradeaus und ganzzahligem Abstand")
    void tickShowsRelativeDirectionAndDistance() {
        start();

        fixture.tick();

        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));
    }

    @Test
    @DisplayName("Takt: Richtung wird bei jedem Takt neu aus der Blickrichtung berechnet")
    void directionIsRecalculatedEveryTick() {
        start();
        fixture.tick();

        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y,
                PingTestFixture.TARGET_Z + 0.5 - 20.0, 90.0f);
        fixture.tick();
        assertEquals("ping-navigation-relative|ping-direction-left|20", fixture.lastActionBar(player));

        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y,
                PingTestFixture.TARGET_Z + 0.5 - 20.0, 180.0f);
        fixture.tick();
        assertEquals("ping-navigation-relative|ping-direction-behind|20", fixture.lastActionBar(player));
    }

    @Test
    @DisplayName("Takt: Ziel 4 Bloecke hoeher und 3 Bloecke tiefer ergibt den passenden Hoehenhinweis")
    void tickShowsHeightHints() {
        start();

        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y - 4,
                PingTestFixture.TARGET_Z + 0.5 - 20.0, 0.0f);
        fixture.tick();
        assertEquals("ping-navigation-relative-above|ping-direction-ahead|20|4", fixture.lastActionBar(player));

        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y + 3,
                PingTestFixture.TARGET_Z + 0.5 - 20.0, 0.0f);
        fixture.tick();
        assertEquals("ping-navigation-relative-below|ping-direction-ahead|20|3", fixture.lastActionBar(player));
    }

    @Test
    @DisplayName("Ankunft: gruene Meldung, der Ping laeuft noch die Nachlaufzeit und endet danach")
    void arrivalShowsMessageAndEndsAfterLinger() {
        start();
        moveNearTarget();

        fixture.tick();
        Component arrival = fixture.lastActionBarComponent(player);
        assertEquals("ping-angekommen", PingTestFixture.text(arrival));
        assertEquals(NamedTextColor.GREEN, arrival.color());
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));

        fixture.advanceSeconds(14.9);
        fixture.tick();
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertFalse(fixture.isCancelled(0));

        fixture.advanceSeconds(0.1);
        fixture.tick();
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
    }

    @Test
    @DisplayName("Nachlaufzeit: Wer sich wieder entfernt, sieht die Navigation, der Ping endet trotzdem nach der Nachlaufzeit")
    void lingerShowsNavigationWhenWalkingAway() {
        start();
        moveNearTarget();
        fixture.tick();

        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y,
                PingTestFixture.TARGET_Z + 0.5 - 20.0, 0.0f);
        fixture.advanceSeconds(5);
        fixture.tick();
        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));

        fixture.advanceSeconds(10);
        fixture.tick();
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("ping.arrived_seconds 0: Ankunftsmeldung und sofortiges Ende im selben Takt")
    void zeroArrivedSecondsEndsImmediately() {
        when(fixture.config.pingArrivedSeconds()).thenReturn(0);
        start();
        moveNearTarget();

        fixture.tick();

        assertEquals("ping-angekommen", fixture.lastActionBar(player));
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
        assertTrue(fixture.spawnedDisplays.isEmpty());
    }

    @Test
    @DisplayName("Zeitlimit: nach 300 Sekunden Ende mit Meldung, vorher nicht")
    void timeoutEndsPingWithMessage() {
        start();
        fixture.advanceSeconds(299.9);
        fixture.tick();
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        clearInvocations(player);

        fixture.advanceSeconds(0.1);
        fixture.tick();

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
        assertEquals(List.of("ping-timed-out"), fixture.chatMessages(player));
        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    @DisplayName("ping.timeout_seconds 0: kein Zeitlimit, auch nach 10 Stunden noch aktiv")
    void zeroTimeoutMeansNoLimit() {
        when(fixture.config.pingTimeoutSeconds()).thenReturn(0);
        start();

        fixture.advanceSeconds(36_000);
        fixture.tick();

        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertFalse(fixture.isCancelled(0));
    }

    @Test
    @DisplayName("Einstellungen werden beim Start gelesen: spaetere Aenderung wirkt erst beim naechsten Ping")
    void settingsAreReadAtStart() {
        when(fixture.config.pingTimeoutSeconds()).thenReturn(10);
        start();
        when(fixture.config.pingTimeoutSeconds()).thenReturn(0);

        fixture.advanceSeconds(10);
        fixture.tick();

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("Quit: Spieler offline beendet den Ping im naechsten Takt ohne Meldung")
    void offlinePlayerEndsPing() {
        start();
        fixture.goOffline(player);

        fixture.tick();

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
        verify(player, never()).sendActionBar(any(Component.class));
        assertTrue(fixture.chatMessages(player).isEmpty());
    }

    @Test
    @DisplayName("Quit: stopPing aus dem PlayerQuitListener beendet den Ping sofort")
    void stopPingOnQuit() {
        start();

        fixture.manager.stopPing(player);

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
    }

    @Test
    @DisplayName("Spieler ohne Eintrag in Bukkit.getPlayer: Ping endet ohne Exception")
    void missingPlayerEndsPing() {
        start();
        fixture.bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(null);

        assertDoesNotThrow(fixture::tick);

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("Andere Welt: gelbe Meldung, Ping laeuft weiter und endet durch das Zeitlimit")
    void otherWorldShowsMessageUntilTimeout() {
        start();
        when(player.getWorld()).thenReturn(fixture.otherWorld);

        fixture.tick();
        Component otherWorld = fixture.lastActionBarComponent(player);
        assertEquals("ping-andere-welt", PingTestFixture.text(otherWorld));
        assertEquals(NamedTextColor.YELLOW, otherWorld.color());
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));

        fixture.advanceSeconds(300);
        fixture.tick();
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertEquals(List.of("ping-timed-out"), fixture.chatMessages(player));
    }

    @Test
    @DisplayName("Welt entladen: kein Exception, Ping endet mit ping-world-unloaded")
    void unloadedWorldEndsPingWithoutException() {
        start();
        fixture.tick();
        fixture.bukkit.when(() -> Bukkit.getWorld(fixture.worldId)).thenReturn(null);

        assertDoesNotThrow(fixture::tick);

        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertTrue(fixture.isCancelled(0));
        assertEquals(List.of("ping-world-unloaded"), fixture.chatMessages(player));
        assertDoesNotThrow(fixture::tick);
    }

    @Test
    @DisplayName("Sperre entfernt: Actionbar laeuft weiter, nur das Leuchten verschwindet")
    void removedLockKeepsNavigation() {
        start();
        fixture.tick();
        assertEquals(1, fixture.spawnedDisplays.size());

        fixture.lockPresent(false);
        fixture.tick();

        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));
        verify(fixture.spawnedDisplays.getFirst()).remove();
        assertEquals(0, fixture.manager.highlightDisplayCount(player.getUniqueId()));
    }

    @Test
    @DisplayName("Neuer Ping ersetzt den alten: alter Timer abgebrochen, nur das neue Ziel aktiv")
    void newPingReplacesOldOne() {
        LockKey otherTarget = new LockKey(fixture.worldId, 0, 64, 0);
        fixture.manager.startPing(player, otherTarget);

        start();

        assertEquals(2, fixture.scheduledCount());
        assertTrue(fixture.isCancelled(0));
        assertFalse(fixture.isCancelled(1));
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), otherTarget));
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("stopPing ohne laufenden Ping: nichts passiert")
    void stopPingWithoutPing() {
        assertDoesNotThrow(() -> fixture.manager.stopPing(player));
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("stopAll: beendet die Pings aller Spieler")
    void stopAllEndsEveryPing() {
        Player secondPlayer = fixture.player();
        start();
        fixture.manager.startPing(secondPlayer, fixture.target);

        fixture.manager.stopAll();

        assertTrue(fixture.isCancelled(0));
        assertTrue(fixture.isCancelled(1));
        assertFalse(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
        assertFalse(fixture.manager.isPinging(secondPlayer.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("Abgebrochener Timer laeuft nicht weiter: nach dem Stopp keine Actionbar mehr")
    void stoppedPingSendsNothingMore() {
        start();
        fixture.manager.stopPing(player);

        fixture.tick();

        verify(player, never()).sendActionBar(any(Component.class));
        verify(player, never()).showEntity(any(Plugin.class), any());
    }
}
