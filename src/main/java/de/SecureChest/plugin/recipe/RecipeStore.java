package de.SecureChest.plugin.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class RecipeStore {

    public static final String FILE_NAME = "recipes.yml";

    static final String REMOVED_SECURE_HOPPER_KEY = "secure_hopper";
    static final String SECURE_HOPPER_REPLACEMENT_HINT =
            "Hold the Chest Key and right click a hopper that faces your locked container instead.";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SHAPE = "shape";
    private static final String KEY_INGREDIENTS = "ingredients";
    private static final String KEY_AMOUNT = "amount";
    private static final int INVALID_AMOUNT = 0;

    private final Path file;
    private final Logger logger;
    private boolean unreadableAtLoad;

    public RecipeStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    public boolean isUnreadableAtLoad() {
        return unreadableAtLoad;
    }

    public Map<RecipeKind, RecipeDefinition> load() {
        unreadableAtLoad = false;
        if (!Files.exists(file)) return Map.of();

        YamlConfiguration yaml;
        try {
            yaml = readYaml();
        } catch (IOException | InvalidConfigurationException ex) {
            unreadableAtLoad = true;
            logger.warning(FILE_NAME + " could not be read (" + ex.getMessage()
                    + "). The recipes from config.yml are used. The file is not overwritten.");
            return Map.of();
        }

        Map<RecipeKind, RecipeDefinition> overrides = new EnumMap<>(RecipeKind.class);
        for (String key : yaml.getKeys(false)) {
            if (key.equals(REMOVED_SECURE_HOPPER_KEY)) {
                logger.info(REMOVED_SECURE_HOPPER_KEY + " in " + FILE_NAME
                        + " is no longer used, the Secure Hopper recipe was removed. "
                        + SECURE_HOPPER_REPLACEMENT_HINT);
                continue;
            }
            RecipeKind kind = RecipeKind.fromConfigKey(key);
            if (kind == null) {
                logger.warning(FILE_NAME + ": entry '" + key + "' is not a known recipe and was skipped.");
                continue;
            }
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null || !isReadable(section)) {
                logger.warning(FILE_NAME + ": entry '" + key
                        + "' could not be read and was skipped. The recipe from config.yml is used.");
                continue;
            }
            overrides.put(kind, readDefinition(section));
        }
        return Collections.unmodifiableMap(overrides);
    }

    public void save(Map<RecipeKind, RecipeDefinition> overrides) throws IOException {
        refuseIfUnreadableAtLoad();

        YamlConfiguration yaml = new YamlConfiguration();
        for (RecipeKind kind : RecipeKind.values()) {
            RecipeDefinition definition = overrides.get(kind);
            if (definition == null) continue;
            ConfigurationSection section = yaml.createSection(kind.configKey());
            section.set(KEY_ENABLED, definition.enabled());
            section.set(KEY_AMOUNT, definition.amount());
            section.set(KEY_SHAPE, definition.shape());
            section.createSection(KEY_INGREDIENTS, definition.ingredients());
        }
        writeAtomically(yaml.saveToString());
    }

    public void remove(RecipeKind kind) throws IOException {
        refuseIfUnreadableAtLoad();
        if (!Files.exists(file)) return;

        YamlConfiguration yaml;
        try {
            yaml = readYaml();
        } catch (InvalidConfigurationException ex) {
            throw new IOException(FILE_NAME + " could not be read (" + ex.getMessage()
                    + ") and is not overwritten.", ex);
        }
        if (!yaml.contains(kind.configKey())) return;
        yaml.set(kind.configKey(), null);
        writeAtomically(yaml.saveToString());
    }

    static RecipeDefinition readDefinition(ConfigurationSection section) {
        if (section == null) return null;
        Map<String, String> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientSection = section.getConfigurationSection(KEY_INGREDIENTS);
        if (ingredientSection != null) {
            ingredientSection.getValues(false).forEach(
                    (symbol, material) -> ingredients.put(symbol, String.valueOf(material)));
        }
        return new RecipeDefinition(section.getBoolean(KEY_ENABLED, true), section.getStringList(KEY_SHAPE),
                ingredients, readAmount(section.get(KEY_AMOUNT)));
    }

    private static int readAmount(Object amount) {
        if (amount == null) return RecipeDefinition.DEFAULT_AMOUNT;
        return amount instanceof Integer wholeNumber ? wholeNumber : INVALID_AMOUNT;
    }

    private static boolean isReadable(ConfigurationSection section) {
        Object enabled = section.get(KEY_ENABLED);
        if (enabled != null && !(enabled instanceof Boolean)) return false;
        Object shape = section.get(KEY_SHAPE);
        if (shape != null && !(shape instanceof List<?> rows && rows.stream().allMatch(String.class::isInstance))) {
            return false;
        }
        Object ingredients = section.get(KEY_INGREDIENTS);
        return ingredients == null || ingredients instanceof ConfigurationSection ingredientSection
                && ingredientSection.getValues(false).values().stream().allMatch(String.class::isInstance);
    }

    private void refuseIfUnreadableAtLoad() throws IOException {
        if (unreadableAtLoad) {
            throw new IOException(FILE_NAME + " could not be read at startup and is not overwritten."
                    + " Fix or remove the file and restart the server.");
        }
    }

    private YamlConfiguration readYaml() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        return yaml;
    }

    private void writeAtomically(String content) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporaryFile = directory.resolve(FILE_NAME + ".tmp");
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
