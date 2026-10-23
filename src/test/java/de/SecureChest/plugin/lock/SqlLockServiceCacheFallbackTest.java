package de.SecureChest.plugin.lock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: Datenbank-Rueckgriff nur vor vollstaendigem Cache-Aufbau")
class SqlLockServiceCacheFallbackTest {

    private LockServiceTestFixture fixture;
    private SqlLockService service;
    private Player alice;

    @AfterEach
    void tearDown() {
        if (fixture != null) fixture.close();
    }

    private void start(boolean completeCacheWarmup) throws SQLException {
        fixture = new LockServiceTestFixture(completeCacheWarmup);
        service = fixture.service;
        alice = fixture.player("Alice", false);
    }

    private Block unlockedDoubleChestOtherHalf() {
        fixture.world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        return fixture.world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
    }

    private Block lockSingleChestThenAttachAtSmallerCoordinates() {
        Block original = fixture.world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, original));
        fixture.world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        return fixture.world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
    }

    private Logger logger() {
        return fixture.plugin.getLogger();
    }

    private static ResultSet singleLockRow(UUID world, int x, int y, int z, UUID owner) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(1)).thenReturn(world.toString());
        when(resultSet.getInt(2)).thenReturn(x);
        when(resultSet.getInt(3)).thenReturn(y);
        when(resultSet.getInt(4)).thenReturn(z);
        when(resultSet.getString(5)).thenReturn(Material.CHEST.name());
        when(resultSet.getString(6)).thenReturn(owner.toString());
        when(resultSet.getLong(7)).thenReturn(1L);
        return resultSet;
    }

    @Test
    @DisplayName("Nicht geladen: Cache-Miss in getLockFromId fragt die Datenbank")
    void nichtGeladen_cacheMiss_fragtDatenbank() throws SQLException {
        start(false);

        assertTrue(service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId()).isEmpty());

        verify(fixture.database).getConnection();
    }

    @Test
    @DisplayName("Nicht geladen: ungesperrte Doppelkiste fragt beide Haelften in der Datenbank ab")
    void nichtGeladen_doppelkisteOhneSperre_fragtBeideHaelften() throws SQLException {
        start(false);
        Block otherHalf = unlockedDoubleChestOtherHalf();

        assertFalse(service.isLocked(otherHalf));

        verify(fixture.database, times(2)).getConnection();
    }

    @Test
    @DisplayName("Nicht geladen: Cache-Miss bei Secure Hoppern fragt die Datenbank")
    void nichtGeladen_secureHopperCacheMiss_fragtDatenbank() throws SQLException {
        start(false);
        Block hopper = fixture.world.setBlock(3, 64, 3, Material.HOPPER);

        assertTrue(service.getSecureHopper(hopper).isEmpty());
        assertFalse(service.isSecureHopper(hopper));

        verify(fixture.database, times(2)).getConnection();
    }

    @Test
    @DisplayName("Geladen: Cache-Miss ist endgueltig, keine Datenbank, nicht gesperrt")
    void geladen_cacheMiss_keineDatenbank() throws SQLException {
        start(true);
        Block chest = fixture.world.setChest(5, 64, 5, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);

        assertTrue(service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId()).isEmpty());
        assertTrue(service.getLock(chest).isEmpty());
        assertFalse(service.isLocked(chest));
        assertTrue(service.canOpen(alice, chest, false));

        verify(fixture.database, never()).getConnection();
    }

    @Test
    @DisplayName("Geladen: ungesperrte Doppelkiste ist von beiden Haelften ohne Datenbank nicht gesperrt")
    void geladen_doppelkisteOhneSperre_keineDatenbank() throws SQLException {
        start(true);
        Block otherHalf = unlockedDoubleChestOtherHalf();
        Block canonicalHalf = fixture.world.blockAt(-1, 64, 0);

        assertFalse(service.isLocked(otherHalf));
        assertFalse(service.isLocked(canonicalHalf));
        assertTrue(service.getLock(otherHalf).isEmpty());

        verify(fixture.database, never()).getConnection();
    }

    @Test
    @DisplayName("Geladen: lock() auf eine ungesperrte Doppelkiste fragt die Datenbank nicht synchron ab")
    void geladen_lockAufUngesperrteDoppelkiste_keineDatenbank() throws SQLException {
        start(true);
        Block otherHalf = unlockedDoubleChestOtherHalf();

        assertTrue(service.lock(alice, otherHalf));
        assertEquals(fixture.world.keyAt(-1, 64, 0), service.getLock(otherHalf).orElseThrow().key());

        verify(fixture.database, never()).getConnection();
    }

    @Test
    @DisplayName("Geladen: Cache-Miss bei Secure Hoppern ist endgueltig, kein Secure Hopper, keine Datenbank")
    void geladen_secureHopperCacheMiss_keineDatenbank() throws SQLException {
        start(true);
        Block hopper = fixture.world.setBlock(3, 64, 3, Material.HOPPER);

        assertTrue(service.getSecureHopper(hopper).isEmpty());
        assertFalse(service.isSecureHopper(hopper));
        assertTrue(service.canBreak(alice, hopper));

        verify(fixture.database, never()).getConnection();
    }

    @ParameterizedTest(name = "Cache vollstaendig geladen: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("Cache-Treffer bei einer Sperre ohne Datenbank")
    void cacheTreffer_sperre_keineDatenbank(boolean completeCacheWarmup) throws SQLException {
        start(completeCacheWarmup);
        Block chest = fixture.world.setChest(5, 64, 5, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, chest));
        clearInvocations(fixture.database);

        assertTrue(service.isLocked(chest));
        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
        assertTrue(service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId()).isPresent());

        verify(fixture.database, never()).getConnection();
    }

    @ParameterizedTest(name = "Cache vollstaendig geladen: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("Cache-Treffer auf der anderen Haelfte einer Doppelkiste ohne Datenbank")
    void cacheTreffer_doppelkiste_keineDatenbank(boolean completeCacheWarmup) throws SQLException {
        start(completeCacheWarmup);
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();
        clearInvocations(fixture.database);

        assertEquals(fixture.world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertTrue(service.isLocked(fixture.world.blockAt(0, 64, 0)));

        verify(fixture.database, never()).getConnection();
    }

    @ParameterizedTest(name = "Cache vollstaendig geladen: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("Cache-Treffer bei einem Secure Hopper ohne Datenbank")
    void cacheTreffer_secureHopper_keineDatenbank(boolean completeCacheWarmup) throws SQLException {
        start(completeCacheWarmup);
        Block chest = fixture.world.setChest(5, 64, 5, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block hopper = fixture.world.setBlock(5, 65, 5, Material.HOPPER);
        assertTrue(service.lock(alice, chest));
        assertTrue(service.bindSecureHopper(alice, hopper, chest));
        clearInvocations(fixture.database);

        assertEquals(alice.getUniqueId(), service.getSecureHopper(hopper).orElseThrow().owner());
        assertTrue(service.isSecureHopper(hopper));

        verify(fixture.database, never()).getConnection();
    }

    @Test
    @DisplayName("Aufbau erfolgreich: geladene Sperre wird gefunden, andere Positionen ohne Datenbank nicht")
    void aufbauErfolgreich_geladeneSperreGefunden_restEndgueltig() throws SQLException {
        start(false);
        UUID owner = UUID.randomUUID();
        Connection connection = LockServiceTestFixture.emptyDatabaseConnection();
        Statement statement = connection.createStatement();
        ResultSet lockRows = singleLockRow(fixture.world.world().getUID(), 7, 64, 7, owner);
        when(statement.executeQuery(contains("FROM locks"))).thenReturn(lockRows);

        fixture.runCacheWarmupWith(connection);

        assertEquals(owner, service.getLockFromId(fixture.world.keyAt(7, 64, 7).asId()).orElseThrow().owner());
        assertTrue(service.getLockFromId(fixture.world.keyAt(8, 64, 8).asId()).isEmpty());
        verify(fixture.database, never()).getConnection();
    }

    @Test
    @DisplayName("Aufbau fehlgeschlagen: Flag bleibt false, Cache-Miss fragt weiter die Datenbank")
    void aufbauFehlgeschlagen_rueckgriffBleibt() throws SQLException {
        start(false);

        fixture.runCacheWarmup();
        verify(logger()).log(eq(Level.SEVERE), eq("Cache warmup failed"), any(SQLException.class));
        clearInvocations(fixture.database);

        assertTrue(service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId()).isEmpty());
        assertTrue(service.getSecureHopper(fixture.world.setBlock(3, 64, 3, Material.HOPPER)).isEmpty());

        verify(fixture.database, times(2)).getConnection();
    }

    @Test
    @DisplayName("Aufbau unvollstaendig (Secure Hopper nicht geladen): Flag bleibt false")
    void aufbauUnvollstaendig_rueckgriffBleibt() throws SQLException {
        start(false);
        Connection connection = LockServiceTestFixture.emptyDatabaseConnection();
        Statement statement = connection.createStatement();
        when(statement.executeQuery(contains("FROM secure_hoppers"))).thenThrow(new SQLException("hopper table broken"));

        fixture.runCacheWarmupWith(connection);
        verify(logger()).log(eq(Level.SEVERE), eq("Cache warmup failed"), any(SQLException.class));

        assertTrue(service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId()).isEmpty());

        verify(fixture.database).getConnection();
    }

    @Test
    @DisplayName("Nicht geladen, Hauptthread: mehrere Rueckgriffe kurz hintereinander ergeben eine Warnung")
    void nichtGeladen_hauptthread_eineWarnung() throws SQLException {
        start(false);
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        String lockId = fixture.world.keyAt(388, 72, 374).asId();
        Block hopper = fixture.world.setBlock(3, 64, 3, Material.HOPPER);

        service.getLockFromId(lockId);
        service.getLockFromId(lockId);
        service.getLockFromId(fixture.world.keyAt(388, 72, 375).asId());
        service.getSecureHopper(hopper);
        service.isSecureHopper(hopper);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger(), times(1)).warning(messageCaptor.capture());
        assertEquals("[SecureChest] getLockFromId(\"" + lockId
                        + "\") hits the synchronous DB fallback on the main thread! "
                        + "Make sure the cache warmup finishes before player events.",
                messageCaptor.getValue());
        verify(fixture.database, times(5)).getConnection();
    }

    @Test
    @DisplayName("Nicht geladen, Secure Hopper zuerst: die Warnung nennt getSecureHopper")
    void nichtGeladen_hauptthread_warnungNenntSecureHopper() throws SQLException {
        start(false);
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        Block hopper = fixture.world.setBlock(3, 64, 3, Material.HOPPER);

        service.isSecureHopper(hopper);

        verify(logger()).warning(contains("getSecureHopper(\"" + fixture.world.keyAt(3, 64, 3).asId() + "\")"));
    }

    @Test
    @DisplayName("Nicht geladen, ausserhalb des Hauptthreads: Rueckgriff ohne Warnung")
    void nichtGeladen_ausserhalbHauptthread_keineWarnung() throws SQLException {
        start(false);
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

        service.getLockFromId(fixture.world.keyAt(5, 64, 5).asId());

        verify(fixture.database).getConnection();
        verify(logger(), never()).warning(anyString());
    }

    @Test
    @DisplayName("Geladen, Hauptthread: Cache-Miss schreibt keine Warnung")
    void geladen_hauptthread_keineWarnung() throws SQLException {
        start(true);
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        Block otherHalf = unlockedDoubleChestOtherHalf();
        Block hopper = fixture.world.setBlock(3, 64, 3, Material.HOPPER);

        assertFalse(service.isLocked(otherHalf));
        assertFalse(service.isSecureHopper(hopper));
        assertTrue(service.getSecureHopper(hopper).isEmpty());

        verify(logger(), never()).warning(anyString());
    }
}
