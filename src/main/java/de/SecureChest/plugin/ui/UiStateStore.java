package de.SecureChest.plugin.ui;

import de.SecureChest.plugin.recipe.RecipeEditorModel;
import de.SecureChest.plugin.recipe.RecipeKind;
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

        public String labelKey() {
            return switch (this) {
                case DISTANCE -> "enum-lock-sort-distance";
                case WORLD    -> "enum-lock-sort-world";
                case TYPE     -> "enum-lock-sort-type";
                case RECENT   -> "enum-lock-sort-recent";
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

        public String labelKey() {
            return switch (this) {
                case ALL     -> "enum-world-filter-all";
                case CURRENT -> "enum-world-filter-current";
            };
        }
    }

    public enum LockTypeFilter {
        ALL,
        CHESTS,
        BARRELS,
        SHULKERS,
        FURNACES,
        DROPPERS_AND_DISPENSERS,
        BREWING_STANDS,
        CRAFTERS;

        public LockTypeFilter next() {
            LockTypeFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String labelKey() {
            return switch (this) {
                case ALL                     -> "enum-lock-type-filter-all";
                case CHESTS                  -> "enum-lock-type-filter-chests";
                case BARRELS                 -> "enum-lock-type-filter-barrels";
                case SHULKERS                -> "enum-lock-type-filter-shulkers";
                case FURNACES                -> "enum-lock-type-filter-furnaces";
                case DROPPERS_AND_DISPENSERS -> "enum-lock-type-filter-droppers-and-dispensers";
                case BREWING_STANDS          -> "enum-lock-type-filter-brewing-stands";
                case CRAFTERS                -> "enum-lock-type-filter-crafters";
            };
        }
    }

    public enum LogsActionFilter {
        ALL,
        UNAUTHORIZED_BREAK,
        ADMIN_BREAK,
        LOCK_REMOVE_AREA,
        LOCK_TRANSFER_ADMIN,
        TRUST_SET_ADMIN,
        TRUST_REMOVE_ADMIN;

        public LogsActionFilter next() {
            LogsActionFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public String labelKey() {
            return switch (this) {
                case ALL                 -> "enum-logs-action-filter-all";
                case UNAUTHORIZED_BREAK  -> "enum-logs-action-filter-unauthorized-break";
                case ADMIN_BREAK         -> "enum-logs-action-filter-admin-break";
                case LOCK_REMOVE_AREA    -> "enum-logs-action-filter-lock-remove-area";
                case LOCK_TRANSFER_ADMIN -> "enum-logs-action-filter-lock-transfer-admin";
                case TRUST_SET_ADMIN     -> "enum-logs-action-filter-trust-set-admin";
                case TRUST_REMOVE_ADMIN  -> "enum-logs-action-filter-trust-remove-admin";
            };
        }

        public String databaseValue() {
            return switch (this) {
                case UNAUTHORIZED_BREAK  -> "UNAUTHORIZED_BREAK";
                case ADMIN_BREAK         -> "ADMIN_BREAK";
                case LOCK_REMOVE_AREA    -> "LOCK_REMOVE_AREA";
                case LOCK_TRANSFER_ADMIN -> "LOCK_TRANSFER_ADMIN";
                case TRUST_SET_ADMIN     -> "TRUST_SET_ADMIN";
                case TRUST_REMOVE_ADMIN  -> "TRUST_REMOVE_ADMIN";
                default                  -> "ALL";
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

        public String labelKey() {
            return switch (this) {
                case LAST_24_HOURS -> "enum-logs-time-filter-last-24-hours";
                case LAST_7_DAYS   -> "enum-logs-time-filter-last-7-days";
                case LAST_30_DAYS  -> "enum-logs-time-filter-last-30-days";
                case ALL_TIME      -> "enum-logs-time-filter-all-time";
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
    private final Map<UUID, RecipeEditorModel> recipeEditors = new ConcurrentHashMap<>();

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

    public RecipeEditorModel recipeEditor(UUID playerUuid, RecipeKind kind) {
        RecipeEditorModel model = recipeEditors.get(playerUuid);
        return model != null && model.kind() == kind ? model : null;
    }

    public void setRecipeEditor(UUID playerUuid, RecipeEditorModel model) {
        recipeEditors.put(playerUuid, model);
    }

    public void clearRecipeEditor(UUID playerUuid) {
        recipeEditors.remove(playerUuid);
    }

    public void clearRecipeEditors() {
        recipeEditors.clear();
    }

    public void cleanup(UUID playerUuid) {
        lockListStates.remove(playerUuid);
        logStates.remove(playerUuid);
        inspectModes.remove(playerUuid);
        recipeEditors.remove(playerUuid);
    }
}
