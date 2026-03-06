package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

public final class RecipeRegistry {

    private final ChestSecurePlugin plugin;

    private ShapedRecipe chestKeyRecipe;
    private ShapedRecipe secureHopperRecipe;

    public RecipeRegistry(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        Items items = plugin.items();

        ConfigurationSection keySec = plugin.getConfig().getConfigurationSection("recipes.chest_key");
        if (keySec != null && keySec.getBoolean("enabled", true)) {
            String[] shape = keySec.getStringList("shape").toArray(new String[0]);
            if (shape.length != 3) {
                plugin.getLogger().warning(
                        "recipes.chest_key.shape must have exactly 3 rows but has " + shape.length
                        + " — Chest Key recipe NOT registered. Fix your config.yml.");
            } else {
                NamespacedKey nk = new NamespacedKey(plugin, "chest_key");
                chestKeyRecipe = new ShapedRecipe(nk, items.chestKey());
                chestKeyRecipe.shape(shape[0], shape[1], shape[2]);

                ConfigurationSection ing = keySec.getConfigurationSection("ingredients");
                if (ing != null) {
                    for (Map.Entry<String, Object> e : ing.getValues(false).entrySet()) {
                        if (e.getKey().length() != 1) continue;
                        char ch = e.getKey().charAt(0);
                        String matName = String.valueOf(e.getValue());
                        Material m = Material.matchMaterial(matName);
                        if (m != null) chestKeyRecipe.setIngredient(ch, m);
                    }
                }
                plugin.getServer().addRecipe(chestKeyRecipe);
            }
        }

        ConfigurationSection hopSec = plugin.getConfig().getConfigurationSection("recipes.secure_hopper");
        if (hopSec != null && hopSec.getBoolean("enabled", true)) {
            String[] shape = hopSec.getStringList("shape").toArray(new String[0]);
            if (shape.length != 3) {
                plugin.getLogger().warning(
                        "recipes.secure_hopper.shape must have exactly 3 rows but has " + shape.length
                        + " — Secure Hopper recipe NOT registered. Fix your config.yml.");
            } else {
                NamespacedKey nk = new NamespacedKey(plugin, "secure_hopper");
                secureHopperRecipe = new ShapedRecipe(nk, items.secureHopper());
                secureHopperRecipe.shape(shape[0], shape[1], shape[2]);

                ConfigurationSection ing = hopSec.getConfigurationSection("ingredients");
                if (ing != null) {
                    for (Map.Entry<String, Object> e : ing.getValues(false).entrySet()) {
                        if (e.getKey().length() != 1) continue;
                        char ch = e.getKey().charAt(0);
                        String val = String.valueOf(e.getValue());
                        if (val.equalsIgnoreCase("CHEST_KEY")) {
                            secureHopperRecipe.setIngredient(ch, new RecipeChoice.ExactChoice(items.chestKey()));
                        } else {
                            Material m = Material.matchMaterial(val);
                            if (m != null) secureHopperRecipe.setIngredient(ch, m);
                        }
                    }
                }
                plugin.getServer().addRecipe(secureHopperRecipe);
            }
        }
    }

    public ShapedRecipe chestKeyRecipe() {
        return chestKeyRecipe;
    }

    public ShapedRecipe secureHopperRecipe() {
        return secureHopperRecipe;
    }
}
