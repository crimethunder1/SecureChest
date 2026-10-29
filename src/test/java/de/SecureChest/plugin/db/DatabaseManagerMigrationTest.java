package de.SecureChest.plugin.db;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DatabaseManager: Spalte show_owner_name in player_settings beim Start")
class DatabaseManagerMigrationTest {

    private static final String ALTER_PLAYER_SETTINGS =
            "ALTER TABLE player_settings ADD COLUMN show_owner_name INTEGER NOT NULL DEFAULT 1;";
    private static final String MIGRATION_LOG = "Migration: column show_owner_name added to table player_settings.";
    private static final Pattern TABLE_INFO = Pattern.compile("PRAGMA table_info\\((\\w+)\\);");

    private static final class FakeSqliteDriver implements Driver {

        private final Connection connection;

        FakeSqliteDriver(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection connect(String url, Properties info) {
            return acceptsURL(url) ? connection : null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:sqlite:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @TempDir
    Path folder;

    private final Map<String, List<String>> columnsByTable = new HashMap<>();
    private final List<String> updates = new ArrayList<>();
    private Connection connection;
    private FakeSqliteDriver driver;
    private Logger logger;
    private DatabaseManager database;

    @AfterEach
    void tearDown() throws SQLException {
        if (database != null) database.close();
        if (driver != null) DriverManager.deregisterDriver(driver);
    }

    private ResultSet tableInfo(String sql) throws SQLException {
        Matcher matcher = TABLE_INFO.matcher(sql);
        assertTrue(matcher.matches(), sql);
        List<String> columns = columnsByTable.getOrDefault(matcher.group(1), List.of());
        ResultSet resultSet = mock(ResultSet.class);
        AtomicInteger row = new AtomicInteger(-1);
        when(resultSet.next()).thenAnswer(invocation -> row.incrementAndGet() < columns.size());
        when(resultSet.getString("name")).thenAnswer(invocation -> columns.get(row.get()));
        return resultSet;
    }

    private void startWith(List<String> playerSettingsColumns) throws SQLException {
        columnsByTable.put("notifications",
                List.of("id", "owner_uuid", "message", "created_at", "always_deliver"));
        columnsByTable.put("player_settings", playerSettingsColumns);

        connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(true);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(anyString())).thenAnswer(invocation -> {
            updates.add(invocation.getArgument(0));
            return 0;
        });
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> tableInfo(invocation.getArgument(0)));

        driver = new FakeSqliteDriver(connection);
        DriverManager.registerDriver(driver);

        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        database = new DatabaseManager(plugin);
        assertTrue(database.init());
        verify(connection, atLeastOnce()).createStatement();
    }

    private List<String> statementsOn(String table) {
        return updates.stream().filter(sql -> sql.contains(" " + table + " ") || sql.contains(" " + table + "("))
                .toList();
    }

    @Test
    @DisplayName("Bestehende Datenbank ohne die Spalte: genau ein ALTER TABLE mit Standard 1, nichts geloescht")
    void existingDatabaseGetsColumn() throws SQLException {
        startWith(List.of("player_uuid", "quick_lock", "quick_remove"));

        assertEquals(1, updates.stream().filter(ALTER_PLAYER_SETTINGS::equals).count());
        for (String sql : statementsOn("player_settings")) {
            assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS") || sql.equals(ALTER_PLAYER_SETTINGS), sql);
        }
        assertTrue(updates.stream().noneMatch(sql -> sql.startsWith("DROP") || sql.startsWith("DELETE")
                || sql.startsWith("UPDATE") || sql.startsWith("INSERT")), updates.toString());
        verify(logger).info(MIGRATION_LOG);
    }

    @Test
    @DisplayName("Bereits migrierte Datenbank: kein ALTER TABLE, keine Meldung")
    void migratedDatabaseIsUnchanged() throws SQLException {
        startWith(List.of("player_uuid", "quick_lock", "quick_remove", "show_owner_name"));

        assertTrue(updates.stream().noneMatch(sql -> sql.startsWith("ALTER TABLE")), updates.toString());
        verify(logger, never()).info(MIGRATION_LOG);
    }

    @Test
    @DisplayName("Spaltenname wird ohne Gross- und Kleinschreibung erkannt")
    void columnNameIgnoresCase() throws SQLException {
        startWith(List.of("PLAYER_UUID", "QUICK_LOCK", "QUICK_REMOVE", "SHOW_OWNER_NAME"));

        assertTrue(updates.stream().noneMatch(ALTER_PLAYER_SETTINGS::equals), updates.toString());
    }

    @Test
    @DisplayName("Neue Datenbank: CREATE TABLE player_settings enthaelt die Spalte mit Standard 1")
    void newDatabaseCreatesColumn() throws SQLException {
        startWith(List.of("player_uuid", "quick_lock", "quick_remove", "show_owner_name"));

        String create = updates.stream()
                .filter(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS player_settings"))
                .findFirst().orElseThrow();
        assertTrue(create.contains(" show_owner_name INTEGER NOT NULL DEFAULT 1"), create);
        assertTrue(create.contains(" quick_lock INTEGER NOT NULL DEFAULT 0"), create);
    }
}
