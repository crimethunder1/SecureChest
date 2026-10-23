package de.SecureChest.plugin.command;

import java.util.regex.Pattern;

public final class GiveKeyArgumentParser {

    public enum Reason {
        TOO_MANY_ARGUMENTS,
        INVALID_AMOUNT
    }

    public sealed interface Result permits Request, Rejected {}

    public record Request(String playerName, int amount) implements Result {

        public boolean hasPlayerName() {
            return playerName != null;
        }
    }

    public record Rejected(Reason reason, String detail) implements Result {}

    public static final int DEFAULT_AMOUNT = 1;
    public static final int MINIMUM_AMOUNT = 1;
    public static final int MAXIMUM_AMOUNT = 64;

    private static final int MAXIMUM_ARGUMENTS = 2;
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private GiveKeyArgumentParser() {}

    public static int upperLimit(int maximumStackSize) {
        return Math.min(MAXIMUM_AMOUNT, maximumStackSize);
    }

    public static Result parse(String[] arguments, int upperLimit) {
        if (arguments.length > MAXIMUM_ARGUMENTS) {
            return new Rejected(Reason.TOO_MANY_ARGUMENTS, String.valueOf(arguments.length));
        }
        String playerName = arguments.length >= 1 ? arguments[0] : null;
        if (arguments.length < MAXIMUM_ARGUMENTS) return new Request(playerName, DEFAULT_AMOUNT);

        String token = arguments[1];
        if (!INTEGER.matcher(token).matches()) return new Rejected(Reason.INVALID_AMOUNT, token);
        int amount;
        try {
            amount = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return new Rejected(Reason.INVALID_AMOUNT, token);
        }
        if (amount < MINIMUM_AMOUNT || amount > upperLimit) return new Rejected(Reason.INVALID_AMOUNT, token);
        return new Request(playerName, amount);
    }
}
