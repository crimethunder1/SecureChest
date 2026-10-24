package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class RecipeRegistry {

    private static final String CONFIG_FILE_NAME = "config.yml";

    record Selection(RecipeDefinition definition,
                     boolean fromOverride,
                     List<RecipeValidationError> overrideErrors,
                     List<RecipeValidationError> defaultErrors) {

        Selection {
            overrideErrors = List.copyOf(overrideErrors);
            defaultErrors = List.copyOf(defaultErrors);
        }
    }

    private final ChestSecurePlugin plugin;
    private final RecipeStore store;
    private final Map<RecipeKind, RecipeDefinition> defaultDefinitions = new EnumMap<>(RecipeKind.class);
    private final Map<RecipeKind, RecipeDefinition> overrides = new EnumMap<>(RecipeKind.class);
    private final Map<RecipeKind, RecipeDefinition> currentDefinitions = new EnumMap<>(RecipeKind.class);

    private ShapedRecipe chestKeyRecipe;

    public RecipeRegistry(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        this.store = new RecipeStore(plugin.getDataFolder().toPath().resolve(RecipeStore.FILE_NAME),
                plugin.getLogger());
    }

    public void registerAll() {
        overrides.clear();
        overrides.putAll(store.load());
        if (plugin.cfg().recipeSection(RecipeStore.REMOVED_SECURE_HOPPER_KEY) != null) {
            logger().info("recipes." + RecipeStore.REMOVED_SECURE_HOPPER_KEY + " in " + CONFIG_FILE_NAME
                    + " is no longer used, the Secure Hopper recipe was removed. "
                    + RecipeStore.SECURE_HOPPER_REPLACEMENT_HINT);
        }
        for (RecipeKind kind : RecipeKind.values()) {
            RecipeDefinition defaults = RecipeStore.readDefinition(plugin.cfg().recipeSection(kind.configKey()));
            if (defaults == null) {
                defaultDefinitions.remove(kind);
            } else {
                defaultDefinitions.put(kind, defaults);
            }

            Selection selection = select(kind, overrides.get(kind), defaults);
            selection.overrideErrors().forEach(error -> logger().warning(
                    RecipeStore.FILE_NAME + ": " + kind.configKey() + "." + error.detail() + ". Using the "
                    + kind.displayName() + " recipe from " + CONFIG_FILE_NAME + " instead."));
            warnConfigErrors(kind, selection.defaultErrors());
            activate(kind, selection.definition(),
                    selection.fromOverride() ? RecipeStore.FILE_NAME : CONFIG_FILE_NAME);
        }
    }

    public RecipeChangeResult apply(RecipeKind kind, RecipeDefinition definition) {
        List<RecipeValidationError> errors = RecipeValidator.validate(kind, definition);
        if (!errors.isEmpty()) return RecipeChangeResult.rejected(kind, errors);

        ShapedRecipe previousRecipe = recipe(kind);
        unregister(kind);
        if (definition.enabled()) {
            String failure = register(kind, definition);
            if (failure != null) {
                logger().warning("The changed " + kind.displayName() + " recipe could not be registered ("
                        + failure + "). The previous recipe is restored.");
                restore(kind, previousRecipe);
                plugin.getServer().updateRecipes();
                return RecipeChangeResult.rejected(kind,
                        List.of(new RecipeValidationError(Code.REGISTRATION_FAILED, failure)));
            }
        }
        currentDefinitions.put(kind, definition);
        overrides.put(kind, definition);
        plugin.getServer().updateRecipes();
        logger().info(kind.displayName() + " recipe changed" + (definition.enabled() ? "." : ", it is disabled."));

        String saveError = null;
        try {
            store.save(overrides);
        } catch (IOException ex) {
            saveError = describe(ex);
            logger().warning("Saving " + RecipeStore.FILE_NAME + " failed: " + saveError
                    + " The change stays active until the next restart.");
        }
        return new RecipeChangeResult(kind, true, List.of(), saveError);
    }

    public RecipeChangeResult resetToDefault(RecipeKind kind) {
        Selection selection = select(kind, null, defaultDefinitions.get(kind));
        warnConfigErrors(kind, selection.defaultErrors());
        List<RecipeValidationError> errors = new ArrayList<>(selection.defaultErrors());

        unregister(kind);
        String failure = activate(kind, selection.definition(), CONFIG_FILE_NAME);
        if (failure != null) errors.add(new RecipeValidationError(Code.REGISTRATION_FAILED, failure));
        overrides.remove(kind);
        plugin.getServer().updateRecipes();
        logger().info(kind.displayName() + " recipe reset to the recipe from " + CONFIG_FILE_NAME + ".");

        String saveError = null;
        try {
            store.remove(kind);
        } catch (IOException ex) {
            saveError = describe(ex);
            logger().warning("Removing the " + kind.configKey() + " entry from " + RecipeStore.FILE_NAME
                    + " failed: " + saveError + " The change stays active until the next restart.");
        }
        return new RecipeChangeResult(kind, true, errors, saveError);
    }

    public RecipeDefinition currentDefinition(RecipeKind kind) {
        return currentDefinitions.get(kind);
    }

    public RecipeDefinition defaultDefinition(RecipeKind kind) {
        return defaultDefinitions.get(kind);
    }

    public boolean isOverridden(RecipeKind kind) {
        return overrides.containsKey(kind);
    }

    public ShapedRecipe chestKeyRecipe() {
        return chestKeyRecipe;
    }

    static Selection select(RecipeKind kind, RecipeDefinition override, RecipeDefinition defaults) {
        List<RecipeValidationError> overrideErrors = errorsOf(kind, override);
        if (override != null && overrideErrors.isEmpty()) {
            return new Selection(override, true, List.of(), List.of());
        }
        List<RecipeValidationError> defaultErrors = errorsOf(kind, defaults);
        RecipeDefinition definition = defaults != null && defaultErrors.isEmpty() ? defaults : null;
        return new Selection(definition, false, overrideErrors, defaultErrors);
    }

    private static List<RecipeValidationError> errorsOf(RecipeKind kind, RecipeDefinition definition) {
        if (definition == null || !definition.enabled()) return List.of();
        return RecipeValidator.validate(kind, definition);
    }

    private void warnConfigErrors(RecipeKind kind, List<RecipeValidationError> errors) {
        errors.forEach(error -> logger().warning(
                "recipes." + kind.configKey() + "." + error.detail() + ". " + kind.displayName()
                + " recipe NOT registered. Fix your " + CONFIG_FILE_NAME + "."));
    }

    private String activate(RecipeKind kind, RecipeDefinition definition, String source) {
        if (definition == null) {
            currentDefinitions.remove(kind);
            return null;
        }
        if (definition.enabled()) {
            String failure = register(kind, definition);
            if (failure != null) {
                logger().warning(kind.displayName() + " recipe from " + source + " could not be registered ("
                        + failure + "). " + kind.displayName() + " recipe NOT registered.");
                currentDefinitions.remove(kind);
                return failure;
            }
        }
        currentDefinitions.put(kind, definition);
        return null;
    }

    private String register(RecipeKind kind, RecipeDefinition definition) {
        ShapedRecipe recipe;
        try {
            recipe = build(kind, definition);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return describe(ex);
        }
        String failure = addToServer(recipe);
        if (failure == null) setRecipe(kind, recipe);
        return failure;
    }

    private void restore(RecipeKind kind, ShapedRecipe previousRecipe) {
        if (previousRecipe == null) return;
        String failure = addToServer(previousRecipe);
        if (failure == null) {
            setRecipe(kind, previousRecipe);
            return;
        }
        logger().warning("The previous " + kind.displayName() + " recipe could not be restored (" + failure
                + "). " + kind.displayName() + " recipe NOT registered.");
        currentDefinitions.remove(kind);
    }

    private void unregister(RecipeKind kind) {
        if (recipe(kind) == null) return;
        plugin.getServer().removeRecipe(recipeKey(kind));
        setRecipe(kind, null);
    }

    private String addToServer(ShapedRecipe recipe) {
        try {
            return plugin.getServer().addRecipe(recipe) ? null : "the server did not accept the recipe";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return describe(ex);
        }
    }

    private ShapedRecipe build(RecipeKind kind, RecipeDefinition definition) {
        Items items = plugin.items();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey(kind), result(kind, items));
        recipe.shape(definition.shape().toArray(new String[0]));
        definition.ingredients().forEach(
                (symbol, material) -> recipe.setIngredient(symbol.charAt(0), Material.matchMaterial(material)));
        return recipe;
    }

    private static ItemStack result(RecipeKind kind, Items items) {
        return switch (kind) {
            case CHEST_KEY -> items.chestKey();
        };
    }

    private NamespacedKey recipeKey(RecipeKind kind) {
        return new NamespacedKey(plugin, kind.configKey());
    }

    private ShapedRecipe recipe(RecipeKind kind) {
        return switch (kind) {
            case CHEST_KEY -> chestKeyRecipe;
        };
    }

    private void setRecipe(RecipeKind kind, ShapedRecipe recipe) {
        switch (kind) {
            case CHEST_KEY -> chestKeyRecipe = recipe;
        }
    }

    private Logger logger() {
        return plugin.getLogger();
    }

    private static String describe(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
