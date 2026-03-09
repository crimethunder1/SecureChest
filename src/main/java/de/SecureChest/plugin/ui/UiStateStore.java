package de.SecureChest.plugin.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UiStateStore {

    public enum LockSort {
        DISTANCE,
        WORLD,
        TYPE,
        RECENT;

        public LockSort next() {
            LockSort[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String label() {
            return switch (this) {
                case DISTANCE -> "Entfernung";
                case WORLD    -> "Welt";
                case TYPE     -> "Typ";
                case RECENT   -> "Neueste";
            };
        }
    }

    public enum WorldFilter {
        ALL,
        CURRENT;

        public WorldFilter next() {
            WorldFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String label() {
            return switch (this) {
                case ALL     -> "Alle Welten";
                case CURRENT -> "Aktuelle Welt";
            };
        }
    }

    public enum LockTypeFilter {
        ALL,
        CHESTS,
        BARRELS,
        SHULKERS;

        public LockTypeFilter next() {
            LockTypeFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String label() {
            return switch (this) {
                case ALL      -> "Alle Typen";
                case CHESTS   -> "Kisten";
                case BARRELS  -> "Fässer";
                case SHULKERS -> "Shulker-Boxen";
            };
        }
    }

    public enum LogsActionFilter {
        ALL,
        UNAUTHORIZED_BREAK,
        ADMIN_BREAK;

        public LogsActionFilter next() {
            LogsActionFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String label() {
            return switch (this) {
                case ALL                -> "Alle Aktionen";
                case UNAUTHORIZED_BREAK -> "Unerlaubte Breaks";
                case ADMIN_BREAK        -> "Admin-Breaks";
            };
        }

        public String databaseValue() {
            return switch (this) {
                case UNAUTHORIZED_BREAK -> "UNAUTHORIZED_BREAK";
                case ADMIN_BREAK        -> "ADMIN_BREAK";
                default                 -> "ALL";
            };
        }
    }

    public enum LogsTimeFilter {
        LAST_24_HOURS,
        LAST_7_DAYS,
        LAST_30_DAYS,
        ALL_TIME;

        public LogsTimeFilter next() {
            LogsTimeFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String label() {
            return switch (this) {
                case LAST_24_HOURS -> "Letzte 24 Std";
                case LAST_7_DAYS   -> "Letzte 7 Tage";
                case LAST_30_DAYS  -> "Letzte 30 Tage";
                case ALL_TIME      -> "Gesamte Zeit";
            };
        }

        public long minTimestamp() {
            long now = System.currentTimeMillis();
            return switch (this) {
                case LAST_24_HOURS -> now - 24L * 60L * 60L * 1000L;
                case LAST_7_DAYS   -> now - 7L  * 24L * 60L * 60L * 1000L;
                case LAST_30_DAYS  -> now - 30L * 24L * 60L * 60L * 1000L;
                case ALL_TIME      -> 0L;
            };
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
        public LogsTimeFilter time = LogsTimeFilter.LAST_24_HOURS;
        public String actorFilter = "";
    }

    private final Map<UUID, LockListState> lockListStates = new ConcurrentHashMap<>();
    private final Map<UUID, LogsState>     logStates      = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean>       inspectModes   = new ConcurrentHashMap<>();

    public LockListState locks(UUID playerUuid) {
        return lockListStates.computeIfAbsent(playerUuid, ignored -> new LockListState());
    }

    public LogsState logs(UUID playerUuid) {
        return logStates.computeIfAbsent(playerUuid, ignored -> new LogsState());
    }

    public boolean inspect(UUID playerUuid) {
        return inspectModes.getOrDefault(playerUuid, false);
    }

    public void setInspect(UUID playerUuid, boolean enabled) {
        inspectModes.put(playerUuid, enabled);
    }

    public void cleanup(UUID playerUuid) {
        lockListStates.remove(playerUuid);
        logStates.remove(playerUuid);
        inspectModes.remove(playerUuid);
    }
}
