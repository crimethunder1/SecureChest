package de.SecureChest.plugin.ping;

public record PingDirection(Sector sector, int heightDifferenceBlocks, double distance) {

    public static final double AHEAD_HALF_WIDTH_DEGREES      = 12.0;
    public static final double AHEAD_DIAGONAL_LIMIT_DEGREES  = 67.5;
    public static final double SIDE_LIMIT_DEGREES            = 112.5;
    public static final double BEHIND_DIAGONAL_LIMIT_DEGREES = 157.5;
    public static final int    HEIGHT_HINT_MINIMUM_BLOCKS    = 2;

    public enum Sector {
        AHEAD, AHEAD_RIGHT, RIGHT, BEHIND_RIGHT, BEHIND, BEHIND_LEFT, LEFT, AHEAD_LEFT
    }

    public enum HeightHint {
        NONE, ABOVE, BELOW
    }

    public static PingDirection calculate(double playerX, double playerY, double playerZ, double playerYaw,
                                          double targetX, double targetY, double targetZ) {
        double deltaX = targetX - playerX;
        double deltaY = targetY - playerY;
        double deltaZ = targetZ - playerZ;
        double relativeAngle = relativeAngleDegrees(playerYaw, deltaX, deltaZ);
        int heightDifference = (int) Math.floor(targetY) - (int) Math.floor(playerY);
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        return new PingDirection(sectorFor(relativeAngle), heightDifference, distance);
    }

    public static double relativeAngleDegrees(double playerYaw, double deltaX, double deltaZ) {
        double bearingDegrees = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        return normalizeDegrees(bearingDegrees - playerYaw);
    }

    public static double normalizeDegrees(double angleDegrees) {
        double normalized = angleDegrees % 360.0;
        if (normalized > 180.0) normalized -= 360.0;
        if (normalized <= -180.0) normalized += 360.0;
        return normalized;
    }

    public static Sector sectorFor(double relativeAngleDegrees) {
        double normalizedAngle = normalizeDegrees(relativeAngleDegrees);
        double absoluteAngle = Math.abs(normalizedAngle);
        boolean toTheRight = normalizedAngle > 0;
        if (absoluteAngle <= AHEAD_HALF_WIDTH_DEGREES) return Sector.AHEAD;
        if (absoluteAngle <= AHEAD_DIAGONAL_LIMIT_DEGREES) return toTheRight ? Sector.AHEAD_RIGHT : Sector.AHEAD_LEFT;
        if (absoluteAngle <= SIDE_LIMIT_DEGREES) return toTheRight ? Sector.RIGHT : Sector.LEFT;
        if (absoluteAngle <= BEHIND_DIAGONAL_LIMIT_DEGREES) return toTheRight ? Sector.BEHIND_RIGHT : Sector.BEHIND_LEFT;
        return Sector.BEHIND;
    }

    public HeightHint heightHint() {
        if (heightDifferenceBlocks >= HEIGHT_HINT_MINIMUM_BLOCKS) return HeightHint.ABOVE;
        if (heightDifferenceBlocks <= -HEIGHT_HINT_MINIMUM_BLOCKS) return HeightHint.BELOW;
        return HeightHint.NONE;
    }

    public int distanceBlocks() {
        return (int) distance;
    }
}
