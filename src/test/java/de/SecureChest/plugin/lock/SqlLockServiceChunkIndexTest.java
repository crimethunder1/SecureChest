package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ui.UndoManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: Chunk-Index bleibt bei allen Aenderungen der Sperren konsistent")
class SqlLockServiceChunkIndexTest {

    private static final int MIN_CHUNK = -3;
    private static final int MAX_CHUNK = 2;

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private UUID worldUuid;
    private Player alice;
    private Player bob;
    private Player operator;

    private void start(boolean completeCacheWarmup) throws SQLException {
        fixture = new LockServiceTestFixture(completeCacheWarmup);
        world = fixture.world;
        service = fixture.service;
        worldUuid = world.world().getUID();
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        alice = playerWithLocation("Alice", false);
        bob = playerWithLocation("Bob", false);
        operator = playerWithLocation("Operator", true);
    }

    private void start() throws SQLException {
        start(true);
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) fixture.close();
    }

    private Player playerWithLocation(String name, boolean admin) {
        Player player = fixture.player(name, admin);
        Block standingOn = world.blockAt(0, 100, 0);
        Location location = mock(Location.class);
        when(location.getBlock()).thenReturn(standingOn);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private Block barrel(int x, int z) {
        return world.setBlock(x, 64, z, Material.BARREL);
    }

    private Block lockedBarrel(Player owner, int x, int z) {
        Block block = barrel(x, z);
        assertTrue(service.lock(owner, block));
        return block;
    }

    private Block secureHopper(Player owner, Block container) {
        Block hopper = world.setBlock(container.getX(), 65, container.getZ(), Material.HOPPER);
        assertTrue(service.bindSecureHopper(owner, hopper, container));
        return hopper;
    }

    private String idOf(Block block) {
        return world.keyAt(block.getX(), block.getY(), block.getZ()).asId();
    }

    private Set<LockKey> lockKeysInChunk(int chunkX, int chunkZ) {
        return service.getCachedLocksInChunk(worldUuid, chunkX, chunkZ).stream()
                .map(LockEntry::key).collect(Collectors.toSet());
    }

    private Set<LockKey> hopperKeysInChunk(int chunkX, int chunkZ) {
        return service.getCachedHoppersInChunk(worldUuid, chunkX, chunkZ).stream()
                .map(SecureHopperEntry::hopperKey).collect(Collectors.toSet());
    }

    private void assertIndexConsistent() {
        for (int chunkX = MIN_CHUNK; chunkX <= MAX_CHUNK; chunkX++) {
            for (int chunkZ = MIN_CHUNK; chunkZ <= MAX_CHUNK; chunkZ++) {
                int x = chunkX;
                int z = chunkZ;
                Set<LockEntry> expectedLocks = service.getAllCachedLocks().stream()
                        .filter(entry -> entry.key().worldUuid().equals(worldUuid))
                        .filter(entry -> (entry.key().x() >> 4) == x && (entry.key().z() >> 4) == z)
                        .collect(Collectors.toSet());
                Set<SecureHopperEntry> expectedHoppers = service.getAllCachedHoppers().stream()
                        .filter(entry -> entry.hopperKey().worldUuid().equals(worldUuid))
                        .filter(entry -> (entry.hopperKey().x() >> 4) == x && (entry.hopperKey().z() >> 4) == z)
                        .collect(Collectors.toSet());
                assertEquals(expectedLocks, Set.copyOf(service.getCachedLocksInChunk(worldUuid, x, z)),
                        "Sperren in Chunk " + x + "/" + z);
                assertEquals(expectedHoppers, Set.copyOf(service.getCachedHoppersInChunk(worldUuid, x, z)),
                        "Secure Hopper in Chunk " + x + "/" + z);
            }
        }
    }

    private void lockSpreadOverChunks() {
        lockedBarrel(alice, 5, 5);
        lockedBarrel(alice, 15, 15);
        lockedBarrel(alice, 16, 0);
        lockedBarrel(alice, -1, -1);
        lockedBarrel(bob, -16, -16);
        lockedBarrel(bob, -17, 30);
    }

    private void runPendingAsyncTasks() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), taskCaptor.capture());
        clearInvocations(fixture.scheduler);
        for (Runnable task : taskCaptor.getAllValues()) task.run();
    }

    private void runSchedulerImmediately() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        });
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        });
        fixture.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @Test
    @DisplayName("Anlegen: jede Sperre steht im Chunk ihrer Koordinaten, auch bei negativen Koordinaten")
    void anlegen() throws SQLException {
        start();

        lockSpreadOverChunks();

        assertEquals(Set.of(world.keyAt(5, 64, 5), world.keyAt(15, 64, 15)), lockKeysInChunk(0, 0));
        assertEquals(Set.of(world.keyAt(16, 64, 0)), lockKeysInChunk(1, 0));
        assertEquals(Set.of(world.keyAt(-1, 64, -1), world.keyAt(-16, 64, -16)), lockKeysInChunk(-1, -1));
        assertEquals(Set.of(world.keyAt(-17, 64, 30)), lockKeysInChunk(-2, 1));
        assertTrue(service.getCachedLocksInChunk(UUID.randomUUID(), 0, 0).isEmpty());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Abgelehntes Anlegen (schon gesperrt) aendert den Index nicht")
    void anlegenAbgelehnt() throws SQLException {
        start();
        Block block = lockedBarrel(alice, 5, 5);

        assertFalse(service.lock(bob, block));

        assertEquals(alice.getUniqueId(), service.getCachedLocksInChunk(worldUuid, 0, 0).iterator().next().owner());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Entsperren mit Undo durch Besitzer und Admin entfernt die Sperren aus dem Index")
    void entsperren() throws SQLException {
        start();
        lockSpreadOverChunks();

        assertTrue(service.unlockByIdWithUndo(alice, world.keyAt(5, 64, 5).asId()));
        assertTrue(service.unlockByIdWithUndo(alice, world.keyAt(16, 64, 0).asId()));
        assertTrue(service.unlockByIdWithUndo(operator, world.keyAt(-17, 64, 30).asId()));

        assertEquals(Set.of(world.keyAt(15, 64, 15)), lockKeysInChunk(0, 0));
        assertTrue(lockKeysInChunk(1, 0).isEmpty());
        assertTrue(lockKeysInChunk(-2, 1).isEmpty());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Entsperren durch einen Fremden wird abgelehnt, die Sperre bleibt im Index")
    void entsperrenAbgelehnt() throws SQLException {
        start();
        Block block = lockedBarrel(alice, 5, 5);

        assertFalse(service.unlockByIdWithUndo(bob, idOf(block)));

        assertEquals(Set.of(world.keyAt(5, 64, 5)), lockKeysInChunk(0, 0));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Uebertragen: Eintrag bleibt im Index und zeigt auf den neuen Besitzer")
    void uebertragen() throws SQLException {
        start();
        lockSpreadOverChunks();

        assertTrue(service.transferLock(alice, world.keyAt(-1, 64, -1).asId(), bob.getUniqueId()));

        assertEquals(Set.of(world.keyAt(-1, 64, -1), world.keyAt(-16, 64, -16)), lockKeysInChunk(-1, -1));
        assertTrue(service.getCachedLocksInChunk(worldUuid, -1, -1).stream()
                .allMatch(entry -> entry.owner().equals(bob.getUniqueId())));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Uebertragen entfernt fremde Secure Hopper aus dem Index")
    void uebertragenEntferntFremdeSecureHopper() throws SQLException {
        start();
        Block container = lockedBarrel(alice, -20, 3);
        secureHopper(alice, container);
        assertEquals(Set.of(world.keyAt(-20, 65, 3)), hopperKeysInChunk(-2, 0));

        assertTrue(service.transferLock(alice, idOf(container), bob.getUniqueId()));

        assertTrue(hopperKeysInChunk(-2, 0).isEmpty());
        assertEquals(Set.of(world.keyAt(-20, 64, 3)), lockKeysInChunk(-2, 0));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Vertrauen setzen und entfernen ersetzt den Eintrag, der Schluessel bleibt im Index")
    void vertrauen() throws SQLException {
        start();
        Block block = lockedBarrel(alice, 5, 5);
        String id = idOf(block);

        assertTrue(service.setTrustById(alice, id, bob.getUniqueId(), AccessLevel.VIEW));
        assertEquals(AccessLevel.VIEW,
                service.getCachedLocksInChunk(worldUuid, 0, 0).iterator().next().trusted().get(bob.getUniqueId()));
        assertTrue(service.removeTrustById(alice, id, bob.getUniqueId()));

        assertEquals(Set.of(world.keyAt(5, 64, 5)), lockKeysInChunk(0, 0));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Rueckgaengig nach dem Entsperren und nach dem Uebertragen stellt den Index wieder her")
    void rueckgaengig() throws SQLException {
        start();
        lockSpreadOverChunks();

        assertTrue(service.unlockByIdWithUndo(alice, world.keyAt(16, 64, 0).asId()));
        assertTrue(lockKeysInChunk(1, 0).isEmpty());
        assertTrue(service.restoreUndo(alice));
        assertEquals(Set.of(world.keyAt(16, 64, 0)), lockKeysInChunk(1, 0));
        assertIndexConsistent();

        assertTrue(service.transferLock(alice, world.keyAt(5, 64, 5).asId(), bob.getUniqueId()));
        assertTrue(service.restoreUndo(alice));
        assertEquals(Set.of(world.keyAt(5, 64, 5), world.keyAt(15, 64, 15)), lockKeysInChunk(0, 0));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Globales Entsperren mit Undo: Sperren und Secure Hopper des Besitzers verschwinden, Rueckgaengig bringt sie zurueck")
    void globalesEntsperren() throws SQLException {
        start();
        lockSpreadOverChunks();
        secureHopper(alice, world.blockAt(-1, 64, -1));
        secureHopper(bob, world.blockAt(-16, 64, -16));

        assertTrue(service.unlockAllWithUndo(alice, alice.getUniqueId()));

        assertTrue(lockKeysInChunk(0, 0).isEmpty());
        assertTrue(lockKeysInChunk(1, 0).isEmpty());
        assertEquals(Set.of(world.keyAt(-16, 64, -16)), lockKeysInChunk(-1, -1));
        assertEquals(Set.of(world.keyAt(-16, 65, -16)), hopperKeysInChunk(-1, -1));
        assertIndexConsistent();

        assertTrue(service.restoreUndo(alice));

        assertEquals(Set.of(world.keyAt(5, 64, 5), world.keyAt(15, 64, 15)), lockKeysInChunk(0, 0));
        assertEquals(Set.of(world.keyAt(-1, 65, -1), world.keyAt(-16, 65, -16)), hopperKeysInChunk(-1, -1));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Globales Entsperren durch einen Admin fuer zwei Besitzer haelt den Index konsistent")
    void globalesEntsperrenAdminZweiBesitzer() throws SQLException {
        start();
        lockSpreadOverChunks();
        secureHopper(bob, world.blockAt(-17, 64, 30));

        assertTrue(service.unlockAllWithUndo(operator, alice.getUniqueId()));
        assertIndexConsistent();
        assertTrue(service.unlockAllWithUndo(operator, bob.getUniqueId()));

        assertTrue(service.getAllCachedLocks().isEmpty());
        assertTrue(lockKeysInChunk(-1, -1).isEmpty());
        assertTrue(hopperKeysInChunk(-2, 1).isEmpty());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Entfernen ausserhalb des Wirkungsbereichs entfernt Sperren und Secure Hopper aus dem Index")
    void wirkungsbereich() throws SQLException {
        start();
        lockSpreadOverChunks();
        secureHopper(bob, world.blockAt(-17, 64, 30));
        List<LockEntry> outside = List.copyOf(service.getCachedLocksInChunk(worldUuid, -2, 1));
        List<SecureHopperEntry> outsideHoppers = List.copyOf(service.getCachedHoppersInChunk(worldUuid, -2, 1));

        LockRemovalBatch batch = service.removeOutsideArea(outside, outsideHoppers);

        assertEquals(1, batch.locks().size());
        assertEquals(1, batch.secureHoppers().size());
        assertTrue(lockKeysInChunk(-2, 1).isEmpty());
        assertTrue(hopperKeysInChunk(-2, 1).isEmpty());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Wiederherstellen nach fehlgeschlagenem Speichern der Wirkungsbereich-Entfernung fuellt den Index wieder")
    void wirkungsbereichWiederhergestellt() throws SQLException {
        start();
        lockSpreadOverChunks();
        secureHopper(bob, world.blockAt(-17, 64, 30));
        List<LockEntry> outside = List.copyOf(service.getCachedLocksInChunk(worldUuid, -2, 1));
        List<SecureHopperEntry> outsideHoppers = List.copyOf(service.getCachedHoppersInChunk(worldUuid, -2, 1));
        runSchedulerImmediately();

        service.removeOutsideArea(outside, outsideHoppers);

        assertEquals(Set.of(world.keyAt(-17, 64, 30)), lockKeysInChunk(-2, 1));
        assertEquals(Set.of(world.keyAt(-17, 65, 30)), hopperKeysInChunk(-2, 1));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("forceRemoveLockAt und forceRemoveHopperAt entfernen aus dem Index, unbekannte Schluessel aendern nichts")
    void forceRemove() throws SQLException {
        start();
        lockSpreadOverChunks();
        secureHopper(alice, world.blockAt(15, 64, 15));

        service.forceRemoveLockAt(world.keyAt(5, 64, 5));
        service.forceRemoveHopperAt(world.keyAt(15, 65, 15));
        service.forceRemoveLockAt(world.keyAt(7, 64, 7));
        service.forceRemoveHopperAt(world.keyAt(7, 65, 7));

        assertEquals(Set.of(world.keyAt(15, 64, 15)), lockKeysInChunk(0, 0));
        assertTrue(hopperKeysInChunk(0, 0).isEmpty());
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Secure Hopper binden und entfernen pflegen den Hopper-Index, der Sperren-Index bleibt")
    void secureHopperBindenUndEntfernen() throws SQLException {
        start();
        Block container = lockedBarrel(alice, -1, 0);
        Block hopper = secureHopper(alice, container);

        assertEquals(Set.of(world.keyAt(-1, 65, 0)), hopperKeysInChunk(-1, 0));
        assertEquals(Set.of(world.keyAt(-1, 64, 0)), lockKeysInChunk(-1, 0));
        assertFalse(service.bindSecureHopper(alice, hopper, container));
        assertIndexConsistent();

        assertTrue(service.removeSecureHopper(hopper));

        assertTrue(hopperKeysInChunk(-1, 0).isEmpty());
        assertEquals(Set.of(world.keyAt(-1, 64, 0)), lockKeysInChunk(-1, 0));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Fehlgeschlagenes Speichern beim Anlegen und Entsperren: Rueckgaengig im Cache haelt den Index konsistent")
    void datenbankfehlerRollback() throws SQLException {
        start();
        clearInvocations(fixture.scheduler);
        lockedBarrel(alice, 5, 5);
        runPendingAsyncTasks();
        assertTrue(lockKeysInChunk(0, 0).isEmpty());
        assertIndexConsistent();

        lockedBarrel(alice, 20, 20);
        clearInvocations(fixture.scheduler);
        assertTrue(service.unlockByIdWithUndo(alice, world.keyAt(20, 64, 20).asId()));
        assertTrue(lockKeysInChunk(1, 1).isEmpty());
        runPendingAsyncTasks();
        assertEquals(Set.of(world.keyAt(20, 64, 20)), lockKeysInChunk(1, 1));
        assertIndexConsistent();
    }

    @Test
    @DisplayName("Vollstaendiger Aufbau beim Start: Index enthaelt alle geladenen Sperren und Secure Hopper")
    void aufbauBeimStart() throws SQLException {
        start(false);
        UUID owner = UUID.randomUUID();
        Connection connection = LockServiceTestFixture.emptyDatabaseConnection();
        Statement statement = connection.createStatement();
        ResultSet lockRows = rows(new int[][] {{5, 64, 5}, {-1, 64, -1}, {-17, 64, 30}, {16, 70, 0}}, owner, 7);
        ResultSet hopperRows = rows(new int[][] {{5, 65, 5}, {-33, 65, -33}}, owner, 9);
        when(statement.executeQuery(contains("FROM locks"))).thenReturn(lockRows);
        when(statement.executeQuery(contains("FROM secure_hoppers"))).thenReturn(hopperRows);

        fixture.runCacheWarmupWith(connection);

        assertEquals(4, service.getAllCachedLocks().size());
        assertEquals(Set.of(world.keyAt(5, 64, 5)), lockKeysInChunk(0, 0));
        assertEquals(Set.of(world.keyAt(-1, 64, -1)), lockKeysInChunk(-1, -1));
        assertEquals(Set.of(world.keyAt(-17, 64, 30)), lockKeysInChunk(-2, 1));
        assertEquals(Set.of(world.keyAt(16, 70, 0)), lockKeysInChunk(1, 0));
        assertEquals(Set.of(world.keyAt(5, 65, 5)), hopperKeysInChunk(0, 0));
        assertEquals(Set.of(world.keyAt(-33, 65, -33)), hopperKeysInChunk(-3, -3));
        assertIndexConsistent();
    }

    private ResultSet rows(int[][] positions, UUID owner, int columns) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        Boolean[] more = new Boolean[positions.length];
        String[] worlds = new String[positions.length];
        Integer[] xs = new Integer[positions.length];
        Integer[] ys = new Integer[positions.length];
        Integer[] zs = new Integer[positions.length];
        for (int row = 0; row < positions.length; row++) {
            more[row] = row < positions.length - 1;
            worlds[row] = worldUuid.toString();
            xs[row] = positions[row][0];
            ys[row] = positions[row][1];
            zs[row] = positions[row][2];
        }
        when(resultSet.next()).thenReturn(true, more);
        when(resultSet.getString(1)).thenReturn(worlds[0], tail(worlds));
        when(resultSet.getInt(2)).thenReturn(xs[0], tail(xs));
        when(resultSet.getInt(3)).thenReturn(ys[0], tail(ys));
        when(resultSet.getInt(4)).thenReturn(zs[0], tail(zs));
        if (columns == 7) {
            when(resultSet.getString(5)).thenReturn(Material.BARREL.name());
            when(resultSet.getString(6)).thenReturn(owner.toString());
            when(resultSet.getLong(7)).thenReturn(1L);
        } else {
            when(resultSet.getString(5)).thenReturn(owner.toString());
            when(resultSet.getString(6)).thenReturn(worldUuid.toString());
            when(resultSet.getInt(7)).thenReturn(0);
            when(resultSet.getInt(8)).thenReturn(64);
            when(resultSet.getInt(9)).thenReturn(0);
        }
        return resultSet;
    }

    private static <T> T[] tail(T[] values) {
        return Arrays.copyOfRange(values, 1, values.length);
    }
}
