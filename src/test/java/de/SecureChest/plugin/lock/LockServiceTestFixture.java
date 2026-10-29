package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.area.AreaStore;
import de.SecureChest.plugin.area.RecordingAreaEnvironment;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.names.PlayerNameService;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.sign.SignManager;
import de.SecureChest.plugin.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class LockServiceTestFixture implements AutoCloseable {

    final DoubleChestTestWorld world = new DoubleChestTestWorld();
    final MockedStatic<Bukkit> bukkit;
    final BukkitScheduler scheduler;
    final ChestSecurePlugin plugin;
    final PluginConfig config;
    final DatabaseManager database;
    final SqlLockService service;
    final Path areaFolder;
    final RecordingAreaEnvironment areaEnvironment;
    final AreaService areas;

    LockServiceTestFixture() throws SQLException {
        this(true);
    }

    LockServiceTestFixture(boolean completeCacheWarmup) throws SQLException {
        bukkit = mockStatic(Bukkit.class);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getWorld(world.world().getUID())).thenReturn(world.world());

        plugin = mock(ChestSecurePlugin.class);
        config = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(config);
        when(plugin.signs()).thenReturn(mock(SignManager.class));
        when(plugin.names()).thenReturn(mock(PlayerNameService.class));
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.chestKeyConsumption()).thenReturn(mock(ChestKeyConsumptionSetting.class));

        MessageService messages = mock(MessageService.class);
        when(messages.get(anyString())).thenReturn("message");
        when(messages.get(anyString(), any())).thenReturn("message");
        when(plugin.msg()).thenReturn(messages);

        Worlds worlds = mock(Worlds.class);
        when(worlds.isEnabled(any())).thenReturn(true);
        when(plugin.worlds()).thenReturn(worlds);

        database = mock(DatabaseManager.class);
        when(database.getConnection()).thenThrow(new SQLException("no database in unit test"));

        service = new SqlLockService(plugin, database, null);
        when(plugin.locks()).thenReturn(service);

        areaFolder = createAreaFolder();
        areaEnvironment = new RecordingAreaEnvironment().addWorld(world.world().getUID(), "world");
        areas = new AreaService(new AreaStore(areaFolder.resolve(AreaStore.FILE_NAME), plugin.getLogger()),
                service, areaEnvironment, plugin.getLogger());
        when(plugin.areas()).thenReturn(areas);

        if (completeCacheWarmup) completeCacheWarmup();
    }

    void completeCacheWarmup() throws SQLException {
        runCacheWarmupWith(emptyDatabaseConnection());
    }

    void runCacheWarmupWith(Connection connection) throws SQLException {
        doReturn(connection).when(database).getConnection();
        runCacheWarmup();
        doThrow(new SQLException("no database in unit test")).when(database).getConnection();
        clearInvocations(database);
    }

    void runCacheWarmup() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).runTaskAsynchronously(eq(plugin), taskCaptor.capture());
        taskCaptor.getAllValues().get(0).run();
    }

    static Connection emptyDatabaseConnection() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> mock(ResultSet.class));
        return connection;
    }

    private static Path createAreaFolder() {
        try {
            return Files.createTempDirectory("chestsecure-area-test");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    Player player(String name, boolean admin) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(admin);
        return player;
    }

    @Override
    public void close() {
        bukkit.close();
        try (Stream<Path> paths = Files.walk(areaFolder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
