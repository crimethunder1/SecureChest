package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprachdateien: Schluessel des Admin-Verwaltungsmenues")
class AdminLockManageLanguageKeysTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");

    private static final Map<String, Integer> NEW_KEYS_WITH_PLACEHOLDERS = Map.ofEntries(
            Map.entry("gui-admin-lock-manage-title", 1),
            Map.entry("gui-admin-lock-manage-info-name", 2),
            Map.entry("gui-admin-lock-manage-transfer-name", 0),
            Map.entry("gui-admin-lock-manage-transfer-lore", 0),
            Map.entry("gui-admin-lock-manage-remove-name", 0),
            Map.entry("gui-admin-lock-manage-remove-lore", 0),
            Map.entry("gui-admin-lock-manage-add-viewer-name", 0),
            Map.entry("gui-admin-lock-manage-add-viewer-lore", 0),
            Map.entry("gui-admin-lock-manage-add-stocker-name", 0),
            Map.entry("gui-admin-lock-manage-add-stocker-lore", 0),
            Map.entry("gui-admin-lock-manage-trusted-entry-lore", 2),
            Map.entry("gui-admin-lock-manage-close-name", 0),
            Map.entry("gui-admin-lock-manage-close-lore", 0),
            Map.entry("confirm-admin-unlock-title", 0),
            Map.entry("confirm-admin-unlock-question", 1),
            Map.entry("confirm-admin-unlock-block", 2),
            Map.entry("confirm-admin-owner-notified", 0),
            Map.entry("confirm-admin-transfer-title", 0),
            Map.entry("confirm-admin-transfer-question", 2),
            Map.entry("confirm-admin-remove-trust-title", 0),
            Map.entry("confirm-admin-remove-trust-question", 2),
            Map.entry("admin-transfer-bereits-besitzer", 1),
            Map.entry("notification-admin-lock-removed", 2),
            Map.entry("notification-admin-lock-transferred", 3),
            Map.entry("gui-logs-entry-action-lock-transfer-admin", 0),
            Map.entry("gui-logs-entry-action-trust-set-admin", 0),
            Map.entry("gui-logs-entry-action-trust-remove-admin", 0));

    private static List<String> values(YamlConfiguration file, String key) {
        if (file.isList(key)) return file.getStringList(key);
        String value = file.getString(key);
        return value == null ? List.of() : List.of(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en"})
    @DisplayName("Jeder neue Schluessel existiert, ist nicht leer, hat die erwarteten Platzhalter und keine Gedankenstriche")
    void neueSchluessel(String language) {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(
                LANGUAGE_FOLDER.resolve(language + ".yml").toFile());

        NEW_KEYS_WITH_PLACEHOLDERS.forEach((key, placeholders) -> {
            List<String> lines = values(file, key);
            assertFalse(lines.isEmpty(), language + ".yml: fehlender Schluessel " + key);
            String joined = String.join("\n", lines);
            assertFalse(joined.isBlank(), language + ".yml: leerer Wert bei " + key);
            for (int index = 0; index < placeholders; index++) {
                assertTrue(joined.contains("{" + index + "}"), language + ".yml: {" + index + "} fehlt bei " + key);
            }
            assertFalse(joined.contains("{" + placeholders + "}"),
                    language + ".yml: unerwarteter Platzhalter bei " + key);
            assertFalse(joined.contains("\u2013") || joined.contains("\u2014"),
                    language + ".yml: Gedankenstrich bei " + key);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en"})
    @DisplayName("Benachrichtigungen nennen einen Admin allgemein")
    void benachrichtigungNenntEinenAdmin(String language) {
        YamlConfiguration file = YamlConfiguration.loadConfiguration(
                LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
        String expected = language.equals("de") ? "Ein Admin" : "An admin";

        assertTrue(file.getString("notification-admin-lock-removed").startsWith(expected));
        assertTrue(file.getString("notification-admin-lock-transferred").startsWith(expected));
    }
}
