package de.SecureChest.plugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class PluginConfig {

    static final List<String> DEFAULT_ENABLED_WORLDS =
            List.of("world", "world_nether", "world_the_end");

    static final String DEFAULT_LANGUAGE = "en";
    static final List<String> SUPPORTED_LANGUAGES = List.of("en", "de");

    private final ChestSecurePlugin plugin;

    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private boolean signsEnabled;
    private boolean signsHideWhenCovered;
    private boolean notificationsEnabled;
    private int     notificationsMaxPerPlayer;
    private int     confirmTimeoutSeconds;
    private int     undoSeconds;
    private int     maxUndoHistory;
    private double  trustNearbyRadius;
    private boolean autoLockEnabled;
    private boolean autoLockRequireKey;
    private boolean autoLockSneakOnly;
    private boolean consumeChestKey;
    private int     adminLogsBookEntries;
    private boolean adminLogsTeleportEnabled;
    private int     maxLocksPerPlayer;
    private int     maxHoppersPerPlayer;
    private long    lockActionCooldownMs;
    private long    breakNotificationCooldownMs;
    private int     pingTimeoutSeconds;
    private int     pingArrivedSeconds;
    private boolean pingHighlightEnabled;
    private List<String> enabledWorlds;
    private String  language;

    public PluginConfig(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    PluginConfig(FileConfiguration cfg) {
        this.plugin = null;
        loadFrom(cfg);
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        loadFrom(cfg);
    }

    private void loadFrom(FileConfiguration cfg) {

        soundsEnabled              = cfg.getBoolean("sounds.enabled",                 true);
        particlesEnabled           = cfg.getBoolean("particles.enabled",               true);
        signsEnabled               = cfg.getBoolean("signs.enabled",                   true);
        signsHideWhenCovered       = cfg.getBoolean("signs.hide_when_covered",         true);
        notificationsEnabled       = cfg.getBoolean("notifications.enabled",           true);
        notificationsMaxPerPlayer  = cfg.getInt    ("notifications.max_per_player",    20);
        confirmTimeoutSeconds      = cfg.getInt    ("ui.confirm_timeout_seconds",       15);
        undoSeconds                = cfg.getInt    ("ui.undo_seconds",                  20);
        maxUndoHistory             = cfg.getInt    ("ui.max_undo_history",               5);
        trustNearbyRadius          = cfg.getDouble ("ui.trust_nearby_radius",           32.0);
        autoLockEnabled            = cfg.getBoolean("auto_lock.enabled",               false);
        autoLockRequireKey         = cfg.getBoolean("auto_lock.require_key",            true);
        autoLockSneakOnly          = cfg.getBoolean("auto_lock.sneak_only",            false);
        consumeChestKey            = cfg.getBoolean("locking.consume_chest_key",       false);
        adminLogsBookEntries       = cfg.getInt    ("admin.logs.book_entries",          200);
        adminLogsTeleportEnabled   = cfg.getBoolean("admin.logs.teleport_enabled",     false);
        maxLocksPerPlayer          = cfg.getInt    ("limits.max_locks_per_player",        1000);
        maxHoppersPerPlayer        = cfg.getInt    ("limits.max_hoppers_per_player",       50);
        lockActionCooldownMs        = cfg.getLong   ("cooldowns.lock_action_ms",             1000L);
        breakNotificationCooldownMs = cfg.getLong   ("cooldowns.break_notification_ms",     30000L);
        pingTimeoutSeconds         = readNonNegativeInt(cfg, "ping.timeout_seconds",          300);
        pingArrivedSeconds         = readNonNegativeInt(cfg, "ping.arrived_seconds",           15);
        pingHighlightEnabled       = cfg.getBoolean("ping.highlight_enabled",             true);
        enabledWorlds             = cfg.isList("worlds.enabled")
                ? cfg.getStringList("worlds.enabled")
                : DEFAULT_ENABLED_WORLDS;
        language                   = resolveLanguage(cfg.getString("language"));
    }

    private String resolveLanguage(String configuredLanguage) {
        if (configuredLanguage == null) return DEFAULT_LANGUAGE;
        String normalizedLanguage = configuredLanguage.trim().toLowerCase(Locale.ROOT);
        if (SUPPORTED_LANGUAGES.contains(normalizedLanguage)) return normalizedLanguage;
        logger().warning("Unsupported language '" + configuredLanguage + "' in config.yml, using "
                + DEFAULT_LANGUAGE + ". Supported values: " + String.join(", ", SUPPORTED_LANGUAGES) + ".");
        return DEFAULT_LANGUAGE;
    }

    private int readNonNegativeInt(FileConfiguration cfg, String path, int defaultValue) {
        if (!cfg.contains(path)) return defaultValue;
        if (cfg.isInt(path) && cfg.getInt(path) >= 0) return cfg.getInt(path);
        logger().warning("Invalid value '" + cfg.get(path) + "' for " + path + " in config.yml, using "
                + defaultValue + ". Expected a whole number of 0 or more.");
        return defaultValue;
    }

    private Logger logger() {
        return plugin != null ? plugin.getLogger() : Logger.getLogger("ChestSecure");
    }

    public boolean soundsEnabled()            { return soundsEnabled; }
    public boolean particlesEnabled()         { return particlesEnabled; }
    public boolean signsEnabled()             { return signsEnabled; }
    public boolean signsHideWhenCovered()     { return signsHideWhenCovered; }
    public boolean notificationsEnabled()     { return notificationsEnabled; }
    public int     notificationsMaxPerPlayer(){ return notificationsMaxPerPlayer; }

    public int     confirmTimeoutSeconds()    { return confirmTimeoutSeconds; }
    public int     undoSeconds()              { return undoSeconds; }
    public int     maxUndoHistory()           { return maxUndoHistory; }
    public double  trustNearbyRadius()        { return trustNearbyRadius; }

    public boolean autoLockEnabled()          { return autoLockEnabled; }
    public boolean autoLockRequireKey()       { return autoLockRequireKey; }
    public boolean autoLockSneakOnly()        { return autoLockSneakOnly; }

    public boolean consumeChestKey()          { return consumeChestKey; }

    public int     adminLogsBookEntries()     { return adminLogsBookEntries; }
    public boolean adminLogsTeleportEnabled() { return adminLogsTeleportEnabled; }

    public int     maxLocksPerPlayer()        { return maxLocksPerPlayer; }
    public int     maxHoppersPerPlayer()      { return maxHoppersPerPlayer; }

    public long    lockActionCooldownMs()        { return lockActionCooldownMs; }
    public long    breakNotificationCooldownMs() { return breakNotificationCooldownMs; }

    public int     pingTimeoutSeconds()       { return pingTimeoutSeconds; }
    public int     pingArrivedSeconds()       { return pingArrivedSeconds; }
    public boolean pingHighlightEnabled()     { return pingHighlightEnabled; }

    public List<String> enabledWorlds()       { return enabledWorlds; }

    public String  language()                 { return language; }

    public org.bukkit.configuration.ConfigurationSection recipeSection(String name) {
        if (plugin == null) {
            throw new IllegalStateException(
                    "recipeSection(\"" + name + "\") benoetigt die Plugin-Instanz, um die Rezepte "
                            + "aus der Live-Config zu lesen. Diese PluginConfig wurde ueber den "
                            + "paketprivaten Konstruktor aus einer FileConfiguration erzeugt und "
                            + "kennt kein Plugin.");
        }
        return plugin.getConfig().getConfigurationSection("recipes." + name);
    }
}
