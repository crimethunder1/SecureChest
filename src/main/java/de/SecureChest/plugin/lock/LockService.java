package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LockService {

    boolean isLocked(Block block);

    Optional<LockEntry> getLock(Block block);

    Optional<LockEntry> getLockFromId(String lockId);

    boolean lock(Player owner, Block block);

    boolean unlock(Player caller, Block block);

    boolean unlockById(Player caller, String lockId);

    /**
     * Unlock a single lock and create an undo snapshot for the actor.
     */
    boolean unlockByIdWithUndo(Player actor, String lockId);

    boolean unlockAll(UUID owner);

    /**
     * Unlock all locks & secure hoppers of the target owner and create an undo snapshot for the actor.
     */
    boolean unlockAllWithUndo(Player actor, UUID owner);

    /**
     * Restore the last undo snapshot of the actor (UNLOCK only).
     */
    boolean restoreUndo(Player actor);

    boolean hasUndo(Player actor);

    boolean canOpen(Player player, Block block, boolean usingAdminKey);

    boolean canBreak(Player player, Block block);

    AccessLevel getAccessLevel(Player player, Block block);

    boolean setTrust(Player owner, Block block, UUID target, AccessLevel level);

    boolean setTrustById(Player owner, String lockId, UUID target, AccessLevel level);

    boolean removeTrust(Player owner, Block block, UUID target);

    boolean removeTrustById(Player owner, String lockId, UUID target);

    List<Location> listLocks(UUID owner);

    // Secure Hopper
    boolean isSecureHopper(Block hopperBlock);

    Optional<SecureHopperEntry> getSecureHopper(Block hopperBlock);

    boolean bindSecureHopper(Player placer, Block hopperBlock, Block boundContainer);

    boolean removeSecureHopper(Block hopperBlock);

    List<Location> listSecureHoppers(UUID owner);

    // Player settings
    boolean getQuickLock(UUID player);
    boolean getQuickRemove(UUID player);
    void setQuickLock(UUID player, boolean value);
    void setQuickRemove(UUID player, boolean value);

    // Logs
    void logUnauthorizedBreak(Player actor, Block target);
    void logAdminBreak(Player actor, Block target);

    ItemStack createLogsBook(int maxEntries);

    // Logs UI
    List<LogRecord> listLogs(String actionFilter, long minCreatedAt, String actorNameLike, int offset, int limit);
    int countLogs(String actionFilter, long minCreatedAt, String actorNameLike);
    Optional<LogRecord> getLogById(long id);

    ItemStack createAdminReportBook();
}
