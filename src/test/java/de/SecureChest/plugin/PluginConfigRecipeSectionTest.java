package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginConfig – recipeSection ohne Plugin-Instanz")
class PluginConfigRecipeSectionTest {

    private static PluginConfig withoutPlugin() {
        return new PluginConfig(YamlConfiguration.loadConfiguration(new StringReader(
                "recipes:\n  chest_key:\n    enabled: true")));
    }

    @Test
    @DisplayName("recipeSection wirft IllegalStateException statt NullPointerException")
    void recipeSectionWithoutPluginThrowsIllegalStateException() {
        PluginConfig config = withoutPlugin();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> config.recipeSection("chest_key"));

        assertTrue(failure.getMessage().contains("chest_key"),
                "Die Meldung soll das angefragte Rezept nennen: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("Plugin-Instanz"),
                "Die Meldung soll die fehlende Plugin-Instanz nennen: " + failure.getMessage());
    }

    @Test
    @DisplayName("recipeSection wirft keine NullPointerException mehr")
    void recipeSectionWithoutPluginDoesNotThrowNullPointerException() {
        PluginConfig config = withoutPlugin();

        Throwable failure =
                assertThrows(Throwable.class, () -> config.recipeSection("secure_hopper"));

        assertFalse(failure instanceof NullPointerException,
                "Erwartet wird ein aussagekraeftiger Fehler statt der rohen NPE");
    }
}
