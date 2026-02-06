package de.SecureChest.plugin.lock;

import java.util.UUID;

public final class SecureHopperEntry {

    private final LockKey hopperKey;
    private final UUID owner;
    private final LockKey boundLock;

    public SecureHopperEntry(LockKey hopperKey, UUID owner, LockKey boundLock) {
        this.hopperKey = hopperKey;
        this.owner = owner;
        this.boundLock = boundLock;
    }

    public LockKey hopperKey() { return hopperKey; }
    public UUID owner() { return owner; }
    public LockKey boundLock() { return boundLock; }
}
