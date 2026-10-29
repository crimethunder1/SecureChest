package de.SecureChest.plugin.settings;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class SettingsStore {

    public static final String FILE_NAME = "settings.yml";

    static final String KEY_OWNER_NAME_DISPLAY = "owner_name_display";
    static final String KEY_OWNER_NAME_HIDE_WHEN_COVERED = "owner_name_hide_when_covered";
    static final String KEY_CHEST_KEY_CONSUMPTION = "chest_key_consumption";

    public record Contents(Boolean ownerNameDisplay, Boolean ownerNameHideWhenCovered,
                           Boolean chestKeyConsumption) {

        public static final Contents EMPTY = new Contents(null, null, null);
    }

    private final Path file;
    private final Logger logger;
    private boolean unreadableAtLoad;
    private Contents current;

    public SettingsStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    public boolean isUnreadableAtLoad() {
        return unreadableAtLoad;
    }

    public Contents current() {
        if (current == null) load();
        return current;
    }

    public Contents load() {
        current = read();
        return current;
    }

    private Contents read() {
        unreadableAtLoad = false;
        if (!Files.exists(file)) return Contents.EMPTY;

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            unreadableAtLoad = true;
            logger.warning(FILE_NAME + " could not be read (" + ex.getMessage()
                    + "). The settings from config.yml are used. The file is not overwritten.");
            return Contents.EMPTY;
        }

        Boolean ownerNameDisplay = null;
        Boolean ownerNameHideWhenCovered = null;
        Boolean chestKeyConsumption = null;
        for (String key : yaml.getKeys(false)) {
            if (!key.equals(KEY_OWNER_NAME_DISPLAY) && !key.equals(KEY_OWNER_NAME_HIDE_WHEN_COVERED)
                    && !key.equals(KEY_CHEST_KEY_CONSUMPTION)) {
                logger.warning(FILE_NAME + ": entry '" + key + "' is not a known setting and was skipped.");
                continue;
            }
            if (!(yaml.get(key) instanceof Boolean value)) {
                logger.warning(FILE_NAME + ": entry '" + key
                        + "' is not true or false and was skipped. The value from config.yml is used.");
                continue;
            }
            switch (key) {
                case KEY_OWNER_NAME_DISPLAY -> ownerNameDisplay = value;
                case KEY_OWNER_NAME_HIDE_WHEN_COVERED -> ownerNameHideWhenCovered = value;
                default -> chestKeyConsumption = value;
            }
        }
        return new Contents(ownerNameDisplay, ownerNameHideWhenCovered, chestKeyConsumption);
    }

    public void save(Contents contents) throws IOException {
        current = contents;
        if (unreadableAtLoad) {
            throw new IOException(FILE_NAME + " could not be read at startup and is not overwritten."
                    + " Fix or remove the file and restart the server.");
        }

        YamlConfiguration yaml = new YamlConfiguration();
        if (contents.ownerNameDisplay() != null) {
            yaml.set(KEY_OWNER_NAME_DISPLAY, contents.ownerNameDisplay());
        }
        if (contents.ownerNameHideWhenCovered() != null) {
            yaml.set(KEY_OWNER_NAME_HIDE_WHEN_COVERED, contents.ownerNameHideWhenCovered());
        }
        if (contents.chestKeyConsumption() != null) {
            yaml.set(KEY_CHEST_KEY_CONSUMPTION, contents.chestKeyConsumption());
        }
        writeAtomically(yaml.saveToString());
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
