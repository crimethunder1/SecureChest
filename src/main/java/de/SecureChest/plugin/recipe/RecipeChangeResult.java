package de.SecureChest.plugin.recipe;

import java.util.List;
import java.util.Objects;

public record RecipeChangeResult(RecipeKind kind,
                                 boolean applied,
                                 List<RecipeValidationError> errors,
                                 String saveError) {

    public RecipeChangeResult {
        Objects.requireNonNull(kind, "kind");
        errors = List.copyOf(errors);
    }

    static RecipeChangeResult rejected(RecipeKind kind, List<RecipeValidationError> errors) {
        return new RecipeChangeResult(kind, false, errors, null);
    }

    public boolean isSaved() {
        return applied && saveError == null;
    }
}
