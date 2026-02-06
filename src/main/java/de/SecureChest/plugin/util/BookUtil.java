package de.SecureChest.plugin.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class BookUtil {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private BookUtil() {}

    public static String formatLogLine(long epochMillis, String action, String actorName, String world, int x, int y, int z) {
        String time = DF.format(Instant.ofEpochMilli(epochMillis));
        return "[" + time + "] " + action + " | " + actorName + " | " + world + " " + x + " " + y + " " + z;
    }

    public static ItemStack buildLogsBook(List<String> lines) {
        return buildBook("ChestSecure Logs", "ChestSecure Logs", "ChestSecure", lines);
    }

    public static ItemStack buildBook(String displayName, String title, String author, List<String> lines) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.displayName(Component.text(displayName));
        meta.setTitle(title);
        meta.setAuthor(author);

        List<Component> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            page.append(line).append("\n");
            lineCount++;
            if (lineCount >= 13) {
                pages.add(Component.text(page.toString()));
                page.setLength(0);
                lineCount = 0;
            }
        }
        if (!page.isEmpty()) {
            pages.add(Component.text(page.toString()));
        }

        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }
}
