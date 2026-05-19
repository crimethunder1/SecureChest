package de.SecureChest.plugin.lock;

public enum AccessLevel {
    VIEW,
    STOCKER;

    public static AccessLevel fromString(String s) {
        if (s == null) return null;
        try {
            return AccessLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
