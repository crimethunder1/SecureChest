package de.SecureChest.plugin.lock;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkIndex {

    private record ChunkPosition(UUID worldUuid, int chunkX, int chunkZ) {

        static ChunkPosition of(LockKey position) {
            return new ChunkPosition(position.worldUuid(),
                    chunkCoordinate(position.x()), chunkCoordinate(position.z()));
        }
    }

    private final Map<ChunkPosition, Set<String>> idsByChunk = new ConcurrentHashMap<>();

    public static int chunkCoordinate(int blockCoordinate) {
        return blockCoordinate >> 4;
    }

    public static boolean isSameChunk(LockKey first, LockKey second) {
        return ChunkPosition.of(first).equals(ChunkPosition.of(second));
    }

    public void add(LockKey position, String id) {
        Objects.requireNonNull(id, "id");
        idsByChunk.compute(ChunkPosition.of(position), (chunk, ids) -> {
            Set<String> chunkIds = ids == null ? ConcurrentHashMap.newKeySet() : ids;
            chunkIds.add(id);
            return chunkIds;
        });
    }

    public void remove(LockKey position, String id) {
        Objects.requireNonNull(id, "id");
        idsByChunk.computeIfPresent(ChunkPosition.of(position), (chunk, ids) -> {
            ids.remove(id);
            return ids.isEmpty() ? null : ids;
        });
    }

    public void clear() {
        idsByChunk.clear();
    }

    public Set<String> idsInChunk(UUID worldUuid, int chunkX, int chunkZ) {
        Set<String> ids = idsByChunk.get(new ChunkPosition(worldUuid, chunkX, chunkZ));
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    int chunkCount() {
        return idsByChunk.size();
    }
}
