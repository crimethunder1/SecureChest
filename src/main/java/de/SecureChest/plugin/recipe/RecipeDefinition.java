package de.SecureChest.plugin.recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RecipeDefinition(boolean enabled, List<String> shape, Map<String, String> ingredients, int amount) {

    public static final int DEFAULT_AMOUNT = 1;
    public static final int MINIMUM_AMOUNT = 1;
    public static final int MAXIMUM_AMOUNT = 64;

    public RecipeDefinition {
        shape = List.copyOf(shape);
        Map<String, String> orderedIngredients = new LinkedHashMap<>();
        ingredients.forEach((symbol, material) -> orderedIngredients.put(
                Objects.requireNonNull(symbol, "symbol"), Objects.requireNonNull(material, "material")));
        ingredients = Collections.unmodifiableMap(orderedIngredients);
    }

    public RecipeDefinition(boolean enabled, List<String> shape, Map<String, String> ingredients) {
        this(enabled, shape, ingredients, DEFAULT_AMOUNT);
    }
}
