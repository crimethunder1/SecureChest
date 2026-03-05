package de.SecureChest.plugin.lock;

import java.util.UUID;

/** A stored notification that should be shown to a player, possibly after re-login. */
public record Notification(long id, UUID ownerUuid, String message, long createdAt) {}
