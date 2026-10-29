package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.names.PlayerNameService;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TrustChatListener: eigene Chat-Eingaben des Admin-Verwaltungsmenues")
class TrustChatListenerAdminLockManageTest {

    private final UUID adminUuid = UUID.randomUUID();
    private final UUID ownerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private LockService locks;
    private GuiManager gui;
    private ConfirmManager confirms;
    private Player admin;
    private Player carol;
    private OfflinePlayer dave;
    private TrustChatListener listener;
    private String lockId;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        when(plugin.pendingChat()).thenReturn(pendingChat);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        PluginConfig config = mock(PluginConfig.class);
        when(config.confirmTimeoutSeconds()).thenReturn(30);
        when(plugin.cfg()).thenReturn(config);
        PlayerNameService names = mock(PlayerNameService.class);
        when(names.resolve(any())).thenAnswer(invocation -> "name-" + invocation.getArgument(0));
        when(plugin.names()).thenReturn(names);

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(new LockEntry(key, Material.CHEST, ownerUuid, 0L)));
        when(locks.setTrustById(any(), anyString(), any(), any())).thenReturn(true);

        admin = mock(Player.class);
        when(admin.getUniqueId()).thenReturn(adminUuid);
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(adminUuid)).thenReturn(admin);

        carol = mock(Player.class);
        when(carol.getUniqueId()).thenReturn(UUID.randomUUID());
        when(carol.getName()).thenReturn("Carol");
        bukkit.when(() -> Bukkit.getPlayerExact("Carol")).thenReturn(carol);

        dave = mock(OfflinePlayer.class);
        when(dave.getUniqueId()).thenReturn(UUID.randomUUID());
        when(dave.getName()).thenReturn("Dave");
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Dave")).thenReturn(dave);

        OfflinePlayer owner = mock(OfflinePlayer.class);
        when(owner.getUniqueId()).thenReturn(ownerUuid);
        when(owner.getName()).thenReturn("Alice");
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Alice")).thenReturn(owner);

        listener = new TrustChatListener(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private void chat(PendingChatAction action, String message) {
        pendingChat.put(adminUuid, action);
        listener.handleOnMain(adminUuid, message);
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(admin, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @SuppressWarnings("unchecked")
    private List<String> confirmLines() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(gui).openConfirm(eq(admin), eq("confirm-admin-transfer-title"), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Uebertragen an Online-Spieler: Eingabe beendet, eigener Dialog ADMIN_TRANSFER_LOCK, noch kein Dienst")
    void uebertragen_online() {
        chat(new PendingChatAction.AdminTransferLock(lockId), "Carol");

        assertFalse(pendingChat.containsKey(adminUuid));
        ConfirmRequest request = confirms.get(adminUuid).orElseThrow();
        assertEquals(ConfirmType.ADMIN_TRANSFER_LOCK, request.type());
        assertEquals(lockId + "|" + carol.getUniqueId(), request.data());
        assertEquals("ADMIN_LOCK_MANAGE|" + lockId, request.returnTo());
        assertEquals(List.of("confirm-admin-transfer-question", "", "confirm-transfer-zeile4",
                "confirm-transfer-zeile5", "confirm-admin-owner-notified"), confirmLines());
        verify(locks, never()).transferLock(any(), anyString(), any());
    }

    @Test
    @DisplayName("Uebertragen an Offline-Spieler aus dem Namenscache: gleicher Dialog")
    void uebertragen_offline() {
        chat(new PendingChatAction.AdminTransferLock(lockId), "Dave");

        assertEquals(lockId + "|" + dave.getUniqueId(), confirms.get(adminUuid).orElseThrow().data());
        assertFalse(pendingChat.containsKey(adminUuid));
    }

    @Test
    @DisplayName("Uebertragen: ungueltiger Name fragt erneut, Eingabe bleibt offen")
    void uebertragen_ungueltigerName() {
        PendingChatAction action = new PendingChatAction.AdminTransferLock(lockId);

        chat(action, "!");

        assertEquals(action, pendingChat.get(adminUuid));
        assertEquals(List.of("trust-eingabe-ungueltig", "chat-prompt-transfer"), sentTexts());
        assertTrue(confirms.get(adminUuid).isEmpty());
    }

    @Test
    @DisplayName("Uebertragen: unbekannter Spieler fragt erneut, Eingabe bleibt offen")
    void uebertragen_unbekannt() {
        PendingChatAction action = new PendingChatAction.AdminTransferLock(lockId);

        chat(action, "Unknown");

        assertEquals(action, pendingChat.get(adminUuid));
        assertEquals(List.of("trust-spieler-nicht-gefunden", "chat-prompt-transfer"), sentTexts());
    }

    @Test
    @DisplayName("Uebertragen an den bisherigen Besitzer: Meldung und erneute Frage")
    void uebertragen_anBesitzer() {
        PendingChatAction action = new PendingChatAction.AdminTransferLock(lockId);

        chat(action, "Alice");

        assertEquals(action, pendingChat.get(adminUuid));
        assertEquals(List.of("admin-transfer-bereits-besitzer", "chat-prompt-transfer"), sentTexts());
        assertTrue(confirms.get(adminUuid).isEmpty());
    }

    @Test
    @DisplayName("Uebertragen: Sperre fehlt, Eingabe beendet mit schloss-nicht-gefunden")
    void uebertragen_sperreFehlt() {
        when(locks.getLockFromId(lockId)).thenReturn(Optional.empty());

        chat(new PendingChatAction.AdminTransferLock(lockId), "Carol");

        assertFalse(pendingChat.containsKey(adminUuid));
        assertEquals(List.of("schloss-nicht-gefunden"), sentTexts());
        verifyNoInteractions(gui);
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen: Dienst setzt die Stufe, Menue neu, keine Benachrichtigung")
    void vertrauen_hinzufuegen() {
        chat(new PendingChatAction.AdminAddTrust(lockId, "VIEW"), "Carol");

        verify(locks).setTrustById(admin, lockId, carol.getUniqueId(), AccessLevel.VIEW);
        assertFalse(pendingChat.containsKey(adminUuid));
        assertEquals(List.of("trust-vergeben"), sentTexts());
        verify(gui).openAdminLockManage(admin, lockId);
        verify(locks, never()).addAlwaysDeliveredNotification(any(), anyString());
        verify(locks, never()).addNotification(any(), anyString());
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen fuer Offline-Spieler aus dem Namenscache")
    void vertrauen_offline() {
        chat(new PendingChatAction.AdminAddTrust(lockId, "STOCKER"), "Dave");

        verify(locks).setTrustById(admin, lockId, dave.getUniqueId(), AccessLevel.STOCKER);
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen: ungueltiger und unbekannter Name fragen erneut")
    void vertrauen_ungueltigFragtErneut() {
        PendingChatAction action = new PendingChatAction.AdminAddTrust(lockId, "VIEW");

        chat(action, "!");
        chat(action, "Unknown");

        assertEquals(action, pendingChat.get(adminUuid));
        assertEquals(List.of("trust-eingabe-ungueltig", "chat-prompt-add-trust",
                "trust-spieler-nicht-gefunden", "chat-prompt-add-trust"), sentTexts());
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen mit ungueltiger Stufe: Eingabe beendet")
    void vertrauen_ungueltigeStufe() {
        chat(new PendingChatAction.AdminAddTrust(lockId, "OWNER"), "Carol");

        assertFalse(pendingChat.containsKey(adminUuid));
        assertEquals(List.of("trust-ungueltige-stufe"), sentTexts());
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Ohne Admin-Permission: beide Eingaben werden entfernt, kein Dienst, kein Dialog")
    void ohnePermission() {
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(false);

        chat(new PendingChatAction.AdminTransferLock(lockId), "Carol");
        assertFalse(pendingChat.containsKey(adminUuid));
        chat(new PendingChatAction.AdminAddTrust(lockId, "VIEW"), "Carol");
        assertFalse(pendingChat.containsKey(adminUuid));

        assertEquals(List.of("trust-keine-berechtigung", "trust-keine-berechtigung"), sentTexts());
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        verify(gui, never()).openConfirm(any(), anyString(), anyList());
        assertTrue(confirms.get(adminUuid).isEmpty());
    }

    @Test
    @DisplayName("'cancel' bricht beide Eingaben ab")
    void cancel() {
        chat(new PendingChatAction.AdminTransferLock(lockId), "cancel");
        assertFalse(pendingChat.containsKey(adminUuid));
        chat(new PendingChatAction.AdminAddTrust(lockId, "VIEW"), "cancel");
        assertFalse(pendingChat.containsKey(adminUuid));

        assertEquals(List.of("trust-abgebrochen", "trust-abgebrochen"), sentTexts());
        verifyNoInteractions(gui);
    }

    @Test
    @DisplayName("Besitzer-Eingabe AddTrust lehnt einen fremden Admin weiterhin ab")
    void besitzerEingabe_unveraendert() {
        chat(new PendingChatAction.AddTrust(lockId, "STOCKER"), "Carol");

        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        assertEquals(List.of("trust-vergeben-fehlgeschlagen"), sentTexts());
    }
}
