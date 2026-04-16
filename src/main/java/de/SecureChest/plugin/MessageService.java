package de.SecureChest.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Laedt alle Spieler-Nachrichten aus {@code messages.yml} und stellt sie
 * ueber {@link #get(String, Object...)} bereit.
 *
 * <p>Platzhalter {0}, {1}, … werden durch die uebergebenen Argumente ersetzt.
 * Ampersand-Farbcodes (&amp;a, &amp;c, …) werden in §-Codes umgewandelt.</p>
 */
public final class MessageService {

    private final ChestSecurePlugin plugin;
    private FileConfiguration messages;

    public MessageService(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Gibt die Nachricht zum {@code key} zurueck.
     * Unbekannte Keys werden als {@code "[key]"} zurueckgegeben und geloggt.
     *
     * @param key  Schluessel aus messages.yml
     * @param args Optionale Platzhalter-Werte fuer {0}, {1}, …
     */
    public String get(String key, Object... args) {
        String raw = messages.getString(key);
        if (raw == null) {
            plugin.getLogger().warning("[MessageService] Fehlender Schluessel: " + key);
            raw = "[" + key + "]";
        }
        // Platzhalter ersetzen
        for (int i = 0; i < args.length; i++) {
            raw = raw.replace("{" + i + "}", args[i] == null ? "" : args[i].toString());
        }
        // &-Farbcodes → §-Codes
        return translateColorCodes(raw);
    }

    /** Laedt messages.yml neu (z.B. nach /cs reload). */
    public void reload() {
        plugin.saveResource("messages.yml", false);
        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        // Fehlende Schluessel aus der eingebetteten Datei ergaenzen
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            loaded.setDefaults(defaults);
        }

        this.messages = loaded;
        plugin.getLogger().info("messages.yml geladen (" + loaded.getKeys(false).size() + " Schluessel).");
    }

    // ── Hilfsmethod ──────────────────────────────────────────────────────────

    private static String translateColorCodes(String text) {
        if (text == null) return "";
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) >= 0) {
                chars[i]     = '\u00A7'; // §
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
