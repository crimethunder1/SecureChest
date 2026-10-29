package de.SecureChest.plugin.names;

import java.util.UUID;
import java.util.function.Function;

public final class PlayerNameResolver {

    private static final int SHORT_UUID_LENGTH = 8;
    private static final String SHORT_UUID_SUFFIX = "…";

    private final Function<UUID, String> onlineNames;
    private final Function<UUID, String> serverCacheNames;
    private final Function<UUID, String> storedNames;

    public PlayerNameResolver(Function<UUID, String> onlineNames,
                              Function<UUID, String> serverCacheNames,
                              Function<UUID, String> storedNames) {
        this.onlineNames      = onlineNames;
        this.serverCacheNames = serverCacheNames;
        this.storedNames      = storedNames;
    }

    public String resolve(UUID playerUuid) {
        String onlineName = onlineNames.apply(playerUuid);
        if (isUsable(onlineName)) return onlineName;
        String serverCacheName = serverCacheNames.apply(playerUuid);
        if (isUsable(serverCacheName)) return serverCacheName;
        String storedName = storedNames.apply(playerUuid);
        if (isUsable(storedName)) return storedName;
        return shortUuid(playerUuid);
    }

    public static String shortUuid(UUID playerUuid) {
        return playerUuid.toString().substring(0, SHORT_UUID_LENGTH) + SHORT_UUID_SUFFIX;
    }

    public static boolean isUsable(String name) {
        return name != null && !name.isBlank();
    }
}
