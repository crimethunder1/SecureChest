package de.SecureChest.plugin.lock;

public record LockStats(
        int totalLocks,
        int chestCount,
        int trappedChestCount,
        int barrelCount,
        int shulkerCount,
        int hopperCount,
        int furnaceCount,
        int blastFurnaceCount,
        int smokerCount,
        int dropperCount,
        int dispenserCount,
        int brewingStandCount,
        int crafterCount,
        int totalTrustedRelations,
        int totalSecureHoppers,
        long oldestLockAt,
        long newestLockAt
) {}
