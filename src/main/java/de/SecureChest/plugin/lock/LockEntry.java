package de.SecureChest.plugin.lock;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LockEntry {

    private final LockKey key;
    private final Material blockType;
    private final UUID owner;
    private final long createdAt;

    private final Map<UUID, AccessLevel> trusted = new ConcurrentHashMap<>();

    public LockEntry(LockKey key, Material blockType, UUID owner, long createdAt) {
        this.key = key;
        this.blockType = blockType;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    public LockKey key() { return key; }
    public Material blockType() { return blockType; }
    public UUID owner() { return owner; }
    public long createdAt() { return createdAt; }

    public Map<UUID, AccessLevel> trusted() { return trusted; }
}
