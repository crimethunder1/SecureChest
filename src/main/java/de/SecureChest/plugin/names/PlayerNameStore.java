package de.SecureChest.plugin.names;

import java.util.Map;
import java.util.UUID;

public interface PlayerNameStore {

    Map<UUID, String> loadAll();

    void save(UUID playerUuid, String name, long lastSeen);

    void saveAll(Map<UUID, String> names, long lastSeen);
}
