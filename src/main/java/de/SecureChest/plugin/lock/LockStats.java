package de.SecureChest.plugin.lock;

public record LockStats(
        int totalLocks,
        int chestCount,
        int trappedChestCount,
        int barrelCount,
        int shulkerCount,
        int hopperCount,
        int totalTrustedRelations,
        int totalSecureHoppers,
        long oldestLockAt,
        long newestLockAt
) {}
