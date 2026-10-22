package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecipeRegistry - Auswahl der wirksamen Definition")
class RecipeRegistrySelectionTest {

    private static final RecipeDefinition VALID_CONFIG = new RecipeDefinition(true,
            List.of("NDN", "DHD", "NDN"), Map.of("N", "NETHERITE_INGOT", "D", "DIAMOND", "H", "TRIPWIRE_HOOK"));
    private static final RecipeDefinition VALID_OVERRIDE = new RecipeDefinition(true,
            List.of("DDD", "DHD", "DDD"), Map.of("D", "DIAMOND", "H", "TRIPWIRE_HOOK"));
    private static final RecipeDefinition INVALID_ROW_COUNT = new RecipeDefinition(true,
            List.of("DDD", "DHD"), Map.of("D", "DIAMOND", "H", "TRIPWIRE_HOOK"));
    private static final RecipeDefinition INVALID_MATERIAL = new RecipeDefinition(true,
            List.of("DDD", "DHD", "DDD"), Map.of("D", "DIAMOND_X", "H", "TRIPWIRE_HOOK"));

    private static List<Code> codes(List<RecipeValidationError> errors) {
        return errors.stream().map(RecipeValidationError::code).toList();
    }

    @Test
    @DisplayName("Gueltige Ueberschreibung gewinnt, config.yml wird nicht geprueft")
    void validOverrideWins() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, VALID_OVERRIDE, INVALID_MATERIAL);

        assertSame(VALID_OVERRIDE, selection.definition());
        assertTrue(selection.fromOverride());
        assertEquals(List.of(), selection.overrideErrors());
        assertEquals(List.of(), selection.defaultErrors());
    }

    @Test
    @DisplayName("Ungueltige Ueberschreibung faellt auf gueltige config.yml zurueck und meldet ihre Fehler")
    void invalidOverrideFallsBackToConfig() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, INVALID_ROW_COUNT, VALID_CONFIG);

        assertSame(VALID_CONFIG, selection.definition());
        assertFalse(selection.fromOverride());
        assertEquals(List.of(Code.SHAPE_ROW_COUNT), codes(selection.overrideErrors()));
        assertEquals(List.of(), selection.defaultErrors());
    }

    @Test
    @DisplayName("Ungueltige Ueberschreibung und ungueltige config.yml: kein Rezept, beide Fehlerlisten")
    void invalidOverrideAndInvalidConfigGiveNoRecipe() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, INVALID_ROW_COUNT, INVALID_MATERIAL);

        assertNull(selection.definition());
        assertFalse(selection.fromOverride());
        assertEquals(List.of(Code.SHAPE_ROW_COUNT), codes(selection.overrideErrors()));
        assertEquals(List.of(Code.INGREDIENT_MATERIAL_INVALID), codes(selection.defaultErrors()));
    }

    @Test
    @DisplayName("Ohne Ueberschreibung gilt die gueltige config.yml")
    void withoutOverrideConfigApplies() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, null, VALID_CONFIG);

        assertSame(VALID_CONFIG, selection.definition());
        assertFalse(selection.fromOverride());
        assertEquals(List.of(), selection.overrideErrors());
        assertEquals(List.of(), selection.defaultErrors());
    }

    @Test
    @DisplayName("Ohne Ueberschreibung und mit ungueltiger config.yml: kein Rezept, Fehler der config.yml")
    void withoutOverrideInvalidConfigGivesNoRecipe() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, null, INVALID_ROW_COUNT);

        assertNull(selection.definition());
        assertEquals(List.of(), selection.overrideErrors());
        assertEquals(List.of(Code.SHAPE_ROW_COUNT), codes(selection.defaultErrors()));
    }

    @Test
    @DisplayName("Weder Ueberschreibung noch config.yml-Abschnitt: kein Rezept, keine Fehler")
    void nothingConfiguredGivesNoRecipe() {
        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.SECURE_HOPPER, null, null);

        assertNull(selection.definition());
        assertEquals(List.of(), selection.overrideErrors());
        assertEquals(List.of(), selection.defaultErrors());
    }

    @Test
    @DisplayName("Abgeschaltete Ueberschreibung gewinnt ohne Pruefung, auch mit ungueltigem Inhalt")
    void disabledOverrideWinsWithoutValidation() {
        RecipeDefinition disabledInvalid = new RecipeDefinition(false, List.of("D"), Map.of("X", "AIR"));

        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, disabledInvalid, VALID_CONFIG);

        assertSame(disabledInvalid, selection.definition());
        assertTrue(selection.fromOverride());
        assertEquals(List.of(), selection.overrideErrors());
    }

    @Test
    @DisplayName("Abgeschaltetes Rezept in der config.yml wird wie bisher nicht geprueft")
    void disabledConfigIsNotValidated() {
        RecipeDefinition disabledInvalid = new RecipeDefinition(false, List.of(), Map.of());

        RecipeRegistry.Selection selection = RecipeRegistry.select(RecipeKind.CHEST_KEY, null, disabledInvalid);

        assertSame(disabledInvalid, selection.definition());
        assertFalse(selection.definition().enabled());
        assertEquals(List.of(), selection.defaultErrors());
    }

    @Test
    @DisplayName("CHEST_KEY in der Ueberschreibung des Chest-Key-Rezepts fuehrt zum Fallback")
    void chestKeyInChestKeyOverrideFallsBack() {
        RecipeDefinition withChestKey = new RecipeDefinition(true,
                List.of("DDD", "DKD", "DDD"), Map.of("D", "DIAMOND", "K", "CHEST_KEY"));

        RecipeRegistry.Selection chestKeySelection = RecipeRegistry.select(RecipeKind.CHEST_KEY, withChestKey, VALID_CONFIG);
        RecipeRegistry.Selection hopperSelection = RecipeRegistry.select(RecipeKind.SECURE_HOPPER, withChestKey, null);

        assertSame(VALID_CONFIG, chestKeySelection.definition());
        assertEquals(List.of(Code.INGREDIENT_CHEST_KEY_NOT_ALLOWED), codes(chestKeySelection.overrideErrors()));
        assertSame(withChestKey, hopperSelection.definition());
        assertTrue(hopperSelection.fromOverride());
    }
}
