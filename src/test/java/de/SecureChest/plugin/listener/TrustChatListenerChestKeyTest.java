package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TrustChatListener: Chat-Eingabe der Anzahl Chest Keys")
class TrustChatListenerChestKeyTest {

    private static final List<String> NEW_MESSAGE_KEYS = List.of(
            "gui-admin-chest-key-name",
            "gui-admin-chest-key-lore",
            "chat-prompt-chest-key-amount",
            "chest-key-anzahl-ungueltig",
            "chest-key-erhalten",
            "chest-key-fallen-gelassen");

    private final UUID playerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private MessageService messages;
    private Items items;
    private ItemStack chestKey;
    private GuiManager gui;
    private Player player;
    private PlayerInventory inventory;
    private World world;
    private Location location;
    private TrustChatListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        when(plugin.pendingChat()).thenReturn(pendingChat);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        items = mock(Items.class);
        chestKey = mock(ItemStack.class);
        when(chestKey.getMaxStackSize()).thenReturn(64);
        when(items.chestKey()).thenReturn(chestKey);
        when(plugin.items()).thenReturn(items);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(player.getInventory()).thenReturn(inventory);
        world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        location = mock(Location.class);
        when(player.getLocation()).thenReturn(location);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);
        listener = new TrustChatListener(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private void chat(String message) {
        pendingChat.put(playerUuid, new PendingChatAction.ChestKeyAmount());
        listener.handleOnMain(playerUuid, message);
    }

    private List<TextComponent> sentMessages() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<TextComponent> sent = new ArrayList<>();
        for (Component component : captor.getAllValues()) sent.add((TextComponent) component);
        return sent;
    }

    private List<String> sentTexts() {
        return sentMessages().stream().map(TextComponent::content).toList();
    }

    private static YamlConfiguration language(String language) {
        return YamlConfiguration.loadConfiguration(
                Path.of("src", "main", "resources", "lang", language + ".yml").toFile());
    }

    @Test
    @DisplayName("Gueltige Anzahl: genau diese Anzahl ins Inventar, Bestaetigung, Eintrag entfernt, GUI bleibt zu")
    void gueltig_gibtAnzahl() {
        chat("5");

        verify(chestKey).setAmount(5);
        verify(inventory).addItem(chestKey);
        verify(world, never()).dropItemNaturally(any(), any());
        assertFalse(pendingChat.containsKey(playerUuid));
        assertEquals(List.of("chest-key-erhalten"), sentTexts());
        assertEquals(NamedTextColor.GREEN, sentMessages().getFirst().color());
        verify(messages).get("chest-key-erhalten", 5);
        verifyNoInteractions(gui);
    }

    @Test
    @DisplayName("Untergrenze 1 und Obergrenze 64 werden vergeben")
    void grenzen_werdenVergeben() {
        chat("1");
        chat("64");

        verify(chestKey).setAmount(1);
        verify(chestKey).setAmount(64);
        assertFalse(pendingChat.containsKey(playerUuid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-3", "65", "1.5", "abc", "99999999999", "1 2"})
    @DisplayName("Ungueltige Anzahl: Fehlermeldung mit Grenze, erneuter Prompt, Eintrag bleibt, keine Keys")
    void ungueltig_fragtErneut(String input) {
        chat(input);

        assertEquals(List.of("chest-key-anzahl-ungueltig"), sentTexts());
        assertEquals(NamedTextColor.RED, sentMessages().getFirst().color());
        verify(messages).get("chest-key-anzahl-ungueltig", input, 64);
        verify(gui).promptChestKeyAmount(player);
        assertEquals(new PendingChatAction.ChestKeyAmount(), pendingChat.get(playerUuid));
        verify(chestKey, never()).setAmount(anyInt());
        verifyNoInteractions(inventory);
    }

    @Test
    @DisplayName("Kleinere Stapelgroesse: Grenze in der Meldung ist die Stapelgroesse")
    void kleinereStapelgroesse_grenze() {
        when(chestKey.getMaxStackSize()).thenReturn(16);

        chat("17");

        verify(messages).get("chest-key-anzahl-ungueltig", "17", 16);
        verify(gui).promptChestKeyAmount(player);
        verifyNoInteractions(inventory);
    }

    @Test
    @DisplayName("Ohne Admin-Permission: Eintrag entfernt, Meldung, keine Keys, kein Prompt")
    void ohnePermission_entferntEintrag() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);

        chat("5");

        assertFalse(pendingChat.containsKey(playerUuid));
        assertEquals(List.of("trust-keine-berechtigung"), sentTexts());
        verifyNoInteractions(items, gui, inventory);
    }

    @Test
    @DisplayName("Volles Inventar: Rest an der Position fallen gelassen, Hinweis mit Anzahl, danach Bestaetigung")
    void vollesInventar_restFallenGelassen() {
        ItemStack leftover = mock(ItemStack.class);
        when(leftover.getAmount()).thenReturn(7);
        HashMap<Integer, ItemStack> notStored = new HashMap<>();
        notStored.put(0, leftover);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(notStored);

        chat("10");

        verify(chestKey).setAmount(10);
        verify(world).dropItemNaturally(location, leftover);
        assertEquals(List.of("chest-key-fallen-gelassen", "chest-key-erhalten"), sentTexts());
        assertEquals(NamedTextColor.YELLOW, sentMessages().getFirst().color());
        verify(messages).get("chest-key-fallen-gelassen", 7);
        verify(messages).get("chest-key-erhalten", 10);
        assertFalse(pendingChat.containsKey(playerUuid));
        verifyNoInteractions(gui);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "CANCEL", "Cancel"})
    @DisplayName("'cancel' bricht ab: Eintrag entfernt, Meldung, keine Keys, kein Prompt")
    void cancel_abbruch(String input) {
        chat(input);

        assertFalse(pendingChat.containsKey(playerUuid));
        assertEquals(List.of("trust-abgebrochen"), sentTexts());
        verifyNoInteractions(items, gui, inventory);
    }

    @Test
    @DisplayName("Alle neuen Sprachschluessel existieren in de.yml und en.yml mit Text")
    void sprachschluessel_inBeidenDateien() {
        for (String language : List.of("de", "en")) {
            YamlConfiguration messagesFile = language(language);
            for (String key : NEW_MESSAGE_KEYS) {
                assertTrue(messagesFile.contains(key), language + ".yml: Schluessel fehlt: " + key);
                List<String> values = messagesFile.isList(key)
                        ? messagesFile.getStringList(key)
                        : List.of(String.valueOf(messagesFile.getString(key)));
                assertFalse(values.isEmpty(), language + ".yml: leer bei " + key);
                for (String value : values) assertFalse(value.isBlank(), language + ".yml: leer bei " + key);
            }
        }
    }

    @Test
    @DisplayName("Keine Sprachschluessel des entfernten Befehls givekey mehr")
    void keineGivekeySchluessel() {
        for (String language : List.of("de", "en")) {
            for (String key : language(language).getKeys(false)) {
                assertFalse(key.startsWith("givekey-"), language + ".yml: veralteter Schluessel " + key);
            }
        }
    }
}
