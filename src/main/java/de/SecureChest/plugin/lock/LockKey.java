package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class LockKey {
    private final UUID worldUuid;
    private final int x;
    private final int y;
    private final int z;

    public LockKey(UUID worldUuid, int x, int y, int z) {
        this.worldUuid = Objects.requireNonNull(worldUuid, "worldUuid");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID worldUuid() { return worldUuid; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public static LockKey fromLocation(Location loc) {
        World w = Objects.requireNonNull(loc.getWorld(), "world");
        return new LockKey(w.getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public String asId() {
        return worldUuid + ":" + x + ":" + y + ":" + z;
    }

    public static LockKey fromId(String id) {
        String[] parts = id.split(":");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid lockId");
        UUID w = UUID.fromString(parts[0]);
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int z = Integer.parseInt(parts[3]);
        return new LockKey(w, x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LockKey other)) return false;
        return x == other.x && y == other.y && z == other.z && worldUuid.equals(other.worldUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldUuid, x, y, z);
    }
}
