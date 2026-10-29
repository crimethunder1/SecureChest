package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.names.PlayerNameService;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("GuiHelper: Spielernamen in den Listen")
class GuiHelperPlayerNameTest {

    @Test
    @DisplayName("resolvePlayerName nutzt die Namensaufloesung des Plugins und keine Suche per UUID-Text")
    void resolvePlayerNameUsesNameService() {
        UUID playerUuid = UUID.randomUUID();
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        PlayerNameService names = mock(PlayerNameService.class);
        when(names.resolve(playerUuid)).thenReturn("Alex");
        when(plugin.names()).thenReturn(names);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertEquals("Alex", new GuiHelper(plugin).resolvePlayerName(playerUuid));
            bukkit.verify(() -> Bukkit.getOfflinePlayerIfCached(anyString()), never());
        }
    }
}
