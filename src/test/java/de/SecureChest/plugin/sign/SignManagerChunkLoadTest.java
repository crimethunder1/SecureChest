package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SignManager: Besitzernamen nach erneutem Laden eines Chunks")
class SignManagerChunkLoadTest {

    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;

    private MockedStatic<Bukkit> bukkit;
    private ChestSecurePlugin plugin;
    private OwnerNameDisplaySetting ownerNameDisplay;
    private LockService locks;
    private BukkitScheduler scheduler;
    private World world;
    private LockKey key;
    private String lockId;
    private TextDisplay firstDisplay;
    private TextDisplay secondDisplay;
    private UUID firstDisplayId;
    private UUID secondDisplayId;
    private SignManager signManager;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);

        plugin = mock(ChestSecurePlugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        MessageService messages = mock(MessageService.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("owner");
        when(plugin.msg()).thenReturn(messages);
        ownerNameDisplay = mock(OwnerNameDisplaySetting.class);
        when(ownerNameDisplay.isEnabled()).thenReturn(true);
        when(plugin.ownerNameDisplay()).thenReturn(ownerNameDisplay);

        UUID worldId = UUID.randomUUID();
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).thenReturn(true);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);

        key = new LockKey(worldId, CHUNK_X * 16 + 5, 64, CHUNK_Z * 16 + 7);
        lockId = key.asId();
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(key.x(), key.y(), key.z())).thenReturn(block);

        locks = mock(LockService.class);
        when(locks.getAllCachedLocks()).thenReturn(List.of(new LockEntry(key, Material.CHEST, UUID.randomUUID(), 0L)));
        when(plugin.locks()).thenReturn(locks);

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        firstDisplayId = UUID.randomUUID();
        secondDisplayId = UUID.randomUUID();
        firstDisplay = display(firstDisplayId);
        secondDisplay = display(secondDisplayId);
        when(world.spawn(any(Location.class), eq(TextDisplay.class), anyConfigurator())).thenAnswer(new SpawnSequence());

        signManager = new SignManager(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private TextDisplay display(UUID entityId) {
        TextDisplay display = mock(TextDisplay.class);
        when(display.getUniqueId()).thenReturn(entityId);
        when(display.isValid()).thenReturn(true);
        bukkit.when(() -> Bukkit.getEntity(entityId)).thenReturn(display);
        return display;
    }

    private final class SpawnSequence implements Answer<TextDisplay> {
        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public TextDisplay answer(InvocationOnMock invocation) {
            TextDisplay display = calls++ == 0 ? firstDisplay : secondDisplay;
            invocation.getArgument(2, Consumer.class).accept(display);
            return display;
        }
    }

    private void loadChunk() {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(CHUNK_X);
        when(chunk.getZ()).thenReturn(CHUNK_Z);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getChunk()).thenReturn(chunk);
        signManager.onChunkLoad(event);
    }

    private void verifySpawnCount(int expected) {
        verify(world, times(expected)).spawn(any(Location.class), eq(TextDisplay.class), anyConfigurator());
    }

    private static Consumer<? super TextDisplay> anyConfigurator() {
        return any();
    }

    @Test
    @DisplayName("hasLiveSign: ohne Eintrag false")
    void hasLiveSignWithoutEntry() {
        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("hasLiveSign: Eintrag mit gueltiger Entity true")
    void hasLiveSignWithValidEntity() {
        signManager.spawnSign(key, "Alex");

        assertTrue(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("hasLiveSign: Eintrag mit ungueltiger Entity false")
    void hasLiveSignWithInvalidEntity() {
        signManager.spawnSign(key, "Alex");
        when(firstDisplay.isValid()).thenReturn(false);

        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("hasLiveSign: Eintrag mit nicht auffindbarer Entity false")
    void hasLiveSignWithMissingEntity() {
        signManager.spawnSign(key, "Alex");
        bukkit.when(() -> Bukkit.getEntity(firstDisplayId)).thenReturn(null);

        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad ohne Eintrag: Text wird erzeugt")
    void chunkLoadWithoutEntrySpawnsSign() {
        loadChunk();

        verifySpawnCount(1);
        verify(firstDisplay).setPersistent(false);
        assertTrue(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad mit gueltiger Entity: kein zweiter Text, vorhandener bleibt")
    void chunkLoadWithLiveSignSpawnsNothing() {
        signManager.spawnSign(key, "Alex");

        loadChunk();

        verifySpawnCount(1);
        verify(firstDisplay, never()).remove();
        assertTrue(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad mit ungueltiger Entity: veralteter Eintrag entfernt, neuer Text, Eintrag zeigt auf neue Entity")
    void chunkLoadWithInvalidEntityRespawnsSign() {
        signManager.spawnSign(key, "Alex");
        when(firstDisplay.isValid()).thenReturn(false);

        loadChunk();

        verifySpawnCount(2);
        verify(firstDisplay, never()).remove();
        assertTrue(signManager.hasLiveSign(lockId));
        signManager.removeSign(lockId);
        verify(secondDisplay).remove();
        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad mit nicht auffindbarer Entity: veralteter Eintrag entfernt, neuer Text, Eintrag zeigt auf neue Entity")
    void chunkLoadWithMissingEntityRespawnsSign() {
        signManager.spawnSign(key, "Alex");
        bukkit.when(() -> Bukkit.getEntity(firstDisplayId)).thenReturn(null);

        loadChunk();

        verifySpawnCount(2);
        assertTrue(signManager.hasLiveSign(lockId));
        signManager.removeSign(lockId);
        verify(secondDisplay).remove();
        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad bei ausgeschalteter Namensanzeige: nichts erzeugt, Eintrag unveraendert")
    void chunkLoadWhileDisabledChangesNothing() {
        signManager.spawnSign(key, "Alex");
        when(firstDisplay.isValid()).thenReturn(false);
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        loadChunk();

        verifySpawnCount(1);
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(locks, never()).getAllCachedLocks();
        when(firstDisplay.isValid()).thenReturn(true);
        assertTrue(signManager.hasLiveSign(lockId));
    }
}
