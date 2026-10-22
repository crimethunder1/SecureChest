package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Doppelkisten: Aufloesung des gespeicherten Schluessels gegen LockCacheManager")
class DoubleChestLockResolutionTest {

    private final DoubleChestTestWorld world = new DoubleChestTestWorld();
    private final LockCacheManager cache = new LockCacheManager();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private LockEntry storeLock(LockKey key, UUID owner) {
        LockEntry entry = new LockEntry(key, Material.CHEST, owner, System.currentTimeMillis());
        cache.putLock(key.asId(), entry);
        return entry;
    }

    private Optional<LockEntry> resolve(Block block) {
        return SqlLockService.resolveStoredLock(LockUtil.storedKeyCandidates(block),
                cache::getLock, id -> Optional.ofNullable(cache.getLock(id)));
    }

    @Test
    @DisplayName("Angebaut bei groesseren Koordinaten: beide Haelften finden die Sperre")
    void angebautGroessereKoordinaten_beideHaelftenFindenSperre() {
        LockKey original = world.keyAt(0, 64, 0);
        storeLock(original, alice);
        Block lockedHalf = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block addedHalf = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertEquals(original, resolve(lockedHalf).orElseThrow().key());
        assertEquals(original, resolve(addedHalf).orElseThrow().key());
    }

    @Test
    @DisplayName("Angebaut bei kleineren Koordinaten: beide Haelften finden die Sperre an der alten Position")
    void angebautKleinereKoordinaten_beideHaelftenFindenSperre() {
        LockKey original = world.keyAt(0, 64, 0);
        storeLock(original, alice);
        Block addedHalf = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block lockedHalf = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertEquals(world.keyAt(-1, 64, 0), LockUtil.canonicalKey(lockedHalf));
        assertEquals(original, resolve(addedHalf).orElseThrow().key());
        assertEquals(original, resolve(lockedHalf).orElseThrow().key());
    }

    @Test
    @DisplayName("Angebaut bei kleineren Koordinaten auf Achse z: beide Haelften finden die Sperre")
    void angebautKleinereKoordinatenAchseZ_beideHaelftenFindenSperre() {
        LockKey original = world.keyAt(8, 64, -5);
        storeLock(original, alice);
        Block addedHalf = world.setChest(8, 64, -6, Material.CHEST, BlockFace.EAST, Chest.Type.LEFT);
        Block lockedHalf = world.setChest(8, 64, -5, Material.CHEST, BlockFace.EAST, Chest.Type.RIGHT);

        assertEquals(original, resolve(addedHalf).orElseThrow().key());
        assertEquals(original, resolve(lockedHalf).orElseThrow().key());
    }

    @Test
    @DisplayName("Zwei Sperren vorhanden: die Sperre am kanonischen Schluessel gewinnt")
    void zweiSperren_kanonischeGewinnt() {
        storeLock(world.keyAt(-1, 64, 0), bob);
        storeLock(world.keyAt(0, 64, 0), alice);
        Block canonicalHalf = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block otherHalf = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertEquals(bob, resolve(canonicalHalf).orElseThrow().owner());
        assertEquals(bob, resolve(otherHalf).orElseThrow().owner());
    }

    @Test
    @DisplayName("Datenbank-Durchgang: Reihenfolge kanonisch vor anderer Haelfte")
    void datenbankDurchgang_kanonischZuerst() {
        Map<String, LockEntry> database = new HashMap<>();
        LockKey canonical = world.keyAt(-1, 64, 0);
        LockKey other = world.keyAt(0, 64, 0);
        database.put(canonical.asId(), new LockEntry(canonical, Material.CHEST, bob, 1L));
        database.put(other.asId(), new LockEntry(other, Material.CHEST, alice, 1L));
        List<String> queriedIds = new ArrayList<>();

        Optional<LockEntry> resolved = SqlLockService.resolveStoredLock(List.of(canonical, other),
                id -> null,
                id -> {
                    queriedIds.add(id);
                    return Optional.ofNullable(database.get(id));
                });

        assertEquals(canonical, resolved.orElseThrow().key());
        assertEquals(List.of(canonical.asId()), queriedIds);
    }

    @Test
    @DisplayName("Cache-Treffer auf der anderen Haelfte loest keine Datenbankabfrage aus")
    void cacheTrefferAndereHaelfte_keineDatenbankabfrage() {
        LockKey canonical = world.keyAt(-1, 64, 0);
        LockKey other = world.keyAt(0, 64, 0);
        storeLock(other, alice);
        List<String> queriedIds = new ArrayList<>();

        Optional<LockEntry> resolved = SqlLockService.resolveStoredLock(List.of(canonical, other),
                cache::getLock,
                id -> {
                    queriedIds.add(id);
                    return Optional.empty();
                });

        assertEquals(other, resolved.orElseThrow().key());
        assertTrue(queriedIds.isEmpty());
    }

    @Test
    @DisplayName("Nicht gesperrte Doppelkiste: beide Haelften werden in der Datenbank nachgeschlagen")
    void nichtGesperrt_beideHaelftenInDatenbank() {
        LockKey canonical = world.keyAt(-1, 64, 0);
        LockKey other = world.keyAt(0, 64, 0);
        List<String> queriedIds = new ArrayList<>();

        Optional<LockEntry> resolved = SqlLockService.resolveStoredLock(List.of(canonical, other),
                cache::getLock,
                id -> {
                    queriedIds.add(id);
                    return Optional.empty();
                });

        assertTrue(resolved.isEmpty());
        assertEquals(List.of(canonical.asId(), other.asId()), queriedIds);
    }

    @Test
    @DisplayName("Entfernen ueber den aufgeloesten Schluessel trifft die Sperre an der alten Position")
    void entfernen_trifftTatsaechlichenSchluessel() {
        LockKey original = world.keyAt(0, 64, 0);
        storeLock(original, alice);
        Block addedHalf = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block lockedHalf = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        LockEntry resolved = resolve(addedHalf).orElseThrow();
        assertTrue(cache.removeLockAtomic(resolved.key().asId(), resolved));

        assertEquals(0, cache.countLocksFor(alice));
        assertTrue(resolve(addedHalf).isEmpty());
        assertTrue(resolve(lockedHalf).isEmpty());
    }
}
