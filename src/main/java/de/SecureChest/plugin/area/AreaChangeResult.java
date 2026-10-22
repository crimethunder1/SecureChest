package de.SecureChest.plugin.area;

import java.util.List;
import java.util.Objects;

public record AreaChangeResult(AreaChange change,
                               boolean applied,
                               List<AreaValidationError> errors,
                               int removedLocks,
                               int removedSecureHoppers,
                               int notifiedOwners,
                               String saveError) {

    public AreaChangeResult {
        Objects.requireNonNull(change, "change");
        errors = List.copyOf(errors);
    }

    static AreaChangeResult rejected(AreaChange change, List<AreaValidationError> errors) {
        return new AreaChangeResult(change, false, errors, 0, 0, 0, null);
    }

    public boolean isSaved() {
        return applied && saveError == null;
    }
}
