package de.SecureChest.plugin.db;

import de.SecureChest.plugin.ChestSecurePlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public final class DatabaseManager {

    private final ChestSecurePlugin plugin;
    private String jdbcUrl;

    public DatabaseManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            File dir = plugin.getDataFolder();
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().severe("Could not create plugin data folder.");
                return false;
            }

            File dbFile = new File(dir, "chestsecure.db");
            this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
                plugin.getLogger().warning("SQLite JDBC driver class not found. Paper should bundle it. Attempting to continue...");
            }

            try (Connection c = getConnection();
                 Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
            }

            createTables();
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "DB init failed", ex);
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS locks (" +
                " world_uuid TEXT NOT NULL," +
                " x INTEGER NOT NULL," +
                " y INTEGER NOT NULL," +
                " z INTEGER NOT NULL," +
                " block_type TEXT NOT NULL," +
                " owner_uuid TEXT NOT NULL," +
                " created_at INTEGER NOT NULL," +
                " PRIMARY KEY (world_uuid, x, y, z)" +
                ");"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS trusted (" +
                " world_uuid TEXT NOT NULL," +
                " x INTEGER NOT NULL," +
                " y INTEGER NOT NULL," +
                " z INTEGER NOT NULL," +
                " target_uuid TEXT NOT NULL," +
                " level TEXT NOT NULL," +
                " PRIMARY KEY (world_uuid, x, y, z, target_uuid)," +
                " FOREIGN KEY (world_uuid, x, y, z)" +
                "   REFERENCES locks(world_uuid, x, y, z)" +
                "   ON DELETE CASCADE" +
                ");"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS secure_hoppers (" +
                " world_uuid TEXT NOT NULL," +
                " x INTEGER NOT NULL," +
                " y INTEGER NOT NULL," +
                " z INTEGER NOT NULL," +
                " owner_uuid TEXT NOT NULL," +
                " bound_world_uuid TEXT NOT NULL," +
                " bound_x INTEGER NOT NULL," +
                " bound_y INTEGER NOT NULL," +
                " bound_z INTEGER NOT NULL," +
                " created_at INTEGER NOT NULL," +
                " PRIMARY KEY (world_uuid, x, y, z)" +
                ");"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS logs (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " created_at INTEGER NOT NULL," +
                " action TEXT NOT NULL," +
                " actor_uuid TEXT NOT NULL," +
                " actor_name TEXT NOT NULL," +
                " world_name TEXT NOT NULL," +
                " world_uuid TEXT NOT NULL," +
                " x INTEGER NOT NULL," +
                " y INTEGER NOT NULL," +
                " z INTEGER NOT NULL," +
                " extra TEXT" +
                ");"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_settings (" +
                " player_uuid TEXT PRIMARY KEY," +
                " quick_lock INTEGER NOT NULL DEFAULT 0," +
                " quick_remove INTEGER NOT NULL DEFAULT 0" +
                ");"
            );
            
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_locks_owner ON locks(owner_uuid);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_created ON logs(created_at);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_action ON logs(action);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_actor ON logs(actor_name);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_secure_hoppers_owner ON secure_hoppers(owner_uuid);");
        }
    }

    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException ignored) {
            
        }
        return c;
    }

    public void close() {
        
    }
}
