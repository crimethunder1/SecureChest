package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkIndex: Zuordnung von Chunk zu Sperren")
class ChunkIndexTest {

    private final UUID world = UUID.randomUUID();
    private final UUID otherWorld = UUID.randomUUID();
    private final ChunkIndex index = new ChunkIndex();

    private LockKey keyAt(int x, int z) {
        return new LockKey(world, x, 64, z);
    }

    @ParameterizedTest(name = "Block {0} liegt in Chunk {1}")
    @CsvSource({"0, 0", "15, 0", "16, 1", "31, 1", "32, 2", "-1, -1", "-16, -1", "-17, -2", "-32, -2", "-33, -3",
            "30000000, 1875000", "-30000000, -1875000"})
    @DisplayName("chunkCoordinate: arithmetische Verschiebung um 4 Bit, auch fuer negative Koordinaten")
    void chunkCoordinate(int blockCoordinate, int expectedChunk) {
        assertEquals(expectedChunk, ChunkIndex.chunkCoordinate(blockCoordinate));
    }

    @ParameterizedTest(name = "Block ({0}, {1}) liegt in Chunk ({2}, {3})")
    @CsvSource({"0, 0, 0, 0", "15, 15, 0, 0", "16, 0, 1, 0", "0, 16, 0, 1", "-1, -1, -1, -1",
            "-16, -16, -1, -1", "-17, -17, -2, -2", "15, -1, 0, -1", "-1, 16, -1, 1"})
    @DisplayName("add: Sperre landet im Chunk ihrer Koordinaten und nur dort")
    void addLandetImRichtigenChunk(int x, int z, int chunkX, int chunkZ) {
        LockKey key = keyAt(x, z);

        index.add(key, key.asId());

        assertEquals(Set.of(key.asId()), index.idsInChunk(world, chunkX, chunkZ));
        assertTrue(index.idsInChunk(world, chunkX + 1, chunkZ).isEmpty());
        assertTrue(index.idsInChunk(world, chunkX - 1, chunkZ).isEmpty());
        assertTrue(index.idsInChunk(world, chunkX, chunkZ + 1).isEmpty());
        assertTrue(index.idsInChunk(world, chunkX, chunkZ - 1).isEmpty());
        assertEquals(1, index.chunkCount());
    }

    @Test
    @DisplayName("add: Hoehe spielt keine Rolle, Grenzbloecke 0 und 15 teilen sich einen Chunk")
    void addMehrereImSelbenChunk() {
        LockKey low = new LockKey(world, 0, -64, 0);
        LockKey high = new LockKey(world, 15, 319, 15);

        index.add(low, low.asId());
        index.add(high, high.asId());

        assertEquals(Set.of(low.asId(), high.asId()), index.idsInChunk(world, 0, 0));
        assertEquals(1, index.chunkCount());
    }

    @Test
    @DisplayName("add: doppeltes Hinzufuegen ergibt einen Eintrag, ein Entfernen reicht")
    void addDoppelt() {
        LockKey key = keyAt(5, 5);

        index.add(key, key.asId());
        index.add(key, key.asId());

        assertEquals(Set.of(key.asId()), index.idsInChunk(world, 0, 0));
        index.remove(key, key.asId());
        assertTrue(index.idsInChunk(world, 0, 0).isEmpty());
        assertEquals(0, index.chunkCount());
    }

    @Test
    @DisplayName("remove: entfernt nur den angegebenen Schluessel")
    void removeEntferntNurDenSchluessel() {
        LockKey first = keyAt(1, 1);
        LockKey second = keyAt(2, 2);
        index.add(first, first.asId());
        index.add(second, second.asId());

        index.remove(first, first.asId());

        assertEquals(Set.of(second.asId()), index.idsInChunk(world, 0, 0));
        assertEquals(1, index.chunkCount());
    }

    @Test
    @DisplayName("remove: unbekannter Schluessel und unbekannter Chunk aendern nichts und werfen nicht")
    void removeUnbekannt() {
        LockKey key = keyAt(1, 1);
        index.add(key, key.asId());

        assertDoesNotThrow(() -> index.remove(key, "unknown"));
        assertDoesNotThrow(() -> index.remove(keyAt(100, 100), key.asId()));
        assertDoesNotThrow(() -> new ChunkIndex().remove(key, key.asId()));

        assertEquals(Set.of(key.asId()), index.idsInChunk(world, 0, 0));
        assertEquals(1, index.chunkCount());
    }

    @Test
    @DisplayName("remove: der letzte Schluessel eines Chunks entfernt den Chunk aus dem Index")
    void leererChunkWirdEntfernt() {
        LockKey first = keyAt(-1, -1);
        LockKey second = keyAt(-16, -16);
        LockKey elsewhere = keyAt(40, 40);
        index.add(first, first.asId());
        index.add(second, second.asId());
        index.add(elsewhere, elsewhere.asId());
        assertEquals(2, index.chunkCount());

        index.remove(first, first.asId());
        assertEquals(2, index.chunkCount());
        index.remove(second, second.asId());

        assertEquals(1, index.chunkCount());
        assertTrue(index.idsInChunk(world, -1, -1).isEmpty());
        assertEquals(Set.of(elsewhere.asId()), index.idsInChunk(world, 2, 2));
    }

    @Test
    @DisplayName("Mehrere Welten: gleiche Koordinaten sind getrennte Chunks")
    void mehrereWelten() {
        LockKey here = new LockKey(world, 5, 64, 5);
        LockKey there = new LockKey(otherWorld, 5, 64, 5);

        index.add(here, here.asId());
        index.add(there, there.asId());

        assertEquals(Set.of(here.asId()), index.idsInChunk(world, 0, 0));
        assertEquals(Set.of(there.asId()), index.idsInChunk(otherWorld, 0, 0));
        assertEquals(2, index.chunkCount());

        index.remove(there, there.asId());
        assertEquals(Set.of(here.asId()), index.idsInChunk(world, 0, 0));
        assertTrue(index.idsInChunk(otherWorld, 0, 0).isEmpty());
    }

    @Test
    @DisplayName("idsInChunk: liefert eine Kopie, die sich spaeter nicht mehr aendert und nicht veraenderbar ist")
    void abfrageIstKopie() {
        LockKey first = keyAt(1, 1);
        LockKey second = keyAt(2, 2);
        index.add(first, first.asId());

        Set<String> snapshot = index.idsInChunk(world, 0, 0);
        index.add(second, second.asId());
        index.remove(first, first.asId());

        assertEquals(Set.of(first.asId()), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("other"));
        assertThrows(UnsupportedOperationException.class, () -> index.idsInChunk(world, 5, 5).add("other"));
        assertEquals(Set.of(second.asId()), index.idsInChunk(world, 0, 0));
    }

    @Test
    @DisplayName("idsInChunk: unbekannter Chunk ergibt eine leere Menge, Abfrage legt keinen Chunk an")
    void abfrageOhneNebenwirkung() {
        assertTrue(index.idsInChunk(world, 7, -7).isEmpty());
        assertEquals(0, index.chunkCount());
    }

    @Test
    @DisplayName("clear: entfernt alle Chunks aller Welten")
    void clearEntferntAlles() {
        LockKey here = new LockKey(world, 5, 64, 5);
        LockKey there = new LockKey(otherWorld, -50, 64, 70);
        index.add(here, here.asId());
        index.add(there, there.asId());

        index.clear();

        assertEquals(0, index.chunkCount());
        assertTrue(index.idsInChunk(world, 0, 0).isEmpty());
        assertTrue(index.idsInChunk(otherWorld, -4, 4).isEmpty());
        index.add(here, here.asId());
        assertEquals(Set.of(here.asId()), index.idsInChunk(world, 0, 0));
    }

    @Test
    @DisplayName("isSameChunk: gleiche Welt und gleiche Chunk-Koordinaten, Hoehe egal")
    void isSameChunk() {
        assertTrue(ChunkIndex.isSameChunk(keyAt(0, 0), new LockKey(world, 15, 10, 15)));
        assertFalse(ChunkIndex.isSameChunk(keyAt(15, 0), keyAt(16, 0)));
        assertFalse(ChunkIndex.isSameChunk(keyAt(0, 0), keyAt(-1, 0)));
        assertFalse(ChunkIndex.isSameChunk(keyAt(0, 0), new LockKey(otherWorld, 0, 64, 0)));
    }

    @Test
    @DisplayName("null als Schluessel wird abgelehnt")
    void nullSchluessel() {
        assertThrows(NullPointerException.class, () -> index.add(keyAt(0, 0), null));
        assertThrows(NullPointerException.class, () -> index.remove(keyAt(0, 0), null));
        assertEquals(0, index.chunkCount());
    }

    @Test
    @DisplayName("Gleichzeitige Zugriffe: keine Ausnahme, Ergebnis entspricht den verbliebenen Schluesseln")
    void gleichzeitigeZugriffe() throws Exception {
        int threads = 8;
        int keysPerThread = 400;
        ExecutorService executor = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writersDone = new AtomicBoolean();
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int threadNumber = thread;
                writers.add(executor.submit(() -> {
                    start.await();
                    for (int number = 0; number < keysPerThread; number++) {
                        LockKey key = new LockKey(world, (number % 4) * 16 - 32, threadNumber, number);
                        index.add(key, key.asId());
                        if (number % 2 == 1) index.remove(key, key.asId());
                    }
                    return null;
                }));
            }
            Future<?> reader = executor.submit(() -> {
                start.await();
                while (!writersDone.get()) {
                    for (int chunkX = -2; chunkX <= 1; chunkX++) {
                        for (int chunkZ = 0; chunkZ < keysPerThread / 16; chunkZ++) {
                            for (String id : index.idsInChunk(world, chunkX, chunkZ)) assertNotNull(id);
                        }
                    }
                }
                return null;
            });
            start.countDown();
            for (Future<?> writer : writers) writer.get(30, TimeUnit.SECONDS);
            writersDone.set(true);
            reader.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Set<String> expected = new HashSet<>();
        Set<String> actual = new HashSet<>();
        Set<List<Integer>> expectedChunks = new HashSet<>();
        for (int thread = 0; thread < threads; thread++) {
            for (int number = 0; number < keysPerThread; number += 2) {
                LockKey key = new LockKey(world, (number % 4) * 16 - 32, thread, number);
                expected.add(key.asId());
                expectedChunks.add(List.of(key.x() >> 4, key.z() >> 4));
            }
        }
        for (int chunkX = -2; chunkX <= 1; chunkX++) {
            for (int chunkZ = 0; chunkZ < keysPerThread / 16; chunkZ++) {
                actual.addAll(index.idsInChunk(world, chunkX, chunkZ));
            }
        }
        assertEquals(expected, actual);
        assertEquals(expectedChunks.size(), index.chunkCount());
    }

    @Test
    @DisplayName("Gleichzeitiges Leeren und Fuellen eines Chunks verliert keinen Schluessel")
    void gleichzeitigLeerenUndFuellen() throws Exception {
        int rounds = 20_000;
        LockKey flickering = keyAt(1, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> stableIds = new ArrayList<>();
        try {
            Future<?> flicker = executor.submit(() -> {
                start.await();
                for (int round = 0; round < rounds; round++) {
                    index.add(flickering, flickering.asId());
                    index.remove(flickering, flickering.asId());
                }
                return null;
            });
            Future<?> filler = executor.submit(() -> {
                start.await();
                for (int number = 0; number < rounds / 10; number++) {
                    LockKey key = new LockKey(world, 2, number, 2);
                    index.add(key, key.asId());
                    synchronized (stableIds) {
                        stableIds.add(key.asId());
                    }
                }
                return null;
            });
            start.countDown();
            flicker.get(30, TimeUnit.SECONDS);
            filler.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(Set.copyOf(stableIds), index.idsInChunk(world, 0, 0));
        assertEquals(1, index.chunkCount());
    }
}
