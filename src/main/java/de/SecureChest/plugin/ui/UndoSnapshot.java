<<<<<<< HEAD
package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.lock.LockKey;

import java.util.List;
import java.util.UUID;

public record UndoSnapshot(
        List<LockRow> locks,
        List<TrustRow> trusts,
        List<SecureHopperRow> secureHoppers,
        long createdAt,
        long expiresAt
) {

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
=======
package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.lock.LockKey;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot for UNLOCK operations only.
 */
public record UndoSnapshot(
        List<LockRow> locks,
        List<TrustRow> trusts,
        List<SecureHopperRow> secureHoppers,
        long createdAt,
        long expiresAt
) {

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
>>>>>>> a213fa6b4069e01c09e863d0744f947774f9fe82
