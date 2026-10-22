package de.SecureChest.plugin.area;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Area(String name, String worldName, int minX, int minZ, int maxX, int maxZ) {

    public static final int MAX_NAME_LENGTH = 32;

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1," + MAX_NAME_LENGTH + "}");

    public Area {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(worldName, "worldName");
        if (!isValidName(name)) throw new IllegalArgumentException("Invalid area name: " + name);
        if (worldName.isBlank()) throw new IllegalArgumentException("World name must not be blank");
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Area corners are not normalized: " + name);
        }
    }

    public static Area normalized(String name, String worldName, int firstX, int firstZ, int secondX, int secondZ) {
        return new Area(name, worldName,
                Math.min(firstX, secondX), Math.min(firstZ, secondZ),
                Math.max(firstX, secondX), Math.max(firstZ, secondZ));
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public boolean contains(String otherWorldName, int x, int z) {
        return worldName.equals(otherWorldName)
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ;
    }

    public Area withCorners(int firstX, int firstZ, int secondX, int secondZ) {
        return normalized(name, worldName, firstX, firstZ, secondX, secondZ);
    }
}
