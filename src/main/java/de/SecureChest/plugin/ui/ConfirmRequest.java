package de.SecureChest.plugin.ui;

/**
 * A pending confirmation.
 *
 * @param type confirmation type
 * @param data context data (type-specific)
 * @param returnTo return route after completion (e.g. MAIN, LOCK_MANAGE|<lockId>, ADMIN, LOGS)
 * @param expiresAt epoch millis
 */
public record ConfirmRequest(ConfirmType type, String data, String returnTo, long expiresAt) {
}
