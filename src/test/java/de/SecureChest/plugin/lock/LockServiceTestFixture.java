package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.sign.SignManager;
import de.SecureChest.plugin.util.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.MockedStatic;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class LockServiceTestFixture implements AutoCloseable {

    final DoubleChestTestWorld world = new DoubleChestTestWorld();
    final MockedStatic<Bukkit> bukkit;
    final ChestSecurePlugin plugin;
    final PluginConfig config;
    final SqlLockService service;

    LockServiceTestFixture() throws SQLException {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));

        plugin = mock(ChestSecurePlugin.class);
        config = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(config);
        when(plugin.signs()).thenReturn(mock(SignManager.class));
        when(plugin.getLogger()).thenReturn(mock(Logger.class));

        MessageService messages = mock(MessageService.class);
        when(messages.get(anyString())).thenReturn("message");
        when(messages.get(anyString(), any())).thenReturn("message");
        when(plugin.msg()).thenReturn(messages);

        Worlds worlds = mock(Worlds.class);
        when(worlds.isEnabled(any())).thenReturn(true);
        when(plugin.worlds()).thenReturn(worlds);

        DatabaseManager database = mock(DatabaseManager.class);
        when(database.getConnection()).thenThrow(new SQLException("no database in unit test"));

        service = new SqlLockService(plugin, database, null);
        when(plugin.locks()).thenReturn(service);
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
    }
}
