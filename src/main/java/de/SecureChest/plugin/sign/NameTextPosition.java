package de.SecureChest.plugin.sign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.SecureChest.plugin.lock.ChunkIndex;
import de.SecureChest.plugin.lock.LockKey;

public record NameTextPosition(UUID worldUuid, double x, double y, double z) {

    public static final double HEIGHT_ABOVE_BLOCK = 1.35;

    public NameTextPosition {
        Objects.requireNonNull(worldUuid, "worldUuid");
    }

    public static NameTextPosition above(Collection<LockKey> fields) {
        if (fields.isEmpty()) throw new IllegalArgumentException("At least one field is required");
        LockKey first = fields.iterator().next();
        long sumX = 0;
        long sumZ = 0;
        for (LockKey field : fields) {
            if (!field.worldUuid().equals(first.worldUuid()) || field.y() != first.y()) {
                throw new IllegalArgumentException("All fields must be in the same world at the same height");
            }
            sumX += field.x();
            sumZ += field.z();
        }
        int count = fields.size();
        return new NameTextPosition(first.worldUuid(),
                (double) sumX / count + 0.5,
                first.y() + HEIGHT_ABOVE_BLOCK,
                (double) sumZ / count + 0.5);
    }

    public int chunkX() {
        return ChunkIndex.chunkCoordinate((int) Math.floor(x));
    }

    public int chunkZ() {
        return ChunkIndex.chunkCoordinate((int) Math.floor(z));
    }

    public LockKey textField() {
        return new LockKey(worldUuid, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public List<LockKey> touchedTextFields() {
        int fieldY = (int) Math.floor(y);
        List<LockKey> fields = new ArrayList<>(4);
        for (int fieldX : touchedCoordinates(x)) {
            for (int fieldZ : touchedCoordinates(z)) fields.add(new LockKey(worldUuid, fieldX, fieldY, fieldZ));
        }
        return List.copyOf(fields);
    }

    private static int[] touchedCoordinates(double coordinate) {
        int floor = (int) Math.floor(coordinate);
        return floor == coordinate ? new int[] {floor - 1, floor} : new int[] {floor};
    }
}
