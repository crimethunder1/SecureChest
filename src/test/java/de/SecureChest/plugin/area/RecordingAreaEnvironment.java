package de.SecureChest.plugin.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RecordingAreaEnvironment implements AreaEnvironment {

    public record OwnerNotification(UUID owner, int removedLocks, int removedSecureHoppers) {}

    private final Map<UUID, String> worldNames = new HashMap<>();
    private final Set<String> existingWorlds = new HashSet<>();
    private final List<OwnerNotification> notifications = new ArrayList<>();

    public RecordingAreaEnvironment addWorld(UUID worldUuid, String worldName) {
        worldNames.put(worldUuid, worldName);
        existingWorlds.add(worldName);
        return this;
    }

    public void removeWorld(String worldName) {
        existingWorlds.remove(worldName);
        worldNames.values().removeIf(worldName::equals);
    }

    public List<OwnerNotification> notifications() {
        return List.copyOf(notifications);
    }

    @Override
    public String worldName(UUID worldUuid) {
        return worldNames.get(worldUuid);
    }

    @Override
    public boolean worldExists(String worldName) {
        return existingWorlds.contains(worldName);
    }

    @Override
    public void notifyOwner(UUID owner, int removedLocks, int removedSecureHoppers) {
        notifications.add(new OwnerNotification(owner, removedLocks, removedSecureHoppers));
    }
}
