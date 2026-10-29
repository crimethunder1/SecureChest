package de.SecureChest.plugin.names;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BukkitPlayerNameSources {

    private BukkitPlayerNameSources() {}

    public static String onlineName(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        return player == null ? null : player.getName();
    }

    public static String serverCacheName(UUID playerUuid) {
        return Bukkit.getOfflinePlayer(playerUuid).getName();
    }
}
