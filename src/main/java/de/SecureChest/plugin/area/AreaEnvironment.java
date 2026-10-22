package de.SecureChest.plugin.area;

import java.util.UUID;

public interface AreaEnvironment {

    String worldName(UUID worldUuid);

    boolean worldExists(String worldName);

    void notifyOwner(UUID owner, int removedLocks, int removedSecureHoppers);
}
