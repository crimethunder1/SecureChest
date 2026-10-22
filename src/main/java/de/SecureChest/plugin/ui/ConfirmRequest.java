package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.area.AreaChange;

public record ConfirmRequest(ConfirmType type, String data, String returnTo, long expiresAt, AreaChange areaChange) {

    public ConfirmRequest(ConfirmType type, String data, String returnTo, long expiresAt) {
        this(type, data, returnTo, expiresAt, null);
    }
}
