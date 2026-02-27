package de.SecureChest.plugin.ui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores a single pending confirm request per player.
 */
public final class ConfirmManager {

    private final Map<UUID, ConfirmRequest> pending = new ConcurrentHashMap<>();

    public void set(UUID player, ConfirmRequest req) {
        pending.put(player, req);
    }

    public Optional<ConfirmRequest> get(UUID player) {
        ConfirmRequest req = pending.get(player);
        if (req == null) return Optional.empty();
        if (System.currentTimeMillis() > req.expiresAt()) {
            pending.remove(player);
            return Optional.empty();
        }
        return Optional.of(req);
    }

    public void clear(UUID player) {
        pending.remove(player);
    }
}
