package de.SecureChest.plugin.sign;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import de.SecureChest.plugin.lock.ChunkIndex;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SecureHopperEntry;

final class CachedNameGroupMembers implements NameGroupMemberLookup {

    private record ChunkPosition(UUID worldUuid, int chunkX, int chunkZ) {

        static ChunkPosition of(LockKey position) {
            return new ChunkPosition(position.worldUuid(),
                    ChunkIndex.chunkCoordinate(position.x()), ChunkIndex.chunkCoordinate(position.z()));
        }
    }

    private final LockService locks;
    private final Function<LockEntry, LockKey> doubleChestPartner;
    private final Set<ChunkPosition> readChunks = new HashSet<>();
    private final Map<LockKey, LockEntry> locksByPosition = new HashMap<>();
    private final Map<LockKey, SecureHopperEntry> hoppersByPosition = new HashMap<>();
    private final Map<LockKey, NameGroupMember> membersByPosition = new HashMap<>();

    CachedNameGroupMembers(LockService locks, Function<LockEntry, LockKey> doubleChestPartner) {
        this.locks = Objects.requireNonNull(locks, "locks");
        this.doubleChestPartner = Objects.requireNonNull(doubleChestPartner, "doubleChestPartner");
    }

    @Override
    public NameGroupMember memberAt(LockKey field) {
        NameGroupMember stored = memberStoredAt(field);
        if (stored != null) return stored;
        for (LockKey neighbour : NameGroupCalculator.lateralNeighbours(field)) {
            NameGroupMember candidate = memberStoredAt(neighbour);
            if (candidate != null && candidate.fields().contains(field)) return candidate;
        }
        return null;
    }

    private NameGroupMember memberStoredAt(LockKey position) {
        NameGroupMember known = membersByPosition.get(position);
        if (known != null) return known;
        readChunkOf(position);

        NameGroupMember member;
        LockEntry lock = locksByPosition.get(position);
        if (lock != null) {
            member = lockedBlock(position, lock);
        } else {
            SecureHopperEntry hopper = hoppersByPosition.get(position);
            if (hopper == null) return null;
            member = NameGroupMember.secureHopper(position, hopper.owner());
        }
        membersByPosition.put(position, member);
        return member;
    }

    private NameGroupMember lockedBlock(LockKey position, LockEntry lock) {
        LockKey partner = doubleChestPartner.apply(lock);
        if (partner == null || !NameGroupCalculator.isLateralNeighbour(position, partner) || isStored(partner)) {
            return NameGroupMember.lockedBlock(position, lock.owner());
        }
        return NameGroupMember.doubleChest(position, partner, lock.owner());
    }

    private boolean isStored(LockKey position) {
        readChunkOf(position);
        return locksByPosition.containsKey(position) || hoppersByPosition.containsKey(position);
    }

    private void readChunkOf(LockKey position) {
        ChunkPosition chunk = ChunkPosition.of(position);
        if (!readChunks.add(chunk)) return;
        for (LockEntry lock : locks.getCachedLocksInChunk(chunk.worldUuid(), chunk.chunkX(), chunk.chunkZ())) {
            locksByPosition.put(lock.key(), lock);
        }
        for (SecureHopperEntry hopper : locks.getCachedHoppersInChunk(chunk.worldUuid(), chunk.chunkX(), chunk.chunkZ())) {
            hoppersByPosition.put(hopper.hopperKey(), hopper);
        }
    }
}
