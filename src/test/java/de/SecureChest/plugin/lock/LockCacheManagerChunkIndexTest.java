package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockCacheManager: Chunk-Index wird bei jeder Aenderung des Caches gepflegt")
class LockCacheManagerChunkIndexTest {

    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID otherOwner = UUID.randomUUID();
    private final LockCacheManager cache = new LockCacheManager();

    private LockEntry lockAt(int x, int z, UUID lockOwner) {
        return new LockEntry(new LockKey(world, x, 64, z), Material.CHEST, lockOwner, 1L);
    }

    private SecureHopperEntry hopperAt(int x, int z, UUID hopperOwner) {
        return new SecureHopperEntry(new LockKey(world, x, 65, z), hopperOwner, new LockKey(world, x, 64, z));
    }

    private LockEntry put(LockEntry entry) {
        cache.putLock(entry.key().asId(), entry);
        return entry;
    }

    private SecureHopperEntry put(SecureHopperEntry entry) {
        cache.putHopper(entry.hopperKey().asId(), entry);
        return entry;
    }

    private Set<LockEntry> locksIn(int chunkX, int chunkZ) {
        return Set.copyOf(cache.locksInChunk(world, chunkX, chunkZ));
    }

    private Set<SecureHopperEntry> hoppersIn(int chunkX, int chunkZ) {
        return Set.copyOf(cache.hoppersInChunk(world, chunkX, chunkZ));
    }

    private void assertConsistent(int chunkX, int chunkZ) {
        Set<LockEntry> expectedLocks = cache.lockValues().stream()
                .filter(entry -> entry.key().worldUuid().equals(world))
                .filter(entry -> (entry.key().x() >> 4) == chunkX && (entry.key().z() >> 4) == chunkZ)
                .collect(Collectors.toSet());
        Set<SecureHopperEntry> expectedHoppers = cache.hopperValues().stream()
                .filter(entry -> entry.hopperKey().worldUuid().equals(world))
                .filter(entry -> (entry.hopperKey().x() >> 4) == chunkX && (entry.hopperKey().z() >> 4) == chunkZ)
                .collect(Collectors.toSet());
        assertEquals(expectedLocks, locksIn(chunkX, chunkZ), "Sperren in Chunk " + chunkX + "/" + chunkZ);
        assertEquals(expectedHoppers, hoppersIn(chunkX, chunkZ), "Secure Hopper in Chunk " + chunkX + "/" + chunkZ);
    }

    @Test
    @DisplayName("putLock: Sperre erscheint in ihrem Chunk, nicht in den Nachbarn")
    void putLock() {
        LockEntry entry = put(lockAt(-17, 16, owner));

        assertEquals(Set.of(entry), locksIn(-2, 1));
        assertTrue(locksIn(-1, 1).isEmpty());
        assertTrue(locksIn(-2, 0).isEmpty());
        assertTrue(cache.locksInChunk(UUID.randomUUID(), -2, 1).isEmpty());
    }

    @Test
    @DisplayName("putLockIfAbsent: nur der erste Eintrag zaehlt, der Index enthaelt ihn einmal")
    void putLockIfAbsent() {
        LockEntry first = lockAt(5, 5, owner);
        LockEntry second = lockAt(5, 5, otherOwner);

        assertNull(cache.putLockIfAbsent(first.key().asId(), first));
        assertSame(first, cache.putLockIfAbsent(second.key().asId(), second));

        assertEquals(List.of(first), List.copyOf(cache.locksInChunk(world, 0, 0)));
    }

    @Test
    @DisplayName("removeLock: Eintrag und leerer Chunk verschwinden, unbekannte Schluessel aendern nichts")
    void removeLock() {
        LockEntry removed = put(lockAt(1, 1, owner));
        LockEntry kept = put(lockAt(40, 40, owner));

        assertSame(removed, cache.removeLock(removed.key().asId()));
        assertNull(cache.removeLock("unknown"));

        assertTrue(locksIn(0, 0).isEmpty());
        assertEquals(Set.of(kept), locksIn(2, 2));
    }

    @Test
    @DisplayName("removeLockAtomic: nur mit dem erwarteten Eintrag, sonst bleibt der Index unveraendert")
    void removeLockAtomic() {
        LockEntry cached = put(lockAt(3, 3, owner));
        LockEntry stale = lockAt(3, 3, owner);

        assertFalse(cache.removeLockAtomic(cached.key().asId(), stale));
        assertEquals(Set.of(cached), locksIn(0, 0));

        assertTrue(cache.removeLockAtomic(cached.key().asId(), cached));
        assertTrue(locksIn(0, 0).isEmpty());
    }

    @Test
    @DisplayName("Ersetzen mit gleichem Schluessel (Vertrauen, Uebertragen): Eintrag bleibt im Index, neuer Wert wird geliefert")
    void ersetzenGleicherSchluessel() {
        LockEntry original = put(lockAt(-5, -5, owner));
        LockEntry transferred = new LockEntry(original.key(), Material.CHEST, otherOwner, 1L);

        cache.putLock(original.key().asId(), original);
        assertEquals(Set.of(original), locksIn(-1, -1));
        cache.putLock(original.key().asId(), transferred);
        assertEquals(Set.of(transferred), locksIn(-1, -1));
    }

    @Test
    @DisplayName("Ersetzen unter derselben ID mit einer Position in einem anderen Chunk verschiebt den Eintrag")
    void ersetzenAndererChunk() {
        LockEntry first = lockAt(0, 0, owner);
        LockEntry moved = lockAt(100, 100, owner);

        cache.putLock("shared", first);
        cache.putLock("shared", moved);

        assertTrue(locksIn(0, 0).isEmpty());
        assertEquals(Set.of(moved), locksIn(6, 6));
    }

    @Test
    @DisplayName("clearLocksOfOwner: nur die Sperren des Besitzers verschwinden aus dem Index")
    void clearLocksOfOwner() {
        put(lockAt(1, 1, owner));
        put(lockAt(2, 2, owner));
        LockEntry foreign = put(lockAt(3, 3, otherOwner));
        put(lockAt(-30, 50, owner));

        assertEquals(3, cache.clearLocksOfOwner(owner));

        assertEquals(Set.of(foreign), locksIn(0, 0));
        assertTrue(locksIn(-2, 3).isEmpty());
        assertEquals(1, cache.countLocksFor(otherOwner));
    }

    @Test
    @DisplayName("Secure Hopper: putHopper, putHopperIfAbsent, removeHopper, clearHoppersOfOwner")
    void hopper() {
        SecureHopperEntry first = put(hopperAt(-1, 0, owner));
        SecureHopperEntry duplicate = hopperAt(-1, 0, otherOwner);
        SecureHopperEntry foreign = hopperAt(-2, 3, otherOwner);
        assertSame(first, cache.putHopperIfAbsent(duplicate.hopperKey().asId(), duplicate));
        assertNull(cache.putHopperIfAbsent(foreign.hopperKey().asId(), foreign));
        SecureHopperEntry second = put(hopperAt(20, 20, owner));

        assertEquals(Set.of(first, foreign), hoppersIn(-1, 0));
        assertEquals(Set.of(second), hoppersIn(1, 1));
        assertTrue(locksIn(-1, 0).isEmpty());

        assertSame(second, cache.removeHopper(second.hopperKey().asId()));
        assertTrue(hoppersIn(1, 1).isEmpty());
        assertEquals(1, cache.clearHoppersOfOwner(owner));
        assertEquals(Set.of(foreign), hoppersIn(-1, 0));
    }

    @Test
    @DisplayName("Abfrage liefert eine Kopie, spaetere Aenderungen wirken nicht auf sie")
    void abfrageIstKopie() {
        LockEntry entry = put(lockAt(1, 1, owner));
        var snapshot = cache.locksInChunk(world, 0, 0);

        cache.removeLock(entry.key().asId());
        put(lockAt(2, 2, owner));

        assertEquals(List.of(entry), List.copyOf(snapshot));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(entry));
    }

    @Test
    @DisplayName("Gleichzeitige Aenderungen aus mehreren Threads: Index stimmt mit dem Cache ueberein")
    void gleichzeitigeAenderungen() throws Exception {
        int threads = 8;
        int operations = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int threadNumber = thread;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int number = 0; number < operations; number++) {
                        int x = (number % 8) * 8 - 32;
                        int z = (threadNumber % 4) * 8 - 16;
                        LockEntry entry = lockAt(x, z, threadNumber % 2 == 0 ? owner : otherOwner);
                        String id = entry.key().asId();
                        switch (number % 5) {
                            case 0, 1 -> cache.putLock(id, entry);
                            case 2 -> cache.putLockIfAbsent(id, entry);
                            case 3 -> cache.removeLock(id);
                            default -> cache.putHopper(id, hopperAt(x, z, owner));
                        }
                        if (number % 97 == 0) cache.removeHopper(id);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertFalse(cache.lockValues().isEmpty());
        for (int chunkX = -3; chunkX <= 2; chunkX++) {
            for (int chunkZ = -2; chunkZ <= 1; chunkZ++) {
                assertConsistent(chunkX, chunkZ);
            }
        }
    }
}
