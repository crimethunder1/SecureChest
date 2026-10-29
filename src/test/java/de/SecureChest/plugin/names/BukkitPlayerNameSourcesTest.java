package de.SecureChest.plugin.names;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("BukkitPlayerNameSources: Bukkit-Quellen fuer Namen")
class BukkitPlayerNameSourcesTest {

    private final UUID playerUuid = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    @DisplayName("onlineName: Name des Spielers, wenn er online ist")
    void onlineNameOfOnlinePlayer() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alex");
        bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);

        assertEquals("Alex", BukkitPlayerNameSources.onlineName(playerUuid));
    }

    @Test
    @DisplayName("onlineName: null, wenn der Spieler offline ist")
    void onlineNameOfOfflinePlayer() {
        assertNull(BukkitPlayerNameSources.onlineName(playerUuid));
    }

    @Test
    @DisplayName("serverCacheName: Name ueber getOfflinePlayer(UUID), nie ueber eine Suche per Name")
    void serverCacheNameUsesUuidLookup() {
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        when(offlinePlayer.getName()).thenReturn("Alex");
        bukkit.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(offlinePlayer);

        assertEquals("Alex", BukkitPlayerNameSources.serverCacheName(playerUuid));
        bukkit.verify(() -> Bukkit.getOfflinePlayerIfCached(anyString()), never());
        bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
    }

    @Test
    @DisplayName("serverCacheName: null, wenn der Server keinen Namen kennt")
    void serverCacheNameUnknown() {
        bukkit.when(() -> Bukkit.getOfflinePlayer(playerUuid)).thenReturn(mock(OfflinePlayer.class));

        assertNull(BukkitPlayerNameSources.serverCacheName(playerUuid));
    }
}
