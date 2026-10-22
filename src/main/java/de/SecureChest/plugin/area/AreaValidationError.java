package de.SecureChest.plugin.area;

import java.util.Objects;

public record AreaValidationError(Code code, String detail) {

    public enum Code {
        NAME_INVALID,
        NAME_ALREADY_USED,
        AREA_NOT_FOUND,
        WORLD_NAME_MISSING,
        WORLD_NOT_FOUND
    }

    public AreaValidationError {
        Objects.requireNonNull(code, "code");
        detail = detail == null ? "" : detail;
    }
}
