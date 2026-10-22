package de.SecureChest.plugin.lock;

import java.util.List;

public record LockRemovalBatch(List<LockEntry> locks, List<SecureHopperEntry> secureHoppers) {

    public static final LockRemovalBatch EMPTY = new LockRemovalBatch(List.of(), List.of());

    public LockRemovalBatch {
        locks = List.copyOf(locks);
        secureHoppers = List.copyOf(secureHoppers);
    }

    public boolean isEmpty() {
        return locks.isEmpty() && secureHoppers.isEmpty();
    }
}
