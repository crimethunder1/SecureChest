package de.SecureChest.plugin.lock;

import java.util.UUID;

public record Notification(long id, UUID ownerUuid, String message, long createdAt) {}
