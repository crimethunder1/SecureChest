package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.sign.SignManager;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Spielerschalter show_owner_name: Laden beim Start, Beitritt, Schreiben")
class SqlLockServiceShowOwnerNameTest {

    private final UUID alice = new UUID(0L, 10L);
    private final UUID bob = new UUID(0L, 20L);
    private final UUID carol = new UUID(0L, 30L);

    private LockServiceTestFixture fixture;

    @AfterEach
    void tearDown() {
        if (fixture != null) fixture.close();
    }

    private static ResultSet rows(Object[][] rows) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        AtomicInteger row = new AtomicInteger(-1);
        when(resultSet.next()).thenAnswer(invocation -> row.incrementAndGet() < rows.length);
        when(resultSet.getString(1)).thenAnswer(invocation -> String.valueOf(rows[row.get()][0]));
        when(resultSet.getInt(2)).thenAnswer(invocation -> (Integer) rows[row.get()][1]);
        return resultSet;
    }

    private static Statement warmupStatement(Answer<ResultSet> playerSettings) throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM player_settings")) return playerSettings.answer(invocation);
            return mock(ResultSet.class);
        });
        return statement;
    }

    private static Connection connectionWith(Statement statement) throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        when(connection.createStatement()).thenReturn(statement);
        return connection;
    }

    private static Connection preloadConnection(Integer storedShowOwnerName) throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            if (sql.startsWith("SELECT show_owner_name") && storedShowOwnerName != null) {
                when(resultSet.next()).thenReturn(true, false);
                when(resultSet.getInt(1)).thenReturn(storedShowOwnerName);
            }
            when(statement.executeQuery()).thenReturn(resultSet);
            return statement;
        });
        return connection;
    }

    private Runnable lastAsyncTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), captor.capture());
        List<Runnable> tasks = captor.getAllValues();
        return tasks.get(tasks.size() - 1);
    }

    private SignManager signs() {
        return fixture.plugin.signs();
    }

    private void preloadWith(Connection connection) throws SQLException {
        fixture.service.preloadPlayerSettings(alice);
        Runnable preload = lastAsyncTask();
        doReturn(connection).when(fixture.database).getConnection();
        preload.run();
    }

    @Test
    @DisplayName("Start: alle Zeilen werden geladen, der Wert eines Offline-Besitzers wirkt, ohne Zeile gilt an")
    void startLoadsAllRows() throws SQLException {
        fixture = new LockServiceTestFixture(false);

        fixture.runCacheWarmupWith(connectionWith(warmupStatement(invocation ->
                rows(new Object[][]{{alice, 0}, {bob, 1}}))));

        assertFalse(fixture.service.getShowOwnerName(alice));
        assertTrue(fixture.service.getShowOwnerName(bob));
        assertTrue(fixture.service.getShowOwnerName(carol));
        verify(fixture.plugin.getLogger()).info("Player settings: 2 loaded, 1 players hide their name above their locks.");
    }

    @Test
    @DisplayName("Start: Zeile mit ungueltiger UUID wird mit Warnung uebersprungen")
    void startSkipsInvalidUuid() throws SQLException {
        fixture = new LockServiceTestFixture(false);

        fixture.runCacheWarmupWith(connectionWith(warmupStatement(invocation ->
                rows(new Object[][]{{"kaputt", 0}, {alice, 0}}))));

        assertFalse(fixture.service.getShowOwnerName(alice));
        verify(fixture.plugin.getLogger()).warning("[SecureChest] Invalid UUID in player_settings skipped: kaputt");
    }

    @Test
    @DisplayName("Start: der Aufbau der Texte (Rueckruf) laeuft erst nach dem Laden der Spielereinstellungen")
    void callbackRunsAfterSettingsAreLoaded() throws SQLException {
        fixture = new LockServiceTestFixture(false);
        AtomicInteger callbacks = new AtomicInteger();
        Runnable callback = callbacks::incrementAndGet;
        SqlLockService service = new SqlLockService(fixture.plugin, fixture.database, callback);
        Runnable warmup = lastAsyncTask();
        Statement statement = warmupStatement(invocation -> rows(new Object[][]{{alice, 0}}));
        doReturn(connectionWith(statement)).when(fixture.database).getConnection();

        warmup.run();

        InOrder order = inOrder(statement, fixture.scheduler);
        order.verify(statement).executeQuery(argThat(sql -> sql.contains("FROM player_settings")));
        order.verify(fixture.scheduler).runTask(fixture.plugin, callback);
        assertFalse(service.getShowOwnerName(alice));
        assertEquals(0, callbacks.get());
    }

    @Test
    @DisplayName("Start: Datenbankfehler beim Laden blockiert nichts, alle Namen gelten als an")
    void loadErrorKeepsDefaults() throws SQLException {
        fixture = new LockServiceTestFixture(false);
        Runnable callback = mock(Runnable.class);
        SqlLockService service = new SqlLockService(fixture.plugin, fixture.database, callback);
        Runnable warmup = lastAsyncTask();
        doReturn(connectionWith(warmupStatement(invocation -> {
            throw new SQLException("player_settings kaputt");
        }))).when(fixture.database).getConnection();

        warmup.run();

        verify(fixture.scheduler).runTask(fixture.plugin, callback);
        assertTrue(service.getShowOwnerName(alice));
        verify(fixture.plugin.getLogger()).log(eq(Level.SEVERE), anyString(), any(SQLException.class));
    }

    @Test
    @DisplayName("Start: unerwartete Ausnahme beim Laden blockiert nichts, alle Namen gelten als an")
    void unexpectedErrorKeepsDefaults() throws SQLException {
        fixture = new LockServiceTestFixture(false);
        Runnable callback = mock(Runnable.class);
        SqlLockService service = new SqlLockService(fixture.plugin, fixture.database, callback);
        Runnable warmup = lastAsyncTask();
        doReturn(connectionWith(warmupStatement(invocation -> {
            throw new IllegalStateException("unerwartet");
        }))).when(fixture.database).getConnection();

        warmup.run();

        verify(fixture.scheduler).runTask(fixture.plugin, callback);
        assertTrue(service.getShowOwnerName(alice));
        verify(fixture.plugin.getLogger()).log(eq(Level.SEVERE), anyString(), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("Schreiben: Wert gilt sofort, wird asynchron gespeichert und markiert die Gruppen des Besitzers")
    void setShowOwnerNameSavesAndMarks() throws SQLException {
        fixture = new LockServiceTestFixture();

        fixture.service.setShowOwnerName(alice, false);

        assertFalse(fixture.service.getShowOwnerName(alice));
        verify(signs()).refreshOwnerSigns(alice);
        Runnable save = lastAsyncTask();
        Connection connection = mock(Connection.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        when(connection.prepareStatement(
                "INSERT OR IGNORE INTO player_settings(player_uuid,quick_lock,quick_remove) VALUES(?,0,0)"))
                .thenReturn(insert);
        when(connection.prepareStatement("UPDATE player_settings SET show_owner_name=? WHERE player_uuid=?"))
                .thenReturn(update);
        doReturn(connection).when(fixture.database).getConnection();

        save.run();

        verify(insert).setString(1, alice.toString());
        verify(insert).executeUpdate();
        verify(update).setInt(1, 0);
        verify(update).setString(2, alice.toString());
        verify(update).executeUpdate();
    }

    @Test
    @DisplayName("Lesen: ohne Eintrag an, nach dem Schreiben der geschriebene Wert")
    void getShowOwnerNameDefaultsToOn() throws SQLException {
        fixture = new LockServiceTestFixture();

        assertTrue(fixture.service.getShowOwnerName(alice));
        fixture.service.setShowOwnerName(alice, false);
        assertFalse(fixture.service.getShowOwnerName(alice));
        fixture.service.setShowOwnerName(alice, true);
        assertTrue(fixture.service.getShowOwnerName(alice));
    }

    @Test
    @DisplayName("Beitritt: geaenderter Wert aus der Datenbank wird uebernommen und markiert die Gruppen")
    void joinUpdatesChangedValue() throws SQLException {
        fixture = new LockServiceTestFixture();

        preloadWith(preloadConnection(0));

        assertFalse(fixture.service.getShowOwnerName(alice));
        verify(signs()).refreshOwnerSigns(alice);
    }

    @Test
    @DisplayName("Beitritt: unveraenderter Wert oder keine Zeile markiert nichts")
    void joinWithSameValueMarksNothing() throws SQLException {
        fixture = new LockServiceTestFixture();

        preloadWith(preloadConnection(1));
        preloadWith(preloadConnection(null));

        assertTrue(fixture.service.getShowOwnerName(alice));
        verify(signs(), never()).refreshOwnerSigns(any());
    }

    @Test
    @DisplayName("Beitritt: Datenbankfehler laesst den bekannten Wert stehen und markiert nichts")
    void joinWithDatabaseErrorKeepsValue() throws SQLException {
        fixture = new LockServiceTestFixture();
        fixture.service.setShowOwnerName(alice, false);
        clearInvocations(signs());

        fixture.service.preloadPlayerSettings(alice);
        lastAsyncTask().run();

        assertFalse(fixture.service.getShowOwnerName(alice));
        verify(signs(), never()).refreshOwnerSigns(any());
    }

    @Test
    @DisplayName("Verlassen: der Wert bleibt fuer Offline-Besitzer im Speicher")
    void evictKeepsShowOwnerName() throws SQLException {
        fixture = new LockServiceTestFixture();
        fixture.service.setShowOwnerName(alice, false);

        fixture.service.evictPlayerCache(alice);

        assertFalse(fixture.service.getShowOwnerName(alice));
    }

    @Test
    @DisplayName("Repository: show_owner_name ist in der Liste erlaubter Felder")
    void whitelistAllowsShowOwnerName() {
        assertDoesNotThrow(() -> PlayerSettingsRepository.requireAllowedField("show_owner_name"));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField("SHOW_OWNER_NAME"));
    }

    @Test
    @DisplayName("Repository: auf dem Hauptthread wird die Datenbank nicht gelesen")
    void repositoryDoesNotReadOnMainThread() throws SQLException {
        fixture = new LockServiceTestFixture();
        fixture.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        PlayerSettingsRepository repository = new PlayerSettingsRepository(fixture.plugin, fixture.database);

        assertEquals(Optional.empty(), repository.loadShowOwnerName(alice));
        assertTrue(repository.loadAllShowOwnerName().isEmpty());
        verifyNoInteractions(fixture.database);
        verify(fixture.plugin.getLogger(), atLeastOnce()).warning(anyString());
    }

    @Test
    @DisplayName("Repository: Lesen einer Zeile liefert 0 als aus und 1 als an")
    void repositoryReadsStoredValue() throws SQLException {
        fixture = new LockServiceTestFixture();
        PlayerSettingsRepository repository = new PlayerSettingsRepository(fixture.plugin, fixture.database);

        doReturn(preloadConnection(0)).when(fixture.database).getConnection();
        assertEquals(Optional.of(false), repository.loadShowOwnerName(alice));
        doReturn(preloadConnection(1)).when(fixture.database).getConnection();
        assertEquals(Optional.of(true), repository.loadShowOwnerName(alice));
        doReturn(preloadConnection(null)).when(fixture.database).getConnection();
        assertEquals(Optional.of(true), repository.loadShowOwnerName(alice));
    }

    @Test
    @DisplayName("Repository: Datenbankfehler beim Lesen einer Zeile liefert keinen Wert")
    void repositoryReadErrorIsEmpty() throws SQLException {
        fixture = new LockServiceTestFixture();
        PlayerSettingsRepository repository = new PlayerSettingsRepository(fixture.plugin, fixture.database);

        assertEquals(Optional.empty(), repository.loadShowOwnerName(alice));
        Logger logger = fixture.plugin.getLogger();
        verify(logger).log(eq(Level.SEVERE), anyString(), any(SQLException.class));
    }
}
