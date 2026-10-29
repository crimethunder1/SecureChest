package de.SecureChest.plugin.recipe;

import java.util.Objects;

public record RecipeValidationError(Code code, String detail) {

    public enum Code {
        SHAPE_ROW_COUNT,
        SHAPE_ROW_LENGTH,
        SHAPE_ROWS_UNEQUAL,
        SHAPE_EMPTY,
        INGREDIENT_SYMBOL_LENGTH,
        INGREDIENT_SYMBOL_SPACE,
        INGREDIENT_SYMBOL_NOT_IN_SHAPE,
        INGREDIENT_MATERIAL_INVALID,
        INGREDIENT_MATERIAL_AIR,
        AMOUNT_OUT_OF_RANGE,
        REGISTRATION_FAILED
    }

    public RecipeValidationError {
        Objects.requireNonNull(code, "code");
        detail = detail == null ? "" : detail;
    }
}
