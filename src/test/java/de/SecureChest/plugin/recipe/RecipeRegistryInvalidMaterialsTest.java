package de.SecureChest.plugin.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecipeRegistry - Pruefung der Zutaten-Materialien")
class RecipeRegistryInvalidMaterialsTest {

    private static ConfigurationSection ingredients(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader("ingredients:\n" + yaml))
                .getConfigurationSection("ingredients");
    }

    @Test
    @DisplayName("Standard-Zutaten des Chest-Key-Rezepts sind gueltig")
    void shippedChestKeyIngredientsAreValid() {
        ConfigurationSection section = ingredients(
                "  N: \"NETHERITE_INGOT\"\n  D: \"DIAMOND\"\n  H: \"TRIPWIRE_HOOK\"\n");

        assertTrue(RecipeRegistry.invalidMaterials(section, false).isEmpty());
    }

    @Test
    @DisplayName("Standard-Zutaten des Secure-Hopper-Rezepts mit CHEST_KEY sind gueltig")
    void shippedSecureHopperIngredientsAreValid() {
        ConfigurationSection section = ingredients(
                "  I: \"IRON_BLOCK\"\n  H: \"HOPPER\"\n  K: \"CHEST_KEY\"\n");

        assertTrue(RecipeRegistry.invalidMaterials(section, true).isEmpty());
    }

    @Test
    @DisplayName("Ungueltiger Materialname wird mit Zeichen und Wert gemeldet")
    void invalidMaterialIsReported() {
        ConfigurationSection section = ingredients(
                "  N: \"NETHERITE_INGOT\"\n  D: \"DIAMOND_X\"\n");

        assertEquals(Map.of("D", "DIAMOND_X"), RecipeRegistry.invalidMaterials(section, false));
    }

    @Test
    @DisplayName("Mehrere ungueltige Materialnamen werden alle in Config-Reihenfolge gemeldet")
    void allInvalidMaterialsAreReportedInOrder() {
        ConfigurationSection section = ingredients(
                "  A: \"STONE_X\"\n  B: \"DIAMOND\"\n  C: \"GOLD_X\"\n");

        Map<String, String> invalid = RecipeRegistry.invalidMaterials(section, false);

        assertEquals(List.of("A", "C"), List.copyOf(invalid.keySet()));
        assertEquals("STONE_X", invalid.get("A"));
        assertEquals("GOLD_X", invalid.get("C"));
    }

    @Test
    @DisplayName("CHEST_KEY ist im Chest-Key-Rezept kein gueltiges Material")
    void chestKeyIsInvalidWhenNotAllowed() {
        ConfigurationSection section = ingredients("  K: \"CHEST_KEY\"\n");

        assertEquals(Map.of("K", "CHEST_KEY"), RecipeRegistry.invalidMaterials(section, false));
    }

    @Test
    @DisplayName("CHEST_KEY wird ohne Ruecksicht auf Gross- und Kleinschreibung erkannt")
    void chestKeyIsCaseInsensitiveWhenAllowed() {
        ConfigurationSection section = ingredients("  K: \"chest_key\"\n");

        assertTrue(RecipeRegistry.invalidMaterials(section, true).isEmpty());
    }

    @Test
    @DisplayName("Materialnamen werden wie von matchMaterial akzeptiert erkannt")
    void lowercaseAndNamespacedMaterialsAreValid() {
        ConfigurationSection section = ingredients(
                "  D: \"diamond\"\n  H: \"minecraft:tripwire_hook\"\n");

        assertTrue(RecipeRegistry.invalidMaterials(section, false).isEmpty());
    }

    @Test
    @DisplayName("Schluessel laenger als ein Zeichen werden nicht geprueft")
    void keysLongerThanOneCharacterAreIgnored() {
        ConfigurationSection section = ingredients("  DD: \"DIAMOND_X\"\n");

        assertTrue(RecipeRegistry.invalidMaterials(section, false).isEmpty());
    }

    @Test
    @DisplayName("Fehlender Abschnitt ingredients ergibt keine ungueltigen Materialien")
    void missingIngredientsSectionIsEmpty() {
        assertTrue(RecipeRegistry.invalidMaterials(null, false).isEmpty());
        assertTrue(RecipeRegistry.invalidMaterials(null, true).isEmpty());
    }
}
