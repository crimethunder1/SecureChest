package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.ping.PingManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LockScreen: Ping-Button wechselt zu Ping stoppen, solange der Ping zu dieser Sperre laeuft")
class LockScreenPingButtonTest {

    private PingManager ping;
    private GuiHelper helper;
    private LockScreen screen;
    private Player player;
    private LockEntry lockEntry;
    private String lockId;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        ping = mock(PingManager.class);
        when(plugin.ping()).thenReturn(ping);
        helper = mock(GuiHelper.class);
        screen = new LockScreen(plugin, helper, mock(GuiManager.class));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockEntry = new LockEntry(key, Material.CHEST, player.getUniqueId(), 0L);
        lockId = key.asId();
    }

    @Test
    @DisplayName("Kein Ping aktiv: Storage anpingen mit der bisherigen Lore und Aktion ping_lock")
    void showsStartButtonWithoutPing() {
        when(ping.isPinging(player.getUniqueId(), lockEntry.key())).thenReturn(false);

        screen.buildPingButton(player, lockEntry, lockId);

        verify(helper).buildButton(Material.RECOVERY_COMPASS, "gui-lock-manage-ping-name",
                List.of("gui-lock-manage-ping-lore"), "ping_lock", lockId);
    }

    @Test
    @DisplayName("Ping zu dieser Sperre aktiv: Ping stoppen mit eigener Lore und derselben Aktion ping_lock")
    void showsStopButtonWhilePinging() {
        when(ping.isPinging(player.getUniqueId(), lockEntry.key())).thenReturn(true);

        screen.buildPingButton(player, lockEntry, lockId);

        verify(helper).buildButton(Material.RECOVERY_COMPASS, "gui-lock-manage-ping-stop-name",
                List.of("gui-lock-manage-ping-stop-lore"), "ping_lock", lockId);
    }
}
