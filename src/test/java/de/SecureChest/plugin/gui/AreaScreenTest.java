package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaCoordinateParser;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AreaScreen: Wirkungsbereich-Screen, Dialoge und Chat-Meldungen")
class AreaScreenTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");

    private final UUID playerUuid = UUID.randomUUID();
    private final List<Area> areas = new ArrayList<>();
    private MessageService messages;
    private AreaService areaService;
    private ConfirmManager confirms;
    private GuiManager manager;
    private Player player;
    private AreaScreen screen;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        areaService = mock(AreaService.class);
        when(areaService.areas()).thenAnswer(invocation -> List.copyOf(areas));
        when(plugin.areas()).thenReturn(areaService);
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        PluginConfig config = mock(PluginConfig.class);
        when(config.confirmTimeoutSeconds()).thenReturn(30);
        when(plugin.cfg()).thenReturn(config);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        manager = mock(GuiManager.class);
        screen = new AreaScreen(plugin, mock(GuiHelper.class), manager);
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @SuppressWarnings("unchecked")
    private List<String> confirmLines(String expectedTitle) {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(manager).openConfirm(eq(player), eq(expectedTitle), captor.capture());
        return captor.getValue();
    }

    private ConfirmRequest storedRequest() {
        return confirms.get(playerUuid).orElseThrow();
    }

    @Test
    @DisplayName("Ohne Admin-Permission oeffnet sich der Screen nicht, es geht zum Hauptmenue")
    void ohnePermission_keinScreen() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            screen.openAreas(player, 0);
            bukkit.verifyNoInteractions();
        }
        assertEquals(List.of("keine-berechtigung"), sentTexts());
        verify(manager).openMain(player);
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    @DisplayName("Paginierung: 37 Areale ergeben 2 Seiten, eine zu grosse Seite wird auf die letzte begrenzt")
    void paginierung() {
        for (int index = 0; index <= AreaScreen.PAGE_SIZE; index++) {
            areas.add(new Area("area" + index, "world", 0, 0, 10, 10));
        }
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screen.openAreas(player, 7);
        }
        verify(messages).get("gui-areas-title", 2, 2);
        verify(messages).getList("gui-areas-entry-lore", "world", 0, 0, 10, 10, 11L, 11L);
    }

    @Test
    @DisplayName("Groesse eines riesigen Areals laeuft nicht ueber")
    void groesse_ohneUeberlauf() {
        areas.add(new Area("Welt", "world", Integer.MIN_VALUE, -5, Integer.MAX_VALUE, 5));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screen.openAreas(player, 0);
        }
        verify(messages).getList("gui-areas-entry-lore", "world",
                Integer.MIN_VALUE, -5, Integer.MAX_VALUE, 5, 4294967296L, 11L);
    }

    @Test
    @DisplayName("Einschalten: Dialog mit Zahlen der Vorschau, Warnung und Hinweis auf null Areale")
    void dialog_einschalten_ohneAreale() {
        AreaChangePreview preview = new AreaChangePreview(new AreaChange.Enable(), List.of(), 3, 1, 2);

        screen.openChangeConfirm(player, preview);

        List<String> lines = confirmLines("confirm-area-enable-title");
        assertTrue(lines.contains("confirm-area-enable-question"));
        assertTrue(lines.contains("confirm-area-counts"));
        assertTrue(lines.contains("confirm-area-enable-warning"));
        assertTrue(lines.contains("confirm-area-enable-no-areas"));
        assertTrue(lines.contains("confirm-area-recalculated"));
        verify(messages).getList("confirm-area-counts", 3, 1, 2);

        ConfirmRequest request = storedRequest();
        assertEquals(ConfirmType.AREA_CHANGE, request.type());
        assertEquals(new AreaChange.Enable(), request.areaChange());
        assertEquals("AREAS", request.returnTo());
    }

    @Test
    @DisplayName("Einschalten mit Arealen: kein Hinweis auf null Areale, Dialog auch bei null Treffern")
    void dialog_einschalten_mitArealen_nullTreffer() {
        areas.add(new Area("Spawn", "world", 0, 0, 10, 10));

        screen.openChangeConfirm(player, new AreaChangePreview(new AreaChange.Enable(), List.of(), 0, 0, 0));

        List<String> lines = confirmLines("confirm-area-enable-title");
        assertFalse(lines.contains("confirm-area-enable-no-areas"));
        assertTrue(lines.contains("confirm-area-enable-warning"));
        verify(messages).getList("confirm-area-counts", 0, 0, 0);
        assertEquals(new AreaChange.Enable(), storedRequest().areaChange());
    }

    @Test
    @DisplayName("Aendern: Dialog nennt das Areal und die normalisierten neuen Ecken")
    void dialog_aendern() {
        AreaChange.Resize resize = new AreaChange.Resize("Spawn", 10, 20, -5, 1);

        screen.openChangeConfirm(player, new AreaChangePreview(resize, List.of(), 4, 0, 1));

        List<String> lines = confirmLines("confirm-area-resize-title");
        assertTrue(lines.contains("confirm-area-removal-warning"));
        assertFalse(lines.contains("confirm-area-enable-warning"));
        verify(messages).get("confirm-area-resize-question", "Spawn");
        verify(messages).get("confirm-area-resize-corners", -5, 1, 10, 20);
        verify(messages).getList("confirm-area-counts", 4, 0, 1);
        assertEquals(resize, storedRequest().areaChange());
    }

    @Test
    @DisplayName("Loeschen: Dialog nennt das Areal und die Zahlen")
    void dialog_loeschen() {
        AreaChange.Delete delete = new AreaChange.Delete("Spawn");

        screen.openChangeConfirm(player, new AreaChangePreview(delete, List.of(), 0, 0, 0));

        List<String> lines = confirmLines("confirm-area-delete-title");
        assertTrue(lines.contains("confirm-area-removal-warning"));
        verify(messages).get("confirm-area-delete-question", "Spawn");
        assertEquals(delete, storedRequest().areaChange());
    }

    @Test
    @DisplayName("Anlegen und Ausschalten haben keinen Dialog")
    void keinDialog_anlegenAusschalten() {
        assertThrows(IllegalArgumentException.class, () -> screen.openChangeConfirm(player,
                new AreaChangePreview(new AreaChange.Create("Spawn", "world", 0, 0, 1, 1), List.of(), 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> screen.openChangeConfirm(player,
                new AreaChangePreview(new AreaChange.Disable(), List.of(), 0, 0, 0)));
        assertTrue(confirms.get(playerUuid).isEmpty());
        verify(manager, never()).openConfirm(any(), any(), anyList());
    }

    @Test
    @DisplayName("Ergebnis nach Bestaetigung: tatsaechliche Zahlen ohne Speicherwarnung")
    void ergebnis_gespeichert() {
        screen.sendResult(player, new AreaChangeResult(new AreaChange.Delete("Spawn"), true, List.of(), 4, 2, 3, null));

        assertEquals(List.of("wirkungsbereich-areal-geloescht", "wirkungsbereich-ergebnis"), sentTexts());
        verify(messages).get("wirkungsbereich-areal-geloescht", "Spawn");
        verify(messages).get("wirkungsbereich-ergebnis", 4, 2, 3);
    }

    @Test
    @DisplayName("Ergebnis mit saveError zeigt die Warnung mit dem Fehler")
    void ergebnis_speicherfehler() {
        screen.sendResult(player, new AreaChangeResult(new AreaChange.Enable(), true, List.of(), 0, 0, 0, "disk full"));

        assertEquals(List.of("wirkungsbereich-eingeschaltet", "wirkungsbereich-ergebnis",
                "wirkungsbereich-speichern-fehlgeschlagen"), sentTexts());
        verify(messages).get("wirkungsbereich-speichern-fehlgeschlagen", "disk full");
    }

    @Test
    @DisplayName("Anlegen und Ausschalten melden keine Entfernungszahlen")
    void ergebnis_anlegenAusschalten_ohneZahlen() {
        screen.sendResult(player, new AreaChangeResult(new AreaChange.Create("Spawn", "world", 10, 5, -10, -5),
                true, List.of(), 0, 0, 0, null));
        screen.sendResult(player, new AreaChangeResult(new AreaChange.Disable(), true, List.of(), 0, 0, 0, null));

        assertEquals(List.of("wirkungsbereich-areal-angelegt", "wirkungsbereich-ausgeschaltet"), sentTexts());
        verify(messages).get("wirkungsbereich-areal-angelegt", "Spawn", "world", -10, -5, 10, 5);
    }

    @Test
    @DisplayName("Abgelehnte Aenderung zeigt die Fehler statt eines Ergebnisses")
    void ergebnis_abgelehnt() {
        screen.sendResult(player, new AreaChangeResult(new AreaChange.Delete("Weg"), false,
                List.of(new AreaValidationError(AreaValidationError.Code.AREA_NOT_FOUND, "Weg")), 0, 0, 0, null));

        assertEquals(List.of("wirkungsbereich-fehler-areal-nicht-gefunden"), sentTexts());
        verify(messages).get("wirkungsbereich-fehler-areal-nicht-gefunden", "Weg");
    }

    @Test
    @DisplayName("Validierungs- und Koordinatenfehler werden mit dem Detail uebersetzt")
    void fehler_mitDetail() {
        screen.sendValidationErrors(player, List.of(
                new AreaValidationError(AreaValidationError.Code.NAME_INVALID, "bad name"),
                new AreaValidationError(AreaValidationError.Code.WORLD_NOT_FOUND, "world_the_end")));
        screen.sendCoordinateError(player,
                new AreaCoordinateParser.Rejected(AreaCoordinateParser.Reason.OUT_OF_RANGE, "2147483648"));

        assertEquals(List.of("wirkungsbereich-fehler-name-ungueltig", "wirkungsbereich-fehler-welt-nicht-gefunden",
                "wirkungsbereich-koordinaten-ausserhalb"), sentTexts());
        verify(messages).get("wirkungsbereich-fehler-name-ungueltig", "bad name");
        verify(messages).get("wirkungsbereich-fehler-welt-nicht-gefunden", "world_the_end");
        verify(messages).get("wirkungsbereich-koordinaten-ausserhalb", "2147483648");
    }

    @Test
    @DisplayName("Prompt beim Anlegen nennt die aktuelle Welt, Prompt beim Aendern die aktuellen Ecken")
    void prompts() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world_nether");
        when(player.getWorld()).thenReturn(world);

        screen.promptName(player);
        screen.promptCreateCoordinates(player, "Spawn");
        screen.promptResizeCoordinates(player, new Area("Spawn", "world", -3, -4, 5, 6));

        assertEquals(List.of("chat-prompt-area-name", "chat-prompt-area-create-coordinates",
                "chat-prompt-area-resize-coordinates"), sentTexts());
        verify(messages).get("chat-prompt-area-create-coordinates", "Spawn", "world_nether");
        verify(messages).get("chat-prompt-area-resize-coordinates", "Spawn", "world", -3, -4, 5, 6);
    }

    @Test
    @DisplayName("Jeder AreaValidationError.Code hat einen eigenen Schluessel in de.yml und en.yml")
    void jederFehlercode_hatSprachschluessel() {
        assertKeysPresent(collectKeys(AreaValidationError.Code.values(), true),
                AreaValidationError.Code.values().length);
    }

    @Test
    @DisplayName("Jeder Grund des Koordinaten-Parsers hat einen eigenen Schluessel in de.yml und en.yml")
    void jederKoordinatenGrund_hatSprachschluessel() {
        assertKeysPresent(collectKeys(AreaCoordinateParser.Reason.values(), false),
                AreaCoordinateParser.Reason.values().length);
    }

    private static Set<String> collectKeys(Enum<?>[] values, boolean validationCodes) {
        Set<String> keys = new HashSet<>();
        for (Enum<?> value : values) {
            keys.add(validationCodes
                    ? AreaScreen.validationErrorKey((AreaValidationError.Code) value)
                    : AreaScreen.coordinateErrorKey((AreaCoordinateParser.Reason) value));
        }
        return keys;
    }

    private static void assertKeysPresent(Set<String> keys, int expectedDistinctKeys) {
        assertEquals(expectedDistinctKeys, keys.size(), "Jeder Wert braucht einen eigenen Schluessel");
        for (String language : List.of("de", "en")) {
            YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                    LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
            for (String key : keys) {
                assertTrue(messages.isString(key), language + ".yml: Schluessel fehlt: " + key);
                assertFalse(messages.getString(key).isBlank(), language + ".yml: leerer Wert bei " + key);
            }
        }
    }
}
