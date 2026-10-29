package de.SecureChest.plugin.names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecordingPlayerNameStore implements PlayerNameStore {

    record SavedName(UUID playerUuid, String name, long lastSeen) {}

    final Map<UUID, String> rows = new HashMap<>();
    final List<SavedName> saves = new ArrayList<>();
    final List<Boolean> loadAllInsideAsync = new ArrayList<>();
    RuntimeException loadFailure;
    int saveAllCalls;

    @Override
    public Map<UUID, String> loadAll() {
        loadAllInsideAsync.add(QueuedExecutor.isRunningAsync());
        if (loadFailure != null) throw loadFailure;
        return new LinkedHashMap<>(rows);
    }

    @Override
    public void save(UUID playerUuid, String name, long lastSeen) {
        saves.add(new SavedName(playerUuid, name, lastSeen));
        rows.put(playerUuid, name);
    }

    @Override
    public void saveAll(Map<UUID, String> names, long lastSeen) {
        saveAllCalls++;
        names.forEach((playerUuid, name) -> save(playerUuid, name, lastSeen));
    }
}
