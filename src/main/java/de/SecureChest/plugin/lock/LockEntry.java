package de.SecureChest.plugin.lock;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LockEntry {

    private final LockKey key;
    private final Material blockType;
    private final UUID owner;
    private final long createdAt;

    private final Map<UUID, AccessLevel> trustedInternal = new ConcurrentHashMap<>();

    /** Unveränderliche Sicht auf die Trust-Map – sicher für externe Caller. */
    private final Map<UUID, AccessLevel> trustedView = Collections.unmodifiableMap(trustedInternal);

    public LockEntry(LockKey key, Material blockType, UUID owner, long createdAt) {
        this.key = key;
        this.blockType = blockType;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    public LockKey key()       { return key; }
    public Material blockType(){ return blockType; }
    public UUID owner()        { return owner; }
    public long createdAt()    { return createdAt; }

    /**
     * Unveränderliche Sicht auf die vertrauten Spieler.
     * Für Lese-Zugriff von außen (GUI, Listener).
     */
    public Map<UUID, AccessLevel> trusted() { return trustedView; }

    /**
     * Veränderliche interne Map – nur für {@link SqlLockService} im selben Paket.
     * Externe Klassen sollen ausschließlich über {@link SqlLockService}-Methoden
     * (setTrust, removeTrust) mutieren.
     */
    Map<UUID, AccessLevel> trustedMutable() { return trustedInternal; }
}
