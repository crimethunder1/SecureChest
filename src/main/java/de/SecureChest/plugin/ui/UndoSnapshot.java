package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.lock.LockKey;

import java.util.List;
import java.util.UUID;

public record UndoSnapshot(
        List<LockRow> locks,
        List<TrustRow> trusts,
        List<SecureHopperRow> secureHoppers,
        long createdAt,
        long expiresAt,
        List<LockRow> locksAfterAction
) {

    public UndoSnapshot(List<LockRow> locks, List<TrustRow> trusts, List<SecureHopperRow> secureHoppers,
                        long createdAt, long expiresAt) {
        this(locks, trusts, secureHoppers, createdAt, expiresAt, List.of());
    }

    public record LockRow(
            LockKey key,
            String blockType,
            UUID owner,
            long createdAt
    ) {}

    public record TrustRow(
            LockKey key,
            UUID target,
            String level
    ) {}

    public record SecureHopperRow(
            LockKey hopperKey,
            UUID owner,
            LockKey boundKey,
            long createdAt
    ) {}
}
