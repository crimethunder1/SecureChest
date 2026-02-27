package de.SecureChest.plugin.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral (in-memory) UI state per player.
 */
public final class UiStateStore {

    public enum LockSort {
        DISTANCE,
        WORLD,
        TYPE,
        RECENT;

        public LockSort next() {
            LockSort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum WorldFilter {
        ALL,
        CURRENT;

        public WorldFilter next() {
            WorldFilter[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum LockTypeFilter {
        ALL,
        CHESTS,
        BARRELS,
        SHULKERS;

        public LockTypeFilter next() {
            LockTypeFilter[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum LogsActionFilter {
        ALL,
        UNAUTHORIZED_BREAK,
        ADMIN_BREAK;

        public LogsActionFilter next() {
            LogsActionFilter[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum LogsTimeFilter {
        H24,
        D7,
        D30,
        ALL;

        public LogsTimeFilter next() {
            LogsTimeFilter[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static final class LockListState {
        public int page = 0;
        public LockSort sort = LockSort.DISTANCE;
        public WorldFilter world = WorldFilter.ALL;
        public LockTypeFilter type = LockTypeFilter.ALL;
    }

    public static final class LogsState {
        public int page = 0;
        public LogsActionFilter action = LogsActionFilter.ALL;
        public LogsTimeFilter time = LogsTimeFilter.H24;
        public String actorFilter = "";
    }

    private final Map<UUID, LockListState> locks = new ConcurrentHashMap<>();
    private final Map<UUID, LogsState> logs = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> inspectMode = new ConcurrentHashMap<>();

    public LockListState locks(UUID player) {
        return locks.computeIfAbsent(player, u -> new LockListState());
    }

    public LogsState logs(UUID player) {
        return logs.computeIfAbsent(player, u -> new LogsState());
    }

    public boolean inspect(UUID player) {
        return inspectMode.getOrDefault(player, false);
    }

    public void setInspect(UUID player, boolean on) {
        inspectMode.put(player, on);
    }

    /** Remove all state for a player (call on disconnect). */
    public void cleanup(UUID player) {
        locks.remove(player);
        logs.remove(player);
        inspectMode.remove(player);
    }
}
