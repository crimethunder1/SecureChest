package de.SecureChest.plugin.util;

import java.util.regex.Pattern;

public final class ChestKeyAmountParser {

    public sealed interface Result permits Amount, Rejected {}

    public record Amount(int value) implements Result {}

    public record Rejected(String detail) implements Result {}

    public static final int MINIMUM_AMOUNT = 1;
    public static final int MAXIMUM_AMOUNT = 64;

    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private ChestKeyAmountParser() {}

    public static int upperLimit(int maximumStackSize) {
        return Math.min(MAXIMUM_AMOUNT, maximumStackSize);
    }

    public static Result parse(String input, int upperLimit) {
        String token = input == null ? "" : input.strip();
        if (!INTEGER.matcher(token).matches()) return new Rejected(token);
        int amount;
        try {
            amount = Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            return new Rejected(token);
        }
        if (amount < MINIMUM_AMOUNT || amount > upperLimit) return new Rejected(token);
        return new Amount(amount);
    }
}
