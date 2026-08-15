package de.SecureChest.plugin;

import de.SecureChest.plugin.util.Worlds;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("worlds.enabled – ausgelieferte Whitelist gegen Code-Default und Whitelist-Semantik")
class EnabledWorldsWhitelistTest {

    private static final Path SHIPPED_CONFIG_FILE = Path.of("src", "main", "resources", "config.yml");

    private static final List<String> EXPECTED_ENABLED_WORLDS =
            List.of("world", "world_nether", "world_the_end");

    private static PluginConfig configFrom(String yaml) {
        return new PluginConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static PluginConfig shippedConfig() throws IOException {
        return configFrom(Files.readString(SHIPPED_CONFIG_FILE, StandardCharsets.UTF_8));
    }

    private static Worlds worldsFor(PluginConfig config) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.cfg()).thenReturn(config);
        return new Worlds(plugin);
    }

    private static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    @Test
    @DisplayName("Ausgelieferte config.yml listet genau die drei Standardwelten")
    void shippedConfigListsTheThreeDefaultWorlds() throws IOException {
        assertEquals(EXPECTED_ENABLED_WORLDS, shippedConfig().enabledWorlds(),
                "worlds.enabled in config.yml ist von den drei Standardwelten abgewichen; "
                        + "eine leere Liste bedeutet stattdessen Schutz in allen Welten");
    }

    @Test
    @DisplayName("Code-Default in PluginConfig listet genau die drei Standardwelten")
    void codeDefaultListsTheThreeDefaultWorlds() {
        assertEquals(EXPECTED_ENABLED_WORLDS, configFrom("").enabledWorlds(),
                "Der Code-Default fuer worlds.enabled ist von den drei Standardwelten abgewichen");
    }

    @Test
    @DisplayName("Code-Default und ausgelieferter Wert sind identisch und nicht leer")
    void codeDefaultMatchesShippedValueAndIsNotEmpty() throws IOException {
        List<String> codeDefault = configFrom("").enabledWorlds();
        List<String> shipped = shippedConfig().enabledWorlds();

        assertFalse(codeDefault.isEmpty(),
                "Ein leerer Code-Default wuerde alle Welten schuetzen und damit von der "
                        + "ausgelieferten Whitelist abweichen");
        assertEquals(codeDefault, shipped,
                "Code-Default in PluginConfig und worlds.enabled in config.yml sind "
                        + "auseinandergelaufen");
    }

    @Test
    @DisplayName("Ausgelieferte Whitelist schuetzt die drei Standardwelten, aber keine eigene Welt")
    void shippedWhitelistProtectsOnlyTheListedWorlds() throws IOException {
        Worlds worlds = worldsFor(shippedConfig());

        for (String name : EXPECTED_ENABLED_WORLDS) {
            assertTrue(worlds.isEnabled(world(name)), name + " muss geschuetzt sein");
        }
        assertFalse(worlds.isEnabled(world("creative")),
                "Eine eigene Welt darf mit der ausgelieferten Whitelist nicht geschuetzt sein; "
                        + "trifft das nicht mehr zu, ist worlds.enabled leer geworden");
    }

    @Test
    @DisplayName("Leere Liste schuetzt alle Welten und ist damit nicht gleichbedeutend mit der Whitelist")
    void emptyListProtectsEveryWorld() {
        Worlds worlds = worldsFor(configFrom("worlds:\n  enabled: []"));

        assertTrue(worlds.isEnabled(world("creative")));
        assertTrue(worlds.isEnabled(world("world")));
    }
}
