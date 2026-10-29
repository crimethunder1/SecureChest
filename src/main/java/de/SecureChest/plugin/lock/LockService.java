package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LockService {

    boolean isLocked(Block block);

    Optional<LockEntry> getLock(Block block);

    Optional<LockEntry> getLockFromId(String lockId);

    Collection<LockEntry> getAllCachedLocks();

    Collection<LockEntry> getCachedLocksInChunk(UUID worldUuid, int chunkX, int chunkZ);

    boolean lock(Player owner, Block block);

    boolean unlockByIdWithUndo(Player actor, String lockId);

    boolean unlockAllWithUndo(Player actor, UUID owner);

    boolean restoreUndo(Player actor);

    boolean hasUndo(Player actor);

    boolean transferLock(Player actor, String lockId, UUID newOwner);

    boolean canOpen(Player player, Block block, boolean usingAdminKey);

    boolean canBreak(Player player, Block block);

    AccessLevel getAccessLevel(Player player, Block block);

    boolean setTrustById(Player owner, String lockId, UUID target, AccessLevel level);

    boolean removeTrustById(Player owner, String lockId, UUID target);

    List<Location> listLocks(UUID owner);

    List<Location> listLocks(UUID owner, int offset, int limit);

    int countLocks(UUID owner);

    LockStats getLockStats(UUID owner);

    boolean isSecureHopper(Block hopperBlock);

    Optional<SecureHopperEntry> getSecureHopper(Block hopperBlock);

    boolean bindSecureHopper(Player placer, Block hopperBlock, Block boundContainer);

    boolean removeSecureHopper(Block hopperBlock);

    List<Location> listSecureHoppers(UUID owner);

    boolean getQuickLock(UUID player);
    boolean getQuickRemove(UUID player);
    void setQuickLock(UUID player, boolean value);
    void setQuickRemove(UUID player, boolean value);

    boolean getShowOwnerName(UUID player);
    void setShowOwnerName(UUID player, boolean value);

    void forceRemoveLockAt(LockKey key);

    void forceRemoveHopperAt(LockKey key);

    LockRemovalBatch removeOutsideArea(Collection<LockEntry> locks, Collection<SecureHopperEntry> secureHoppers);

    Collection<SecureHopperEntry> getAllCachedHoppers();

    Collection<SecureHopperEntry> getCachedHoppersInChunk(UUID worldUuid, int chunkX, int chunkZ);

    void logUnauthorizedBreak(Player actor, Block target);
    void logAdminBreak(Player actor, Block target);
    void logSecureHopperRemove(Player actor, Block hopperBlock);
    void logInspectToggle(Player actor, boolean newState);

    void addNotification(UUID ownerUuid, String message);
    void addAlwaysDeliveredNotification(UUID ownerUuid, String message);
    List<Notification> fetchAndClearNotifications(UUID playerUuid);
    List<Notification> fetchAndClearAlwaysDeliveredNotifications(UUID playerUuid);

    ItemStack createLogsBook(int maxEntries);

    List<LogRecord> listLogs(String actionFilter, long minCreatedAt, String actorNameLike, int offset, int limit);
    int countLogs(String actionFilter, long minCreatedAt, String actorNameLike);
    Optional<LogRecord> getLogById(long id);

    ItemStack createAdminReportBook();

    void evictPlayerCache(UUID player);

    void preloadPlayerSettings(UUID player);
}
