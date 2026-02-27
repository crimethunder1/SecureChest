package de.SecureChest.plugin.ui;









public record ConfirmRequest(ConfirmType type, String data, String returnTo, long expiresAt) {
}
