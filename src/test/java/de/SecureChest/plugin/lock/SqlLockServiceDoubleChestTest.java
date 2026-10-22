package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: Doppelkisten-Sperren ueber beide Haelften")
class SqlLockServiceDoubleChestTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Block lockSingleChestThenAttachAtSmallerCoordinates() {
        Block original = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, original));
        world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        return world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
    }

    private List<LockKey> cachedKeys() {
        return service.getAllCachedLocks().stream().map(LockEntry::key).toList();
    }

    @Test
    @DisplayName("Angebaut bei kleineren Koordinaten: getLock und isLocked finden die Sperre von beiden Haelften")
    void angebautKleinereKoordinaten_beideHaelftenGesperrt() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();
        Block originalHalf = world.blockAt(0, 64, 0);

        assertTrue(service.isLocked(addedHalf));
        assertTrue(service.isLocked(originalHalf));
        assertEquals(world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertEquals(alice.getUniqueId(), service.getLock(originalHalf).orElseThrow().owner());
    }

    @Test
    @DisplayName("Angebaut bei groesseren Koordinaten: getLock findet die Sperre von beiden Haelften")
    void angebautGroessereKoordinaten_beideHaelftenGesperrt() {
        Block original = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, original));
        world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block addedHalf = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertEquals(world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertEquals(world.keyAt(0, 64, 0), service.getLock(original).orElseThrow().key());
    }

    @Test
    @DisplayName("lock() auf der Haelfte neben einer Sperre legt keine zweite Sperre an")
    void lock_partnerGesperrt_keineZweiteSperre() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();

        assertFalse(service.lock(alice, addedHalf));
        assertFalse(service.lock(bob, addedHalf));

        assertEquals(List.of(world.keyAt(0, 64, 0)), cachedKeys());
        assertEquals(1, service.countLocks(alice.getUniqueId()));
        assertEquals(0, service.countLocks(bob.getUniqueId()));
    }

    @Test
    @DisplayName("lock() auf der Haelfte neben einer Sperre erhoeht den Limit-Zaehler nicht")
    void lock_partnerGesperrt_zaehlerUnveraendert() {
        when(fixture.config.maxLocksPerPlayer()).thenReturn(2);
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();
        Block secondChest = world.setChest(10, 64, 10, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block thirdChest = world.setChest(20, 64, 20, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);

        assertFalse(service.lock(alice, addedHalf));
        assertTrue(service.lock(alice, secondChest));
        assertFalse(service.lock(alice, thirdChest));
        assertEquals(2, service.countLocks(alice.getUniqueId()));
    }

    @Test
    @DisplayName("unlock() ueber die angebaute Haelfte entfernt die Sperre an der alten Position")
    void unlock_angebauteHaelfte_entferntTatsaechlicheSperre() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();

        assertTrue(service.unlock(alice, addedHalf));

        assertTrue(cachedKeys().isEmpty());
        assertFalse(service.isLocked(addedHalf));
        assertFalse(service.isLocked(world.blockAt(0, 64, 0)));
    }

    @Test
    @DisplayName("unlock() durch fremden Spieler aendert nichts")
    void unlock_fremderSpieler_keineAenderung() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();

        assertFalse(service.unlock(bob, addedHalf));
        assertEquals(List.of(world.keyAt(0, 64, 0)), cachedKeys());
    }

    @Test
    @DisplayName("setTrust() und removeTrust() ueber die angebaute Haelfte aendern die tatsaechliche Sperre")
    void trust_angebauteHaelfte_aendertTatsaechlicheSperre() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();

        assertTrue(service.setTrust(alice, addedHalf, bob.getUniqueId(), AccessLevel.VIEW));
        assertEquals(List.of(world.keyAt(0, 64, 0)), cachedKeys());
        LockEntry entry = service.getLockFromId(world.keyAt(0, 64, 0).asId()).orElseThrow();
        assertEquals(AccessLevel.VIEW, entry.trusted().get(bob.getUniqueId()));

        assertTrue(service.removeTrust(alice, addedHalf, bob.getUniqueId()));
        assertEquals(List.of(world.keyAt(0, 64, 0)), cachedKeys());
        assertFalse(entry.trusted().containsKey(bob.getUniqueId()));
    }

    @Test
    @DisplayName("Zwei Sperren aus Altdaten: kanonische gewinnt, Entfernen trifft genau die gepruefte Sperre")
    void zweiSperren_kanonischeGewinnt_entfernenTrifftGepruefteSperre() {
        Block aliceChest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block bobChest = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, aliceChest));
        assertTrue(service.lock(bob, bobChest));
        world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);

        assertEquals(bob.getUniqueId(), service.getLock(aliceChest).orElseThrow().owner());
        assertEquals(bob.getUniqueId(), service.getLock(bobChest).orElseThrow().owner());

        assertTrue(service.unlock(bob, aliceChest));
        assertEquals(List.of(world.keyAt(0, 64, 0)), cachedKeys());
        assertEquals(alice.getUniqueId(), service.getLock(bobChest).orElseThrow().owner());
    }

    @Test
    @DisplayName("bindSecureHopper() bindet an den tatsaechlich gespeicherten Schluessel")
    void bindSecureHopper_bindetAnTatsaechlichenSchluessel() {
        Block addedHalf = lockSingleChestThenAttachAtSmallerCoordinates();
        Block hopper = world.setBlock(-1, 65, 0, Material.HOPPER);

        assertTrue(service.bindSecureHopper(alice, hopper, addedHalf));

        List<SecureHopperEntry> hoppers = List.copyOf(service.getAllCachedHoppers());
        assertEquals(1, hoppers.size());
        assertEquals(world.keyAt(0, 64, 0), hoppers.get(0).boundLock());
    }
}
