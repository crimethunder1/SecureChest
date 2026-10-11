package de.SecureChest.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class MessageService {

    static final String LANGUAGE_FOLDER = "lang";
    static final String FALLBACK_LANGUAGE = PluginConfig.DEFAULT_LANGUAGE;
    static final String LEGACY_MESSAGES_FILE = "messages.yml";
    static final String LEGACY_MESSAGES_BACKUP_FILE = "messages.yml.old";

    private final ChestSecurePlugin plugin;
    private FileConfiguration activeMessages;
    private FileConfiguration bundledMessages;
    private FileConfiguration fallbackMessages;

    public MessageService(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public String get(String key, Object... args) {
        Object rawValue = lookup(key);
        String raw;
        if (rawValue == null) {
            raw = "[" + key + "]";
        } else if (rawValue instanceof List<?> rawLines) {
            raw = String.join("\n", rawLines.stream().map(String::valueOf).toList());
        } else {
            raw = rawValue.toString();
        }
        return format(raw, args);
    }

    public List<String> getList(String key, Object... args) {
        Object rawValue = lookup(key);
        List<String> lines = new ArrayList<>();
        if (rawValue == null) {
            lines.add("[" + key + "]");
        } else if (rawValue instanceof List<?> rawLines) {
            for (Object rawLine : rawLines) {
                lines.add(format(rawLine == null ? "" : rawLine.toString(), args));
            }
        } else {
            lines.add(format(rawValue.toString(), args));
        }
        return lines;
    }

    public void reload() {
        File dataFolder = plugin.getDataFolder();
        File languageFolder = new File(dataFolder, LANGUAGE_FOLDER);

        migrateLegacyMessages(dataFolder, languageFolder);
        for (String supportedLanguage : PluginConfig.SUPPORTED_LANGUAGES) {
            File languageFile = new File(languageFolder, supportedLanguage + ".yml");
            if (!languageFile.exists()) {
                plugin.saveResource(resourcePath(supportedLanguage), false);
            }
        }

        String language = plugin.cfg().language();
        File activeFile = new File(languageFolder, language + ".yml");
        this.activeMessages   = YamlConfiguration.loadConfiguration(activeFile);
        this.bundledMessages  = loadBundled(language);
        this.fallbackMessages = loadBundled(FALLBACK_LANGUAGE);

        plugin.getLogger().info("Language " + language + " loaded from " + LANGUAGE_FOLDER + "/"
                + activeFile.getName() + " (" + activeMessages.getKeys(false).size() + " keys).");
    }

    private Object lookup(String key) {
        for (FileConfiguration source : List.of(activeMessages, bundledMessages, fallbackMessages)) {
            if (source.contains(key, true)) {
                Object value = source.get(key);
                if (value != null) return value;
            }
        }
        plugin.getLogger().warning("[MessageService] Missing message key: " + key);
        return null;
    }

    private void migrateLegacyMessages(File dataFolder, File languageFolder) {
        File legacyFile = new File(dataFolder, LEGACY_MESSAGES_FILE);
        File germanFile = new File(languageFolder, "de.yml");
        if (!legacyFile.isFile() || germanFile.exists()) return;

        try {
            Files.createDirectories(languageFolder.toPath());
            Files.copy(legacyFile.toPath(), germanFile.toPath());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not migrate " + LEGACY_MESSAGES_FILE + " to " + LANGUAGE_FOLDER + "/de.yml", ex);
            return;
        }

        File backupFile = new File(dataFolder, LEGACY_MESSAGES_BACKUP_FILE);
        try {
            Files.move(legacyFile.toPath(), backupFile.toPath());
            plugin.getLogger().info("Migrated " + LEGACY_MESSAGES_FILE + " to " + LANGUAGE_FOLDER
                    + "/de.yml, the old file was renamed to " + LEGACY_MESSAGES_BACKUP_FILE + ".");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Migrated " + LEGACY_MESSAGES_FILE + " to " + LANGUAGE_FOLDER + "/de.yml, but could not rename it to "
                            + LEGACY_MESSAGES_BACKUP_FILE, ex);
        }
    }

    private FileConfiguration loadBundled(String language) {
        try (InputStream resourceStream = plugin.getResource(resourcePath(language))) {
            if (resourceStream == null) {
                plugin.getLogger().warning("Bundled language file " + resourcePath(language) + " is missing in the plugin jar.");
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled language file " + resourcePath(language), ex);
            return new YamlConfiguration();
        }
    }

    private static String resourcePath(String language) {
        return LANGUAGE_FOLDER + "/" + language + ".yml";
    }

    private static String format(String raw, Object... args) {
        String formatted = translateColorCodes(raw);
        for (int i = 0; i < args.length; i++) {
            formatted = formatted.replace("{" + i + "}", args[i] == null ? "" : args[i].toString());
        }
        return formatted;
    }

    private static String translateColorCodes(String text) {
        if (text == null) return "";
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) >= 0) {
                chars[i]     = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
