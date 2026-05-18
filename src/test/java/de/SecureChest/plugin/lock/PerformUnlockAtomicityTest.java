package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PerformUnlock – TOCTOU Atomic-Remove (SEC-15)")
class PerformUnlockAtomicityTest {

    @Test
    @DisplayName("ConcurrentHashMap.remove(key, value) ist atomar – genau ein Caller gewinnt")
    void atomicRemove_genauEinSieger() throws InterruptedException {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        UUID expectedValue = UUID.randomUUID();
        String key = "lock-abc";
        cache.put(key, expectedValue);

        int threadCount = 20;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger winnerCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startSignal.await();
                    if (cache.remove(key, expectedValue)) {
                        winnerCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }
        startSignal.countDown();
        assertTrue(doneSignal.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, winnerCount.get(),
                "Bei " + threadCount + " parallelen remove(k,v) auf gleichen Eintrag muss genau einer gewinnen");
        assertFalse(cache.containsKey(key));
    }

    @Test
    @DisplayName("remove(key, value) gibt false zurueck wenn Wert nicht (mehr) matched")
    void removeKeyValue_returnsFalse_wennWertGeaendert() {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        UUID originalValue = UUID.randomUUID();
        UUID otherValue = UUID.randomUUID();
        String key = "lock-xyz";
        cache.put(key, otherValue);

        boolean removed = cache.remove(key, originalValue);

        assertFalse(removed, "Wert hat nicht gematched, darf nicht removed worden sein");
        assertTrue(cache.containsKey(key), "Eintrag muss noch vorhanden sein");
        assertEquals(otherValue, cache.get(key));
    }

    @Test
    @DisplayName("remove(key, value) gibt false zurueck wenn Key nicht existiert")
    void removeKeyValue_returnsFalse_wennKeyFehlt() {
        ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();
        boolean removed = cache.remove("non-existing", UUID.randomUUID());
        assertFalse(removed);
    }
}
