package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginConfig – Code-Defaults gegen ausgelieferte config.yml")
class PluginConfigShippedDefaultsTest {

    private static final Path SHIPPED_CONFIG_FILE = Path.of("src", "main", "resources", "config.yml");

    private static final Map<String, String> YAML_PATH_BY_ACCESSOR = Map.ofEntries(
            Map.entry("soundsEnabled",               "sounds.enabled"),
            Map.entry("particlesEnabled",            "particles.enabled"),
            Map.entry("signsEnabled",                "signs.enabled"),
            Map.entry("notificationsEnabled",        "notifications.enabled"),
            Map.entry("notificationsMaxPerPlayer",   "notifications.max_per_player"),
            Map.entry("confirmTimeoutSeconds",       "ui.confirm_timeout_seconds"),
            Map.entry("undoSeconds",                 "ui.undo_seconds"),
            Map.entry("maxUndoHistory",              "ui.max_undo_history"),
            Map.entry("trustNearbyRadius",           "ui.trust_nearby_radius"),
            Map.entry("autoLockEnabled",             "auto_lock.enabled"),
            Map.entry("autoLockRequireKey",          "auto_lock.require_key"),
            Map.entry("autoLockSneakOnly",           "auto_lock.sneak_only"),
            Map.entry("adminLogsBookEntries",        "admin.logs.book_entries"),
            Map.entry("adminLogsTeleportEnabled",    "admin.logs.teleport_enabled"),
            Map.entry("maxLocksPerPlayer",           "limits.max_locks_per_player"),
            Map.entry("maxHoppersPerPlayer",         "limits.max_hoppers_per_player"),
            Map.entry("lockActionCooldownMs",        "cooldowns.lock_action_ms"),
            Map.entry("breakNotificationCooldownMs", "cooldowns.break_notification_ms"),
            Map.entry("pingTimeoutSeconds",          "ping.timeout_seconds"),
            Map.entry("pingArrivedSeconds",          "ping.arrived_seconds"),
            Map.entry("pingHighlightEnabled",        "ping.highlight_enabled"),
            Map.entry("enabledWorlds",               "worlds.enabled"),
            Map.entry("language",                    "language"));

    private static final Set<String> ACCESSORS_WITHOUT_CODE_DEFAULT = Set.of();

    private static PluginConfig configFrom(Reader reader) {
        return new PluginConfig(YamlConfiguration.loadConfiguration(reader));
    }

    private static PluginConfig codeDefaults() {
        return configFrom(new StringReader(""));
    }

    private static String shippedConfigContent() throws IOException {
        return Files.readString(SHIPPED_CONFIG_FILE, StandardCharsets.UTF_8);
    }

    private static PluginConfig shippedConfig() throws IOException {
        return configFrom(new StringReader(shippedConfigContent()));
    }

    private static List<Method> valueAccessors() {
        return Stream.of(PluginConfig.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getReturnType() != void.class)
                .sorted((left, right) -> left.getName().compareTo(right.getName()))
                .toList();
    }

    @Test
    @DisplayName("Ausgelieferte config.yml existiert und ist lesbar")
    void shippedConfigFileIsReadable() throws IOException {
        assertTrue(Files.isRegularFile(SHIPPED_CONFIG_FILE),
                "Erwartet: " + SHIPPED_CONFIG_FILE.toAbsolutePath());
        assertFalse(shippedConfigContent().isBlank(), "config.yml darf nicht leer sein");
    }

    @Test
    @DisplayName("Ausgelieferte config.yml enthaelt unter recipes nur noch chest_key")
    void shippedConfigContainsOnlyChestKeyRecipe() throws IOException {
        YamlConfiguration shipped =
                YamlConfiguration.loadConfiguration(new StringReader(shippedConfigContent()));

        assertEquals(Set.of("chest_key"), shipped.getConfigurationSection("recipes").getKeys(false));
        assertFalse(shipped.contains("recipes.secure_hopper"),
                "recipes.secure_hopper wurde entfernt und darf nicht mehr ausgeliefert werden");
    }

    @Test
    @DisplayName("Jeder Wert-Accessor ist entweder zugeordnet oder als Ausnahme dokumentiert")
    void everyValueAccessorIsRegistered() {
        Set<String> registered = new HashSet<>(YAML_PATH_BY_ACCESSOR.keySet());
        registered.addAll(ACCESSORS_WITHOUT_CODE_DEFAULT);

        Set<String> declared = new HashSet<>();
        for (Method accessor : valueAccessors()) {
            declared.add(accessor.getName());
        }

        assertEquals(registered, declared,
                "Neue Config-Felder muessen in YAML_PATH_BY_ACCESSOR eingetragen werden, "
                        + "damit der Abgleich mit config.yml sie erfasst");
    }

    @TestFactory
    @DisplayName("Jeder Code-Default entspricht dem Wert in der ausgelieferten config.yml")
    Stream<DynamicTest> codeDefaultMatchesShippedValue() throws IOException {
        PluginConfig defaults = codeDefaults();
        PluginConfig shipped = shippedConfig();

        return valueAccessors().stream()
                .filter(accessor -> YAML_PATH_BY_ACCESSOR.containsKey(accessor.getName()))
                .map(accessor -> DynamicTest.dynamicTest(
                        YAML_PATH_BY_ACCESSOR.get(accessor.getName()),
                        () -> assertEquals(accessor.invoke(defaults), accessor.invoke(shipped),
                                YAML_PATH_BY_ACCESSOR.get(accessor.getName())
                                        + ": Code-Default in PluginConfig und Wert in config.yml "
                                        + "sind auseinandergelaufen")));
    }

    @TestFactory
    @DisplayName("Jeder von PluginConfig gelesene Pfad steht in der ausgelieferten config.yml")
    Stream<DynamicTest> shippedConfigContainsEveryReadPath() throws IOException {
        YamlConfiguration shipped =
                YamlConfiguration.loadConfiguration(new StringReader(shippedConfigContent()));

        return YAML_PATH_BY_ACCESSOR.values().stream()
                .sorted()
                .map(path -> DynamicTest.dynamicTest(path,
                        () -> assertTrue(shipped.contains(path),
                                path + " fehlt in config.yml; der Code-Default greift dann "
                                        + "unbemerkt statt des ausgelieferten Werts")));
    }
}
