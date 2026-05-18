package de.SecureChest.plugin.util;

public final class PlayerNameValidator {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;

    private PlayerNameValidator() {}

    public static boolean isValid(String name) {
        if (name == null) return false;
        int length = name.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) return false;
        return name.chars().allMatch(PlayerNameValidator::isAllowedChar);
    }

    private static boolean isAllowedChar(int codepoint) {
        return (codepoint >= 'a' && codepoint <= 'z')
                || (codepoint >= 'A' && codepoint <= 'Z')
                || (codepoint >= '0' && codepoint <= '9')
                || codepoint == '_';
    }
}
