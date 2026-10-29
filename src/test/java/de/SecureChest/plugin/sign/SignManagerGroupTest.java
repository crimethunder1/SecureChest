package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockCacheManager;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import de.SecureChest.plugin.names.PlayerNameService;
import de.SecureChest.plugin.settings.OwnerNameDisplaySetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SignManager: ein Namenstext pro Gruppe")
class SignManagerGroupTest {

    private static final int Y = 64;
    private static final double TEXT_Y = Y + NameTextPosition.HEIGHT_ABOVE_BLOCK;

    private record ChunkCoordinates(int x, int z) {}

    private static final class SpawnedText {
        final UUID id = UUID.randomUUID();
        final double x;
        final double y;
        final double z;
        TextDisplay display;
        String text;
        volatile boolean valid = true;
        boolean removed;

        SpawnedText(Location location) {
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
        }
    }

    private final UUID worldUuid = new UUID(0L, 1L);
    private final UUID alice = new UUID(0L, 10L);
    private final UUID bob = new UUID(0L, 20L);

    private MockedStatic<Bukkit> bukkit;
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private PlayerNameService names;
    private OwnerNameDisplaySetting ownerNameDisplay;
    private LockCacheManager cache;
    private LockService locks;
    private BukkitScheduler scheduler;
    private World world;
    private SignManager signManager;

    private final Deque<Runnable> queuedTasks = new ConcurrentLinkedDeque<>();
    private final List<Runnable> laterTasks = new ArrayList<>();
    private final List<Long> laterDelays = new ArrayList<>();
    private final Set<ChunkCoordinates> unloadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<LockKey, Material> materials = new HashMap<>();
    private final Map<LockKey, BlockData> blockData = new HashMap<>();
    private final Map<LockKey, Block> blocks = new HashMap<>();
    private final List<SpawnedText> spawned = new ArrayList<>();
    private final Map<UUID, SpawnedText> spawnedById = new HashMap<>();

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);

        plugin = mock(ChestSecurePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        messages = mock(MessageService.class);
        when(messages.get(eq("sign-owner-display"), any(Object[].class)))
                .thenAnswer(invocation -> "name:" + invocation.getArgument(1));
        when(plugin.msg()).thenReturn(messages);
        ownerNameDisplay = mock(OwnerNameDisplaySetting.class);
        when(ownerNameDisplay.isEnabled()).thenReturn(true);
        when(plugin.ownerNameDisplay()).thenReturn(ownerNameDisplay);
        names = mock(PlayerNameService.class);
        when(names.resolve(alice)).thenReturn("Alex");
        when(names.resolve(bob)).thenReturn("Bob");
        when(plugin.names()).thenReturn(names);

        cache = new LockCacheManager();
        locks = mock(LockService.class);
        when(locks.getCachedLocksInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.locksInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(locks.getCachedHoppersInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.hoppersInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(locks.getAllCachedLocks()).thenAnswer(invocation -> List.copyOf(cache.lockValues()));
        when(locks.getAllCachedHoppers()).thenAnswer(invocation -> List.copyOf(cache.hopperValues()));
        when(locks.getShowOwnerName(any())).thenReturn(true);
        when(plugin.locks()).thenReturn(locks);

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            queuedTasks.add(invocation.getArgument(1, Runnable.class));
            return null;
        });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            laterTasks.add(invocation.getArgument(1, Runnable.class));
            laterDelays.add(invocation.getArgument(2, Long.class));
            return null;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        world = mock(World.class);
        when(world.getUID()).thenReturn(worldUuid);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(invocation -> !unloadedChunks.contains(
                new ChunkCoordinates(invocation.getArgument(0), invocation.getArgument(1))));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> blockAt(
                new LockKey(worldUuid, invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        when(world.spawn(any(Location.class), eq(TextDisplay.class), anyConfigurator())).thenAnswer(invocation -> {
            SpawnedText text = new SpawnedText(invocation.getArgument(0, Location.class));
            TextDisplay display = mock(TextDisplay.class);
            text.display = display;
            when(display.getUniqueId()).thenReturn(text.id);
            when(display.isValid()).thenAnswer(validity -> text.valid);
            doAnswer(removal -> {
                text.valid = false;
                text.removed = true;
                return null;
            }).when(display).remove();
            doAnswer(content -> {
                text.text = ((TextComponent) content.getArgument(0)).content();
                return null;
            }).when(display).text(any(Component.class));
            @SuppressWarnings("unchecked")
            Consumer<TextDisplay> configurator = invocation.getArgument(2, Consumer.class);
            configurator.accept(display);
            spawned.add(text);
            spawnedById.put(text.id, text);
            return display;
        });
        bukkit.when(() -> Bukkit.getWorld(worldUuid)).thenReturn(world);
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        when(world.getLoadedChunks()).thenAnswer(invocation -> loadedChunksWithEntries());
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class))).thenAnswer(invocation -> {
            SpawnedText text = spawnedById.get(invocation.getArgument(0, UUID.class));
            return text == null ? null : text.display;
        });

        signManager = new SignManager(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private Chunk[] loadedChunksWithEntries() {
        List<LockKey> positions = new ArrayList<>();
        cache.lockValues().forEach(entry -> positions.add(entry.key()));
        cache.hopperValues().forEach(hopper -> positions.add(hopper.hopperKey()));
        List<Chunk> chunks = new ArrayList<>();
        for (ChunkCoordinates coordinates : positions.stream()
                .map(position -> new ChunkCoordinates(position.x() >> 4, position.z() >> 4)).distinct().toList()) {
            if (unloadedChunks.contains(coordinates)) continue;
            Chunk chunk = mock(Chunk.class);
            when(chunk.getX()).thenReturn(coordinates.x());
            when(chunk.getZ()).thenReturn(coordinates.z());
            chunks.add(chunk);
        }
        return chunks.toArray(new Chunk[0]);
    }

    private static Consumer<? super TextDisplay> anyConfigurator() {
        return any();
    }

    private LockKey at(int x, int z) {
        return new LockKey(worldUuid, x, Y, z);
    }

    private Block blockAt(LockKey key) {
        return blocks.computeIfAbsent(key, position -> {
            Block block = mock(Block.class);
            when(block.getType()).thenAnswer(invocation -> materials.getOrDefault(position, Material.AIR));
            when(block.getBlockData()).thenAnswer(invocation -> blockData.get(position));
            when(block.getLocation()).thenAnswer(invocation ->
                    new Location(world, position.x(), position.y(), position.z()));
            return block;
        });
    }

    private LockKey lock(int x, int z, UUID owner) {
        return lock(x, z, owner, Material.BARREL);
    }

    private LockKey lock(int x, int z, UUID owner, Material type) {
        LockKey key = at(x, z);
        materials.put(key, type);
        cache.putLock(key.asId(), new LockEntry(key, type, owner, 0L));
        signManager.spawnSign(key, "unused");
        return key;
    }

    private void unlock(LockKey key) {
        cache.removeLock(key.asId());
        materials.remove(key);
        signManager.removeSign(key.asId());
    }

    private LockKey hopper(int x, int z, UUID owner) {
        LockKey key = at(x, z);
        materials.put(key, Material.HOPPER);
        cache.putHopper(key.asId(), new SecureHopperEntry(key, owner, at(x, z - 1)));
        signManager.markChanged(key);
        return key;
    }

    private void unbindHopper(LockKey key) {
        cache.removeHopper(key.asId());
        signManager.markChanged(key);
    }

    private Chest chestData(Chest.Type connection) {
        Chest chest = mock(Chest.class);
        when(chest.getFacing()).thenReturn(BlockFace.NORTH);
        when(chest.getType()).thenReturn(connection);
        return chest;
    }

    private LockKey doubleChest(int x, int z, UUID owner) {
        return doubleChest(x, z, owner, Material.CHEST, Material.CHEST, Material.CHEST);
    }

    private LockKey doubleChest(int x, int z, UUID owner, Material storedType, Material leftType, Material rightType) {
        LockKey key = at(x, z);
        LockKey partner = at(x + 1, z);
        materials.put(key, leftType);
        materials.put(partner, rightType);
        blockData.put(key, chestData(Chest.Type.LEFT));
        blockData.put(partner, chestData(Chest.Type.RIGHT));
        cache.putLock(key.asId(), new LockEntry(key, storedType, owner, 0L));
        signManager.spawnSign(key, "unused");
        return key;
    }

    private void runTicks() {
        Runnable task;
        while ((task = queuedTasks.poll()) != null) task.run();
    }

    private void loadChunk(int chunkX, int chunkZ) {
        unloadedChunks.remove(new ChunkCoordinates(chunkX, chunkZ));
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getChunk()).thenReturn(chunk);
        signManager.onChunkLoad(event);
    }

    private List<SpawnedText> live() {
        return spawned.stream().filter(text -> text.valid).toList();
    }

    private SpawnedText onlyLive() {
        List<SpawnedText> live = live();
        assertEquals(1, live.size(), "lebende Texte");
        return live.get(0);
    }

    private void assertNoLeftovers() {
        Set<UUID> liveIds = live().stream().map(text -> text.id).collect(Collectors.toSet());
        assertEquals(liveIds, signManager.trackedEntityIds(), "Map und lebende Entities stimmen nicht ueberein");
        for (String groupId : signManager.trackedGroupIds()) assertTrue(signManager.hasLiveSign(groupId), groupId);
    }

    @Test
    @DisplayName("3x3 gesperrte Bloecke: genau ein Text mit dem Besitzernamen ueber der Mitte (vorher neun)")
    void threeByThreeHasOneText() {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) lock(x, z, alice);
        }

        runTicks();

        assertEquals(1, spawned.size());
        SpawnedText text = onlyLive();
        assertEquals("name:Alex", text.text);
        assertEquals(1.5, text.x, 0.0);
        assertEquals(TEXT_Y, text.y, 1e-9);
        assertEquals(1.5, text.z, 0.0);
        assertTrue(signManager.hasLiveSign(at(0, 0).asId()));
        assertEquals(Set.of(at(0, 0).asId()), signManager.trackedGroupIds());
        verify(text.display).setPersistent(false);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Mehrere Aenderungen in einem Tick: ein Task, eine Berechnung, ein Text")
    void severalChangesInOneTickAreOneCalculation() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        hopper(2, 0, alice);
        signManager.removeSign(at(7, 7).asId());
        signManager.refreshOwnerSigns(alice);

        assertEquals(1, queuedTasks.size());
        runTicks();

        verify(scheduler, times(1)).runTask(any(Plugin.class), any(Runnable.class));
        verify(names, times(1)).resolve(alice);
        assertEquals(1, spawned.size());
        assertEquals(1.5, onlyLive().x, 0.0);
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("Hinzufuegen eines Blocks verbindet zwei Gruppen zu einem Text, der zweite wird entfernt")
    void addingBlockJoinsGroups() {
        lock(0, 0, alice);
        lock(2, 0, alice);
        runTicks();
        assertEquals(2, live().size());

        lock(1, 0, alice);
        runTicks();

        assertTrue(spawned.get(0).removed);
        assertTrue(spawned.get(1).removed);
        SpawnedText joined = onlyLive();
        assertEquals(1.5, joined.x, 0.0);
        assertEquals(Set.of(at(0, 0).asId()), signManager.trackedGroupIds());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Entfernen des mittleren Blocks trennt die Gruppe in zwei Texte")
    void removingMiddleBlockSplits() {
        lock(0, 0, alice);
        LockKey middle = lock(1, 0, alice);
        lock(2, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        unlock(middle);
        runTicks();

        assertTrue(before.removed);
        assertEquals(Set.of(0.5, 2.5), live().stream().map(text -> text.x).collect(Collectors.toSet()));
        assertEquals(Set.of(at(0, 0).asId(), at(2, 0).asId()), signManager.trackedGroupIds());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Entfernen eines Randblocks verkleinert die Gruppe und ersetzt den Text")
    void removingEdgeBlockShrinks() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        LockKey edge = lock(2, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        unlock(edge);
        runTicks();

        assertTrue(before.removed);
        assertEquals(1.0, onlyLive().x, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Entfernen der letzten Sperre entfernt den Text und den Eintrag")
    void removingLastLockRemovesText() {
        LockKey key = lock(4, 4, alice);
        runTicks();

        unlock(key);
        runTicks();

        assertTrue(live().isEmpty());
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertFalse(signManager.hasLiveSign(key.asId()));
    }

    @Test
    @DisplayName("Uebertragen aendert den Besitzer und damit die Gruppen")
    void transferChangesGroups() {
        lock(0, 0, alice);
        LockKey transferred = lock(1, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        cache.putLock(transferred.asId(), new LockEntry(transferred, Material.BARREL, bob, 0L));
        signManager.removeSign(transferred.asId());
        signManager.spawnSign(transferred, "Bob");
        runTicks();

        assertTrue(before.removed);
        Map<String, Double> positionsByText = live().stream().collect(Collectors.toMap(text -> text.text, text -> text.x));
        assertEquals(Map.of("name:Alex", 0.5, "name:Bob", 1.5), positionsByText);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Gueltiger Text bleibt bei gleicher Gruppe stehen")
    void validTextStays() {
        lock(0, 0, alice);
        lock(0, 1, alice);
        runTicks();

        signManager.markChanged(at(0, 0));
        signManager.spawnSign(at(0, 1), "Alex");
        runTicks();

        assertEquals(1, spawned.size());
        assertFalse(spawned.get(0).removed);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Ungueltiger Text (Chunk entladen) wird bei der naechsten Berechnung neu erzeugt")
    void invalidTextIsRespawned() {
        lock(0, 0, alice);
        runTicks();
        spawned.get(0).valid = false;
        assertFalse(signManager.hasLiveSign(at(0, 0).asId()));

        signManager.markChanged(at(0, 0));
        runTicks();

        assertEquals(2, spawned.size());
        assertFalse(spawned.get(0).removed);
        assertTrue(signManager.hasLiveSign(at(0, 0).asId()));
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Kein Text bei nicht geladenem Chunk, nachgeholt beim Laden des Chunks")
    void noTextInUnloadedChunkUntilLoad() {
        unloadedChunks.add(new ChunkCoordinates(0, 0));
        lock(5, 5, alice);
        runTicks();

        assertTrue(spawned.isEmpty());
        assertEquals(Set.of(at(5, 5).asId()), signManager.trackedGroupIds());
        assertFalse(signManager.hasLiveSign(at(5, 5).asId()));

        loadChunk(0, 0);
        runTicks();

        assertEquals(1, spawned.size());
        assertTrue(signManager.hasLiveSign(at(5, 5).asId()));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Doppelkiste an der Chunk-Grenze: Text im Nachbarchunk ohne eigene Sperre kommt beim Laden dieses Chunks")
    void textInChunkWithoutMembersAppearsOnLoad() {
        unloadedChunks.add(new ChunkCoordinates(1, 0));
        doubleChest(15, 5, alice);
        runTicks();
        assertTrue(spawned.isEmpty());

        loadChunk(1, 0);
        runTicks();

        SpawnedText text = onlyLive();
        assertEquals(16.0, text.x, 0.0);
        assertEquals(5.5, text.z, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("onChunkLoad markiert nur die Gruppen des Chunks und ruft getAllCachedLocks nicht auf")
    void chunkLoadOnlyMarksGroupsOfChunk() {
        lock(5, 5, alice);
        lock(37, 5, alice);
        runTicks();
        spawned.forEach(text -> text.valid = false);
        clearInvocations(locks);

        loadChunk(0, 0);
        runTicks();

        assertEquals(3, spawned.size());
        assertEquals(5.5, onlyLive().x, 0.0);
        assertFalse(signManager.hasLiveSign(at(37, 5).asId()));
        verify(locks, never()).getAllCachedLocks();
        verify(locks, never()).getAllCachedHoppers();
        verify(locks, never()).getCachedLocksInChunk(worldUuid, 2, 0);
    }

    @Test
    @DisplayName("onChunkLoad markiert auch Secure Hopper aus dem Index des Chunks")
    void chunkLoadMarksSecureHoppersOfChunk() {
        lock(13, 5, alice);
        lock(14, 5, alice);
        hopper(15, 5, alice);
        hopper(16, 5, alice);
        runTicks();
        SpawnedText before = onlyLive();
        assertEquals(15.0, before.x, 0.0);
        before.valid = false;
        clearInvocations(locks);

        loadChunk(1, 0);
        runTicks();

        assertEquals(15.0, onlyLive().x, 0.0);
        verify(locks, atLeastOnce()).getCachedHoppersInChunk(worldUuid, 1, 0);
        verify(locks, never()).getAllCachedHoppers();
        assertNoLeftovers();
    }

    @Test
    @DisplayName("onChunkLoad entfernt weiter verwaiste Sperren und erzeugt fuer sie keinen Text")
    void chunkLoadRemovesOrphanLock() {
        LockKey orphan = at(5, 5);
        cache.putLock(orphan.asId(), new LockEntry(orphan, Material.CHEST, alice, 0L));

        loadChunk(0, 0);
        runTicks();

        verify(locks).forceRemoveLockAt(orphan);
        assertTrue(spawned.isEmpty());
    }

    @Test
    @DisplayName("Ausgeschaltete Namensanzeige: Markieren, Laden, Namensaenderung und spawnAllSigns tun nichts")
    void disabledDisplayDoesNothing() {
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        lock(0, 0, alice);
        hopper(1, 0, alice);
        signManager.removeSign(at(0, 0).asId());
        signManager.refreshOwnerSigns(alice);
        signManager.spawnAllSigns();
        loadChunk(0, 0);

        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        assertTrue(spawned.isEmpty());
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("Abschalten vor der Verarbeitung: ausstehende Aenderungen werden verworfen")
    void disablingBeforeProcessingDropsChanges() {
        lock(0, 0, alice);
        when(ownerNameDisplay.isEnabled()).thenReturn(false);

        runTicks();

        assertTrue(spawned.isEmpty());
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("refreshOwnerSigns ersetzt alle Texte des Besitzers mit dem neuen Namen, andere bleiben")
    void refreshOwnerSignsReplacesNames() {
        lock(0, 0, alice);
        lock(5, 5, alice);
        lock(10, 10, bob);
        runTicks();
        SpawnedText bobText = live().stream().filter(text -> text.text.equals("name:Bob")).findFirst().orElseThrow();
        when(names.resolve(alice)).thenReturn("Alexander");

        signManager.refreshOwnerSigns(alice);
        runTicks();

        assertEquals(5, spawned.size());
        assertFalse(bobText.removed);
        assertEquals(List.of("name:Alexander", "name:Alexander", "name:Bob"),
                live().stream().map(text -> text.text).sorted().toList());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("removeAllSigns entfernt alle Texte und leert die Zuordnungen")
    void removeAllSignsRemovesEverything() {
        lock(0, 0, alice);
        lock(5, 5, alice);
        doubleChest(10, 10, bob);
        runTicks();
        assertEquals(3, live().size());

        signManager.removeAllSigns();

        assertTrue(live().isEmpty());
        assertTrue(spawned.stream().allMatch(text -> text.removed));
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.trackedEntityIds().isEmpty());
    }

    @Test
    @DisplayName("spawnAllSigns markiert die Sperren der geladenen Chunks, die Texte entstehen zu 50 pro Tick")
    void spawnAllSignsInBatchesOfFifty() {
        for (int i = 0; i < 120; i++) {
            LockKey key = at(i * 2, 0);
            cache.putLock(key.asId(), new LockEntry(key, Material.BARREL, alice, 0L));
        }

        signManager.spawnAllSigns();

        assertTrue(laterDelays.isEmpty());
        assertEquals(1, queuedTasks.size());
        queuedTasks.poll().run();
        assertEquals(50, live().size());
        queuedTasks.poll().run();
        assertEquals(100, live().size());
        queuedTasks.poll().run();
        assertEquals(120, live().size());
        assertTrue(queuedTasks.isEmpty());
        verify(locks, never()).getAllCachedLocks();
        assertNoLeftovers();
    }

    @Test
    @DisplayName("spawnAllSigns: ein Text pro Gruppe, vorhandene gueltige Texte bleiben")
    void spawnAllSignsOneTextPerGroup() {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) lock(x, z, alice);
        }
        runTicks();
        SpawnedText existing = onlyLive();
        doubleChest(20, 20, alice);

        signManager.spawnAllSigns();
        runTicks();

        assertTrue(laterDelays.isEmpty());
        assertEquals(2, live().size());
        assertFalse(existing.removed);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Secure Hopper neben einer Sperre: Binden verschiebt den Text, Entfernen setzt ihn zurueck, allein kein Text")
    void secureHopperJoinsAndLeavesGroup() {
        lock(0, 0, alice);
        runTicks();
        assertEquals(0.5, onlyLive().x, 0.0);

        LockKey bound = hopper(1, 0, alice);
        runTicks();
        assertEquals(1.0, onlyLive().x, 0.0);

        unbindHopper(bound);
        runTicks();
        assertEquals(0.5, onlyLive().x, 0.0);

        hopper(10, 10, alice);
        runTicks();
        assertEquals(1, live().size());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Doppelkiste: Text genau zwischen den Haelften")
    void doubleChestIsCentered() {
        doubleChest(0, 0, alice);
        runTicks();

        SpawnedText text = onlyLive();
        assertEquals(1.0, text.x, 0.0);
        assertEquals(0.5, text.z, 0.0);
    }

    @Test
    @DisplayName("Anbauen einer zweiten Kiste an eine gesperrte Kiste setzt den Text in die Mitte")
    void placingSecondChestCentersText() {
        LockKey stored = at(1, 0);
        materials.put(stored, Material.CHEST);
        blockData.put(stored, chestData(Chest.Type.SINGLE));
        cache.putLock(stored.asId(), new LockEntry(stored, Material.CHEST, alice, 0L));
        signManager.spawnSign(stored, "Alex");
        runTicks();
        SpawnedText before = onlyLive();
        assertEquals(1.5, before.x, 0.0);

        LockKey placed = at(0, 0);
        materials.put(placed, Material.CHEST);
        blockData.put(placed, chestData(Chest.Type.LEFT));
        blockData.put(stored, chestData(Chest.Type.RIGHT));
        Block placedBlock = blockAt(placed);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        signManager.onChestPlace(event);
        runTicks();

        assertTrue(before.removed);
        assertEquals(1.0, onlyLive().x, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Platzieren anderer Bloecke markiert nichts")
    void placingOtherBlocksMarksNothing() {
        LockKey placed = at(0, 0);
        materials.put(placed, Material.STONE);
        Block placedBlock = blockAt(placed);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);

        signManager.onChestPlace(event);

        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(queuedTasks.isEmpty());
    }

    @Test
    @DisplayName("Keine Entity-Reste: nach einer Folge von Aenderungen stimmen Map und Entities, am Ende ist alles leer")
    void noEntityLeftovers() {
        List<LockKey> all = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) all.add(lock(x, z, alice));
        }
        runTicks();
        assertNoLeftovers();

        all.add(lock(3, 1, alice));
        all.add(lock(6, 6, bob));
        runTicks();
        assertNoLeftovers();

        unlock(at(1, 1));
        unlock(at(1, 0));
        unlock(at(1, 2));
        all.removeAll(List.of(at(1, 1), at(1, 0), at(1, 2)));
        runTicks();
        assertNoLeftovers();

        LockKey hopperKey = hopper(7, 6, bob);
        cache.putLock(at(0, 0).asId(), new LockEntry(at(0, 0), Material.BARREL, bob, 0L));
        signManager.removeSign(at(0, 0).asId());
        signManager.spawnSign(at(0, 0), "Bob");
        when(names.resolve(bob)).thenReturn("Robert");
        signManager.refreshOwnerSigns(bob);
        runTicks();
        assertNoLeftovers();

        for (LockKey key : all) unlock(key);
        unbindHopper(hopperKey);
        runTicks();

        assertNoLeftovers();
        assertTrue(live().isEmpty());
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.trackedEntityIds().isEmpty());
    }

    @Test
    @DisplayName("Markieren aus mehreren Threads: keine Ausnahme, ein Task, alle Markierungen verarbeitet")
    void markingFromManyThreads() throws InterruptedException {
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 6; z++) {
                LockKey key = at(x, z);
                materials.put(key, Material.BARREL);
                cache.putLock(key.asId(), new LockEntry(key, Material.BARREL, alice, 0L));
            }
        }
        signManager.markChanged(at(0, 0));

        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int thread = 0; thread < 8; thread++) {
            final int offset = thread;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int round = 0; round < 200; round++) {
                        signManager.markChanged(at((round + offset) % 10, round % 6));
                        signManager.removeSign(at(100 + offset, round).asId());
                        signManager.spawnSign(at(round % 10, (round + offset) % 6), "Alex");
                        signManager.refreshOwnerSigns(alice);
                    }
                } catch (Throwable failure) {
                    failures.add(failure);
                }
            });
            threads.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : threads) worker.join();

        assertTrue(failures.isEmpty(), () -> "Ausnahmen: " + failures);
        verify(scheduler, times(1)).runTask(any(Plugin.class), any(Runnable.class));
        runTicks();

        assertEquals(0, signManager.pendingChangeCount());
        assertEquals(Set.of(at(0, 0).asId()), signManager.trackedGroupIds());
        SpawnedText text = onlyLive();
        assertEquals(5.0, text.x, 0.0);
        assertEquals(3.0, text.z, 0.0);
        assertNoLeftovers();
    }

    private void unloadChunk(int chunkX, int chunkZ) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        ChunkUnloadEvent event = mock(ChunkUnloadEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getChunk()).thenReturn(chunk);
        signManager.onChunkUnload(event);
        unloadedChunks.add(new ChunkCoordinates(chunkX, chunkZ));
        for (SpawnedText text : spawned) {
            if ((int) Math.floor(text.x) >> 4 == chunkX && (int) Math.floor(text.z) >> 4 == chunkZ) text.valid = false;
        }
    }

    private void lockSilently(int x, int z, UUID owner) {
        LockKey key = at(x, z);
        materials.put(key, Material.BARREL);
        cache.putLock(key.asId(), new LockEntry(key, Material.BARREL, owner, 0L));
    }

    @Test
    @DisplayName("120 markierte Felder: 50, 100, 120 pro Tick, immer nur ein geplanter Task, keine Markierung geht verloren")
    void changesInBatchesOfFifty() {
        for (int i = 0; i < 120; i++) lock(i * 2, 0, alice);
        assertEquals(120, signManager.pendingChangeCount());
        assertEquals(1, queuedTasks.size());

        queuedTasks.poll().run();
        assertEquals(50, signManager.trackedGroupIds().size());
        assertEquals(70, signManager.pendingChangeCount());
        assertEquals(1, queuedTasks.size());

        queuedTasks.poll().run();
        assertEquals(100, signManager.trackedGroupIds().size());
        assertEquals(20, signManager.pendingChangeCount());
        assertEquals(1, queuedTasks.size());

        queuedTasks.poll().run();
        assertEquals(120, signManager.trackedGroupIds().size());
        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(queuedTasks.isEmpty());
        assertEquals(120, live().size());
        verify(scheduler, times(3)).runTask(any(Plugin.class), any(Runnable.class));
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Markierungen zwischen zwei Teilen planen keinen zweiten Task und werden im naechsten Teil verarbeitet")
    void markingBetweenBatchesDoesNotScheduleTwice() {
        for (int i = 0; i < 60; i++) lock(i * 2, 0, alice);
        queuedTasks.poll().run();
        assertEquals(10, signManager.pendingChangeCount());

        lock(500, 0, bob);
        signManager.refreshOwnerSigns(alice);

        assertEquals(1, queuedTasks.size());
        runTicks();
        assertEquals(0, signManager.pendingChangeCount());
        assertEquals(61, signManager.trackedGroupIds().size());
        assertEquals(61, live().size());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Eine Gruppe ueber mehrere Teile: die zweite Berechnung aendert nichts, ein Text")
    void recalculationAcrossBatchesIsIdempotent() {
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 6; z++) {
                lockSilently(x, z, alice);
                signManager.markChanged(at(x, z));
            }
        }
        assertEquals(60, signManager.pendingChangeCount());

        queuedTasks.poll().run();
        SpawnedText text = onlyLive();
        assertEquals(10, signManager.pendingChangeCount());
        runTicks();

        assertSame(text, onlyLive());
        assertEquals(1, spawned.size());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("spawnAllSigns markiert nur Sperren in geladenen Chunks, die anderen entstehen beim Laden")
    void spawnAllSignsOnlyLoadedChunks() {
        lockSilently(0, 0, alice);
        lockSilently(40, 0, bob);
        unloadedChunks.add(new ChunkCoordinates(2, 0));

        signManager.spawnAllSigns();
        assertEquals(1, signManager.pendingChangeCount());
        runTicks();

        assertEquals(Set.of(at(0, 0).asId()), signManager.trackedGroupIds());
        assertEquals("name:Alex", onlyLive().text);
        verify(locks, never()).getAllCachedLocks();

        loadChunk(2, 0);
        runTicks();

        assertEquals(Set.of(at(0, 0).asId(), at(40, 0).asId()), signManager.trackedGroupIds());
        assertEquals(2, live().size());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("spawnAllSigns: Secure Hopper in geladenem Chunk erzeugt den Text seiner Gruppe, die Sperre liegt im nicht geladenen Chunk")
    void spawnAllSignsMarksSecureHoppers() {
        lockSilently(15, 0, alice);
        LockKey hopperKey = at(16, 0);
        materials.put(hopperKey, Material.HOPPER);
        cache.putHopper(hopperKey.asId(), new SecureHopperEntry(hopperKey, alice, at(15, 0)));
        unloadedChunks.add(new ChunkCoordinates(0, 0));

        signManager.spawnAllSigns();
        runTicks();

        assertEquals(16.0, onlyLive().x, 0.0);
        assertEquals(Set.of(at(15, 0).asId()), signManager.trackedGroupIds());
    }

    @Test
    @DisplayName("Plugin deaktiviert: Markieren, Besitzer, spawnAllSigns, refreshAllGroups, Laden und Kontrolle planen nichts")
    void disabledPluginSchedulesNothing() {
        lock(0, 0, alice);
        runTicks();
        clearInvocations(scheduler);
        when(plugin.isEnabled()).thenReturn(false);

        lock(5, 5, alice);
        hopper(6, 5, alice);
        signManager.removeSign(at(0, 0).asId());
        signManager.refreshOwnerSigns(alice);
        signManager.spawnAllSigns();
        signManager.refreshAllGroups();
        loadChunk(0, 0);
        signManager.startCoverCheck();

        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        assertTrue(queuedTasks.isEmpty());
    }

    @Test
    @DisplayName("Plugin waehrend der Verarbeitung deaktiviert: der Rest wird nicht neu geplant")
    void disablingDuringProcessingStopsScheduling() {
        for (int i = 0; i < 60; i++) lock(i * 2, 0, alice);
        when(plugin.isEnabled()).thenReturn(false);

        queuedTasks.poll().run();

        assertTrue(queuedTasks.isEmpty());
        assertEquals(10, signManager.pendingChangeCount());
        verify(scheduler, times(1)).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    @DisplayName("Chunk entladen: Registrierungen weg, Entity nicht entfernt, beim Laden kommt der Text zurueck")
    void chunkUnloadDropsRegistrationsAndLoadRestores() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        unloadChunk(0, 0);

        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.trackedFields().isEmpty());
        assertTrue(signManager.trackedTextFields().isEmpty());
        assertEquals(0, signManager.trackedTextChunkCount());
        assertFalse(before.removed, "Die Entity entlaedt der Server, das Plugin entfernt sie nicht");

        loadChunk(0, 0);
        runTicks();

        SpawnedText after = onlyLive();
        assertNotSame(before, after);
        assertEquals(1.0, after.x, 0.0);
        assertEquals(Set.of(at(0, 0).asId()), signManager.trackedGroupIds());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Viele Chunks laden und entladen: der Speicher waechst nicht mit der Zahl der je geladenen Gruppen")
    void chunkUnloadKeepsMapsSmall() {
        for (int chunk = 0; chunk < 20; chunk++) lock(chunk * 16 + 3, 3, alice);
        runTicks();
        assertEquals(20, signManager.trackedGroupIds().size());

        for (int chunk = 0; chunk < 20; chunk++) unloadChunk(chunk, 0);

        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.trackedFields().isEmpty());
        assertTrue(signManager.trackedTextFields().isEmpty());
        assertEquals(0, signManager.trackedTextChunkCount());
    }

    @Test
    @DisplayName("Gruppe ueber zwei Chunks: entladener Textchunk mit geladenen Mitgliedern bleibt registriert und kommt beim Laden zurueck")
    void groupAcrossChunksKeepsRegistrationWhileMembersLoaded() {
        for (int x = 14; x <= 17; x++) lock(x, 0, alice);
        runTicks();
        assertEquals(16.0, onlyLive().x, 0.0);

        unloadChunk(1, 0);
        assertEquals(Set.of(at(14, 0).asId()), signManager.trackedGroupIds());
        assertTrue(live().isEmpty());

        loadChunk(1, 0);
        runTicks();
        assertEquals(16.0, onlyLive().x, 0.0);

        unloadChunk(0, 0);
        assertEquals(Set.of(at(14, 0).asId()), signManager.trackedGroupIds());
        unloadChunk(1, 0);
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.trackedFields().isEmpty());
    }

    @Test
    @DisplayName("Randfall: Text in einem Chunk ohne Mitglieder, der Text kommt zurueck, sobald ein Mitgliedschunk geladen ist")
    void textChunkWithoutMembersEdgeCase() {
        for (int x = 0; x <= 16; x++) lock(x, 15, alice);
        for (int z = 16; z <= 22; z++) lock(16, z, alice);
        runTicks();
        SpawnedText text = onlyLive();
        assertEquals(0, (int) Math.floor(text.x) >> 4);
        assertEquals(1, (int) Math.floor(text.z) >> 4);

        unloadChunk(0, 1);
        assertEquals(1, signManager.trackedGroupIds().size());
        loadChunk(0, 1);
        runTicks();
        assertEquals(1, live().size());

        unloadChunk(0, 1);
        unloadChunk(0, 0);
        unloadChunk(1, 0);
        assertEquals(1, signManager.trackedGroupIds().size());
        unloadChunk(1, 1);
        assertTrue(signManager.trackedGroupIds().isEmpty());

        loadChunk(0, 1);
        runTicks();
        assertTrue(live().isEmpty());

        loadChunk(1, 1);
        runTicks();
        assertEquals(1, live().size());
        assertNoLeftovers();
    }

    @Test
    @DisplayName("onChunkLoad: ist der Chunk im naechsten Tick nicht mehr geladen, wird kein Block gelesen und nichts markiert")
    void chunkLoadSkipsUnloadedChunk() {
        lockSilently(5, 5, alice);
        materials.remove(at(5, 5));
        loadChunk(0, 0);
        unloadedChunks.add(new ChunkCoordinates(0, 0));
        clearInvocations(world);

        runTicks();

        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(locks, never()).forceRemoveLockAt(any());
        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(spawned.isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Neue Typen: ein einzelner gesperrter Block bekommt einen Text ueber seiner Mitte")
    void newTypeGetsText(Material type) {
        lock(0, 0, alice, type);

        runTicks();

        SpawnedText text = onlyLive();
        assertEquals("name:Alex", text.text);
        assertEquals(0.5, text.x, 0.0);
        assertEquals(TEXT_Y, text.y, 1e-9);
        assertEquals(0.5, text.z, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Neue Typen: Ofen, Schmelzofen, Raeucherofen, Braustand und Secure Hopper in einer Reihe sind eine Gruppe")
    void rowOfFurnacesIsOneGroup() {
        lock(0, 0, alice, Material.FURNACE);
        lock(1, 0, alice, Material.BLAST_FURNACE);
        lock(2, 0, alice, Material.SMOKER);
        lock(3, 0, alice, Material.BREWING_STAND);
        hopper(4, 0, alice);
        lock(6, 0, alice, Material.CRAFTER);

        runTicks();

        assertEquals(2, live().size());
        SpawnedText row = live().stream().filter(text -> text.x < 3.0).findFirst().orElseThrow();
        assertEquals(2.5, row.x, 0.0);
        assertEquals(0.5, row.z, 0.0);
        SpawnedText crafter = live().stream().filter(text -> text.x > 6.0).findFirst().orElseThrow();
        assertEquals(6.5, crafter.x, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("onChunkLoad behandelt Sperren an neuen Typen nicht als verwaist")
    void chunkLoadKeepsNewTypes() {
        List<Material> types = List.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.DROPPER,
                Material.DISPENSER, Material.BREWING_STAND, Material.CRAFTER);
        for (int index = 0; index < types.size(); index++) {
            LockKey key = at(index * 2, 5);
            materials.put(key, types.get(index));
            cache.putLock(key.asId(), new LockEntry(key, types.get(index), alice, 0L));
        }

        loadChunk(0, 0);
        runTicks();

        verify(locks, never()).forceRemoveLockAt(any());
        assertEquals(types.size(), live().size());
        assertNoLeftovers();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Kupferkisten: Doppelkiste bekommt einen Text genau zwischen den Haelften")
    void copperDoubleChestIsCentered(Material type) {
        doubleChest(0, 0, alice, type, type, type);
        runTicks();

        SpawnedText text = onlyLive();
        assertEquals("name:Alex", text.text);
        assertEquals(1.0, text.x, 0.0);
        assertEquals(0.5, text.z, 0.0);
        assertNoLeftovers();
    }

    @ParameterizedTest(name = "gesperrt als COPPER_CHEST, jetzt {0}")
    @EnumSource(value = Material.class, names = {"EXPOSED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST", "WAXED_COPPER_CHEST"})
    @DisplayName("Kupferkisten nach Oxidation oder Wachsen: Text bleibt mittig, Laden und Kontrolle entfernen die Sperre nicht")
    void oxidizedCopperDoubleChestKeepsTextAndLock(Material current) {
        doubleChest(0, 0, alice, Material.COPPER_CHEST, current, Material.COPPER_CHEST);
        runTicks();
        assertEquals(1.0, onlyLive().x, 0.0);

        materials.put(at(1, 0), current);
        loadChunk(0, 0);
        runTicks();
        signManager.validateAndCleanOrphans();

        verify(locks, never()).forceRemoveLockAt(any());
        SpawnedText text = onlyLive();
        assertEquals(1.0, text.x, 0.0);
        assertEquals(0.5, text.z, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Kupferkisten: Anbauen an eine gesperrte Kupferkiste anderer Stufe setzt den Text in die Mitte")
    void placingSecondCopperChestCentersText() {
        LockKey stored = at(1, 0);
        materials.put(stored, Material.WEATHERED_COPPER_CHEST);
        blockData.put(stored, chestData(Chest.Type.SINGLE));
        cache.putLock(stored.asId(), new LockEntry(stored, Material.WEATHERED_COPPER_CHEST, alice, 0L));
        signManager.spawnSign(stored, "Alex");
        runTicks();
        SpawnedText before = onlyLive();

        LockKey placed = at(0, 0);
        materials.put(placed, Material.COPPER_CHEST);
        blockData.put(placed, chestData(Chest.Type.LEFT));
        blockData.put(stored, chestData(Chest.Type.RIGHT));
        Block placedBlock = blockAt(placed);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placedBlock);
        signManager.onChestPlace(event);
        runTicks();

        assertTrue(before.removed);
        assertEquals(1.0, onlyLive().x, 0.0);
        assertNoLeftovers();
    }

    @Test
    @DisplayName("Mischfall: der Text richtet sich wie bisher nur nach dem Blockzustand der gesperrten Haelfte, nicht nach dem Material des Nachbarn")
    void copperChestNextToChestFollowsOwnBlockState() {
        doubleChest(0, 0, alice, Material.COPPER_CHEST, Material.COPPER_CHEST, Material.CHEST);
        runTicks();

        SpawnedText text = onlyLive();
        assertEquals(1.0, text.x, 0.0);
        assertEquals(0.5, text.z, 0.0);
        assertNoLeftovers();
    }
}
