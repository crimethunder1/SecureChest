package de.SecureChest.plugin.lock;

import java.util.Map;
import java.util.UUID;

/** Combines the trust of a single lock with the default trust of its owner. */
public final class AccessResolver {

    private AccessResolver() {
    }

    /**
     * The trust set on the lock itself wins. Without it the default trust of the owner applies.
     * Returns null when the player has no access.
     */
    public static AccessLevel resolve(AccessLevel perLock, Map<UUID, AccessLevel> ownerDefaults, UUID player) {
        if (perLock != null) return perLock;
        return ownerDefaults == null ? null : ownerDefaults.get(player);
    }
}
