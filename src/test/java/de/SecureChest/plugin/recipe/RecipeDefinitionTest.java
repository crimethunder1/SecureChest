package de.SecureChest.plugin.recipe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecipeDefinition - unveraenderliche Kopie")
class RecipeDefinitionTest {

    @Test
    @DisplayName("Shape und Zutaten werden kopiert und sind unveraenderlich")
    void shapeAndIngredientsAreCopiedAndUnmodifiable() {
        List<String> shape = new ArrayList<>(List.of("NDN", "DHD", "NDN"));
        Map<String, String> ingredients = new LinkedHashMap<>(Map.of("N", "NETHERITE_INGOT"));
        RecipeDefinition definition = new RecipeDefinition(true, shape, ingredients);

        shape.set(0, "XXX");
        ingredients.put("D", "DIAMOND");

        assertEquals(List.of("NDN", "DHD", "NDN"), definition.shape());
        assertEquals(Map.of("N", "NETHERITE_INGOT"), definition.ingredients());
        assertThrows(UnsupportedOperationException.class, () -> definition.shape().add("NDN"));
        assertThrows(UnsupportedOperationException.class, () -> definition.ingredients().put("H", "HOPPER"));
    }

    @Test
    @DisplayName("Reihenfolge der Zutaten bleibt erhalten")
    void ingredientOrderIsKept() {
        Map<String, String> ingredients = new LinkedHashMap<>();
        ingredients.put("Z", "STONE");
        ingredients.put("A", "DIAMOND");
        ingredients.put("M", "HOPPER");

        RecipeDefinition definition = new RecipeDefinition(true, List.of("ZAM", "ZAM", "ZAM"), ingredients);

        assertEquals(List.of("Z", "A", "M"), List.copyOf(definition.ingredients().keySet()));
    }

    @Test
    @DisplayName("Gleicher Inhalt ergibt gleiche Definitionen")
    void equalContentIsEqual() {
        assertEquals(new RecipeDefinition(true, List.of("N", "N", "N"), Map.of("N", "STONE")),
                new RecipeDefinition(true, new ArrayList<>(List.of("N", "N", "N")),
                        new LinkedHashMap<>(Map.of("N", "STONE"))));
    }

    @Test
    @DisplayName("Null in Shape oder Zutaten wird abgelehnt")
    void nullValuesAreRejected() {
        List<String> shapeWithNull = new ArrayList<>();
        shapeWithNull.add(null);
        Map<String, String> ingredientsWithNull = new LinkedHashMap<>();
        ingredientsWithNull.put("N", null);

        assertThrows(NullPointerException.class, () -> new RecipeDefinition(true, shapeWithNull, Map.of()));
        assertThrows(NullPointerException.class,
                () -> new RecipeDefinition(true, List.of("N", "N", "N"), ingredientsWithNull));
    }
}
