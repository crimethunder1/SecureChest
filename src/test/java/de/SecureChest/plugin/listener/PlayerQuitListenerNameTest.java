package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.names.PlayerNameService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlayerQuitListener: Name beim Beitritt")
class PlayerQuitListenerNameTest {

    private MockedStatic<Bukkit> bukkit;
    private BukkitScheduler scheduler;
    private ChestSecurePlugin plugin;
    private PlayerNameService names;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        plugin = mock(ChestSecurePlugin.class);
        names = mock(PlayerNameService.class);
        when(plugin.names()).thenReturn(names);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    @DisplayName("onPlayerJoin meldet den aktuellen Namen an die Namensverwaltung, die Benachrichtigungen bleiben asynchron")
    void joinRecordsCurrentName() {
        UUID playerUuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("Alex");
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new PlayerQuitListener(plugin).onPlayerJoin(event);

        verify(names).recordJoin(playerUuid, "Alex");
        verify(scheduler).runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
    }
}
