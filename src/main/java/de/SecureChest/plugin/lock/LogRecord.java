<<<<<<< HEAD
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
=======
package de.SecureChest.plugin.lock;

import java.util.UUID;

/**
 * Single log record for admin GUI.
 */
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
>>>>>>> a213fa6b4069e01c09e863d0744f947774f9fe82
