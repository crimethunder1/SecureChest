package de.SecureChest.plugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Zentralisierter Zugriff auf alle Werte aus {@code config.yml}.
 *
 * <p>Vorteile gegenueber direktem {@code plugin.getConfig().getX("key", default)}:
 * <ul>
 *   <li>Alle Schluessel an einer Stelle – Tippfehler fallen sofort auf.</li>
 *   <li>Haeufig gelesene Werte (z.B. {@link #confirmTimeoutSeconds()},
 *       das fruher 9x pro Event gelesen wurde) werden einmalig gecacht.</li>
 *   <li>{@link #reload()} aktualisiert den Cache synchron nach {@code /cs reload}.</li>
 * </ul>
 */
public final class PluginConfig {

    private final ChestSecurePlugin plugin;

    // ── gecachte Werte (einmalig beim Laden / Reload gelesen) ────────────────
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
    private List<String> enabledWorlds;

    public PluginConfig(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Test-Konstruktor: laedt Werte direkt aus einer {@code FileConfiguration}
     * ohne laufenden Bukkit-Server. {@code plugin} bleibt {@code null}.
     */
    PluginConfig(FileConfiguration cfg) {
        this.plugin = null;
        loadFrom(cfg);
    }

    /** Liest alle gecachten Werte neu aus der aktuellen {@code FileConfiguration}. */
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
        maxLocksPerPlayer          = cfg.getInt    ("limits.max_locks_per_player",        0);
        maxHoppersPerPlayer        = cfg.getInt    ("limits.max_hoppers_per_player",       0);
        enabledWorlds              = cfg.getStringList("worlds.enabled");
    }

    // ── Getter ───────────────────────────────────────────────────────────────

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

    public List<String> enabledWorlds()       { return enabledWorlds; }

    /**
     * Liefert den Konfigurations-Abschnitt fuer ein Rezept direkt
     * (nicht gecacht, da selten gelesen und strukturell komplex).
     */
    public org.bukkit.configuration.ConfigurationSection recipeSection(String name) {
        return plugin.getConfig().getConfigurationSection("recipes." + name);
    }
}
