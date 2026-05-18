package de.SecureChest.plugin.lock;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("bindSecureHopper – putIfAbsent Atomaritaet (SEC-09)")
class BindSecureHopperAtomicityTest {

    @Test
    @DisplayName("Genau ein paralleler Aufruf gewinnt, alle anderen returnen null")
    void parallel_putIfAbsent_genauEinSieger() throws InterruptedException {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        int threadCount = 20;
        String key = "lock-abc";
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger winnerCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startSignal.await();
                    UUID ownCandidate = UUID.randomUUID();
                    UUID previousValue = cache.putIfAbsent(key, ownCandidate);
                    if (previousValue == null) winnerCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }
        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS), "Timeout");
        pool.shutdown();

        assertEquals(1, winnerCount.get(),
                "Bei " + threadCount + " parallelen putIfAbsent-Aufrufen muss genau einer gewinnen");
        assertEquals(1, cache.size(), "Cache darf nur einen Eintrag haben");
    }

    @Test
    @DisplayName("putIfAbsent ueberschreibt bestehenden Wert nicht")
    void bestehenderWert_wirdNichtUeberschrieben() {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        String key = "lock-xyz";

        UUID firstResult = cache.putIfAbsent(key, firstOwner);
        UUID secondResult = cache.putIfAbsent(key, secondOwner);

        assertNull(firstResult, "Erster Aufruf in leeren Slot darf null zurueckgeben");
        assertEquals(firstOwner, secondResult,
                "Zweiter Aufruf muss bestehenden Wert zurueckgeben, nicht ueberschreiben");
        assertEquals(firstOwner, cache.get(key));
    }

    @Test
    @DisplayName("Rollback-Pattern: remove() nach fehlgeschlagenem persist")
    void rollback_pattern_funktioniert() {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        UUID candidate = UUID.randomUUID();
        String key = "lock-rollback";

        UUID putResult = cache.putIfAbsent(key, candidate);
        assertNull(putResult);
        assertTrue(cache.containsKey(key));

        cache.remove(key);
        assertFalse(cache.containsKey(key),
                "Nach Rollback muss der Slot wieder frei sein");

        UUID retryResult = cache.putIfAbsent(key, candidate);
        assertNull(retryResult, "Slot ist nach Rollback wieder verfuegbar");
    }
}
