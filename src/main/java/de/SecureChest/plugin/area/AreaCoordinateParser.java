package de.SecureChest.plugin.area;

import java.util.regex.Pattern;

public final class AreaCoordinateParser {

    public enum Reason {
        WRONG_COUNT,
        NOT_AN_INTEGER,
        OUT_OF_RANGE
    }

    public sealed interface Result permits Coordinates, Rejected {}

    public record Coordinates(int firstX, int firstZ, int secondX, int secondZ) implements Result {}

    public record Rejected(Reason reason, String detail) implements Result {}

    private static final int EXPECTED_COUNT = 4;
    private static final Pattern SEPARATOR = Pattern.compile(" +");
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private AreaCoordinateParser() {}

    public static Result parse(String input) {
        String trimmed = input == null ? "" : input.strip();
        String[] tokens = trimmed.isEmpty() ? new String[0] : SEPARATOR.split(trimmed);
        if (tokens.length != EXPECTED_COUNT) {
            return new Rejected(Reason.WRONG_COUNT, String.valueOf(tokens.length));
        }

        int[] values = new int[EXPECTED_COUNT];
        for (int index = 0; index < EXPECTED_COUNT; index++) {
            String token = tokens[index];
            if (!INTEGER.matcher(token).matches()) return new Rejected(Reason.NOT_AN_INTEGER, token);
            try {
                values[index] = Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                return new Rejected(Reason.OUT_OF_RANGE, token);
            }
        }
        return new Coordinates(values[0], values[1], values[2], values[3]);
    }
}
