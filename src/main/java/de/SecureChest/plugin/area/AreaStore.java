package de.SecureChest.plugin.area;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class AreaStore {

    public static final String FILE_NAME = "areas.yml";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AREAS = "areas";
    private static final String KEY_NAME = "name";
    private static final String KEY_WORLD = "world";
    private static final String KEY_MIN_X = "min_x";
    private static final String KEY_MIN_Z = "min_z";
    private static final String KEY_MAX_X = "max_x";
    private static final String KEY_MAX_Z = "max_z";

    public record Contents(boolean enabled, List<Area> areas) {

        public static final Contents EMPTY = new Contents(false, List.of());

        public Contents {
            areas = List.copyOf(areas);
        }
    }

    private final Path file;
    private final Logger logger;
    private boolean unreadableAtLoad;

    public AreaStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    public boolean isUnreadableAtLoad() {
        return unreadableAtLoad;
    }

    public Contents load() {
        unreadableAtLoad = false;
        if (!Files.exists(file)) return Contents.EMPTY;

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            unreadableAtLoad = true;
            logger.warning(FILE_NAME + " could not be read (" + ex.getMessage()
                    + "). Area restriction is off and no areas are loaded. The file is not overwritten.");
            return Contents.EMPTY;
        }

        boolean enabled = yaml.getBoolean(KEY_ENABLED, false);
        List<Area> areas = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        int index = 0;
        for (Map<?, ?> rawArea : yaml.getMapList(KEY_AREAS)) {
            index++;
            Area area = parseArea(rawArea);
            if (area == null) {
                logger.warning(FILE_NAME + ": area entry " + index + " is invalid and was skipped.");
                continue;
            }
            if (!seenNames.add(Area.nameKey(area.name()))) {
                logger.warning(FILE_NAME + ": area name '" + area.name() + "' is used more than once, entry "
                        + index + " was skipped.");
                continue;
            }
            areas.add(area);
        }
        return new Contents(enabled, areas);
    }

    public void save(Contents contents) throws IOException {
        if (unreadableAtLoad) {
            throw new IOException(FILE_NAME + " could not be read at startup and is not overwritten."
                    + " Fix or remove the file and restart the server.");
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(KEY_ENABLED, contents.enabled());
        List<Map<String, Object>> serializedAreas = new ArrayList<>();
        for (Area area : contents.areas()) {
            Map<String, Object> serializedArea = new LinkedHashMap<>();
            serializedArea.put(KEY_NAME, area.name());
            serializedArea.put(KEY_WORLD, area.worldName());
            serializedArea.put(KEY_MIN_X, area.minX());
            serializedArea.put(KEY_MIN_Z, area.minZ());
            serializedArea.put(KEY_MAX_X, area.maxX());
            serializedArea.put(KEY_MAX_Z, area.maxZ());
            serializedAreas.add(serializedArea);
        }
        yaml.set(KEY_AREAS, serializedAreas);
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

    private static Area parseArea(Map<?, ?> rawArea) {
        if (!(rawArea.get(KEY_NAME) instanceof String name) || !Area.isValidName(name)) return null;
        if (!(rawArea.get(KEY_WORLD) instanceof String worldName) || worldName.isBlank()) return null;
        Integer minX = integerValue(rawArea.get(KEY_MIN_X));
        Integer minZ = integerValue(rawArea.get(KEY_MIN_Z));
        Integer maxX = integerValue(rawArea.get(KEY_MAX_X));
        Integer maxZ = integerValue(rawArea.get(KEY_MAX_Z));
        if (minX == null || minZ == null || maxX == null || maxZ == null) return null;
        return Area.normalized(name, worldName, minX, minZ, maxX, maxZ);
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Integer integer) return integer;
        return null;
    }
}
