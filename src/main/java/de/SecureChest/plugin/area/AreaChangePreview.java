package de.SecureChest.plugin.area;

import java.util.List;
import java.util.Objects;

public record AreaChangePreview(AreaChange change,
                                List<AreaValidationError> errors,
                                int affectedLocks,
                                int affectedSecureHoppers,
                                int affectedOwners) {

    public AreaChangePreview {
        Objects.requireNonNull(change, "change");
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
