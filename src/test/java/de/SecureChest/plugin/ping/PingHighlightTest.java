package de.SecureChest.plugin.ping;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PingHighlight: leuchtender Behaelter nur fuer den pingenden Spieler")
class PingHighlightTest {

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

    private void start(Player pingingPlayer) {
        fixture.manager.startPing(pingingPlayer, fixture.target);
    }

    private BlockDisplay onlyDisplay() {
        assertEquals(1, fixture.spawnedDisplays.size(), "Erwartet genau ein Display");
        return fixture.spawnedDisplays.getFirst();
    }

    @Test
    @DisplayName("Geladener Chunk: ein Display mit Block-Daten, Leuchten in Gold, nicht persistent, nur fuer den Spieler sichtbar")
    void spawnsDisplayWithExpectedProperties() {
        start(player);

        fixture.tick();

        BlockDisplay display = onlyDisplay();
        verify(display).setBlock(fixture.targetBlockData);
        verify(display).setGlowing(true);
        verify(display).setGlowColorOverride(Color.fromRGB(0xFFAA00));
        verify(display).setPersistent(false);
        verify(display).setVisibleByDefault(false);
        verify(display).setViewRange(2.0f);
        verify(display).setBrightness(new Display.Brightness(15, 15));
        verify(player).showEntity(fixture.plugin, display);
        assertEquals(1, fixture.manager.highlightDisplayCount(player.getUniqueId()));
    }

    @Test
    @DisplayName("Display sitzt an der Blockecke und ist um die Mitte auf 1,02 vergroessert")
    void displayIsScaledAroundBlockCenter() {
        start(player);

        fixture.tick();

        Location location = fixture.spawnLocations.getFirst();
        assertSame(fixture.world, location.getWorld());
        assertEquals(PingTestFixture.TARGET_X, location.getX());
        assertEquals(PingTestFixture.TARGET_Y, location.getY());
        assertEquals(PingTestFixture.TARGET_Z, location.getZ());
        ArgumentCaptor<Transformation> transformation = ArgumentCaptor.forClass(Transformation.class);
        verify(onlyDisplay()).setTransformation(transformation.capture());
        Vector3f scale = transformation.getValue().getScale();
        Vector3f translation = transformation.getValue().getTranslation();
        assertEquals(1.02f, scale.x(), 1.0e-6f);
        assertEquals(1.02f, scale.y(), 1.0e-6f);
        assertEquals(1.02f, scale.z(), 1.0e-6f);
        assertEquals(-0.01f, translation.x(), 1.0e-6f);
        assertEquals(-0.01f, translation.y(), 1.0e-6f);
        assertEquals(-0.01f, translation.z(), 1.0e-6f);
    }

    @Test
    @DisplayName("Weitere Takte mit gueltigem Display erzeugen kein zweites Display")
    void validDisplayIsKept() {
        start(player);

        fixture.tick();
        fixture.tick();
        fixture.tick();

        verify(onlyDisplay(), never()).remove();
        verify(player, times(1)).showEntity(any(), any());
    }

    @Test
    @DisplayName("Nicht geladener Chunk: kein Display, kein Blockzugriff, nach dem Laden nachgeholt")
    void unloadedChunkSpawnsLater() {
        fixture.chunkLoaded.set(false);
        start(player);

        fixture.tick();
        fixture.tick();

        assertTrue(fixture.spawnedDisplays.isEmpty());
        verify(fixture.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));

        fixture.chunkLoaded.set(true);
        fixture.tick();

        verify(player).showEntity(fixture.plugin, onlyDisplay());
    }

    @Test
    @DisplayName("Doppelkiste: zwei Displays an beiden Haelften, beide fuer den Spieler sichtbar")
    void doubleChestSpawnsTwoDisplays() {
        BlockData partnerData = fixture.makeDoubleChest().getBlockData();
        start(player);

        fixture.tick();

        assertEquals(2, fixture.spawnedDisplays.size());
        assertEquals(PingTestFixture.TARGET_X, fixture.spawnLocations.get(0).getX());
        assertEquals(PingTestFixture.TARGET_X + 1, fixture.spawnLocations.get(1).getX());
        verify(fixture.spawnedDisplays.get(0)).setBlock(fixture.targetBlockData);
        verify(fixture.spawnedDisplays.get(1)).setBlock(partnerData);
        verify(player).showEntity(fixture.plugin, fixture.spawnedDisplays.get(0));
        verify(player).showEntity(fixture.plugin, fixture.spawnedDisplays.get(1));

        fixture.tick();
        assertEquals(2, fixture.spawnedDisplays.size());
    }

    @Test
    @DisplayName("Ende durch Ankunft und Nachlaufzeit entfernt das Display")
    void endRemovesDisplay() {
        start(player);
        fixture.tick();
        fixture.moveTo(player, PingTestFixture.TARGET_X + 0.5, PingTestFixture.TARGET_Y,
                PingTestFixture.TARGET_Z + 0.5 - 2.0, 0.0f);
        fixture.tick();

        fixture.advanceSeconds(15);
        fixture.tick();

        verify(onlyDisplay()).remove();
    }

    @Test
    @DisplayName("Zeitlimit entfernt das Display")
    void timeoutRemovesDisplay() {
        start(player);
        fixture.tick();

        fixture.advanceSeconds(300);
        fixture.tick();

        verify(onlyDisplay()).remove();
    }

    @Test
    @DisplayName("Stopp entfernt das Display, auch beide Haelften einer Doppelkiste")
    void stopRemovesDisplays() {
        fixture.makeDoubleChest();
        start(player);
        fixture.tick();

        fixture.manager.stopPing(player);

        verify(fixture.spawnedDisplays.get(0)).remove();
        verify(fixture.spawnedDisplays.get(1)).remove();
    }

    @Test
    @DisplayName("Quit (Spieler offline im naechsten Takt) entfernt das Display")
    void quitRemovesDisplay() {
        start(player);
        fixture.tick();
        fixture.goOffline(player);

        fixture.tick();

        verify(onlyDisplay()).remove();
    }

    @Test
    @DisplayName("stopAll entfernt die Displays aller Spieler")
    void stopAllRemovesDisplays() {
        Player secondPlayer = fixture.player();
        start(player);
        start(secondPlayer);
        fixture.tick();

        fixture.manager.stopAll();

        assertEquals(2, fixture.spawnedDisplays.size());
        verify(fixture.spawnedDisplays.get(0)).remove();
        verify(fixture.spawnedDisplays.get(1)).remove();
    }

    @Test
    @DisplayName("Weltwechsel des Spielers entfernt das Display, Rueckkehr erzeugt ein neues")
    void worldChangeRemovesAndRespawns() {
        start(player);
        fixture.tick();
        BlockDisplay first = onlyDisplay();

        when(player.getWorld()).thenReturn(fixture.otherWorld);
        fixture.tick();
        verify(first).remove();
        assertEquals(0, fixture.manager.highlightDisplayCount(player.getUniqueId()));

        when(player.getWorld()).thenReturn(fixture.world);
        fixture.tick();
        assertEquals(2, fixture.spawnedDisplays.size());
        verify(player).showEntity(fixture.plugin, fixture.spawnedDisplays.get(1));
    }

    @Test
    @DisplayName("Ungueltige Entity im geladenen Chunk: neues Display wird erzeugt")
    void invalidEntityIsRespawned() {
        start(player);
        fixture.tick();
        BlockDisplay first = onlyDisplay();
        fixture.invalidEntities.add(first);

        fixture.tick();

        assertEquals(2, fixture.spawnedDisplays.size());
        verify(player).showEntity(fixture.plugin, fixture.spawnedDisplays.get(1));
        assertEquals(1, fixture.manager.highlightDisplayCount(player.getUniqueId()));
    }

    @Test
    @DisplayName("Chunk entladen: Eintrag verworfen, nach erneutem Laden neues Display")
    void chunkUnloadAndReload() {
        start(player);
        fixture.tick();
        fixture.invalidEntities.add(onlyDisplay());
        fixture.chunkLoaded.set(false);

        fixture.tick();
        assertEquals(0, fixture.manager.highlightDisplayCount(player.getUniqueId()));
        assertEquals(1, fixture.spawnedDisplays.size());

        fixture.chunkLoaded.set(true);
        fixture.tick();
        assertEquals(2, fixture.spawnedDisplays.size());
        assertEquals(1, fixture.manager.highlightDisplayCount(player.getUniqueId()));
    }

    @Test
    @DisplayName("Block ist kein sperrbarer Block mehr: Display entfernt, Ping laeuft weiter")
    void notLockableBlockRemovesDisplay() {
        start(player);
        fixture.tick();

        when(fixture.targetBlock.getType()).thenReturn(Material.STONE);
        fixture.tick();

        verify(onlyDisplay()).remove();
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("Sperre fehlt von Anfang an: kein Display")
    void noLockNoDisplay() {
        fixture.lockPresent(false);
        start(player);

        fixture.tick();

        assertTrue(fixture.spawnedDisplays.isEmpty());
    }

    @Test
    @DisplayName("ping.highlight_enabled false: nur Actionbar, kein Display und kein Blockzugriff")
    void highlightDisabled() {
        when(fixture.config.pingHighlightEnabled()).thenReturn(false);
        start(player);

        fixture.tick();
        fixture.tick();

        assertTrue(fixture.spawnedDisplays.isEmpty());
        verify(fixture.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(player, never()).showEntity(any(), any());
        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));
    }

    @Test
    @DisplayName("Zwei Spieler pingen dieselbe Kiste: getrennte Displays, jeder sieht nur seins")
    void separateDisplaysPerPlayer() {
        Player secondPlayer = fixture.player();
        start(player);
        start(secondPlayer);

        fixture.tick();

        assertEquals(2, fixture.spawnedDisplays.size());
        BlockDisplay firstDisplay = fixture.spawnedDisplays.get(0);
        BlockDisplay secondDisplay = fixture.spawnedDisplays.get(1);
        verify(player).showEntity(fixture.plugin, firstDisplay);
        verify(player, never()).showEntity(fixture.plugin, secondDisplay);
        verify(secondPlayer).showEntity(fixture.plugin, secondDisplay);
        verify(secondPlayer, never()).showEntity(fixture.plugin, firstDisplay);
        verify(firstDisplay).setVisibleByDefault(false);
        verify(secondDisplay).setVisibleByDefault(false);

        fixture.manager.stopPing(player);
        verify(firstDisplay).remove();
        verify(secondDisplay, never()).remove();
        assertEquals(1, fixture.manager.highlightDisplayCount(secondPlayer.getUniqueId()));
    }

    @Test
    @DisplayName("Fehler beim Erzeugen: keine Exception, Warnung nur einmal, Actionbar laeuft weiter")
    void spawnFailureIsLoggedOnce() {
        fixture.failSpawns(new IllegalStateException("spawn failed"));
        start(player);

        assertDoesNotThrow(fixture::tick);
        assertDoesNotThrow(fixture::tick);

        verify(fixture.logger, times(1)).log(eq(Level.WARNING), anyString(), any(Throwable.class));
        assertEquals("ping-navigation-relative|ping-direction-ahead|20", fixture.lastActionBar(player));
        assertTrue(fixture.manager.isPinging(player.getUniqueId(), fixture.target));
    }

    @Test
    @DisplayName("Kein Eingriff in den Spieler: keine Teams, keine Attribute, kein Glowing am Spieler")
    void playerIsNotModified() {
        start(player);

        fixture.tick();

        verify(player, never()).setGlowing(anyBoolean());
        verify(player, never()).getAttribute(any());
        verify(player, never()).getScoreboard();
        verify(player, never()).setScoreboard(any());
    }
}
