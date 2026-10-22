package de.SecureChest.plugin.recipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RecipeDefinition(boolean enabled, List<String> shape, Map<String, String> ingredients) {

    public RecipeDefinition {
        shape = List.copyOf(shape);
        Map<String, String> orderedIngredients = new LinkedHashMap<>();
        ingredients.forEach((symbol, material) -> orderedIngredients.put(
                Objects.requireNonNull(symbol, "symbol"), Objects.requireNonNull(material, "material")));
        ingredients = Collections.unmodifiableMap(orderedIngredients);
    }
}
