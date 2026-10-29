package de.SecureChest.plugin.recipe;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class RecipeEditorModel {

    public static final int GRID_SIZE = 3;
    public static final int CELL_COUNT = GRID_SIZE * GRID_SIZE;
    public static final String MATERIAL_SYMBOLS = "ABCDEFGHI";
    public static final int LARGE_AMOUNT_STEP = 8;

    private static final char EMPTY_SYMBOL = ' ';
    private static final Set<Material> AIR_MATERIALS = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private final RecipeKind kind;
    private final String[] cells = new String[CELL_COUNT];
    private boolean enabled = true;
    private int amount = RecipeDefinition.DEFAULT_AMOUNT;
    private Material selection;

    public RecipeEditorModel(RecipeKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public static RecipeEditorModel fromDefinitions(RecipeKind kind, RecipeDefinition currentDefinition,
                                                    RecipeDefinition defaultDefinition) {
        return fromDefinition(kind, currentDefinition != null ? currentDefinition : defaultDefinition);
    }

    public static RecipeEditorModel fromDefinition(RecipeKind kind, RecipeDefinition definition) {
        RecipeEditorModel model = new RecipeEditorModel(kind);
        if (definition == null) return model;
        model.enabled = definition.enabled();
        model.amount = definition.amount();
        List<String> shape = definition.shape();
        for (int row = 0; row < Math.min(GRID_SIZE, shape.size()); row++) {
            String shapeRow = shape.get(row);
            for (int column = 0; column < Math.min(GRID_SIZE, shapeRow.length()); column++) {
                char symbol = shapeRow.charAt(column);
                if (symbol == EMPTY_SYMBOL) continue;
                model.cells[row * GRID_SIZE + column] =
                        model.cellFor(definition.ingredients().get(String.valueOf(symbol)));
            }
        }
        return model;
    }

    public static boolean isAir(Material material) {
        return AIR_MATERIALS.contains(material);
    }

    public RecipeKind kind() {
        return kind;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
    }

    public int amount() {
        return amount;
    }

    public void changeAmount(int change) {
        amount = Math.max(RecipeDefinition.MINIMUM_AMOUNT, Math.min(RecipeDefinition.MAXIMUM_AMOUNT,
                amount + change));
    }

    public Material selection() {
        return selection;
    }

    public boolean select(Material material, Predicate<Material> itemMaterial) {
        if (material == null || isAir(material) || !itemMaterial.test(material)) return false;
        selection = material;
        return true;
    }

    public String cell(int index) {
        return cells[Objects.checkIndex(index, CELL_COUNT)];
    }

    public boolean isEmpty(int index) {
        return cell(index) == null;
    }

    public Material material(int index) {
        String cell = cell(index);
        if (cell == null) return null;
        return Material.getMaterial(cell);
    }

    public boolean placeSelection(int index) {
        Objects.checkIndex(index, CELL_COUNT);
        if (selection == null) return false;
        cells[index] = selection.name();
        return true;
    }

    public void clearCell(int index) {
        cells[Objects.checkIndex(index, CELL_COUNT)] = null;
    }

    public RecipeDefinition toDefinition() {
        Map<String, Character> symbolsByMaterial = new LinkedHashMap<>();
        Map<String, String> ingredients = new LinkedHashMap<>();
        List<String> shape = new ArrayList<>();
        for (int row = 0; row < GRID_SIZE; row++) {
            StringBuilder shapeRow = new StringBuilder();
            for (int column = 0; column < GRID_SIZE; column++) {
                String cell = cells[row * GRID_SIZE + column];
                char symbol;
                if (cell == null) {
                    symbol = EMPTY_SYMBOL;
                } else {
                    Character assignedSymbol = symbolsByMaterial.get(cell);
                    if (assignedSymbol == null) {
                        assignedSymbol = MATERIAL_SYMBOLS.charAt(symbolsByMaterial.size());
                        symbolsByMaterial.put(cell, assignedSymbol);
                    }
                    symbol = assignedSymbol;
                }
                if (symbol != EMPTY_SYMBOL) ingredients.putIfAbsent(String.valueOf(symbol), cell);
                shapeRow.append(symbol);
            }
            shape.add(shapeRow.toString());
        }
        return new RecipeDefinition(enabled, shape, ingredients, amount);
    }

    private String cellFor(String ingredient) {
        if (ingredient == null) return null;
        Material material = Material.matchMaterial(ingredient);
        return material == null || isAir(material) ? null : material.name();
    }
}
