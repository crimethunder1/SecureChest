package de.SecureChest.plugin.area;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.lock.LockService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BukkitAreaEnvironment: Welten und Besitzer-Benachrichtigung")
class BukkitAreaEnvironmentTest {

    private static final String MESSAGE = "2 Sperren und 1 Hopper entfernt";

    private MockedStatic<Bukkit> bukkit;
    private LockService locks;
    private BukkitAreaEnvironment environment;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = mock(MessageService.class);
        when(messages.get("wirkungsbereich-sperren-entfernt", 2, 1)).thenReturn(MESSAGE);
        when(plugin.msg()).thenReturn(messages);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        environment = new BukkitAreaEnvironment(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    @DisplayName("Online-Besitzer bekommt die Meldung sofort, nichts landet in der Datenbank")
    void online_sofort() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);

        environment.notifyOwner(owner, 2, 1);

        verify(player).sendMessage(any(Component.class));
        verify(locks, never()).addAlwaysDeliveredNotification(any(), anyString());
        verify(locks, never()).addNotification(any(), anyString());
    }

    @Test
    @DisplayName("Offline-Besitzer bekommt die Meldung als immer zuzustellende Benachrichtigung")
    void offline_gespeichert() {
        environment.notifyOwner(owner, 2, 1);

        verify(locks).addAlwaysDeliveredNotification(owner, MESSAGE);
        verify(locks, never()).addNotification(any(), anyString());
    }

    @Test
    @DisplayName("Weltname und Existenz kommen von Bukkit, fehlende Welt ergibt null und false")
    void welten() {
        UUID worldUuid = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        bukkit.when(() -> Bukkit.getWorld(worldUuid)).thenReturn(world);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

        assertEquals("world", environment.worldName(worldUuid));
        assertNull(environment.worldName(UUID.randomUUID()));
        assertTrue(environment.worldExists("world"));
        assertFalse(environment.worldExists("world_nether"));
        assertFalse(environment.worldExists(null));
    }
}
