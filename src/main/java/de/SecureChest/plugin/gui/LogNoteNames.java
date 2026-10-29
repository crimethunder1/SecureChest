package de.SecureChest.plugin.gui;

import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LogNoteNames {

    private static final Pattern PLAYER_VALUE = Pattern.compile(
            "(?<![^,])(target_owner|from|to|target|owner)="
            + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?![^,])");

    private LogNoteNames() {}

    static String withNames(String note, Function<UUID, String> names) {
        if (note == null || note.isEmpty()) return note;
        Matcher matcher = PLAYER_VALUE.matcher(note);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = names.apply(UUID.fromString(matcher.group(2)));
            String replacement = name == null || name.isBlank() ? matcher.group() : matcher.group(1) + "=" + name;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
