package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.names.PlayerNameService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
    private MessageService messages;
    private PlayerNameService names;
    private UUID ownerId;
    private OwnerNameDisplaySetting ownerNameDisplay;
    private LockService locks;
    private BukkitScheduler scheduler;
    private World world;
    private LockKey key;
    private String lockId;
    private Block chestBlock;
    private TextDisplay firstDisplay;
    private TextDisplay secondDisplay;
    private UUID firstDisplayId;
    private UUID secondDisplayId;
    private SignManager signManager;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);

        plugin = mock(ChestSecurePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        messages = mock(MessageService.class);
        when(messages.get(anyString(), any(Object[].class))).thenReturn("owner");
        when(plugin.msg()).thenReturn(messages);
        ownerNameDisplay = mock(OwnerNameDisplaySetting.class);
        when(ownerNameDisplay.isEnabled()).thenReturn(true);
        when(plugin.ownerNameDisplay()).thenReturn(ownerNameDisplay);
        ownerId = UUID.randomUUID();
        names = mock(PlayerNameService.class);
        when(names.resolve(ownerId)).thenReturn("Alex");
        when(plugin.names()).thenReturn(names);

        UUID worldId = UUID.randomUUID();
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).thenReturn(true);
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        Chunk loadedChunk = mock(Chunk.class);
        when(loadedChunk.getX()).thenReturn(CHUNK_X);
        when(loadedChunk.getZ()).thenReturn(CHUNK_Z);
        when(world.getLoadedChunks()).thenReturn(new Chunk[] {loadedChunk});
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));

        key = new LockKey(worldId, CHUNK_X * 16 + 5, 64, CHUNK_Z * 16 + 7);
        lockId = key.asId();
        chestBlock = mock(Block.class);
        when(chestBlock.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(key.x(), key.y(), key.z())).thenReturn(chestBlock);

        locks = mock(LockService.class);
        LockEntry entry = new LockEntry(key, Material.CHEST, ownerId, 0L);
        when(locks.getAllCachedLocks()).thenReturn(List.of(entry));
        when(locks.getCachedLocksInChunk(worldId, CHUNK_X, CHUNK_Z)).thenReturn(List.of(entry));
        when(locks.getShowOwnerName(any())).thenReturn(true);
        when(plugin.locks()).thenReturn(locks);

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
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
        loadChunk(CHUNK_X, CHUNK_Z);
    }

    private void loadChunk(int chunkX, int chunkZ) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
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
        when(locks.getCachedLocksInChunk(world.getUID(), CHUNK_X, CHUNK_Z)).thenReturn(List.of());
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
        when(locks.getCachedLocksInChunk(world.getUID(), CHUNK_X, CHUNK_Z)).thenReturn(List.of());
        signManager.removeSign(lockId);
        verify(secondDisplay).remove();
        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad bei ausgeschalteter Namensanzeige: nichts erzeugt, Eintrag unveraendert")
    void chunkLoadWhileDisabledChangesNothing() {
        signManager.spawnSign(key, "Alex");
        clearInvocations(scheduler, locks);
        when(firstDisplay.isValid()).thenReturn(false);
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        loadChunk();

        verifySpawnCount(1);
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(locks, never()).getAllCachedLocks();
        verify(locks, never()).getCachedLocksInChunk(any(), anyInt(), anyInt());
        when(firstDisplay.isValid()).thenReturn(true);
        assertTrue(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad fragt nur die Sperren des geladenen Chunks ab, nie den ganzen Bestand")
    void chunkLoadUsesChunkIndexOnly() {
        loadChunk();

        verify(locks, times(2)).getCachedLocksInChunk(world.getUID(), CHUNK_X, CHUNK_Z);
        verify(locks, times(2)).getCachedLocksInChunk(any(), anyInt(), anyInt());
        verify(locks, never()).getAllCachedLocks();
        verify(locks, never()).getAllCachedHoppers();
    }

    @Test
    @DisplayName("onChunkLoad laesst Sperren in anderen Chunks und anderen Welten unberuehrt")
    void chunkLoadIgnoresOtherChunks() {
        UUID otherWorldId = UUID.randomUUID();
        LockKey neighbourKey = new LockKey(world.getUID(), (CHUNK_X + 1) * 16, 64, CHUNK_Z * 16);
        LockKey otherWorldKey = new LockKey(otherWorldId, key.x(), key.y(), key.z());
        LockEntry neighbour = new LockEntry(neighbourKey, Material.CHEST, ownerId, 0L);
        LockEntry otherWorld = new LockEntry(otherWorldKey, Material.CHEST, ownerId, 0L);
        when(locks.getAllCachedLocks()).thenReturn(List.of(
                new LockEntry(key, Material.CHEST, ownerId, 0L), neighbour, otherWorld));
        when(locks.getCachedLocksInChunk(world.getUID(), CHUNK_X + 1, CHUNK_Z)).thenReturn(List.of(neighbour));
        when(locks.getCachedLocksInChunk(otherWorldId, CHUNK_X, CHUNK_Z)).thenReturn(List.of(otherWorld));

        loadChunk();

        verifySpawnCount(1);
        verify(world, never()).getBlockAt(neighbourKey.x(), neighbourKey.y(), neighbourKey.z());
        verify(world, times(2)).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(locks, never()).forceRemoveLockAt(any());
        assertTrue(signManager.hasLiveSign(lockId));
        assertFalse(signManager.hasLiveSign(neighbourKey.asId()));
        assertFalse(signManager.hasLiveSign(otherWorldKey.asId()));
    }

    @Test
    @DisplayName("onChunkLoad eines Chunks ohne Sperren erzeugt nichts und liest keinen Block")
    void chunkLoadWithoutLocksDoesNothing() {
        when(world.isChunkLoaded(CHUNK_X + 5, CHUNK_Z - 5)).thenReturn(true);
        loadChunk(CHUNK_X + 5, CHUNK_Z - 5);

        verify(locks).getCachedLocksInChunk(world.getUID(), CHUNK_X + 5, CHUNK_Z - 5);
        verifySpawnCount(0);
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(locks, never()).getAllCachedLocks();
    }

    @Test
    @DisplayName("onChunkLoad entfernt eine verwaiste Sperre des Chunks und erzeugt fuer sie keinen Text")
    void chunkLoadRemovesOrphanLock() {
        when(chestBlock.getType()).thenReturn(Material.AIR);

        loadChunk();

        verify(locks).forceRemoveLockAt(key);
        verifySpawnCount(0);
        assertFalse(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("onChunkLoad mit gueltiger und verwaister Sperre im selben Chunk: nur die verwaiste wird entfernt")
    void chunkLoadWithValidAndOrphanLock() {
        LockKey orphanKey = new LockKey(world.getUID(), CHUNK_X * 16 + 15, 70, CHUNK_Z * 16);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(orphanKey.x(), orphanKey.y(), orphanKey.z())).thenReturn(air);
        when(locks.getCachedLocksInChunk(world.getUID(), CHUNK_X, CHUNK_Z)).thenReturn(List.of(
                new LockEntry(key, Material.CHEST, ownerId, 0L),
                new LockEntry(orphanKey, Material.CHEST, ownerId, 0L)));

        loadChunk();

        verify(locks).forceRemoveLockAt(orphanKey);
        verify(locks, never()).forceRemoveLockAt(key);
        verifySpawnCount(1);
        assertTrue(signManager.hasLiveSign(lockId));
        assertFalse(signManager.hasLiveSign(orphanKey.asId()));
    }

    @Test
    @DisplayName("onChunkLoad: Text nutzt den aufgeloesten Namen des Besitzers")
    void chunkLoadUsesResolvedOwnerName() {
        loadChunk();

        verify(names).resolve(ownerId);
        verify(messages).get("sign-owner-display", "Alex");
    }

    @Test
    @DisplayName("spawnAllSigns: Text nutzt den aufgeloesten Namen des Besitzers")
    void spawnAllSignsUsesResolvedOwnerName() {
        signManager.spawnAllSigns();

        verifySpawnCount(1);
        verify(messages).get("sign-owner-display", "Alex");
    }

    @Test
    @DisplayName("refreshOwnerSigns: vorhandener Text wird mit dem neuen Namen ersetzt")
    void refreshOwnerSignsReplacesSignWithNewName() {
        signManager.spawnSign(key, "Alex");
        when(names.resolve(ownerId)).thenReturn("Steve");

        signManager.refreshOwnerSigns(ownerId);

        verifySpawnCount(2);
        verify(firstDisplay).remove();
        verify(messages).get("sign-owner-display", "Steve");
        assertTrue(signManager.hasLiveSign(lockId));
    }

    @Test
    @DisplayName("refreshOwnerSigns: Sperren anderer Besitzer bleiben unveraendert")
    void refreshOwnerSignsIgnoresOtherOwners() {
        signManager.spawnSign(key, "Alex");
        clearInvocations(names);

        signManager.refreshOwnerSigns(UUID.randomUUID());

        verifySpawnCount(1);
        verify(firstDisplay, never()).remove();
        verify(names, never()).resolve(any(UUID.class));
    }

    @Test
    @DisplayName("refreshOwnerSigns bei ausgeschalteter Namensanzeige: nichts erzeugt")
    void refreshOwnerSignsWhileDisabledChangesNothing() {
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        signManager.refreshOwnerSigns(ownerId);

        verifySpawnCount(0);
        verify(locks, never()).getAllCachedLocks();
    }
}
