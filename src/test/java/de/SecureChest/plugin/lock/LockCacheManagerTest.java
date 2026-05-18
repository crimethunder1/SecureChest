package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockCacheManager – Counter-Konsistenz unter allen Mutationen")
class LockCacheManagerTest {

    private LockCacheManager manager() {
        return new LockCacheManager();
    }

    private LockEntry lockFor(UUID owner) {
        return lockFor(owner, "lock-" + UUID.randomUUID());
    }

    private LockEntry lockFor(UUID owner, String idHint) {
        LockKey key = new LockKey(UUID.randomUUID(), idHint.hashCode(), 64, 0);
        return new LockEntry(key, Material.CHEST, owner, System.currentTimeMillis());
    }

    private SecureHopperEntry hopperFor(UUID owner) {
        LockKey hk = new LockKey(UUID.randomUUID(), 1, 64, 0);
        LockKey bk = new LockKey(UUID.randomUUID(), 2, 64, 0);
        return new SecureHopperEntry(hk, owner, bk);
    }

    @Test
    @DisplayName("Counter ist 0 fuer Owner ohne Locks")
    void counter_initialZero() {
        assertEquals(0, manager().countLocksFor(UUID.randomUUID()));
        assertEquals(0, manager().countHoppersFor(UUID.randomUUID()));
    }

    @Test
    @DisplayName("putLock erhoeht Counter um 1")
    void putLock_inkrementiertCounter() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        LockEntry entry = lockFor(owner, "abc");
        m.putLock(entry.key().asId(), entry);
        assertEquals(1, m.countLocksFor(owner));
    }

    @Test
    @DisplayName("removeLock dekrementiert Counter um 1")
    void removeLock_dekrementiertCounter() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        LockEntry entry = lockFor(owner, "abc");
        String id = entry.key().asId();
        m.putLock(id, entry);
        m.removeLock(id);
        assertEquals(0, m.countLocksFor(owner));
    }

    @Test
    @DisplayName("removeLock auf nicht-existenten Key veraendert Counter nicht")
    void removeLock_nichtExistent_keineAenderung() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        m.putLock("a", lockFor(owner, "a"));
        m.removeLock("does-not-exist");
        assertEquals(1, m.countLocksFor(owner));
    }

    @Test
    @DisplayName("putLockIfAbsent inkrementiert nur wenn Slot frei war")
    void putLockIfAbsent_inkrementiertNurBeiErfolg() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        LockEntry first = lockFor(owner, "abc");
        LockEntry second = lockFor(owner, "abc");
        String id = first.key().asId();

        assertNull(m.putLockIfAbsent(id, first));
        assertEquals(1, m.countLocksFor(owner));

        assertNotNull(m.putLockIfAbsent(id, second));
        assertEquals(1, m.countLocksFor(owner),
                "putIfAbsent darf bei besetztem Slot den Counter NICHT erhoehen");
    }

    @Test
    @DisplayName("removeLockAtomic dekrementiert Counter nur bei erfolgreichem Remove")
    void removeLockAtomic_dekrementiertNurBeiErfolg() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        LockEntry entry = lockFor(owner, "abc");
        String id = entry.key().asId();
        m.putLock(id, entry);

        LockEntry wrongExpected = lockFor(owner, "different");
        assertFalse(m.removeLockAtomic(id, wrongExpected));
        assertEquals(1, m.countLocksFor(owner), "Atomic-Remove mit falschem expected darf Counter nicht aendern");

        assertTrue(m.removeLockAtomic(id, entry));
        assertEquals(0, m.countLocksFor(owner));
    }

    @Test
    @DisplayName("putLock auf bestehenden Key mit Owner-Wechsel: alter Owner -1, neuer Owner +1")
    void putLock_ownerWechsel_balanciert() {
        LockCacheManager m = manager();
        UUID oldOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        LockEntry first = lockFor(oldOwner, "abc");
        String id = first.key().asId();
        m.putLock(id, first);

        LockEntry transferred = new LockEntry(first.key(), Material.CHEST, newOwner, first.createdAt());
        m.putLock(id, transferred);

        assertEquals(0, m.countLocksFor(oldOwner));
        assertEquals(1, m.countLocksFor(newOwner));
    }

    @Test
    @DisplayName("clearLocksOfOwner entfernt alle Locks und setzt Counter auf 0")
    void clearLocksOfOwner_komplett() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        LockEntry a = lockFor(owner, "a");
        LockEntry b = lockFor(owner, "b");
        LockEntry c = lockFor(otherOwner, "c");
        m.putLock(a.key().asId(), a);
        m.putLock(b.key().asId(), b);
        m.putLock(c.key().asId(), c);

        int removed = m.clearLocksOfOwner(owner);

        assertEquals(2, removed);
        assertEquals(0, m.countLocksFor(owner));
        assertEquals(1, m.countLocksFor(otherOwner),
                "Anderer Owner darf nicht betroffen sein");
        assertEquals(1, m.lockValues().size());
    }

    @Test
    @DisplayName("Mehrfaches put/remove balanciert Counter korrekt")
    void mehrfachOperationen_konsistent() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            LockEntry e = lockFor(owner, "k" + i);
            m.putLock(e.key().asId(), e);
        }
        assertEquals(100, m.countLocksFor(owner));

        int removedSoFar = 0;
        for (String key : m.lockEntries().stream().map(java.util.Map.Entry::getKey).toList()) {
            m.removeLock(key);
            removedSoFar++;
            assertEquals(100 - removedSoFar, m.countLocksFor(owner),
                    "Counter nach " + removedSoFar + " removes muss " + (100 - removedSoFar) + " sein");
        }
        assertEquals(0, m.countLocksFor(owner));
    }

    @Test
    @DisplayName("Hopper-Counter funktioniert analog zu Lock-Counter")
    void hopperCounter_analog() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        SecureHopperEntry h = hopperFor(owner);
        m.putHopper(h.hopperKey().asId(), h);
        assertEquals(1, m.countHoppersFor(owner));

        m.removeHopper(h.hopperKey().asId());
        assertEquals(0, m.countHoppersFor(owner));
    }

    @Test
    @DisplayName("clearHoppersOfOwner analog zu clearLocksOfOwner")
    void clearHoppersOfOwner_komplett() {
        LockCacheManager m = manager();
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        m.putHopper("a", hopperFor(owner));
        m.putHopper("b", hopperFor(owner));
        m.putHopper("c", hopperFor(otherOwner));

        int removed = m.clearHoppersOfOwner(owner);
        assertEquals(2, removed);
        assertEquals(0, m.countHoppersFor(owner));
        assertEquals(1, m.countHoppersFor(otherOwner));
    }
}
