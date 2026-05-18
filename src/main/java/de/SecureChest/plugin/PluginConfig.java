package de.SecureChest.plugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class PluginConfig {

    private final ChestSecurePlugin plugin;

    private boolean soundsEnabled;
    private boolean particlesEnabled;
    private boolean signsEnabled;
    private boolean notificationsEnabled;
    private int     notificationsMaxPerPlayer;
    private int     confirmTimeoutSeconds;
    private int     undoSeconds;
    private int     maxUndoHistory;
    private double  trustNearbyRadius;
    private boolean autoLockEnabled;
    private boolean autoLockRequireKey;
    private boolean autoLockSneakOnly;
    private int     adminLogsBookEntries;
    private boolean adminLogsTeleportEnabled;
    private int     maxLocksPerPlayer;
    private int     maxHoppersPerPlayer;
    private long    lockActionCooldownMs;
    private long    breakNotificationCooldownMs;
    private List<String> enabledWorlds;

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
        notificationsEnabled       = cfg.getBoolean("notifications.enabled",           true);
        notificationsMaxPerPlayer  = cfg.getInt    ("notifications.max_per_player",    20);
        confirmTimeoutSeconds      = cfg.getInt    ("ui.confirm_timeout_seconds",       15);
        undoSeconds                = cfg.getInt    ("ui.undo_seconds",                  20);
        maxUndoHistory             = cfg.getInt    ("ui.max_undo_history",               5);
        trustNearbyRadius          = cfg.getDouble ("ui.trust_nearby_radius",           32.0);
        autoLockEnabled            = cfg.getBoolean("auto_lock.enabled",               false);
        autoLockRequireKey         = cfg.getBoolean("auto_lock.require_key",            true);
        autoLockSneakOnly          = cfg.getBoolean("auto_lock.sneak_only",            false);
        adminLogsBookEntries       = cfg.getInt    ("admin.logs.book_entries",          200);
        adminLogsTeleportEnabled   = cfg.getBoolean("admin.logs.teleport_enabled",     false);
        maxLocksPerPlayer          = cfg.getInt    ("limits.max_locks_per_player",        1000);
        maxHoppersPerPlayer        = cfg.getInt    ("limits.max_hoppers_per_player",       50);
        lockActionCooldownMs        = cfg.getLong   ("cooldowns.lock_action_ms",             1000L);
        breakNotificationCooldownMs = cfg.getLong   ("cooldowns.break_notification_ms",     30000L);
        enabledWorlds              = cfg.getStringList("worlds.enabled");
    }

    public boolean soundsEnabled()            { return soundsEnabled; }
    public boolean particlesEnabled()         { return particlesEnabled; }
    public boolean signsEnabled()             { return signsEnabled; }
    public boolean notificationsEnabled()     { return notificationsEnabled; }
    public int     notificationsMaxPerPlayer(){ return notificationsMaxPerPlayer; }

    public int     confirmTimeoutSeconds()    { return confirmTimeoutSeconds; }
    public int     undoSeconds()              { return undoSeconds; }
    public int     maxUndoHistory()           { return maxUndoHistory; }
    public double  trustNearbyRadius()        { return trustNearbyRadius; }

    public boolean autoLockEnabled()          { return autoLockEnabled; }
    public boolean autoLockRequireKey()       { return autoLockRequireKey; }
    public boolean autoLockSneakOnly()        { return autoLockSneakOnly; }

    public int     adminLogsBookEntries()     { return adminLogsBookEntries; }
    public boolean adminLogsTeleportEnabled() { return adminLogsTeleportEnabled; }

    public int     maxLocksPerPlayer()        { return maxLocksPerPlayer; }
    public int     maxHoppersPerPlayer()      { return maxHoppersPerPlayer; }

    public long    lockActionCooldownMs()        { return lockActionCooldownMs; }
    public long    breakNotificationCooldownMs() { return breakNotificationCooldownMs; }

    public List<String> enabledWorlds()       { return enabledWorlds; }

    public org.bukkit.configuration.ConfigurationSection recipeSection(String name) {
        return plugin.getConfig().getConfigurationSection("recipes." + name);
    }
}
