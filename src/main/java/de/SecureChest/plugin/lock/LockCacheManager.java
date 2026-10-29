package de.SecureChest.plugin.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class LockCacheManager {

    private final Map<String, LockEntry>         lockCache   = new ConcurrentHashMap<>();
    private final Map<String, SecureHopperEntry> hopperCache = new ConcurrentHashMap<>();

    private final ChunkIndex lockChunkIndex   = new ChunkIndex();
    private final ChunkIndex hopperChunkIndex = new ChunkIndex();

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

    public Collection<LockEntry> locksInChunk(UUID worldUuid, int chunkX, int chunkZ) {
        return resolve(lockCache, lockChunkIndex.idsInChunk(worldUuid, chunkX, chunkZ));
    }

    public Collection<SecureHopperEntry> hoppersInChunk(UUID worldUuid, int chunkX, int chunkZ) {
        return resolve(hopperCache, hopperChunkIndex.idsInChunk(worldUuid, chunkX, chunkZ));
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
        LockEntry previous = store(lockCache, lockChunkIndex, LockEntry::key, id, entry, true);
        if (previous != null) decrementLockCounter(previous.owner());
        incrementLockCounter(entry.owner());
        return previous;
    }

    public LockEntry putLockIfAbsent(String id, LockEntry entry) {
        LockEntry existing = store(lockCache, lockChunkIndex, LockEntry::key, id, entry, false);
        if (existing == null) incrementLockCounter(entry.owner());
        return existing;
    }

    public LockEntry removeLock(String id) {
        LockEntry removed = discard(lockCache, lockChunkIndex, LockEntry::key, id, null);
        if (removed != null) decrementLockCounter(removed.owner());
        return removed;
    }

    public boolean removeLockAtomic(String id, LockEntry expected) {
        Objects.requireNonNull(expected, "expected");
        if (discard(lockCache, lockChunkIndex, LockEntry::key, id, expected) != null) {
            decrementLockCounter(expected.owner());
            return true;
        }
        return false;
    }

    public int clearLocksOfOwner(UUID owner) {
        int removed = 0;
        for (Map.Entry<String, LockEntry> entry : lockCache.entrySet()) {
            if (entry.getValue().owner().equals(owner)
                    && discard(lockCache, lockChunkIndex, LockEntry::key, entry.getKey(), entry.getValue()) != null) {
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
        SecureHopperEntry previous = store(hopperCache, hopperChunkIndex, SecureHopperEntry::hopperKey, id, entry, true);
        if (previous != null) decrementHopperCounter(previous.owner());
        incrementHopperCounter(entry.owner());
        return previous;
    }

    public SecureHopperEntry putHopperIfAbsent(String id, SecureHopperEntry entry) {
        SecureHopperEntry existing = store(hopperCache, hopperChunkIndex, SecureHopperEntry::hopperKey, id, entry, false);
        if (existing == null) incrementHopperCounter(entry.owner());
        return existing;
    }

    public SecureHopperEntry removeHopper(String id) {
        SecureHopperEntry removed = discard(hopperCache, hopperChunkIndex, SecureHopperEntry::hopperKey, id, null);
        if (removed != null) decrementHopperCounter(removed.owner());
        return removed;
    }

    public int clearHoppersOfOwner(UUID owner) {
        int removed = 0;
        for (Map.Entry<String, SecureHopperEntry> entry : hopperCache.entrySet()) {
            if (entry.getValue().owner().equals(owner)
                    && discard(hopperCache, hopperChunkIndex, SecureHopperEntry::hopperKey,
                            entry.getKey(), entry.getValue()) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            AtomicInteger counter = hopperCountByOwner.get(owner);
            if (counter != null) counter.addAndGet(-removed);
        }
        return removed;
    }

    private static <V> V store(Map<String, V> cache, ChunkIndex index, Function<V, LockKey> position,
                               String id, V entry, boolean replaceExisting) {
        AtomicReference<V> previous = new AtomicReference<>();
        cache.compute(id, (cacheId, current) -> {
            previous.set(current);
            if (current != null && !replaceExisting) return current;
            LockKey newPosition = position.apply(entry);
            index.add(newPosition, cacheId);
            if (current != null) {
                LockKey oldPosition = position.apply(current);
                if (!ChunkIndex.isSameChunk(oldPosition, newPosition)) index.remove(oldPosition, cacheId);
            }
            return entry;
        });
        return previous.get();
    }

    private static <V> V discard(Map<String, V> cache, ChunkIndex index, Function<V, LockKey> position,
                                 String id, V expected) {
        AtomicReference<V> removed = new AtomicReference<>();
        cache.computeIfPresent(id, (cacheId, current) -> {
            if (expected != null && !current.equals(expected)) return current;
            removed.set(current);
            index.remove(position.apply(current), cacheId);
            return null;
        });
        return removed.get();
    }

    private static <V> Collection<V> resolve(Map<String, V> cache, Collection<String> ids) {
        List<V> entries = new ArrayList<>(ids.size());
        for (String id : ids) {
            V entry = cache.get(id);
            if (entry != null) entries.add(entry);
        }
        return Collections.unmodifiableList(entries);
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
