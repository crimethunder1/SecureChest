package de.SecureChest.plugin.lock;

import java.util.UUID;




public record LogRecord(
        long id,
        long createdAt,
        String action,
        UUID actorUuid,
        String actorName,
        String worldName,
        UUID worldUuid,
        int x,
        int y,
        int z,
        String extra
) {
}
