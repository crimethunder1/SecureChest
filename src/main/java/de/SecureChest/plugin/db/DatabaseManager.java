package de.SecureChest.plugin.db;

import de.SecureChest.plugin.ChestSecurePlugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class DatabaseManager {

    /** Poolgröße: für SQLite reichen 4 gleichzeitige Verbindungen problemlos. */
    private static final int POOL_SIZE = 4;

    private final ChestSecurePlugin plugin;
    private String jdbcUrl;

    /** Pool gepoolter, sofort einsatzbereiter Verbindungen. */
    private final BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(POOL_SIZE);
    /** Separate Liste für sauberes Shutdown. */
    private final List<Connection> allConnections = new CopyOnWriteArrayList<>();

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

            try { Class.forName("org.sqlite.JDBC"); }
            catch (ClassNotFoundException ignored) {
                plugin.getLogger().warning("SQLite JDBC driver nicht gefunden – versuche fortzufahren...");
            }

            for (int i = 0; i < POOL_SIZE; i++) {
                Connection c = createConnection();
                pool.add(c);
                allConnections.add(c);
            }

            createTables();
            plugin.getLogger().info("Datenbankverbindungen initialisiert (Pool-Größe: " + POOL_SIZE + ").");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "DB-Initialisierung fehlgeschlagen", ex);
            return false;
        }
    }

    /** Erstellt eine neue, konfigurierte Verbindung (WAL-Modus + Foreign Keys). */
    private Connection createConnection() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA synchronous = NORMAL;");
        }
        return c;
    }

    private void createTables() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {

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

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS notifications (" +
                " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                " owner_uuid TEXT NOT NULL," +
                " message TEXT NOT NULL," +
                " created_at INTEGER NOT NULL" +
                ");"
            );

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_locks_owner       ON locks(owner_uuid);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_created      ON logs(created_at);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_action       ON logs(action);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_actor        ON logs(actor_name);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hoppers_owner     ON secure_hoppers(owner_uuid);");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notifications_owner ON notifications(owner_uuid);");
        }
    }

    /**
     * Gibt eine gepoolte {@link Connection} zurueck, die in try-with-resources genutzt werden kann.
     * <p>
     * Das {@code close()} der zurueckgegebenen Verbindung gibt sie <em>nicht</em> wirklich frei,
     * sondern legt sie zurueck in den Pool. Vor der Rueckgabe wird die Verbindung auf einen
     * sauberen Zustand zurueckgesetzt (autoCommit=true, offene Transaktion rollback,
     * Warnings geleert). Mehrfaches {@code close()} ist idempotent.
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection real = pool.take();
            if (real.isClosed() || !real.isValid(1)) {
                plugin.getLogger().warning("Ungueltige DB-Verbindung erkannt – erstelle neue.");
                real.close();
                real = createConnection();
                allConnections.add(real);
            }
            final Connection finalReal = real;
            final AtomicBoolean alreadyReturned = new AtomicBoolean(false);

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class[]{Connection.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("close".equals(methodName)) {
                            if (!alreadyReturned.compareAndSet(false, true)) {
                                return null;
                            }
                            resetConnectionForPool(finalReal);
                            pool.offer(finalReal);
                            return null;
                        }
                        if ("isClosed".equals(methodName) && alreadyReturned.get()) {
                            return Boolean.TRUE;
                        }
                        if (alreadyReturned.get()) {
                            throw new SQLException("Verbindung wurde bereits an den Pool zurueckgegeben: "
                                    + methodName);
                        }
                        try {
                            return method.invoke(finalReal, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    }
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Unterbrochen beim Warten auf DB-Verbindung", e);
        }
    }

    static void resetConnectionForPool(Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                try { connection.rollback(); }
                catch (SQLException ignored) {}
                try { connection.setAutoCommit(true); }
                catch (SQLException ignored) {}
            }
            try { connection.clearWarnings(); }
            catch (SQLException ignored) {}
        } catch (SQLException ignored) {
            // getAutoCommit() schlug fehl - Connection ist unbrauchbar, Aufrufer entscheidet
        }
    }

    /** Schließt alle gepoolten Verbindungen sauber beim Shutdown. */
    public void close() {
        for (Connection c : allConnections) {
            try {
                if (!c.isClosed()) {
                    c.close();
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Fehler beim Schließen einer DB-Verbindung", ex);
            }
        }
        allConnections.clear();
        pool.clear();
        plugin.getLogger().info("Datenbankverbindungen geschlossen.");
    }
}
