package de.SecureChest.plugin.sign;

public final class NameVisibilityRules {

    private NameVisibilityRules() {}

    public static boolean isShown(boolean displayEnabled, boolean ownerShowsName,
                                  boolean hideWhenCovered, boolean textFieldCovered) {
        return displayEnabled && ownerShowsName && !(hideWhenCovered && textFieldCovered);
    }
}
