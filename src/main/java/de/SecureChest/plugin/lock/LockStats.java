package de.SecureChest.plugin.lock;

/** Aggregated statistics about a player's locks. */
public record LockStats(
        int totalLocks,
        int chestCount,
        int trappedChestCount,
        int barrelCount,
        int shulkerCount,
        int hopperCount,
        int totalTrustedRelations,
        int totalSecureHoppers,
        long oldestLockAt,   // epoch millis, 0 = none
        long newestLockAt    // epoch millis, 0 = none
) {}
