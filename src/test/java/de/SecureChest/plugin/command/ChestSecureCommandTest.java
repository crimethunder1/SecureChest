package de.SecureChest.plugin.command;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ChestSecureCommand: givekey ist kein Unterkommando mehr")
class ChestSecureCommandTest {

    private Items items;
    private GuiManager gui;
    private ChestSecureCommand command;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        items = mock(Items.class);
        when(plugin.items()).thenReturn(items);
        command = new ChestSecureCommand(plugin);
    }

    private void run(CommandSender sender, String arguments) {
        assertTrue(command.onCommand(sender, mock(Command.class), "cs", arguments.split(" ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"givekey", "givekey Steve 5", "GIVEKEY 3", "foo"})
    @DisplayName("Admin mit givekey oder einem unbekannten Argument bekommt das Hauptmenue, keine Keys")
    void unbekanntesArgument_oeffnetHauptmenue(String arguments) {
        Player player = mock(Player.class);
        when(player.hasPermission(Permissions.USE)).thenReturn(true);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);

        run(player, arguments);

        verify(gui).openMain(player);
        verify(player, never()).sendMessage(any(Component.class));
        verifyNoInteractions(items);
    }

    @Test
    @DisplayName("Konsole mit givekey bekommt wie bei jedem unbekannten Argument nur-spieler")
    void konsole_givekey_nurSpieler() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(Permissions.ADMIN)).thenReturn(true);

        run(console, "givekey Steve 5");

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(console).sendMessage(captor.capture());
        assertEquals(List.of("nur-spieler"),
                captor.getAllValues().stream().map(component -> ((TextComponent) component).content()).toList());
        verifyNoInteractions(items, gui);
    }
}
