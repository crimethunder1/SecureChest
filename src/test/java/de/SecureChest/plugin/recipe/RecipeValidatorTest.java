package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecipeValidator - Pruefung von Shape und Zutaten")
class RecipeValidatorTest {

    private static final List<String> FULL_SHAPE = List.of("NDN", "DHD", "NDN");

    private static Map<String, String> ingredients(String... symbolsAndMaterials) {
        Map<String, String> ingredients = new LinkedHashMap<>();
        for (int index = 0; index < symbolsAndMaterials.length; index += 2) {
            ingredients.put(symbolsAndMaterials[index], symbolsAndMaterials[index + 1]);
        }
        return ingredients;
    }

    private static RecipeDefinition definition(List<String> shape, Map<String, String> ingredients) {
        return new RecipeDefinition(true, shape, ingredients);
    }

    private static List<Code> codes(RecipeKind kind, RecipeDefinition definition) {
        return RecipeValidator.validate(kind, definition).stream().map(RecipeValidationError::code).toList();
    }

    private static RecipeDefinition shippedDefinition(RecipeKind kind) throws IOException {
        try (InputStream stream = RecipeValidatorTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(stream, "config.yml fehlt im Klassenpfad");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            return RecipeStore.readDefinition(config.getConfigurationSection("recipes." + kind.configKey()));
        }
    }

    @Test
    @DisplayName("Ausgeliefertes Chest-Key-Rezept aus der config.yml ist gueltig")
    void shippedChestKeyRecipeIsValid() throws IOException {
        RecipeDefinition shipped = shippedDefinition(RecipeKind.CHEST_KEY);

        assertNotNull(shipped);
        assertTrue(shipped.enabled());
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, shipped));
    }

    @Test
    @DisplayName("Zu wenige Zeilen: Fehler mit bisherigem Text")
    void tooFewRowsAreRejected() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("NDN", "DHD"), ingredients("N", "NETHERITE_INGOT")));

        assertEquals(List.of(new RecipeValidationError(Code.SHAPE_ROW_COUNT,
                "shape must have exactly 3 rows but has 2")), errors);
    }

    @Test
    @DisplayName("Zu viele Zeilen werden abgelehnt")
    void tooManyRowsAreRejected() {
        assertEquals(List.of(Code.SHAPE_ROW_COUNT), codes(RecipeKind.CHEST_KEY,
                definition(List.of("NDN", "DHD", "NDN", "NDN"), ingredients("N", "NETHERITE_INGOT"))));
    }

    @Test
    @DisplayName("Fehlende Shape: Zeilenzahl und leere Shape werden gemeldet")
    void missingShapeIsRejected() {
        assertEquals(List.of(Code.SHAPE_ROW_COUNT, Code.SHAPE_EMPTY),
                codes(RecipeKind.CHEST_KEY, definition(List.of(), ingredients())));
    }

    @Test
    @DisplayName("Zeile laenger als 3 Zeichen wird abgelehnt")
    void rowLongerThanThreeIsRejected() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("NDNN", "DHDD", "NDNN"), ingredients("N", "NETHERITE_INGOT")));

        assertEquals(List.of(Code.SHAPE_ROW_LENGTH, Code.SHAPE_ROW_LENGTH, Code.SHAPE_ROW_LENGTH),
                errors.stream().map(RecipeValidationError::code).toList());
        assertEquals("shape row 1 must have 1 to 3 characters but has 4", errors.getFirst().detail());
    }

    @Test
    @DisplayName("Leere Zeile wird abgelehnt und macht die Shape nicht rechteckig")
    void emptyRowIsRejected() {
        assertEquals(List.of(Code.SHAPE_ROW_LENGTH, Code.SHAPE_ROWS_UNEQUAL), codes(RecipeKind.CHEST_KEY,
                definition(List.of("NDN", "", "NDN"), ingredients("N", "NETHERITE_INGOT"))));
    }

    @Test
    @DisplayName("Nicht rechteckige Shape wird mit den Zeilenlaengen gemeldet")
    void unequalRowLengthsAreRejected() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("NDN", "DH", "NDN"), ingredients("N", "NETHERITE_INGOT")));

        assertEquals(List.of(new RecipeValidationError(Code.SHAPE_ROWS_UNEQUAL,
                "shape rows must all have the same length but have the lengths 3, 2, 3")), errors);
    }

    @Test
    @DisplayName("Schmale rechteckige Shapes mit 1 oder 2 Spalten sind gueltig")
    void narrowRectangularShapesAreValid() {
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("N", "D", "N"), ingredients("N", "NETHERITE_INGOT", "D", "DIAMOND"))));
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("ND", "  ", "DN"), ingredients("N", "NETHERITE_INGOT", "D", "DIAMOND"))));
    }

    @Test
    @DisplayName("Shape nur aus Leerzeichen wird abgelehnt")
    void shapeOfOnlySpacesIsRejected() {
        assertEquals(List.of(Code.SHAPE_EMPTY),
                codes(RecipeKind.CHEST_KEY, definition(List.of("   ", "   ", "   "), ingredients())));
    }

    @Test
    @DisplayName("Ein einziges Zeichen ausser Leerzeichen reicht fuer eine nicht leere Shape")
    void singleSymbolMakesShapeNonEmpty() {
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("   ", " D ", "   "), ingredients("D", "DIAMOND"))));
    }

    @Test
    @DisplayName("Zeichen in der Shape ohne Zutat bleibt ein leeres Feld und ist erlaubt")
    void shapeSymbolWithoutIngredientIsAllowed() {
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("N", "NETHERITE_INGOT", "D", "DIAMOND"))));
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients())));
    }

    @Test
    @DisplayName("Zutat, deren Zeichen nicht in der Shape vorkommt, wird abgelehnt")
    void ingredientNotInShapeIsRejected() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("N", "NETHERITE_INGOT", "X", "DIAMOND")));

        assertEquals(List.of(new RecipeValidationError(Code.INGREDIENT_SYMBOL_NOT_IN_SHAPE,
                "ingredients.X does not appear in the shape")), errors);
    }

    @Test
    @DisplayName("Zeichen werden mit Gross- und Kleinschreibung verglichen")
    void ingredientSymbolIsCaseSensitive() {
        assertEquals(List.of(Code.INGREDIENT_SYMBOL_NOT_IN_SHAPE),
                codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("n", "NETHERITE_INGOT"))));
    }

    @Test
    @DisplayName("Zutaten-Schluessel mit mehr als einem Zeichen wird abgelehnt")
    void ingredientSymbolLongerThanOneCharacterIsRejected() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("NN", "NETHERITE_INGOT")));

        assertEquals(List.of(new RecipeValidationError(Code.INGREDIENT_SYMBOL_LENGTH,
                "ingredients.NN must be exactly one character")), errors);
    }

    @Test
    @DisplayName("Leerer Zutaten-Schluessel wird abgelehnt")
    void emptyIngredientSymbolIsRejected() {
        assertEquals(List.of(Code.INGREDIENT_SYMBOL_LENGTH),
                codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("", "DIAMOND"))));
    }

    @Test
    @DisplayName("Leerzeichen als Zutaten-Schluessel wird abgelehnt, auch wenn es in der Shape vorkommt")
    void spaceAsIngredientSymbolIsRejected() {
        assertEquals(List.of(Code.INGREDIENT_SYMBOL_SPACE), codes(RecipeKind.CHEST_KEY,
                definition(List.of(" I ", "HKH", " I "), ingredients(" ", "STONE", "I", "IRON_BLOCK"))));
    }

    @Test
    @DisplayName("Ungueltiger Materialname wird mit Zeichen, Wert und bisherigem Text gemeldet")
    void invalidMaterialIsReported() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("N", "NETHERITE_INGOT", "D", "DIAMOND_X")));

        assertEquals(List.of(new RecipeValidationError(Code.INGREDIENT_MATERIAL_INVALID,
                "ingredients.D has invalid material 'DIAMOND_X'")), errors);
    }

    @Test
    @DisplayName("Mehrere ungueltige Materialnamen werden alle in Reihenfolge der Zutaten gemeldet")
    void allInvalidMaterialsAreReportedInOrder() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of("ABC", "ABC", "ABC"), ingredients("A", "STONE_X", "B", "DIAMOND", "C", "GOLD_X")));

        assertEquals(List.of(
                new RecipeValidationError(Code.INGREDIENT_MATERIAL_INVALID, "ingredients.A has invalid material 'STONE_X'"),
                new RecipeValidationError(Code.INGREDIENT_MATERIAL_INVALID, "ingredients.C has invalid material 'GOLD_X'")),
                errors);
    }

    @Test
    @DisplayName("Leerer Materialname wird abgelehnt")
    void emptyMaterialIsRejected() {
        assertEquals(List.of(Code.INGREDIENT_MATERIAL_INVALID),
                codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("N", ""))));
    }

    @Test
    @DisplayName("Materialnamen werden wie von matchMaterial akzeptiert erkannt")
    void lowercaseAndNamespacedMaterialsAreValid() {
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("D", "diamond", "H", "minecraft:tripwire_hook"))));
    }

    @Test
    @DisplayName("AIR, CAVE_AIR und VOID_AIR sind als Zutat verboten, auch klein und mit Namespace")
    void airIsRejectedAsIngredient() {
        for (String air : List.of("AIR", "CAVE_AIR", "VOID_AIR", "air", "minecraft:cave_air")) {
            assertEquals(List.of(Code.INGREDIENT_MATERIAL_AIR),
                    codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("D", air))), air);
        }
        assertEquals("ingredients.D uses 'AIR', air cannot be an ingredient",
                RecipeValidator.validate(RecipeKind.CHEST_KEY,
                        definition(FULL_SHAPE, ingredients("D", "AIR"))).getFirst().detail());
    }

    @Test
    @DisplayName("CHEST_KEY als Zutat ist ein unbekanntes Material")
    void chestKeyIngredientIsRejectedAsInvalidMaterial() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(FULL_SHAPE, ingredients("H", "CHEST_KEY")));

        assertEquals(List.of(new RecipeValidationError(Code.INGREDIENT_MATERIAL_INVALID,
                "ingredients.H has invalid material 'CHEST_KEY'")), errors);
    }

    @Test
    @DisplayName("CHEST_KEY in anderer Schreibweise ist ebenfalls ein unbekanntes Material")
    void chestKeyIngredientInOtherCaseIsRejectedAsInvalidMaterial() {
        for (String chestKey : List.of("chest_key", "Chest_Key")) {
            assertEquals(List.of(Code.INGREDIENT_MATERIAL_INVALID),
                    codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("H", chestKey))), chestKey);
        }
    }

    @Test
    @DisplayName("Zeichen- und Materialfehler derselben Zutat werden beide gemeldet")
    void symbolAndMaterialErrorsOfOneIngredientAreBothReported() {
        assertEquals(List.of(Code.INGREDIENT_SYMBOL_NOT_IN_SHAPE, Code.INGREDIENT_MATERIAL_INVALID),
                codes(RecipeKind.CHEST_KEY, definition(FULL_SHAPE, ingredients("X", "STONE_X"))));
    }

    @Test
    @DisplayName("Fehler in Shape und Zutaten werden zusammen gemeldet")
    void shapeAndIngredientErrorsAreCollected() {
        assertEquals(List.of(Code.SHAPE_ROW_COUNT, Code.SHAPE_ROWS_UNEQUAL, Code.INGREDIENT_MATERIAL_AIR),
                codes(RecipeKind.CHEST_KEY, definition(List.of("NDN", "D"), ingredients("N", "AIR"))));
    }

    @Test
    @DisplayName("Abgeschaltete Definition wird trotzdem inhaltlich geprueft")
    void disabledDefinitionIsStillValidated() {
        assertEquals(List.of(Code.SHAPE_ROW_COUNT), codes(RecipeKind.CHEST_KEY,
                new RecipeDefinition(false, List.of("NDN"), ingredients("N", "NETHERITE_INGOT"))));
    }

    @Test
    @DisplayName("Fehlerliste ist unveraenderlich")
    void errorListIsUnmodifiable() {
        List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY,
                definition(List.of(), ingredients()));

        assertThrows(UnsupportedOperationException.class, errors::clear);
    }
}
