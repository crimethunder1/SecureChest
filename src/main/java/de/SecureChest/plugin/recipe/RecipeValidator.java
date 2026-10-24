package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecipeValidator {

    private static final int ROW_COUNT = 3;
    private static final int MAX_ROW_LENGTH = 3;
    private static final char EMPTY_SLOT = ' ';
    private static final Set<Material> AIR_MATERIALS = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private RecipeValidator() {}

    public static List<RecipeValidationError> validate(RecipeKind kind, RecipeDefinition definition) {
        List<RecipeValidationError> errors = new ArrayList<>();
        validateShape(definition.shape(), errors);
        String shapeSymbols = String.join("", definition.shape());
        for (Map.Entry<String, String> ingredient : definition.ingredients().entrySet()) {
            validateSymbol(ingredient.getKey(), shapeSymbols, errors);
            validateMaterial(ingredient.getKey(), ingredient.getValue(), errors);
        }
        return List.copyOf(errors);
    }

    private static void validateShape(List<String> shape, List<RecipeValidationError> errors) {
        if (shape.size() != ROW_COUNT) {
            errors.add(new RecipeValidationError(Code.SHAPE_ROW_COUNT,
                    "shape must have exactly " + ROW_COUNT + " rows but has " + shape.size()));
        }
        for (int index = 0; index < shape.size(); index++) {
            int length = shape.get(index).length();
            if (length < 1 || length > MAX_ROW_LENGTH) {
                errors.add(new RecipeValidationError(Code.SHAPE_ROW_LENGTH,
                        "shape row " + (index + 1) + " must have 1 to " + MAX_ROW_LENGTH
                        + " characters but has " + length));
            }
        }
        if (shape.stream().map(String::length).distinct().count() > 1) {
            String lengths = shape.stream().map(row -> String.valueOf(row.length())).collect(Collectors.joining(", "));
            errors.add(new RecipeValidationError(Code.SHAPE_ROWS_UNEQUAL,
                    "shape rows must all have the same length but have the lengths " + lengths));
        }
        if (shape.stream().allMatch(row -> row.chars().allMatch(symbol -> symbol == EMPTY_SLOT))) {
            errors.add(new RecipeValidationError(Code.SHAPE_EMPTY,
                    "shape must contain at least one character that is not a space"));
        }
    }

    private static void validateSymbol(String symbol, String shapeSymbols, List<RecipeValidationError> errors) {
        if (symbol.length() != 1) {
            errors.add(new RecipeValidationError(Code.INGREDIENT_SYMBOL_LENGTH,
                    "ingredients." + symbol + " must be exactly one character"));
        } else if (symbol.charAt(0) == EMPTY_SLOT) {
            errors.add(new RecipeValidationError(Code.INGREDIENT_SYMBOL_SPACE,
                    "ingredients.' ' must not be a space, a space in the shape is an empty slot"));
        } else if (shapeSymbols.indexOf(symbol.charAt(0)) < 0) {
            errors.add(new RecipeValidationError(Code.INGREDIENT_SYMBOL_NOT_IN_SHAPE,
                    "ingredients." + symbol + " does not appear in the shape"));
        }
    }

    private static void validateMaterial(String symbol, String material, List<RecipeValidationError> errors) {
        Material matched = Material.matchMaterial(material);
        if (matched == null) {
            errors.add(new RecipeValidationError(Code.INGREDIENT_MATERIAL_INVALID,
                    "ingredients." + symbol + " has invalid material '" + material + "'"));
        } else if (AIR_MATERIALS.contains(matched)) {
            errors.add(new RecipeValidationError(Code.INGREDIENT_MATERIAL_AIR,
                    "ingredients." + symbol + " uses '" + material + "', air cannot be an ingredient"));
        }
    }
}
