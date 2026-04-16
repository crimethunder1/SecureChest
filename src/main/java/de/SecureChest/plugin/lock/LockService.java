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

    boolean lock(Player owner, Block block);

    boolean unlock(Player caller, Block block);

    boolean unlockById(Player caller, String lockId);

    boolean unlockByIdWithUndo(Player actor, String lockId);

    boolean unlockAll(UUID owner);

    boolean unlockAllWithUndo(Player actor, UUID owner);

    boolean restoreUndo(Player actor);

    boolean hasUndo(Player actor);

    boolean transferLock(Player actor, String lockId, UUID newOwner);

    boolean canOpen(Player player, Block block, boolean usingAdminKey);

    boolean canBreak(Player player, Block block);

    AccessLevel getAccessLevel(Player player, Block block);

    boolean setTrust(Player owner, Block block, UUID target, AccessLevel level);

    boolean setTrustById(Player owner, String lockId, UUID target, AccessLevel level);

    boolean removeTrust(Player owner, Block block, UUID target);

    boolean removeTrustById(Player owner, String lockId, UUID target);

    /** Gibt alle Locks des Spielers sortiert zurueck (ungepaginiert – nur fuer isEmpty-Checks). */
    List<Location> listLocks(UUID owner);

    /**
     * Paginierte Variante: gibt maximal {@code limit} Locks ab {@code offset} zurueck.
     * Effizienter als listLocks() fuer GUI-Paginierung mit vielen Locks.
     */
    List<Location> listLocks(UUID owner, int offset, int limit);

    /** Zaehlt alle Locks des Spielers (fuer Paginierungs-Berechnung). */
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

    /**
     * Entfernt einen Lock vollständig ohne Player-Referenz (systemseitig, z.B. Endportal, Strukturen).
     * Löscht den Cache-Eintrag, das Nametag-Sign und persistiert die Löschung asynchron in der DB.
     */
    void forceRemoveLockAt(LockKey key);

    /**
     * Entfernt einen Secure Hopper vollständig ohne Player-Referenz (systemseitig).
     */
    void forceRemoveHopperAt(LockKey key);

    /**
     * Gibt alle gecachten Secure Hopper zurück (für Validierungsaufgaben).
     */
    Collection<SecureHopperEntry> getAllCachedHoppers();

    void logUnauthorizedBreak(Player actor, Block target);
    void logAdminBreak(Player actor, Block target);

    void addNotification(UUID ownerUuid, String message);
    List<Notification> fetchAndClearNotifications(UUID playerUuid);

    ItemStack createLogsBook(int maxEntries);

    List<LogRecord> listLogs(String actionFilter, long minCreatedAt, String actorNameLike, int offset, int limit);
    int countLogs(String actionFilter, long minCreatedAt, String actorNameLike);
    Optional<LogRecord> getLogById(long id);

    ItemStack createAdminReportBook();

    /**
     * Raeumen den spieler-lokalen Settings-Cache (QuickLock/QuickRemove) auf.
     * Wird beim Logout aufgerufen um Speicher freizugeben.
     */
    void evictPlayerCache(UUID player);

    /**
     * Laedt die Spielereinstellungen asynchron in den Cache.
     * Wird beim Login aufgerufen damit erste Event-Handler direkt Caches treffen.
     */
    void preloadPlayerSettings(UUID player);
}
