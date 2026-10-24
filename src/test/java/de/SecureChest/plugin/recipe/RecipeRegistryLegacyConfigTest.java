package de.SecureChest.plugin.recipe;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PluginConfig;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RecipeRegistry - Altbestand recipes.secure_hopper in config.yml")
class RecipeRegistryLegacyConfigTest {

    private static final String DISABLED_CHEST_KEY_SECTION = String.join("\n",
            "recipes:",
            "  chest_key:",
            "    enabled: false",
            "    shape:",
            "      - \"NDN\"",
            "      - \"DHD\"",
            "      - \"NDN\"",
            "    ingredients:",
            "      N: \"NETHERITE_INGOT\"",
            "      D: \"DIAMOND\"",
            "      H: \"TRIPWIRE_HOOK\"",
            "");
    private static final String LEGACY_SECURE_HOPPER_SECTION = String.join("\n",
            "  secure_hopper:",
            "    enabled: true",
            "    shape:",
            "      - \" I \"",
            "      - \"HKH\"",
            "      - \" I \"",
            "    ingredients:",
            "      I: \"IRON_BLOCK\"",
            "      H: \"HOPPER\"",
            "      K: \"CHEST_KEY\"",
            "");
    private static final RecipeDefinition DISABLED_CHEST_KEY = new RecipeDefinition(false,
            List.of("NDN", "DHD", "NDN"), Map.of("N", "NETHERITE_INGOT", "D", "DIAMOND", "H", "TRIPWIRE_HOOK"));
    private static final String CONFIG_INFO = "recipes.secure_hopper in config.yml is no longer used, "
            + "the Secure Hopper recipe was removed. "
            + "Hold the Chest Key and right click a hopper that faces your locked container instead.";
    private static final String RECIPES_FILE_INFO = "secure_hopper in recipes.yml is no longer used, "
            + "the Secure Hopper recipe was removed. "
            + "Hold the Chest Key and right click a hopper that faces your locked container instead.";

    @TempDir
    Path dataFolder;

    private final ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
    private final Logger logger = mock(Logger.class);
    private final Server server = mock(Server.class);

    @BeforeEach
    void setUp() {
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
    }

    private RecipeRegistry registryWithConfig(String yaml) {
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
        PluginConfig pluginConfig = new PluginConfig(plugin);
        when(plugin.cfg()).thenReturn(pluginConfig);
        clearInvocations(logger);
        return new RecipeRegistry(plugin);
    }

    @Test
    @DisplayName("Abschnitt recipes.secure_hopper wird nicht registriert, kein Fehler, eine Info-Zeile")
    void legacySecureHopperSectionIsIgnoredWithInfo() {
        RecipeRegistry registry = registryWithConfig(DISABLED_CHEST_KEY_SECTION + LEGACY_SECURE_HOPPER_SECTION);

        assertDoesNotThrow(registry::registerAll);

        assertNull(RecipeKind.fromConfigKey("secure_hopper"));
        verify(server, never()).addRecipe(any());
        assertEquals(DISABLED_CHEST_KEY, registry.currentDefinition(RecipeKind.CHEST_KEY));
        assertEquals(DISABLED_CHEST_KEY, registry.defaultDefinition(RecipeKind.CHEST_KEY));
        assertNull(registry.chestKeyRecipe());
        verify(logger).info(CONFIG_INFO);
        verify(logger, times(1)).info(anyString());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Ohne Abschnitt recipes.secure_hopper gibt es keine Info-Zeile")
    void withoutLegacySectionNoInfoIsLogged() {
        RecipeRegistry registry = registryWithConfig(DISABLED_CHEST_KEY_SECTION);

        registry.registerAll();

        assertEquals(DISABLED_CHEST_KEY, registry.currentDefinition(RecipeKind.CHEST_KEY));
        verify(logger, never()).info(anyString());
        verify(logger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Altbestand in config.yml und recipes.yml: je Datei genau eine Info-Zeile, keine Warnung")
    void legacyEntriesInBothFilesLogOneInfoPerFile() throws IOException {
        Files.writeString(dataFolder.resolve(RecipeStore.FILE_NAME),
                "secure_hopper:\n  shape: [\"H\", \"H\", \"H\"]\n  ingredients:\n    H: \"HOPPER\"\n");
        RecipeRegistry registry = registryWithConfig(DISABLED_CHEST_KEY_SECTION + LEGACY_SECURE_HOPPER_SECTION);

        registry.registerAll();

        assertFalse(registry.isOverridden(RecipeKind.CHEST_KEY));
        verify(server, never()).addRecipe(any());
        verify(logger).info(CONFIG_INFO);
        verify(logger).info(RECIPES_FILE_INFO);
        verify(logger, times(2)).info(anyString());
        verify(logger, never()).warning(anyString());
    }
}
