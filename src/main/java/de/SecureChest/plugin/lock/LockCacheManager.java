package de.SecureChest.plugin.lock;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LockCacheManager {

    private final Map<String, LockEntry>         lockCache   = new ConcurrentHashMap<>();
    private final Map<String, SecureHopperEntry> hopperCache = new ConcurrentHashMap<>();

    private final Map<UUID, AtomicInteger> lockCountByOwner   = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> hopperCountByOwner = new ConcurrentHashMap<>();

    public LockEntry getLock(String id) {
        return lockCache.get(id);
    }

    public SecureHopperEntry getHopper(String id) {
        return hopperCache.get(id);
    }

    public boolean hasLock(String id) {
        return lockCache.containsKey(id);
    }

    public boolean hasHopper(String id) {
        return hopperCache.containsKey(id);
    }

    public Collection<LockEntry> lockValues() {
        return Collections.unmodifiableCollection(lockCache.values());
    }

    public Collection<SecureHopperEntry> hopperValues() {
        return Collections.unmodifiableCollection(hopperCache.values());
    }

    public Collection<Map.Entry<String, LockEntry>> lockEntries() {
        return Collections.unmodifiableCollection(lockCache.entrySet());
    }

    public Collection<Map.Entry<String, SecureHopperEntry>> hopperEntries() {
        return Collections.unmodifiableCollection(hopperCache.entrySet());
    }

    public int countLocksFor(UUID owner) {
        AtomicInteger counter = lockCountByOwner.get(owner);
        return counter == null ? 0 : counter.get();
    }

    public int countHoppersFor(UUID owner) {
        AtomicInteger counter = hopperCountByOwner.get(owner);
        return counter == null ? 0 : counter.get();
    }

    public LockEntry putLock(String id, LockEntry entry) {
        LockEntry previous = lockCache.put(id, entry);
        if (previous != null) decrementLockCounter(previous.owner());
        incrementLockCounter(entry.owner());
        return previous;
    }

    public LockEntry putLockIfAbsent(String id, LockEntry entry) {
        LockEntry existing = lockCache.putIfAbsent(id, entry);
        if (existing == null) incrementLockCounter(entry.owner());
        return existing;
    }

    public LockEntry removeLock(String id) {
        LockEntry removed = lockCache.remove(id);
        if (removed != null) decrementLockCounter(removed.owner());
        return removed;
    }

    public boolean removeLockAtomic(String id, LockEntry expected) {
        if (lockCache.remove(id, expected)) {
            decrementLockCounter(expected.owner());
            return true;
        }
        return false;
    }

    public void replaceLockOwner(String id, LockEntry transferred) {
        LockEntry previous = lockCache.put(id, transferred);
        if (previous != null) decrementLockCounter(previous.owner());
        incrementLockCounter(transferred.owner());
    }

    public int clearLocksOfOwner(UUID owner) {
        int removed = 0;
        var iterator = lockCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LockEntry> entry = iterator.next();
            if (entry.getValue().owner().equals(owner)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            AtomicInteger counter = lockCountByOwner.get(owner);
            if (counter != null) counter.addAndGet(-removed);
        }
        return removed;
    }

    public SecureHopperEntry putHopper(String id, SecureHopperEntry entry) {
        SecureHopperEntry previous = hopperCache.put(id, entry);
        if (previous != null) decrementHopperCounter(previous.owner());
        incrementHopperCounter(entry.owner());
        return previous;
    }

    public SecureHopperEntry putHopperIfAbsent(String id, SecureHopperEntry entry) {
        SecureHopperEntry existing = hopperCache.putIfAbsent(id, entry);
        if (existing == null) incrementHopperCounter(entry.owner());
        return existing;
    }

    public SecureHopperEntry removeHopper(String id) {
        SecureHopperEntry removed = hopperCache.remove(id);
        if (removed != null) decrementHopperCounter(removed.owner());
        return removed;
    }

    public int clearHoppersOfOwner(UUID owner) {
        int removed = 0;
        var iterator = hopperCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SecureHopperEntry> entry = iterator.next();
            if (entry.getValue().owner().equals(owner)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            AtomicInteger counter = hopperCountByOwner.get(owner);
            if (counter != null) counter.addAndGet(-removed);
        }
        return removed;
    }

    private void incrementLockCounter(UUID owner) {
        lockCountByOwner.computeIfAbsent(owner, k -> new AtomicInteger()).incrementAndGet();
    }

    private void decrementLockCounter(UUID owner) {
        AtomicInteger counter = lockCountByOwner.get(owner);
        if (counter != null && counter.decrementAndGet() <= 0) {
            lockCountByOwner.remove(owner, counter);
        }
    }

    private void incrementHopperCounter(UUID owner) {
        hopperCountByOwner.computeIfAbsent(owner, k -> new AtomicInteger()).incrementAndGet();
    }

    private void decrementHopperCounter(UUID owner) {
        AtomicInteger counter = hopperCountByOwner.get(owner);
        if (counter != null && counter.decrementAndGet() <= 0) {
            hopperCountByOwner.remove(owner, counter);
        }
    }
}
