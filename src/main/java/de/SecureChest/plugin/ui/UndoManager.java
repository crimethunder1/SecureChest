package de.SecureChest.plugin.ui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a single undo snapshot per player.
 */
public final class UndoManager {

    private final Map<UUID, UndoSnapshot> snapshots = new ConcurrentHashMap<>();

    public void set(UUID player, UndoSnapshot snapshot) {
        snapshots.put(player, snapshot);
    }

    public Optional<UndoSnapshot> get(UUID player) {
        UndoSnapshot s = snapshots.get(player);
        if (s == null) return Optional.empty();
        if (System.currentTimeMillis() > s.expiresAt()) {
            snapshots.remove(player);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    /**
     * Returns and clears the current snapshot.
     */
    public Optional<UndoSnapshot> consume(UUID player) {
        Optional<UndoSnapshot> s = get(player);
        snapshots.remove(player);
        return s;
    }

    public void clear(UUID player) {
        snapshots.remove(player);
    }
}
