package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaCoordinateParser;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TrustChatListener: Chat-Schritte des Wirkungsbereichs")
class TrustChatListenerAreaTest {

    private static final Area SPAWN = new Area("Spawn", "world", 0, 0, 100, 100);

    private final UUID playerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private AreaService areaService;
    private GuiManager gui;
    private Player player;
    private TrustChatListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        when(plugin.pendingChat()).thenReturn(pendingChat);
        areaService = mock(AreaService.class);
        when(areaService.find(anyString())).thenReturn(Optional.empty());
        when(plugin.areas()).thenReturn(areaService);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        ConfirmManager confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        UiStateStore uiStateStore = new UiStateStore();
        when(plugin.ui()).thenReturn(uiStateStore);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(player);
        listener = new TrustChatListener(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    private void chat(PendingChatAction action, String message) {
        pendingChat.put(playerUuid, action);
        listener.handleOnMain(playerUuid, message);
    }

    @Test
    @DisplayName("Ohne Admin-Permission bricht jeder Wirkungsbereich-Schritt ab und entfernt den Eintrag")
    void ohnePermission_abbruch() {
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(false);
        List<PendingChatAction> actions = List.of(
                new PendingChatAction.AreaCreateName(),
                new PendingChatAction.AreaCreateCoordinates("Spawn"),
                new PendingChatAction.AreaResizeCoordinates("Spawn"));

        for (PendingChatAction action : actions) {
            chat(action, "Spawn");
            assertFalse(pendingChat.containsKey(playerUuid), "Eintrag bleibt haengen bei " + action);
        }

        assertEquals(List.of("trust-keine-berechtigung", "trust-keine-berechtigung", "trust-keine-berechtigung"),
                sentTexts());
        verifyNoInteractions(areaService, gui);
    }

    @Test
    @DisplayName("'cancel' bricht jeden Wirkungsbereich-Schritt ab, ohne den Service aufzurufen")
    void cancel_abbruch() {
        List<PendingChatAction> actions = List.of(
                new PendingChatAction.AreaCreateName(),
                new PendingChatAction.AreaCreateCoordinates("Spawn"),
                new PendingChatAction.AreaResizeCoordinates("Spawn"));

        for (PendingChatAction action : actions) {
            chat(action, "CANCEL");
            assertFalse(pendingChat.containsKey(playerUuid), "Eintrag bleibt haengen bei " + action);
        }

        assertEquals(List.of("trust-abgebrochen", "trust-abgebrochen", "trust-abgebrochen"), sentTexts());
        verifyNoInteractions(areaService, gui);
    }

    @Test
    @DisplayName("Name ungueltig: Fehler NAME_INVALID mit Detail, derselbe Prompt erneut, Eintrag bleibt")
    void name_ungueltig_promptErneut() {
        chat(new PendingChatAction.AreaCreateName(), "Mein Areal!");

        verify(gui).sendAreaErrors(player, List.of(
                new AreaValidationError(AreaValidationError.Code.NAME_INVALID, "Mein Areal!")));
        verify(gui).promptAreaName(player);
        assertEquals(new PendingChatAction.AreaCreateName(), pendingChat.get(playerUuid));
        verify(areaService, never()).apply(any());
    }

    @Test
    @DisplayName("Name vergeben: Fehler NAME_ALREADY_USED, derselbe Prompt erneut")
    void name_vergeben_promptErneut() {
        when(areaService.find("spawn")).thenReturn(Optional.of(SPAWN));

        chat(new PendingChatAction.AreaCreateName(), "spawn");

        verify(gui).sendAreaErrors(player, List.of(
                new AreaValidationError(AreaValidationError.Code.NAME_ALREADY_USED, "spawn")));
        verify(gui).promptAreaName(player);
        assertEquals(new PendingChatAction.AreaCreateName(), pendingChat.get(playerUuid));
    }

    @Test
    @DisplayName("Name gueltig: weiter zum Koordinaten-Schritt mit dem Namen")
    void name_gueltig_weiterZuKoordinaten() {
        chat(new PendingChatAction.AreaCreateName(), "Markt_1");

        assertEquals(new PendingChatAction.AreaCreateCoordinates("Markt_1"), pendingChat.get(playerUuid));
        verify(gui).promptAreaCreateCoordinates(player, "Markt_1");
        verify(gui, never()).sendAreaErrors(any(), any());
    }

    @Test
    @DisplayName("Koordinaten beim Anlegen ungueltig: Grund gemeldet, Prompt erneut, kein Service-Aufruf")
    void anlegen_koordinatenUngueltig_promptErneut() {
        chat(new PendingChatAction.AreaCreateCoordinates("Markt"), "1 2 3");

        verify(gui).sendAreaCoordinateError(player,
                new AreaCoordinateParser.Rejected(AreaCoordinateParser.Reason.WRONG_COUNT, "3"));
        verify(gui).promptAreaCreateCoordinates(player, "Markt");
        assertEquals(new PendingChatAction.AreaCreateCoordinates("Markt"), pendingChat.get(playerUuid));
        verifyNoInteractions(areaService);
    }

    @Test
    @DisplayName("Koordinaten beim Anlegen gueltig: Create in der Welt des Spielers, Ergebnis, Screen wieder offen")
    void anlegen_gueltig_ruftServiceAuf() {
        AreaChange.Create expected = new AreaChange.Create("Markt", "world", 10, -20, -30, 40);
        AreaChangeResult result = new AreaChangeResult(expected, true, List.of(), 0, 0, 0, null);
        when(areaService.apply(expected)).thenReturn(result);

        chat(new PendingChatAction.AreaCreateCoordinates("Markt"), "  10   -20 -30 40 ");

        verify(areaService).apply(expected);
        verify(gui).sendAreaResult(player, result);
        verify(gui).openAreas(player, 0);
        assertFalse(pendingChat.containsKey(playerUuid));
        verify(gui, never()).openAreaChangeConfirm(any(), any());
    }

    @Test
    @DisplayName("Anlegen scheitert am Namen (inzwischen vergeben): zurueck zum Namens-Prompt")
    void anlegen_nameInzwischenVergeben_zurueckZumNamen() {
        AreaValidationError error = new AreaValidationError(AreaValidationError.Code.NAME_ALREADY_USED, "Markt");
        when(areaService.apply(any())).thenAnswer(invocation ->
                new AreaChangeResult(invocation.getArgument(0), false, List.of(error), 0, 0, 0, null));

        chat(new PendingChatAction.AreaCreateCoordinates("Markt"), "0 0 5 5");

        verify(gui).sendAreaErrors(player, List.of(error));
        verify(gui).promptAreaName(player);
        assertEquals(new PendingChatAction.AreaCreateName(), pendingChat.get(playerUuid));
        verify(gui, never()).openAreas(any(), anyInt());
    }

    @Test
    @DisplayName("Anlegen scheitert an der Welt: Fehler gemeldet, Koordinaten-Prompt erneut")
    void anlegen_weltFehlt_promptErneut() {
        AreaValidationError error = new AreaValidationError(AreaValidationError.Code.WORLD_NOT_FOUND, "world");
        when(areaService.apply(any())).thenAnswer(invocation ->
                new AreaChangeResult(invocation.getArgument(0), false, List.of(error), 0, 0, 0, null));

        chat(new PendingChatAction.AreaCreateCoordinates("Markt"), "0 0 5 5");

        verify(gui).sendAreaErrors(player, List.of(error));
        verify(gui).promptAreaCreateCoordinates(player, "Markt");
        assertEquals(new PendingChatAction.AreaCreateCoordinates("Markt"), pendingChat.get(playerUuid));
    }

    @Test
    @DisplayName("Koordinaten beim Aendern ungueltig: Grund gemeldet, Prompt mit dem Areal erneut, keine Vorschau")
    void aendern_koordinatenUngueltig_promptErneut() {
        when(areaService.find("Spawn")).thenReturn(Optional.of(SPAWN));

        chat(new PendingChatAction.AreaResizeCoordinates("Spawn"), "1.5 2 3 4");

        verify(gui).sendAreaCoordinateError(player,
                new AreaCoordinateParser.Rejected(AreaCoordinateParser.Reason.NOT_AN_INTEGER, "1.5"));
        verify(gui).promptAreaResizeCoordinates(player, SPAWN);
        assertEquals(new PendingChatAction.AreaResizeCoordinates("Spawn"), pendingChat.get(playerUuid));
        verify(areaService, never()).previewResize(anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Koordinaten beim Aendern gueltig: Vorschau und Bestaetigungsdialog, kein apply")
    void aendern_gueltig_vorschauUndDialog() {
        when(areaService.find("Spawn")).thenReturn(Optional.of(SPAWN));
        AreaChangePreview preview = new AreaChangePreview(
                new AreaChange.Resize("Spawn", -1, -2, 3, 4), List.of(), 0, 0, 0);
        when(areaService.previewResize("Spawn", -1, -2, 3, 4)).thenReturn(preview);

        chat(new PendingChatAction.AreaResizeCoordinates("Spawn"), "-1 -2 3 4");

        verify(areaService).previewResize("Spawn", -1, -2, 3, 4);
        verify(gui).openAreaChangeConfirm(player, preview);
        verify(areaService, never()).apply(any());
        assertFalse(pendingChat.containsKey(playerUuid));
    }

    @Test
    @DisplayName("Areal beim Aendern inzwischen geloescht: Fehler, Eintrag entfernt, Screen wieder offen")
    void aendern_arealWeg_abbruch() {
        chat(new PendingChatAction.AreaResizeCoordinates("Spawn"), "0 0 1 1");

        verify(gui).sendAreaErrors(player, List.of(
                new AreaValidationError(AreaValidationError.Code.AREA_NOT_FOUND, "Spawn")));
        verify(gui).openAreas(player, 0);
        assertFalse(pendingChat.containsKey(playerUuid));
        verify(areaService, never()).previewResize(anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Ungueltige Vorschau beim Aendern: Fehler, Eintrag entfernt, kein Dialog")
    void aendern_vorschauUngueltig_keinDialog() {
        when(areaService.find("Spawn")).thenReturn(Optional.of(SPAWN));
        AreaValidationError error = new AreaValidationError(AreaValidationError.Code.AREA_NOT_FOUND, "Spawn");
        when(areaService.previewResize("Spawn", 0, 0, 1, 1)).thenReturn(new AreaChangePreview(
                new AreaChange.Resize("Spawn", 0, 0, 1, 1), List.of(error), 0, 0, 0));

        chat(new PendingChatAction.AreaResizeCoordinates("Spawn"), "0 0 1 1");

        verify(gui).sendAreaErrors(player, List.of(error));
        verify(gui).openAreas(player, 0);
        verify(gui, never()).openAreaChangeConfirm(any(), any());
        assertFalse(pendingChat.containsKey(playerUuid));
    }

    @Test
    @DisplayName("Jeder PendingChatAction-Typ hat einen Zweig in handleOnMain")
    void jederTyp_wirdBehandelt() throws Exception {
        Class<?>[] permittedTypes = PendingChatAction.class.getPermittedSubclasses();
        assertTrue(permittedTypes.length >= 7, "Erwartet mindestens 7 PendingChatAction-Typen");

        for (Class<?> type : permittedTypes) {
            clearInvocations(player, gui, areaService);
            pendingChat.clear();

            chat(instantiate(type), "!");

            boolean playerGotMessage = mockingDetails(player).getInvocations().stream()
                    .anyMatch(invocation -> invocation.getMethod().getName().equals("sendMessage"));
            boolean guiWasUsed = !mockingDetails(gui).getInvocations().isEmpty();
            assertTrue(playerGotMessage || guiWasUsed,
                    type.getSimpleName() + " hat keinen Zweig in handleOnMain und wuerde den Chat verschlucken");
        }
    }

    private static PendingChatAction instantiate(Class<?> type) throws Exception {
        assertTrue(type.isRecord(), type.getSimpleName() + " sollte ein Record sein");
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            parameterTypes[index] = components[index].getType();
            assertEquals(String.class, parameterTypes[index],
                    "Test kennt nur String-Komponenten, bitte erweitern: " + type.getSimpleName());
            arguments[index] = "x";
        }
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        return (PendingChatAction) constructor.newInstance(arguments);
    }
}
