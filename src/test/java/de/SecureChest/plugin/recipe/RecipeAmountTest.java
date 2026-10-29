package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.recipe.RecipeValidationError.Code;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Rezept-Anzahl: Pruefung, recipes.yml, config.yml, Registrierung und Editor")
class RecipeAmountTest {

    private static final Path SHIPPED_CONFIG_FILE = Path.of("src", "main", "resources", "config.yml");
    private static final List<String> SHAPE = List.of("NDN", "DHD", "NDN");
    private static final Map<String, String> INGREDIENTS =
            Map.of("N", "NETHERITE_INGOT", "D", "DIAMOND", "H", "TRIPWIRE_HOOK");
    private static final String DISABLED_CONFIG_WITHOUT_AMOUNT = String.join("\n",
            "recipes:",
            "  chest_key:",
            "    enabled: false",
            "    shape: [\"NDN\", \"DHD\", \"NDN\"]",
            "    ingredients:",
            "      N: \"NETHERITE_INGOT\"",
            "      D: \"DIAMOND\"",
            "      H: \"TRIPWIRE_HOOK\"",
            "");

    @TempDir
    Path dataFolder;

    private final Logger logger = mock(Logger.class);

    private static RecipeDefinition definition(int amount) {
        return new RecipeDefinition(true, SHAPE, INGREDIENTS, amount);
    }

    private static RecipeDefinition readYaml(String yaml) {
        return RecipeStore.readDefinition(
                YamlConfiguration.loadConfiguration(new StringReader(yaml)).getConfigurationSection("chest_key"));
    }

    private RecipeStore store() {
        return new RecipeStore(dataFolder.resolve(RecipeStore.FILE_NAME), logger);
    }

    private RecipeRegistry registry(String configYaml, Server server) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(new StringReader(configYaml)));
        PluginConfig pluginConfig = new PluginConfig(plugin);
        when(plugin.cfg()).thenReturn(pluginConfig);
        clearInvocations(logger);
        return new RecipeRegistry(plugin);
    }

    @Test
    @DisplayName("Standard 1, Konstruktor ohne Anzahl ergibt 1")
    void defaultAmount() {
        assertEquals(1, RecipeDefinition.DEFAULT_AMOUNT);
        assertEquals(1, new RecipeDefinition(true, SHAPE, INGREDIENTS).amount());
        assertEquals(new RecipeDefinition(true, SHAPE, INGREDIENTS), definition(1));
    }

    @Test
    @DisplayName("Pruefung: 1, 9 und 64 sind gueltig")
    void validAmounts() {
        for (int amount : List.of(1, 9, 64)) {
            assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, definition(amount)), "" + amount);
        }
    }

    @Test
    @DisplayName("Pruefung: 0, -1, 65 und 1000 ergeben den Fehlercode AMOUNT_OUT_OF_RANGE mit Detail")
    void invalidAmounts() {
        for (int amount : List.of(0, -1, 65, 1000)) {
            List<RecipeValidationError> errors = RecipeValidator.validate(RecipeKind.CHEST_KEY, definition(amount));
            assertEquals(1, errors.size(), "" + amount);
            assertEquals(Code.AMOUNT_OUT_OF_RANGE, errors.get(0).code());
            assertEquals("amount must be a whole number from 1 to 64", errors.get(0).detail());
        }
    }

    @Test
    @DisplayName("Grenzen 1 und 64, 64 ist die Stapelgroesse des Hakens, aus dem der Chest Key besteht")
    void bounds() {
        assertEquals(1, RecipeDefinition.MINIMUM_AMOUNT);
        assertEquals(64, RecipeDefinition.MAXIMUM_AMOUNT);
    }

    @Test
    @DisplayName("config.yml ohne amount ergibt 1, ganze Zahl wird gelesen, anderes ergibt einen ungueltigen Wert")
    void readDefinitionAmount() {
        String base = "chest_key:\n  shape: [\"D\"]\n  ingredients:\n    D: \"DIAMOND\"\n";
        assertEquals(1, readYaml(base).amount());
        assertEquals(9, readYaml(base + "  amount: 9\n").amount());
        assertEquals(64, readYaml(base + "  amount: 64\n").amount());
        assertEquals(0, readYaml(base + "  amount: \"neun\"\n").amount());
        assertEquals(0, readYaml(base + "  amount: 2.5\n").amount());
        assertEquals(65, readYaml(base + "  amount: 65\n").amount());
    }

    @Test
    @DisplayName("recipes.yml ohne amount: Anzahl 1, keine Warnung")
    void recipesFileWithoutAmount() throws IOException {
        Files.writeString(dataFolder.resolve(RecipeStore.FILE_NAME),
                "chest_key:\n  enabled: true\n  shape: [\"D\"]\n  ingredients:\n    D: \"DIAMOND\"\n");

        RecipeDefinition loaded = store().load().get(RecipeKind.CHEST_KEY);

        assertEquals(1, loaded.amount());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Speichern und Laden: amount steht in recipes.yml und kommt unveraendert zurueck")
    void saveAndLoad() throws IOException {
        store().save(Map.of(RecipeKind.CHEST_KEY, definition(9)));

        String written = Files.readString(dataFolder.resolve(RecipeStore.FILE_NAME), StandardCharsets.UTF_8);
        assertEquals(9, YamlConfiguration.loadConfiguration(new StringReader(written)).getInt("chest_key.amount"));
        assertEquals(definition(9), store().load().get(RecipeKind.CHEST_KEY));
    }

    @Test
    @DisplayName("Ausgelieferte config.yml: recipes.chest_key.amount ist 1 und entspricht dem Code-Standard")
    void shippedConfigAmount() throws IOException {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(SHIPPED_CONFIG_FILE, StandardCharsets.UTF_8)));

        assertTrue(shipped.isInt("recipes.chest_key.amount"));
        assertEquals(RecipeDefinition.DEFAULT_AMOUNT, shipped.getInt("recipes.chest_key.amount"));
        RecipeDefinition shippedDefinition =
                RecipeStore.readDefinition(shipped.getConfigurationSection("recipes.chest_key"));
        assertEquals(RecipeDefinition.DEFAULT_AMOUNT, shippedDefinition.amount());
        assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, shippedDefinition));
    }

    @Test
    @DisplayName("Registrierung: config.yml ohne amount wird ohne Warnung mit Anzahl 1 uebernommen")
    void registryConfigWithoutAmount() {
        RecipeRegistry registry = registry(DISABLED_CONFIG_WITHOUT_AMOUNT, mock(Server.class));

        registry.registerAll();

        assertEquals(1, registry.currentDefinition(RecipeKind.CHEST_KEY).amount());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Registrierung: ungueltige Anzahl in recipes.yml ergibt eine Warnung, die config.yml gilt")
    void registryInvalidAmountInRecipesFile() throws IOException {
        Files.writeString(dataFolder.resolve(RecipeStore.FILE_NAME),
                "chest_key:\n  enabled: true\n  amount: 0\n  shape: [\"D\"]\n  ingredients:\n    D: \"DIAMOND\"\n");
        RecipeRegistry registry = registry(DISABLED_CONFIG_WITHOUT_AMOUNT, mock(Server.class));

        registry.registerAll();

        verify(logger).warning(contains("recipes.yml: chest_key.amount must be a whole number from 1 to 64"));
        assertFalse(registry.currentDefinition(RecipeKind.CHEST_KEY).enabled());
    }

    @Test
    @DisplayName("Registrierung: ungueltige Anzahl in der config.yml wird gemeldet, das Rezept nicht registriert")
    void registryInvalidAmountInConfig() {
        Server server = mock(Server.class);
        RecipeRegistry registry = registry(DISABLED_CONFIG_WITHOUT_AMOUNT.replace("enabled: false",
                "enabled: true\n    amount: 65"), server);

        registry.registerAll();

        verify(logger).warning(contains("recipes.chest_key.amount must be a whole number from 1 to 64"));
        assertNull(registry.currentDefinition(RecipeKind.CHEST_KEY));
        verify(server, never()).addRecipe(any());
    }

    @Test
    @DisplayName("Editor speichert eine ungueltige Anzahl nicht, der Server bekommt kein Rezept")
    void applyRejectsInvalidAmount() {
        Server server = mock(Server.class);
        RecipeRegistry registry = registry(DISABLED_CONFIG_WITHOUT_AMOUNT, server);
        registry.registerAll();

        RecipeChangeResult result = registry.apply(RecipeKind.CHEST_KEY, definition(65));

        assertFalse(result.applied());
        assertEquals(Code.AMOUNT_OUT_OF_RANGE, result.errors().get(0).code());
        verify(server, never()).addRecipe(any());
        assertFalse(Files.exists(dataFolder.resolve(RecipeStore.FILE_NAME)));
    }

    @Test
    @DisplayName("Ergebnis-Item bekommt die Anzahl")
    void resultWithAmountSetsAmount() {
        ItemStack result = mock(ItemStack.class);
        when(result.getMaxStackSize()).thenReturn(64);

        assertSame(result, RecipeRegistry.resultWithAmount(RecipeKind.CHEST_KEY, result, 9));

        verify(result).setAmount(9);
    }

    @Test
    @DisplayName("Ergebnis-Item: Anzahl ueber der Stapelgroesse wird abgelehnt")
    void resultWithAmountRejectsAboveStackSize() {
        ItemStack result = mock(ItemStack.class);
        when(result.getMaxStackSize()).thenReturn(16);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RecipeRegistry.resultWithAmount(RecipeKind.CHEST_KEY, result, 17));

        assertEquals("amount 17 is larger than the stack size 16 of the Chest Key", failure.getMessage());
        verify(result, never()).setAmount(17);
        assertDoesNotThrow(() -> RecipeRegistry.resultWithAmount(RecipeKind.CHEST_KEY, result, 16));
    }

    @Test
    @DisplayName("Editor: neue Arbeitskopie mit Anzahl 1, Arbeitskopie uebernimmt die Anzahl der Definition")
    void editorTakesAmount() {
        assertEquals(1, new RecipeEditorModel(RecipeKind.CHEST_KEY).amount());
        RecipeEditorModel model = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, definition(9));

        assertEquals(9, model.amount());
        assertEquals(9, model.toDefinition().amount());
    }

    @Test
    @DisplayName("Editor: plus 1, minus 1, plus 8, minus 8 mit den Grenzen 1 und 64")
    void editorChangeAmountBounds() {
        RecipeEditorModel model = new RecipeEditorModel(RecipeKind.CHEST_KEY);

        model.changeAmount(-1);
        assertEquals(1, model.amount());
        model.changeAmount(1);
        assertEquals(2, model.amount());
        model.changeAmount(RecipeEditorModel.LARGE_AMOUNT_STEP);
        assertEquals(10, model.amount());
        model.changeAmount(-RecipeEditorModel.LARGE_AMOUNT_STEP);
        assertEquals(2, model.amount());
        model.changeAmount(-RecipeEditorModel.LARGE_AMOUNT_STEP);
        assertEquals(1, model.amount());
        for (int step = 0; step < 10; step++) model.changeAmount(RecipeEditorModel.LARGE_AMOUNT_STEP);
        assertEquals(64, model.amount());
        model.changeAmount(1);
        assertEquals(64, model.amount());
    }

    @Test
    @DisplayName("Editor: eine ungueltige Anzahl aus der Definition wird durch einen Klick in den gueltigen Bereich gebracht")
    void editorClampsInvalidAmount() {
        RecipeEditorModel tooSmall = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, definition(0));
        tooSmall.changeAmount(1);
        assertEquals(1, tooSmall.amount());

        RecipeEditorModel tooLarge = RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, definition(100));
        tooLarge.changeAmount(-1);
        assertEquals(64, tooLarge.amount());
    }

    @Test
    @DisplayName("Rundlauf: ausgeliefertes Rezept mit Anzahl 1, 9 und 64 ueber den Editor unveraendert")
    void roundTripWithShippedRecipe() throws IOException {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new StringReader(Files.readString(SHIPPED_CONFIG_FILE, StandardCharsets.UTF_8)));
        RecipeDefinition shippedDefinition =
                RecipeStore.readDefinition(shipped.getConfigurationSection("recipes.chest_key"));

        for (int amount : List.of(1, 9, 64)) {
            RecipeDefinition withAmount = new RecipeDefinition(shippedDefinition.enabled(),
                    shippedDefinition.shape(), shippedDefinition.ingredients(), amount);
            RecipeDefinition roundTrip =
                    RecipeEditorModel.fromDefinition(RecipeKind.CHEST_KEY, withAmount).toDefinition();
            assertEquals(amount, roundTrip.amount());
            assertEquals(List.of(), RecipeValidator.validate(RecipeKind.CHEST_KEY, roundTrip));
        }
    }
}
