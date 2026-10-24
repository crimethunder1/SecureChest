package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecipeEditorModel - Arbeitskopie des Rezept-Editors")
class RecipeEditorModelTest {

    private static final Predicate<Material> EVERY_MATERIAL_IS_ITEM = material -> true;

    private static YamlConfiguration shippedConfig() throws IOException {
        try (InputStream stream = RecipeEditorModelTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(stream, "config.yml fehlt im Klassenpfad");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static RecipeDefinition shippedDefinition(RecipeKind kind) throws IOException {
        return RecipeStore.readDefinition(shippedConfig().getConfigurationSection("recipes." + kind.configKey()));
    }

    private static List<String> cells(RecipeEditorModel model) {
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < RecipeEditorModel.CELL_COUNT; index++) cells.add(model.cell(index));
        return cells;
    }

    private static RecipeEditorModel modelWith(RecipeKind kind, Material... materialsPerCell) {
        RecipeEditorModel model = new RecipeEditorModel(kind);
        for (int index = 0; index < materialsPerCell.length; index++) {
            if (materialsPerCell[index] == null) continue;
            assertTrue(model.select(materialsPerCell[index], EVERY_MATERIAL_IS_ITEM));
            assertTrue(model.placeSelection(index));
        }
        return model;
    }

    private static Map<String, String> ingredients(String... symbolsAndMaterials) {
        Map<String, String> ingredients = new LinkedHashMap<>();
        for (int index = 0; index < symbolsAndMaterials.length; index += 2) {
            ingredients.put(symbolsAndMaterials[index], symbolsAndMaterials[index + 1]);
        }
        return ingredients;
    }

    @Test
    @DisplayName("Neue Arbeitskopie: neun leere Zellen, Schalter an, keine Auswahl")
    void neueArbeitskopie_leer() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        assertEquals(RecipeKind.CHEST_KEY, model.kind());
        assertTrue(model.enabled());
        assertNull(model.selection());
        for (int index = 0; index < RecipeEditorModel.CELL_COUNT; index++) {
            assertTrue(model.isEmpty(index));
            assertNull(model.material(index));
        }
    }

    @Test
    @DisplayName("Auswahl: gueltiges Item wird gemerkt")
    void auswahl_gueltig() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        assertTrue(model.select(Material.DIAMOND, EVERY_MATERIAL_IS_ITEM));

        assertEquals(Material.DIAMOND, model.selection());
    }

    @Test
    @DisplayName("Auswahl: Material, das kein Item ist, wird abgelehnt und die alte Auswahl bleibt")
    void auswahl_keinItem_abgelehnt() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);
        model.select(Material.DIAMOND, EVERY_MATERIAL_IS_ITEM);

        assertFalse(model.select(Material.WATER, material -> material != Material.WATER));

        assertEquals(Material.DIAMOND, model.selection());
    }

    @Test
    @DisplayName("Auswahl: AIR, CAVE_AIR, VOID_AIR und null werden immer abgelehnt, auch wenn das Praedikat sie zulaesst")
    void auswahl_luft_immerAbgelehnt() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        for (Material air : List.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)) {
            assertFalse(model.select(air, EVERY_MATERIAL_IS_ITEM), air.name());
            assertTrue(RecipeEditorModel.isAir(air), air.name());
        }
        assertFalse(model.select(null, EVERY_MATERIAL_IS_ITEM));
        assertFalse(RecipeEditorModel.isAir(Material.DIAMOND));
        assertNull(model.selection());
    }

    @Test
    @DisplayName("Setzen und Leeren einer Zelle, ohne Auswahl wird nichts gesetzt")
    void setzenUndLeeren() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        assertFalse(model.placeSelection(4));
        assertTrue(model.isEmpty(4));

        model.select(Material.GOLD_INGOT, EVERY_MATERIAL_IS_ITEM);
        assertTrue(model.placeSelection(4));
        assertEquals("GOLD_INGOT", model.cell(4));
        assertEquals(Material.GOLD_INGOT, model.material(4));
        assertEquals(Material.GOLD_INGOT, model.selection(), "Die Auswahl bleibt nach dem Setzen erhalten");

        model.clearCell(4);
        assertTrue(model.isEmpty(4));
    }

    @Test
    @DisplayName("Zellen ausserhalb von 0 bis 8 werfen IndexOutOfBoundsException")
    void zellenIndex_geprueft() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        assertThrows(IndexOutOfBoundsException.class, () -> model.cell(9));
        assertThrows(IndexOutOfBoundsException.class, () -> model.clearCell(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> model.placeSelection(9));
    }

    @Test
    @DisplayName("Schalter: setzen und umschalten")
    void schalter() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        model.toggleEnabled();
        assertFalse(model.enabled());
        model.setEnabled(true);
        assertTrue(model.enabled());
        assertTrue(model.toDefinition().enabled());
        model.setEnabled(false);
        assertFalse(model.toDefinition().enabled());
    }

    @Test
    @DisplayName("Ausgeliefertes Chest-Key-Rezept als Raster")
    void ausgeliefert_chestKey_alsRaster() throws IOException {
        RecipeEditorModel model = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY,
                shippedDefinition(RecipeKind.CHEST_KEY));

        assertEquals(List.of(
                "NETHERITE_INGOT", "DIAMOND", "NETHERITE_INGOT",
                "DIAMOND", "TRIPWIRE_HOOK", "DIAMOND",
                "NETHERITE_INGOT", "DIAMOND", "NETHERITE_INGOT"), cells(model));
        assertTrue(model.enabled());
    }

    @Test
    @DisplayName("Ausgelieferte Rezepte: nur chest_key, Umwandlung besteht den Validator und ergibt dieselbe Belegung")
    void ausgeliefert_umwandlung_gueltig() throws IOException {
        assertEquals(Set.of(RecipeKind.CHEST_KEY.configKey()),
                shippedConfig().getConfigurationSection("recipes").getKeys(false));

        RecipeDefinition chestKey = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY,
                shippedDefinition(RecipeKind.CHEST_KEY)).toDefinition();

        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, chestKey));
        assertEquals(List.of("ABA", "BCB", "ABA"), chestKey.shape());
        assertEquals(ingredients("A", "NETHERITE_INGOT", "B", "DIAMOND", "C", "TRIPWIRE_HOOK"),
                chestKey.ingredients());
    }

    @Test
    @DisplayName("Rundlauf Raster zu Definition zu Raster ist verlustfrei")
    void rundlauf_verlustfrei() {
        RecipeEditorModel original = modelWith(RecipeKind.CHEST_KEY,
                Material.STONE, null, Material.OAK_PLANKS,
                null, Material.DIAMOND, Material.STONE,
                Material.REDSTONE, Material.OAK_PLANKS, null);
        original.setEnabled(false);

        RecipeEditorModel copy = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, original.toDefinition());

        assertEquals(cells(original), cells(copy));
        assertEquals(original.enabled(), copy.enabled());
        assertEquals(original.toDefinition(), copy.toDefinition());
        assertNull(copy.selection(), "Die Auswahl gehoert nicht zur Definition");
    }

    @Test
    @DisplayName("Leeres Raster ergibt SHAPE_EMPTY aus dem Validator")
    void leeresRaster_shapeEmpty() {
        RecipeDefinition definition = new RecipeEditorModel(RecipeKind.CHEST_KEY).toDefinition();

        assertEquals(List.of("   ", "   ", "   "), definition.shape());
        assertTrue(definition.ingredients().isEmpty());
        assertEquals(List.of(Code.SHAPE_EMPTY), RecipeValidator.validate(RecipeKind.CHEST_KEY, definition)
                .stream().map(RecipeValidationError::code).toList());
    }

    @Test
    @DisplayName("Zeichenvergabe: Reihenfolge des ersten Auftretens Zeile fuer Zeile, deterministisch")
    void zeichenvergabe_deterministisch() {
        RecipeEditorModel model = modelWith(RecipeKind.CHEST_KEY,
                Material.STONE, Material.GOLD_INGOT, null,
                Material.IRON_INGOT, Material.STONE, Material.GOLD_INGOT,
                null, Material.DIAMOND, null);

        RecipeDefinition first = model.toDefinition();
        RecipeDefinition second = model.toDefinition();

        assertEquals(List.of("AB ", "CAB", " D "), first.shape());
        assertEquals(List.of("A", "B", "C", "D"), List.copyOf(first.ingredients().keySet()));
        assertEquals(ingredients("A", "STONE", "B", "GOLD_INGOT", "C", "IRON_INGOT", "D", "DIAMOND"),
                first.ingredients());
        assertEquals(first, second);
        assertEquals(List.copyOf(first.ingredients().keySet()), List.copyOf(second.ingredients().keySet()));
    }

    @Test
    @DisplayName("Neun verschiedene Materialien bekommen A bis I und bestehen den Validator")
    void neunMaterialien() {
        Material[] materials = {Material.STONE, Material.DIRT, Material.SAND, Material.GRAVEL, Material.DIAMOND,
                Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COAL};
        RecipeDefinition definition = modelWith(RecipeKind.CHEST_KEY, materials).toDefinition();

        assertEquals(List.of("ABC", "DEF", "GHI"), definition.shape());
        assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H", "I"),
                List.copyOf(definition.ingredients().keySet()));
        for (int index = 0; index < materials.length; index++) {
            assertEquals(materials[index].name(),
                    definition.ingredients().get(String.valueOf(RecipeEditorModel.MATERIAL_SYMBOLS.charAt(index))));
        }
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, definition));
    }

    @Test
    @DisplayName("Zeichen sind nie Leerzeichen oder Punkt, nur A bis I, Materialnamen in Grossbuchstaben")
    void zeichen_nieLeerzeichenOderPunkt() {
        RecipeEditorModel model = modelWith(RecipeKind.CHEST_KEY,
                Material.STONE, Material.DIRT, Material.SAND, Material.GRAVEL, null,
                Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COAL);

        RecipeDefinition definition = model.toDefinition();

        for (String symbol : definition.ingredients().keySet()) {
            assertEquals(1, symbol.length());
            assertNotEquals(" ", symbol);
            assertNotEquals(".", symbol);
            assertTrue(RecipeEditorModel.MATERIAL_SYMBOLS.contains(symbol), symbol);
        }
        for (String material : definition.ingredients().values()) {
            assertEquals(material.toUpperCase(), material);
        }
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, definition));
    }

    @Test
    @DisplayName("Auffuellen: schmale Shapes werden rechts mit leeren Feldern auf 3x3 aufgefuellt")
    void auffuellen_schmaleShape() {
        RecipeDefinition narrow = new RecipeDefinition(true, List.of("AB", "BA", "A "),
                ingredients("A", "STICK", "B", "STRING"));

        RecipeEditorModel model = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, narrow);

        assertEquals(Arrays.asList(
                "STICK", "STRING", null,
                "STRING", "STICK", null,
                "STICK", null, null), cells(model));
        assertEquals(List.of("AB ", "BA ", "A  "), model.toDefinition().shape());
    }

    @Test
    @DisplayName("Auffuellen: einspaltige Shape und weniger als drei Zeilen")
    void auffuellen_einspaltigUndKurz() {
        RecipeDefinition column = new RecipeDefinition(false, List.of("S", "S"), ingredients("S", "STICK"));

        RecipeEditorModel model = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, column);

        assertEquals(Arrays.asList("STICK", null, null, "STICK", null, null, null, null, null), cells(model));
        assertFalse(model.enabled());
    }

    @Test
    @DisplayName("Aufbau: unbekannte Materialnamen, Luft und Zeichen ohne Zutat werden zu leeren Zellen")
    void aufbau_unbekanntWirdLeer() {
        RecipeDefinition definition = new RecipeDefinition(true, List.of("XAL", "Z  ", "   "),
                ingredients("X", "DIAMOND_X", "A", "diamond", "L", "AIR"));

        RecipeEditorModel model = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, definition);

        assertEquals(Arrays.asList(null, "DIAMOND", null, null, null, null, null, null, null), cells(model));
    }

    @Test
    @DisplayName("Aufbau: CHEST_KEY ist ein unbekanntes Material und wird zu einer leeren Zelle")
    void aufbau_chestKeyWirdLeer() {
        for (String chestKey : List.of("CHEST_KEY", "chest_key")) {
            RecipeDefinition definition = new RecipeDefinition(true, List.of("K", "K", "K"), ingredients("K", chestKey));

            assertTrue(RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, definition).isEmpty(0), chestKey);
        }
    }

    @Test
    @DisplayName("Aufbau: aktuelle Definition vor Standard, ohne beide leer mit Schalter an")
    void aufbau_ausDefinitionen() {
        RecipeDefinition current = new RecipeDefinition(false, List.of("D"), ingredients("D", "DIAMOND"));
        RecipeDefinition defaults = new RecipeDefinition(true, List.of("E"), ingredients("E", "EMERALD"));

        RecipeEditorModel fromCurrent = RecipeEditorModel.fromDefinitions(RecipeKind.CHEST_KEY, current, defaults);
        RecipeEditorModel fromDefaults = RecipeEditorModel.fromDefinitions(RecipeKind.CHEST_KEY, null, defaults);
        RecipeEditorModel empty = RecipeEditorModel.fromDefinitions(RecipeKind.CHEST_KEY, null, null);

        assertEquals("DIAMOND", fromCurrent.cell(0));
        assertFalse(fromCurrent.enabled());
        assertEquals("EMERALD", fromDefaults.cell(0));
        assertTrue(fromDefaults.enabled());
        assertEquals(new RecipeEditorModel(RecipeKind.CHEST_KEY).toDefinition(), empty.toDefinition());
        assertTrue(empty.enabled());
    }
}
