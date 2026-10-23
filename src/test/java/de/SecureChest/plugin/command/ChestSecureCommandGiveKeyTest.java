package de.SecureChest.plugin.command;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ChestSecureCommand: /cs givekey [Spieler] [Anzahl]")
class ChestSecureCommandGiveKeyTest {

    private static final List<String> GIVE_KEY_MESSAGE_KEYS = List.of(
            "givekey-benutzung",
            "givekey-konsole-ohne-spieler",
            "givekey-spieler-nicht-gefunden",
            "givekey-anzahl-ungueltig",
            "givekey-gegeben",
            "givekey-erhalten",
            "givekey-fallen-gelassen-absender",
            "givekey-fallen-gelassen-empfaenger",
            "keine-berechtigung");

    private MockedStatic<Bukkit> bukkit;
    private Items items;
    private ItemStack chestKey;
    private ChestSecureCommand command;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = mock(MessageService.class, invocation -> Arrays.stream(invocation.getArguments())
                .map(String::valueOf)
                .collect(Collectors.joining("|")));
        when(plugin.msg()).thenReturn(messages);
        items = mock(Items.class);
        when(plugin.items()).thenReturn(items);
        chestKey = mock(ItemStack.class);
        when(chestKey.getMaxStackSize()).thenReturn(64);
        when(items.chestKey()).thenReturn(chestKey);
        command = new ChestSecureCommand(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private static Player player(String name, boolean admin) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(admin);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private static ConsoleCommandSender console() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(Permissions.ADMIN)).thenReturn(true);
        return console;
    }

    private void online(Player player) {
        String name = player.getName();
        bukkit.when(() -> Bukkit.getPlayerExact(name)).thenReturn(player);
    }

    private void run(CommandSender sender, String... args) {
        assertTrue(command.onCommand(sender, mock(Command.class), "cs", args));
    }

    private static List<String> messages(CommandSender recipient) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(recipient, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(component -> ((TextComponent) component).content()).toList();
    }

    @Test
    @DisplayName("Ohne chestsecure.admin: Meldung keine-berechtigung, kein Item, keine Spielersuche")
    void ohneBerechtigung() {
        Player sender = player("Steve", false);
        Player target = player("Alex", false);
        online(target);

        run(sender, "givekey", "Alex", "5");

        assertEquals(List.of("keine-berechtigung"), messages(sender));
        verifyNoInteractions(items);
        verify(target.getInventory(), never()).addItem(any(ItemStack[].class));
        bukkit.verify(() -> Bukkit.getPlayerExact(anyString()), never());
        assertEquals(List.of(), messages(target));
    }

    @Test
    @DisplayName("Mit Berechtigung bekommt der Zielspieler die angegebene Anzahl")
    void zielspielerBekommtAnzahl() {
        Player sender = player("Admin", true);
        Player target = player("Alex", false);
        online(target);

        run(sender, "givekey", "Alex", "5");

        verify(chestKey).setAmount(5);
        verify(target.getInventory()).addItem(chestKey);
        verify(sender.getInventory(), never()).addItem(any(ItemStack[].class));
        assertEquals(List.of("givekey-gegeben|5|Alex"), messages(sender));
        assertEquals(List.of("givekey-erhalten|5"), messages(target));
    }

    @Test
    @DisplayName("Nur Spieler angegeben: genau ein Chest Key")
    void nurSpielerEinKey() {
        Player sender = player("Admin", true);
        Player target = player("Alex", false);
        online(target);

        run(sender, "givekey", "Alex");

        verify(chestKey).setAmount(1);
        verify(target.getInventory()).addItem(chestKey);
    }

    @Test
    @DisplayName("Ohne Spieler gibt ein Spieler sich selbst einen Chest Key")
    void ohneSpielerAnSichSelbst() {
        Player sender = player("Admin", true);

        run(sender, "givekey");

        verify(chestKey).setAmount(1);
        verify(sender.getInventory()).addItem(chestKey);
        bukkit.verify(() -> Bukkit.getPlayerExact(anyString()), never());
    }

    @Test
    @DisplayName("Absender gleich Empfaenger bekommt nur eine Meldung")
    void absenderGleichEmpfaenger() {
        Player sender = player("Admin", true);
        online(sender);

        run(sender, "givekey", "Admin", "3");

        verify(sender.getInventory()).addItem(chestKey);
        assertEquals(List.of("givekey-gegeben|3|Admin"), messages(sender));
    }

    @Test
    @DisplayName("Konsole ohne Spieler bekommt den Hinweis, nichts wird vergeben")
    void konsoleOhneSpieler() {
        ConsoleCommandSender console = console();

        run(console, "givekey");

        assertEquals(List.of("givekey-konsole-ohne-spieler"), messages(console));
        verify(chestKey, never()).setAmount(anyInt());
    }

    @Test
    @DisplayName("Konsole mit Spieler vergibt an den Spieler")
    void konsoleMitSpieler() {
        ConsoleCommandSender console = console();
        Player target = player("Alex", false);
        online(target);

        run(console, "givekey", "Alex", "64");

        verify(chestKey).setAmount(64);
        verify(target.getInventory()).addItem(chestKey);
        assertEquals(List.of("givekey-gegeben|64|Alex"), messages(console));
        assertEquals(List.of("givekey-erhalten|64"), messages(target));
    }

    @Test
    @DisplayName("Spieler nicht online: Meldung mit Namen, nichts wird vergeben")
    void spielerNichtGefunden() {
        Player sender = player("Admin", true);

        run(sender, "givekey", "Niemand", "2");

        assertEquals(List.of("givekey-spieler-nicht-gefunden|Niemand"), messages(sender));
        verify(chestKey, never()).setAmount(anyInt());
        verify(sender.getInventory(), never()).addItem(any(ItemStack[].class));
    }

    @Test
    @DisplayName("Ungueltige Anzahl: Meldung mit Eingabe und Obergrenze, nichts wird vergeben")
    void ungueltigeAnzahl() {
        Player sender = player("Admin", true);
        Player target = player("Alex", false);
        online(target);

        run(sender, "givekey", "Alex", "65");

        assertEquals(List.of("givekey-anzahl-ungueltig|65|64"), messages(sender));
        verify(target.getInventory(), never()).addItem(any(ItemStack[].class));
        assertEquals(List.of(), messages(target));
    }

    @Test
    @DisplayName("Zu viele Argumente: Benutzung, nichts wird vergeben")
    void zuVieleArgumente() {
        Player sender = player("Admin", true);
        Player target = player("Alex", false);
        online(target);

        run(sender, "givekey", "Alex", "5", "extra");

        assertEquals(List.of("givekey-benutzung"), messages(sender));
        verify(target.getInventory(), never()).addItem(any(ItemStack[].class));
    }

    @Test
    @DisplayName("Volles Inventar: Rest wird an der Position fallen gelassen, beide werden informiert")
    void vollesInventar() {
        Player sender = player("Admin", true);
        Player target = player("Alex", false);
        online(target);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(target.getWorld()).thenReturn(world);
        when(target.getLocation()).thenReturn(location);
        ItemStack leftover = mock(ItemStack.class);
        when(leftover.getAmount()).thenReturn(7);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, leftover);
        when(target.getInventory().addItem(chestKey)).thenReturn(leftovers);

        run(sender, "givekey", "Alex", "10");

        verify(world).dropItemNaturally(location, leftover);
        assertEquals(List.of("givekey-gegeben|10|Alex", "givekey-fallen-gelassen-absender|7|Alex"), messages(sender));
        assertEquals(List.of("givekey-erhalten|10", "givekey-fallen-gelassen-empfaenger|7"), messages(target));
    }

    @Test
    @DisplayName("Volles Inventar beim Absender selbst: Hinweis auf fallen gelassene Items nur einmal")
    void vollesInventarAnSichSelbst() {
        Player sender = player("Admin", true);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(sender.getWorld()).thenReturn(world);
        when(sender.getLocation()).thenReturn(location);
        ItemStack leftover = mock(ItemStack.class);
        when(leftover.getAmount()).thenReturn(2);
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, leftover);
        when(sender.getInventory().addItem(chestKey)).thenReturn(leftovers);

        run(sender, "givekey");

        verify(world).dropItemNaturally(location, leftover);
        assertEquals(List.of("givekey-gegeben|1|Admin", "givekey-fallen-gelassen-empfaenger|2"), messages(sender));
    }

    @Test
    @DisplayName("Unterkommando ist unabhaengig von Gross- und Kleinschreibung")
    void grossKleinschreibung() {
        Player sender = player("Admin", true);

        run(sender, "GiveKey");

        verify(sender.getInventory()).addItem(chestKey);
    }

    @Test
    @DisplayName("Alle verwendeten Sprachschluessel stehen in de.yml und en.yml")
    void sprachschluesselVorhanden() {
        for (String language : List.of("de", "en")) {
            Path languageFile = Path.of("src", "main", "resources", "lang", language + ".yml");
            YamlConfiguration messages = YamlConfiguration.loadConfiguration(languageFile.toFile());
            for (String key : GIVE_KEY_MESSAGE_KEYS) {
                String value = messages.getString(key);
                assertNotNull(value, language + ".yml: Schluessel fehlt: " + key);
                assertFalse(value.isBlank(), language + ".yml: leerer Wert bei " + key);
            }
        }
    }
}
