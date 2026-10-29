package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockCacheManager;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SignManager: Verstecken bei Abdeckung, Schalter und langsame Kontrolle")
class SignManagerVisibilityTest {

    private static final int Y = 64;
    private static final int TEXT_FIELD_Y = Y + 1;
    private static final Set<Material> SOLID_ON_SERVER = Set.of(Material.STONE, Material.CHEST,
            Material.TRAPPED_CHEST, Material.BARREL, Material.GLASS, Material.SMOOTH_STONE_SLAB);

    private record ChunkCoordinates(int x, int z) {}

    private record LaterTask(Runnable task, long delay, BukkitTask handle) {}

    private static final class SpawnedText {
        final UUID id = UUID.randomUUID();
        final double x;
        final double y;
        final double z;
        TextDisplay display;
        String text;
        boolean valid = true;
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

    private final AtomicBoolean displayEnabled = new AtomicBoolean(true);
    private final AtomicBoolean hideWhenCovered = new AtomicBoolean(true);
    private final Map<UUID, Boolean> showOwnerName = new ConcurrentHashMap<>();

    private MockedStatic<Bukkit> bukkit;
    private ChestSecurePlugin plugin;
    private LockCacheManager cache;
    private BukkitScheduler scheduler;
    private BukkitTask timerHandle;
    private World world;
    private SignManager signManager;

    private final Deque<Runnable> queuedTasks = new ArrayDeque<>();
    private final List<LaterTask> laterTasks = new ArrayList<>();
    private final List<Runnable> timerTasks = new ArrayList<>();
    private final Set<ChunkCoordinates> unloadedChunks = ConcurrentHashMap.newKeySet();
    private final Map<LockKey, Material> materials = new HashMap<>();
    private final Map<LockKey, BlockData> blockData = new HashMap<>();
    private final Map<LockKey, Block> blocks = new HashMap<>();
    private final List<SpawnedText> spawned = new ArrayList<>();
    private final Map<UUID, SpawnedText> spawnedById = new HashMap<>();
    private final AtomicInteger textFieldReads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);

        plugin = mock(ChestSecurePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        MessageService messages = mock(MessageService.class);
        when(messages.get(eq("sign-owner-display"), any(Object[].class)))
                .thenAnswer(invocation -> "name:" + invocation.getArgument(1));
        when(plugin.msg()).thenReturn(messages);
        OwnerNameDisplaySetting ownerNameDisplay = mock(OwnerNameDisplaySetting.class);
        when(ownerNameDisplay.isEnabled()).thenAnswer(invocation -> displayEnabled.get());
        when(ownerNameDisplay.isHideWhenCoveredEnabled()).thenAnswer(invocation -> hideWhenCovered.get());
        when(plugin.ownerNameDisplay()).thenReturn(ownerNameDisplay);
        PlayerNameService names = mock(PlayerNameService.class);
        when(names.resolve(alice)).thenReturn("Alex");
        when(names.resolve(bob)).thenReturn("Bob");
        when(plugin.names()).thenReturn(names);

        cache = new LockCacheManager();
        LockService locks = mock(LockService.class);
        when(locks.getCachedLocksInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.locksInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(locks.getCachedHoppersInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.hoppersInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(locks.getAllCachedLocks()).thenAnswer(invocation -> List.copyOf(cache.lockValues()));
        when(locks.getShowOwnerName(any())).thenAnswer(invocation ->
                showOwnerName.getOrDefault(invocation.getArgument(0, UUID.class), true));
        when(plugin.locks()).thenReturn(locks);

        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            queuedTasks.add(invocation.getArgument(1, Runnable.class));
            return null;
        });
        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            BukkitTask handle = mock(BukkitTask.class);
            laterTasks.add(new LaterTask(invocation.getArgument(1, Runnable.class),
                    invocation.getArgument(2, Long.class), handle));
            return handle;
        });
        timerHandle = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    timerTasks.add(invocation.getArgument(1, Runnable.class));
                    return timerHandle;
                });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        world = mock(World.class);
        when(world.getUID()).thenReturn(worldUuid);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(invocation -> !unloadedChunks.contains(
                new ChunkCoordinates(invocation.getArgument(0), invocation.getArgument(1))));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            if (invocation.getArgument(1, Integer.class) == TEXT_FIELD_Y) textFieldReads.incrementAndGet();
            return blockAt(new LockKey(worldUuid, invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2)));
        });
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

        signManager = new SignManager(plugin, SOLID_ON_SERVER::contains);
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

    private LockKey above(int x, int z) {
        return new LockKey(worldUuid, x, TEXT_FIELD_Y, z);
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

    private LockKey lockAt(LockKey key, UUID owner) {
        materials.put(key, Material.BARREL);
        cache.putLock(key.asId(), new LockEntry(key, Material.BARREL, owner, 0L));
        signManager.spawnSign(key, "unused");
        return key;
    }

    private LockKey lock(int x, int z, UUID owner) {
        return lockAt(at(x, z), owner);
    }

    private void unlock(LockKey key) {
        cache.removeLock(key.asId());
        materials.remove(key);
        signManager.removeSign(key.asId());
    }

    private void cover(int x, int z) {
        materials.put(above(x, z), Material.STONE);
    }

    private void uncover(int x, int z) {
        materials.remove(above(x, z));
    }

    private void place(LockKey key, Material material) {
        materials.put(key, material);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        Block placed = blockAt(key);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getBlock()).thenReturn(placed);
        signManager.onChestPlace(event);
        signManager.onTextFieldPlace(event);
    }

    private void breakBlock(LockKey key) {
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        Block broken = blockAt(key);
        when(event.getBlock()).thenReturn(broken);
        signManager.onTextFieldBreak(event);
        materials.remove(key);
    }

    private Chest chestData(Chest.Type connection) {
        Chest chest = mock(Chest.class);
        when(chest.getFacing()).thenReturn(BlockFace.NORTH);
        when(chest.getType()).thenReturn(connection);
        return chest;
    }

    private LockKey doubleChest(int x, int z, UUID owner) {
        LockKey key = at(x, z);
        LockKey partner = at(x + 1, z);
        materials.put(key, Material.CHEST);
        materials.put(partner, Material.CHEST);
        blockData.put(key, chestData(Chest.Type.LEFT));
        blockData.put(partner, chestData(Chest.Type.RIGHT));
        cache.putLock(key.asId(), new LockEntry(key, Material.CHEST, owner, 0L));
        signManager.spawnSign(key, "unused");
        return key;
    }

    private void runTicks() {
        Runnable task;
        while ((task = queuedTasks.poll()) != null) task.run();
    }

    private LaterTask runNextLaterTask() {
        LaterTask next = laterTasks.remove(0);
        next.task().run();
        return next;
    }

    private void runAllLaterTasks() {
        while (!laterTasks.isEmpty()) runNextLaterTask();
        runTicks();
    }

    private void coverCheckRound() {
        if (timerTasks.isEmpty()) signManager.startCoverCheck();
        timerTasks.get(0).run();
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

    private void assertConsistent() {
        Set<UUID> liveIds = live().stream().map(text -> text.id).collect(Collectors.toSet());
        assertEquals(liveIds, signManager.trackedEntityIds(), "Map und lebende Entities stimmen nicht ueberein");
        for (String groupId : signManager.trackedGroupIds()) {
            assertEquals(!signManager.hiddenGroupIds().contains(groupId), signManager.hasLiveSign(groupId), groupId);
        }
    }

    @Test
    @DisplayName("Ueberdeckte Gruppe: keine Entity, als versteckt vermerkt, weiter gefuehrt")
    void coveredGroupIsHiddenWithoutEntity() {
        LockKey key = lock(0, 0, alice);
        cover(0, 0);

        runTicks();

        assertTrue(spawned.isEmpty());
        assertEquals(Set.of(key.asId()), signManager.trackedGroupIds());
        assertEquals(Set.of(key.asId()), signManager.hiddenGroupIds());
        assertTrue(signManager.trackedEntityIds().isEmpty());
        assertEquals(Set.of(above(0, 0)), signManager.trackedTextFields());
        assertConsistent();
    }

    @Test
    @DisplayName("Abbauen des Blocks im Textfeld (BlockBreakEvent) laesst den Text erscheinen")
    void breakingCoverShowsText() {
        lock(0, 0, alice);
        cover(0, 0);
        runTicks();

        breakBlock(above(0, 0));
        runTicks();

        SpawnedText text = onlyLive();
        assertEquals("name:Alex", text.text);
        assertEquals(0.5, text.x, 0.0);
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertConsistent();
    }

    @Test
    @DisplayName("Platzieren eines festen Blocks im Textfeld (BlockPlaceEvent) entfernt den Text")
    void placingCoverRemovesText() {
        LockKey key = lock(0, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        place(above(0, 0), Material.STONE);
        runTicks();

        assertTrue(before.removed);
        assertTrue(live().isEmpty());
        assertEquals(Set.of(key.asId()), signManager.hiddenGroupIds());
        assertConsistent();
    }

    @Test
    @DisplayName("Fackel im Textfeld: Gruppe wird geprueft, der vorhandene Text bleibt stehen")
    void placingTorchKeepsText() {
        lock(0, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        place(above(0, 0), Material.TORCH);
        assertEquals(1, signManager.pendingChangeCount());
        runTicks();

        assertFalse(before.removed);
        assertEquals(1, spawned.size());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
    }

    @Test
    @DisplayName("Ereignisse an anderen Feldern als dem Textfeld markieren nichts")
    void eventsAtOtherFieldsDoNothing() {
        lock(0, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        place(above(1, 0), Material.STONE);
        place(new LockKey(worldUuid, 0, TEXT_FIELD_Y + 1, 0), Material.STONE);
        place(at(5, 5), Material.STONE);
        breakBlock(above(7, 7));
        breakBlock(new LockKey(worldUuid, 0, Y - 1, 0));

        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(queuedTasks.isEmpty());
        assertFalse(before.removed);
    }

    @Test
    @DisplayName("Admin-Schalter aus: Ereignisse im Textfeld markieren nichts, ueberdeckte Namen bleiben sichtbar")
    void eventsWithHideSwitchOffDoNothing() {
        hideWhenCovered.set(false);
        lock(0, 0, alice);
        cover(0, 0);
        runTicks();
        SpawnedText before = onlyLive();

        breakBlock(above(0, 0));
        place(above(0, 0), Material.STONE);

        assertEquals(0, signManager.pendingChangeCount());
        assertFalse(before.removed);
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertEquals(0, textFieldReads.get());
    }

    @Test
    @DisplayName("Langsame Kontrolle: neuer Block ohne Ereignis (Kolben, Sand) entfernt den Text")
    void coverCheckFindsNewCover() {
        lock(0, 0, alice);
        runTicks();
        SpawnedText before = onlyLive();

        cover(0, 0);
        coverCheckRound();
        assertEquals(1, signManager.pendingChangeCount());
        runTicks();

        assertTrue(before.removed);
        assertEquals(Set.of(at(0, 0).asId()), signManager.hiddenGroupIds());
        assertConsistent();
    }

    @Test
    @DisplayName("Langsame Kontrolle: entfernter Block ohne Ereignis (Wasser, Explosion) laesst den Text erscheinen")
    void coverCheckFindsRemovedCover() {
        lock(0, 0, alice);
        cover(0, 0);
        runTicks();
        assertTrue(spawned.isEmpty());

        uncover(0, 0);
        coverCheckRound();
        runTicks();

        assertEquals(0.5, onlyLive().x, 0.0);
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertConsistent();
    }

    @Test
    @DisplayName("Langsame Kontrolle: unveraenderter Zustand markiert nichts, ein Blockzugriff pro Gruppe")
    void coverCheckMarksOnlyChanges() {
        lock(0, 0, alice);
        lock(5, 5, bob);
        cover(5, 5);
        runTicks();
        textFieldReads.set(0);

        coverCheckRound();

        assertEquals(2, textFieldReads.get());
        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(queuedTasks.isEmpty());
        assertTrue(laterTasks.isEmpty());
    }

    @Test
    @DisplayName("Langsame Kontrolle alle 100 Ticks, verteilt auf 50 Gruppen pro Tick")
    void coverCheckInBatchesOfFifty() {
        for (int index = 0; index < 120; index++) lock(index * 2, 0, alice);
        runTicks();
        assertEquals(120, live().size());
        textFieldReads.set(0);

        signManager.startCoverCheck();
        verify(scheduler).runTaskTimer(plugin, timerTasks.get(0), 100L, 100L);
        timerTasks.get(0).run();

        assertEquals(50, textFieldReads.get());
        assertEquals(70, signManager.pendingCoverCheckCount());
        assertEquals(1, laterTasks.size());
        assertEquals(1L, laterTasks.get(0).delay());

        runNextLaterTask();
        assertEquals(100, textFieldReads.get());
        assertEquals(20, signManager.pendingCoverCheckCount());

        runNextLaterTask();
        assertEquals(120, textFieldReads.get());
        assertEquals(0, signManager.pendingCoverCheckCount());
        assertTrue(laterTasks.isEmpty());
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("Langsame Kontrolle: eine neue Runde startet erst, wenn die vorige fertig ist")
    void coverCheckDoesNotOverlap() {
        for (int index = 0; index < 120; index++) lock(index * 2, 0, alice);
        runTicks();
        textFieldReads.set(0);

        coverCheckRound();
        timerTasks.get(0).run();

        assertEquals(50, textFieldReads.get());
        assertEquals(70, signManager.pendingCoverCheckCount());
    }

    @Test
    @DisplayName("Langsame Kontrolle prueft nur Gruppen in geladenen Chunks")
    void coverCheckSkipsUnloadedChunks() {
        lock(0, 0, alice);
        lock(40, 0, alice);
        runTicks();
        unloadedChunks.add(new ChunkCoordinates(2, 0));
        textFieldReads.set(0);

        coverCheckRound();

        assertEquals(1, textFieldReads.get());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Langsame Kontrolle tut nichts bei ausgeschaltetem Admin-Schalter oder ausgeschalteter Namensanzeige")
    void coverCheckIdleWhenSwitchedOff() {
        lock(0, 0, alice);
        runTicks();
        cover(0, 0);
        textFieldReads.set(0);

        hideWhenCovered.set(false);
        coverCheckRound();
        hideWhenCovered.set(true);
        displayEnabled.set(false);
        timerTasks.get(0).run();

        assertEquals(0, textFieldReads.get());
        assertEquals(0, signManager.pendingCoverCheckCount());
        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(laterTasks.isEmpty());
        assertEquals(1, live().size());
    }

    @Test
    @DisplayName("Langsame Kontrolle bricht ab, wenn der Admin-Schalter waehrend einer Runde ausgeschaltet wird")
    void coverCheckStopsWhenSwitchedOffMidway() {
        for (int index = 0; index < 120; index++) lock(index * 2, 0, alice);
        runTicks();
        textFieldReads.set(0);

        coverCheckRound();
        hideWhenCovered.set(false);
        runNextLaterTask();

        assertEquals(50, textFieldReads.get());
        assertEquals(0, signManager.pendingCoverCheckCount());
        assertTrue(laterTasks.isEmpty());
    }

    @Test
    @DisplayName("Abschalten des Plugins stoppt die Kontrolle: Timer und laufender Teil werden abgebrochen")
    void stopCoverCheckCancelsTasks() {
        for (int index = 0; index < 120; index++) lock(index * 2, 0, alice);
        runTicks();

        signManager.startCoverCheck();
        signManager.startCoverCheck();
        verify(scheduler, times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        timerTasks.get(0).run();
        BukkitTask batchHandle = laterTasks.get(0).handle();

        signManager.stopCoverCheck();

        verify(timerHandle).cancel();
        verify(batchHandle).cancel();
        assertEquals(0, signManager.pendingCoverCheckCount());

        signManager.startCoverCheck();
        verify(scheduler, times(2)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Spielerschalter aus entfernt nur die Texte dieses Besitzers, an stellt sie wieder her")
    void ownerSwitchAffectsOnlyThatOwner() {
        LockKey first = lock(0, 0, alice);
        LockKey second = lock(5, 5, alice);
        lock(10, 10, bob);
        runTicks();
        SpawnedText bobText = live().stream().filter(text -> text.text.equals("name:Bob")).findFirst().orElseThrow();

        showOwnerName.put(alice, false);
        signManager.refreshOwnerSigns(alice);
        runTicks();

        assertEquals(List.of(bobText), live());
        assertEquals(Set.of(first.asId(), second.asId()), signManager.hiddenGroupIds());
        assertConsistent();

        showOwnerName.put(alice, true);
        signManager.refreshOwnerSigns(alice);
        runTicks();

        assertEquals(List.of("name:Alex", "name:Alex", "name:Bob"),
                live().stream().map(text -> text.text).sorted().toList());
        assertFalse(bobText.removed);
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertConsistent();
    }

    @Test
    @DisplayName("Spielerschalter aus und ueberdeckt: die Kontrolle markiert nicht bei jeder Runde neu")
    void ownerSwitchOffAndCoveredIsStable() {
        showOwnerName.put(alice, false);
        lock(0, 0, alice);
        cover(0, 0);
        runTicks();

        coverCheckRound();
        coverCheckRound();

        assertEquals(0, signManager.pendingChangeCount());
        assertTrue(queuedTasks.isEmpty());
    }

    @Test
    @DisplayName("Admin-Schalter an und aus: Texte verschwinden und erscheinen, zu 50 Gruppen pro Tick")
    void hideSwitchRefreshesInBatches() {
        hideWhenCovered.set(false);
        for (int index = 0; index < 120; index++) {
            lock(index * 2, 0, alice);
            cover(index * 2, 0);
        }
        runTicks();
        assertEquals(120, live().size());

        hideWhenCovered.set(true);
        signManager.refreshAllGroups();

        assertEquals(List.of(0L, 1L, 2L), laterTasks.stream().map(LaterTask::delay).toList());
        assertTrue(queuedTasks.isEmpty());
        runNextLaterTask();
        assertEquals(70, live().size());
        runNextLaterTask();
        assertEquals(20, live().size());
        runNextLaterTask();
        assertTrue(live().isEmpty());
        assertEquals(120, signManager.hiddenGroupIds().size());
        assertConsistent();

        hideWhenCovered.set(false);
        signManager.refreshAllGroups();

        assertEquals(List.of(0L, 1L, 2L), laterTasks.stream().map(LaterTask::delay).toList());
        runNextLaterTask();
        assertEquals(50, live().size());
        runAllLaterTasks();
        assertEquals(120, live().size());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertConsistent();
    }

    @Test
    @DisplayName("Globale Anzeige aus hat Vorrang: nach removeAllSigns erzeugen Schalter, Ereignisse und Kontrolle nichts")
    void globalDisplayOffWins() {
        lock(0, 0, alice);
        lock(5, 5, bob);
        cover(5, 5);
        runTicks();

        displayEnabled.set(false);
        signManager.removeAllSigns();
        int spawnedBefore = spawned.size();
        signManager.refreshAllGroups();
        signManager.refreshOwnerSigns(alice);
        signManager.spawnAllSigns();
        breakBlock(above(5, 5));
        coverCheckRound();
        runAllLaterTasks();

        assertEquals(spawnedBefore, spawned.size());
        assertTrue(live().isEmpty());
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("Offline-Besitzer mit Schalter aus beim Start: spawnAllSigns erzeugt nur die anderen Texte")
    void offlineOwnerSettingAppliesAtStart() {
        showOwnerName.put(alice, false);
        LockKey aliceKey = at(0, 0);
        LockKey bobKey = at(5, 5);
        cache.putLock(aliceKey.asId(), new LockEntry(aliceKey, Material.BARREL, alice, 0L));
        cache.putLock(bobKey.asId(), new LockEntry(bobKey, Material.BARREL, bob, 0L));

        signManager.spawnAllSigns();
        runAllLaterTasks();

        assertEquals("name:Bob", onlyLive().text);
        assertEquals(Set.of(aliceKey.asId()), signManager.hiddenGroupIds());
        assertConsistent();
    }

    @Test
    @DisplayName("removeAllSigns: keine Entity-Reste, kein Vermerk versteckt, keine Textfelder, keine offene Kontrolle")
    void removeAllSignsLeavesNothing() {
        lock(0, 0, alice);
        lock(5, 5, bob);
        cover(5, 5);
        showOwnerName.put(alice, false);
        lock(10, 10, alice);
        lock(20, 20, bob);
        runTicks();
        assertEquals(1, live().size());
        assertEquals(3, signManager.hiddenGroupIds().size());

        signManager.removeAllSigns();

        assertTrue(live().isEmpty());
        assertTrue(spawned.stream().allMatch(text -> text.removed));
        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertTrue(signManager.trackedTextFields().isEmpty());
        assertTrue(signManager.trackedEntityIds().isEmpty());
        assertEquals(0, signManager.pendingCoverCheckCount());
    }

    @Test
    @DisplayName("Eine versteckte Gruppe, die nicht mehr existiert, verliert ihren Vermerk und ihr Textfeld")
    void vanishedHiddenGroupLeavesNothing() {
        LockKey key = lock(0, 0, alice);
        cover(0, 0);
        runTicks();
        assertEquals(Set.of(key.asId()), signManager.hiddenGroupIds());

        unlock(key);
        runTicks();

        assertTrue(signManager.trackedGroupIds().isEmpty());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertTrue(signManager.trackedTextFields().isEmpty());
    }

    @Test
    @DisplayName("Kisten uebereinander: die untere Ebene ist von der oberen Sperre ueberdeckt, die obere bleibt sichtbar")
    void stackedLocksHideLowerLevel() {
        LockKey lower = lock(0, 0, alice);
        LockKey upper = lockAt(above(0, 0), alice);

        runTicks();

        assertEquals(Set.of(lower.asId()), signManager.hiddenGroupIds());
        SpawnedText text = onlyLive();
        assertEquals(TEXT_FIELD_Y + NameTextPosition.HEIGHT_ABOVE_BLOCK, text.y, 1e-9);
        assertTrue(signManager.hasLiveSign(upper.asId()));
        assertConsistent();
    }

    @Test
    @DisplayName("Teilweise Abdeckung einer Gruppe: nur ein Block im Textfeld versteckt den Namen")
    void partialCoverOnlyCountsTextField() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        lock(2, 0, alice);
        cover(0, 0);
        cover(2, 0);
        runTicks();

        assertEquals(1.5, onlyLive().x, 0.0);

        place(above(1, 0), Material.SMOOTH_STONE_SLAB);
        runTicks();

        assertTrue(live().isEmpty());
        assertEquals(1, signManager.hiddenGroupIds().size());
    }

    @Test
    @DisplayName("Doppelkiste: Text auf der Grenze der Haelften, ein Block ueber jeder der beiden Haelften versteckt ihn")
    void doubleChestTextField() {
        doubleChest(0, 0, alice);
        runTicks();

        assertEquals(1.0, onlyLive().x, 0.0);
        assertEquals(Set.of(above(0, 0), above(1, 0)), signManager.trackedTextFields());

        place(above(0, 0), Material.CHEST);
        runTicks();

        assertTrue(live().isEmpty());
        assertEquals(Set.of(at(0, 0).asId()), signManager.hiddenGroupIds());
    }

    @Test
    @DisplayName("Nicht geladener Chunk: keine Pruefung des Textfelds, beim Laden versteckt")
    void coverCheckedOnlyInLoadedChunk() {
        unloadedChunks.add(new ChunkCoordinates(0, 0));
        lock(5, 5, alice);
        cover(5, 5);
        runTicks();

        assertEquals(0, textFieldReads.get());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        assertTrue(spawned.isEmpty());

        loadChunk(0, 0);
        runTicks();

        assertEquals(1, textFieldReads.get());
        assertEquals(Set.of(at(5, 5).asId()), signManager.hiddenGroupIds());
        assertTrue(spawned.isEmpty());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    private LockKey doubleChestAlongZ(int x, int z, UUID owner) {
        LockKey key = at(x, z);
        LockKey partner = at(x, z + 1);
        materials.put(key, Material.CHEST);
        materials.put(partner, Material.CHEST);
        Chest left = mock(Chest.class);
        when(left.getFacing()).thenReturn(BlockFace.EAST);
        when(left.getType()).thenReturn(Chest.Type.LEFT);
        Chest right = mock(Chest.class);
        when(right.getFacing()).thenReturn(BlockFace.EAST);
        when(right.getType()).thenReturn(Chest.Type.RIGHT);
        blockData.put(key, left);
        blockData.put(partner, right);
        cache.putLock(key.asId(), new LockEntry(key, Material.CHEST, owner, 0L));
        signManager.spawnSign(key, "unused");
        return key;
    }

    private void assertHiddenAfterPlacingAndShownAfterBreaking(LockKey textField, String groupId) {
        place(textField, Material.STONE);
        assertTrue(signManager.pendingChangeCount() > 0, "Setzen auf " + textField.asId() + " markiert");
        runTicks();
        assertTrue(live().isEmpty(), "versteckt nach Setzen auf " + textField.asId());
        assertEquals(Set.of(groupId), signManager.hiddenGroupIds());

        breakBlock(textField);
        assertTrue(signManager.pendingChangeCount() > 0, "Abbauen auf " + textField.asId() + " markiert");
        runTicks();
        assertEquals(1, live().size(), "sichtbar nach Abbauen auf " + textField.asId());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
    }

    @Test
    @DisplayName("Doppelkiste in x: ein Block ueber jeder der beiden Haelften versteckt den Text, Abbauen zeigt ihn wieder")
    void doubleChestAlongXCoverOverEitherHalf() {
        LockKey key = doubleChest(0, 0, alice);
        runTicks();

        assertHiddenAfterPlacingAndShownAfterBreaking(above(0, 0), key.asId());
        assertHiddenAfterPlacingAndShownAfterBreaking(above(1, 0), key.asId());
        assertConsistent();
    }

    @Test
    @DisplayName("Doppelkiste in z: beide Felder sind registriert, ein Block ueber jeder Haelfte versteckt den Text")
    void doubleChestAlongZCoverOverEitherHalf() {
        LockKey key = doubleChestAlongZ(4, 4, alice);
        runTicks();

        assertEquals(4.5, onlyLive().x, 0.0);
        assertEquals(5.0, onlyLive().z, 0.0);
        assertEquals(Set.of(above(4, 4), above(4, 5)), signManager.trackedTextFields());
        assertHiddenAfterPlacingAndShownAfterBreaking(above(4, 4), key.asId());
        assertHiddenAfterPlacingAndShownAfterBreaking(above(4, 5), key.asId());
    }

    @Test
    @DisplayName("2x2: alle vier beruehrten Felder sind registriert, Setzen und Abbauen auf jedem Feld markiert die Gruppe")
    void twoByTwoEventsOnEveryTouchedField() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        lock(0, 1, alice);
        lock(1, 1, alice);
        runTicks();

        assertEquals(Set.of(above(0, 0), above(1, 0), above(0, 1), above(1, 1)), signManager.trackedTextFields());
        for (LockKey textField : List.of(above(0, 0), above(1, 0), above(0, 1), above(1, 1))) {
            assertHiddenAfterPlacingAndShownAfterBreaking(textField, at(0, 0).asId());
        }
        place(above(2, 0), Material.STONE);
        assertEquals(0, signManager.pendingChangeCount());
    }

    @Test
    @DisplayName("Langsame Kontrolle findet Aenderungen ohne Ereignis auf jedem beruehrten Feld")
    void coverCheckFindsChangesOnEveryTouchedField() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        lock(0, 1, alice);
        lock(1, 1, alice);
        runTicks();

        for (int[] field : new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            cover(field[0], field[1]);
            coverCheckRound();
            runTicks();
            assertTrue(live().isEmpty(), "versteckt nach Abdeckung ohne Ereignis bei " + field[0] + "," + field[1]);

            uncover(field[0], field[1]);
            coverCheckRound();
            runTicks();
            assertEquals(1, live().size(), "sichtbar nach Freigabe bei " + field[0] + "," + field[1]);
        }
        assertConsistent();
    }

    @Test
    @DisplayName("3x3: nur das mittlere Feld zaehlt, ein Block ueber einem Randfeld versteckt nicht")
    void threeByThreeKeepsOneField() {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) lock(x, z, alice);
        }
        runTicks();

        assertEquals(Set.of(above(1, 1)), signManager.trackedTextFields());
        place(above(0, 0), Material.STONE);
        assertEquals(0, signManager.pendingChangeCount());
        assertEquals(1, live().size());
    }

    @Test
    @DisplayName("Beruehrtes Feld im nicht geladenen Nachbarchunk zaehlt als nicht belegt, es wird kein Chunk geladen")
    void unloadedNeighbourFieldCountsAsFree() {
        unloadedChunks.add(new ChunkCoordinates(0, 0));
        lock(15, 0, alice);
        lock(16, 0, alice);
        cover(15, 0);
        textFieldReads.set(0);

        runTicks();

        assertEquals(16.0, onlyLive().x, 0.0);
        assertEquals(1, textFieldReads.get());
        assertTrue(signManager.hiddenGroupIds().isEmpty());
        verify(world, never()).getChunkAt(anyInt(), anyInt());

        loadChunk(0, 0);
        runTicks();

        assertTrue(live().isEmpty());
        assertEquals(Set.of(at(15, 0).asId()), signManager.hiddenGroupIds());
    }

    @Test
    @DisplayName("Langsame Kontrolle einer Doppelkiste liest beide Felder")
    void coverCheckReadsBothHalvesOfDoubleChest() {
        doubleChest(0, 0, alice);
        runTicks();
        textFieldReads.set(0);

        coverCheckRound();

        assertEquals(2, textFieldReads.get());
        assertEquals(0, signManager.pendingChangeCount());
    }
}
