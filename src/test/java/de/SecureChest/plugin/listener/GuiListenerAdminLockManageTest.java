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
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Admin-Verwaltungsmenue mit eigenen Aktionen, Bestaetigungen und Benachrichtigung")
class GuiListenerAdminLockManageTest {

    private final UUID ownerUuid = UUID.randomUUID();
    private final UUID trustedUuid = UUID.randomUUID();
    private final UUID newOwnerUuid = UUID.randomUUID();
    private final UUID worldUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private final Map<UUID, AccessLevel> trusted = new HashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private ChestSecurePlugin plugin;
    private LockService locks;
    private GuiManager gui;
    private ConfirmManager confirms;
    private PluginConfig config;
    private GuiListener listener;
    private String lockId;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("farmwelt");
        bukkit.when(() -> Bukkit.getWorld(worldUuid)).thenReturn(world);

        plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        when(plugin.ui()).thenReturn(new UiStateStore());
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        when(plugin.pendingChat()).thenReturn(pendingChat);
        config = mock(PluginConfig.class);
        when(config.confirmTimeoutSeconds()).thenReturn(30);
        when(config.undoSeconds()).thenReturn(60);
        when(config.notificationsEnabled()).thenReturn(false);
        when(plugin.cfg()).thenReturn(config);
        PlayerNameService names = mock(PlayerNameService.class);
        when(names.resolve(any())).thenAnswer(invocation -> "name-" + invocation.getArgument(0));
        when(plugin.names()).thenReturn(names);

        LockKey key = new LockKey(worldUuid, 10, 64, -20);
        lockId = key.asId();
        trusted.put(trustedUuid, AccessLevel.VIEW);
        LockEntry entry = mock(LockEntry.class);
        when(entry.key()).thenReturn(key);
        when(entry.blockType()).thenReturn(Material.TRAPPED_CHEST);
        when(entry.owner()).thenReturn(ownerUuid);
        when(entry.trusted()).thenReturn(trusted);
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(entry));
        when(locks.unlockByIdWithUndo(any(), anyString())).thenReturn(true);
        when(locks.transferLock(any(), anyString(), any())).thenReturn(true);
        when(locks.setTrustById(any(), anyString(), any(), any())).thenReturn(true);
        when(locks.removeTrustById(any(), anyString(), any())).thenReturn(true);
        listener = new GuiListener(plugin, material -> true);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private Player player(UUID uuid, boolean admin) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(admin);
        return player;
    }

    private Player admin() {
        return player(UUID.randomUUID(), true);
    }

    private Player onlineOwner() {
        Player owner = player(ownerUuid, false);
        when(owner.isOnline()).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(ownerUuid)).thenReturn(owner);
        return owner;
    }

    private void confirm(Player player, ConfirmType type, String data) {
        confirms.set(player.getUniqueId(), new ConfirmRequest(type, data, "ADMIN_LOCK_MANAGE|" + lockId, Long.MAX_VALUE));
        listener.handleConfirmAction(player, true);
    }

    private List<String> sentTexts(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @SuppressWarnings("unchecked")
    private List<String> confirmLines(Player player, String title) {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(gui).openConfirm(eq(player), eq(title), captor.capture());
        return captor.getValue();
    }

    private void assertNoNotification() {
        verify(locks, never()).addAlwaysDeliveredNotification(any(), anyString());
        verify(locks, never()).addNotification(any(), anyString());
    }

    @Test
    @DisplayName("Entfernen: Klick legt eigenen Dialog ADMIN_UNLOCK an, Ziel ist das Admin-Menue")
    void entfernen_dialog() {
        Player admin = admin();

        listener.requestAdminUnlock(admin, lockId);

        ConfirmRequest request = confirms.get(admin.getUniqueId()).orElseThrow();
        assertEquals(ConfirmType.ADMIN_UNLOCK, request.type());
        assertEquals(lockId, request.data());
        assertEquals("ADMIN_LOCK_MANAGE|" + lockId, request.returnTo());
        assertEquals(List.of("confirm-admin-unlock-question", "", "confirm-admin-unlock-block",
                        "confirm-unlock-one-trust-single", "", "confirm-undo-available", "confirm-admin-owner-notified"),
                confirmLines(admin, "confirm-admin-unlock-title"));
        verify(locks, never()).unlockByIdWithUndo(any(), anyString());
    }

    @Test
    @DisplayName("Entfernen bestaetigt: Dienst mit Undo fuer den Admin, Besitzer offline per Datenbank benachrichtigt, auch bei notifications.enabled aus")
    void entfernen_offlineBenachrichtigung() {
        Player admin = admin();

        confirm(admin, ConfirmType.ADMIN_UNLOCK, lockId);

        verify(locks).unlockByIdWithUndo(admin, lockId);
        verify(locks).addAlwaysDeliveredNotification(ownerUuid, "notification-admin-lock-removed");
        verify(locks, never()).addNotification(any(), anyString());
        verify(config, never()).notificationsEnabled();
        assertEquals(List.of("gui-erledigt"), sentTexts(admin));
    }

    @Test
    @DisplayName("Entfernen bestaetigt: Text nennt Typ und Position, nicht den Admin")
    void entfernen_textOhneAdminName() {
        Player admin = admin();
        when(admin.getName()).thenReturn("AdminName");
        var messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);

        confirm(admin, ConfirmType.ADMIN_UNLOCK, lockId);

        verify(messages).get("notification-admin-lock-removed", "trapped chest", "farmwelt 10 64 -20");
        verify(admin, never()).getName();
    }

    @Test
    @DisplayName("Entfernen bestaetigt: Besitzer online bekommt die Meldung sofort im Chat, nichts in der Datenbank")
    void entfernen_onlineBenachrichtigung() {
        Player owner = onlineOwner();

        confirm(admin(), ConfirmType.ADMIN_UNLOCK, lockId);

        assertEquals(List.of("notification-admin-lock-removed"), sentTexts(owner));
        assertNoNotification();
    }

    @Test
    @DisplayName("Entfernen der eigenen Sperre ueber das Admin-Menue: keine Benachrichtigung, kein Hinweis im Dialog")
    void entfernen_eigeneSperre_keineBenachrichtigung() {
        Player admin = player(ownerUuid, true);

        listener.requestAdminUnlock(admin, lockId);
        assertFalse(confirmLines(admin, "confirm-admin-unlock-title").contains("confirm-admin-owner-notified"));
        listener.handleConfirmAction(admin, true);

        verify(locks).unlockByIdWithUndo(admin, lockId);
        assertNoNotification();
    }

    @Test
    @DisplayName("Entfernen bestaetigt und Sperre weg: Fenster schliesst statt Fehlermeldung")
    void entfernen_danachSchliessen() {
        Player admin = admin();
        when(locks.unlockByIdWithUndo(admin, lockId)).thenAnswer(invocation -> {
            when(locks.getLockFromId(lockId)).thenReturn(Optional.empty());
            return true;
        });

        confirm(admin, ConfirmType.ADMIN_UNLOCK, lockId);

        verify(admin).closeInventory();
        verify(gui, never()).openAdminLockManage(any(), anyString());
    }

    @Test
    @DisplayName("Abbrechen im Dialog: zurueck ins Admin-Menue, nichts geaendert")
    void abbrechen_zurueck() {
        Player admin = admin();
        confirms.set(admin.getUniqueId(), new ConfirmRequest(ConfirmType.ADMIN_UNLOCK, lockId,
                "ADMIN_LOCK_MANAGE|" + lockId, Long.MAX_VALUE));

        listener.handleConfirmAction(admin, false);

        verify(gui).openAdminLockManage(admin, lockId);
        verify(locks, never()).unlockByIdWithUndo(any(), anyString());
        assertNoNotification();
    }

    @Test
    @DisplayName("Uebertragen: Klick schliesst das Fenster und startet die eigene Chat-Eingabe")
    void uebertragen_chatEingabe() {
        Player admin = admin();

        listener.startAdminTransferInput(admin, lockId);

        verify(admin).closeInventory();
        assertEquals(new PendingChatAction.AdminTransferLock(lockId), pendingChat.get(admin.getUniqueId()));
        assertEquals(List.of("chat-prompt-transfer"), sentTexts(admin));
    }

    @Test
    @DisplayName("Uebertragen bestaetigt: transferLock, Besitzer offline benachrichtigt mit neuem Besitzer")
    void uebertragen_bestaetigt() {
        Player admin = admin();
        var messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);

        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|" + newOwnerUuid);

        verify(locks).transferLock(admin, lockId, newOwnerUuid);
        verify(messages).get("notification-admin-lock-transferred", "trapped chest", "farmwelt 10 64 -20",
                "name-" + newOwnerUuid);
        verify(locks).addAlwaysDeliveredNotification(ownerUuid, "notification-admin-lock-transferred");
        verify(locks, never()).addNotification(any(), anyString());
    }

    @Test
    @DisplayName("Uebertragen bestaetigt: Besitzer online bekommt die Meldung sofort")
    void uebertragen_online() {
        Player owner = onlineOwner();

        confirm(admin(), ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|" + newOwnerUuid);

        assertEquals(List.of("notification-admin-lock-transferred"), sentTexts(owner));
        assertNoNotification();
    }

    @Test
    @DisplayName("Uebertragen an den bisherigen Besitzer oder mit kaputten Daten: abgelehnt, keine Benachrichtigung")
    void uebertragen_ungueltig() {
        Player admin = admin();

        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|" + ownerUuid);
        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|keine-uuid");
        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId);

        verify(locks, never()).transferLock(any(), anyString(), any());
        assertNoNotification();
        assertEquals(List.of("gui-aktion-fehlgeschlagen", "gui-aktion-fehlgeschlagen", "gui-aktion-fehlgeschlagen"),
                sentTexts(admin));
    }

    @Test
    @DisplayName("Uebertragen fehlgeschlagen im Dienst: keine Benachrichtigung")
    void uebertragen_dienstLehntAb() {
        Player admin = admin();
        when(locks.transferLock(any(), anyString(), any())).thenReturn(false);

        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|" + newOwnerUuid);

        assertNoNotification();
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts(admin));
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen: Klick startet die eigene Chat-Eingabe mit der Stufe")
    void vertrauenHinzufuegen_chatEingabe() {
        Player admin = admin();

        listener.startAdminAddTrustInput(admin, "STOCKER|" + lockId);

        verify(admin).closeInventory();
        assertEquals(new PendingChatAction.AdminAddTrust(lockId, "STOCKER"), pendingChat.get(admin.getUniqueId()));
        assertEquals(List.of("chat-prompt-add-trust"), sentTexts(admin));
    }

    @Test
    @DisplayName("Vertrauen hinzufuegen mit ungueltiger Stufe: keine Chat-Eingabe")
    void vertrauenHinzufuegen_ungueltigeStufe() {
        Player admin = admin();

        listener.startAdminAddTrustInput(admin, "OWNER|" + lockId);
        listener.startAdminAddTrustInput(admin, lockId);

        assertTrue(pendingChat.isEmpty());
        assertEquals(List.of("ungueltige-daten", "ungueltige-daten"), sentTexts(admin));
    }

    @Test
    @DisplayName("Linksklick auf Kopf: Stufe wechselt (Viewer zu Stocker), Menue neu, keine Benachrichtigung")
    void kopf_linksklickWechseltStufe() {
        Player admin = admin();

        listener.handleAdminTrustEntry(admin, lockId + "|" + trustedUuid, ClickType.LEFT);
        trusted.put(trustedUuid, AccessLevel.STOCKER);
        listener.handleAdminTrustEntry(admin, lockId + "|" + trustedUuid, ClickType.LEFT);

        verify(locks).setTrustById(admin, lockId, trustedUuid, AccessLevel.STOCKER);
        verify(locks).setTrustById(admin, lockId, trustedUuid, AccessLevel.VIEW);
        assertEquals(List.of("gui-vertrauen-aktualisiert", "gui-vertrauen-aktualisiert"), sentTexts(admin));
        verify(gui, times(2)).openAdminLockManage(admin, lockId);
        assertNoNotification();
    }

    @Test
    @DisplayName("Linksklick auf Kopf eines nicht mehr vertrauten Spielers: nichts gesetzt, Meldung fehlgeschlagen")
    void kopf_nichtMehrVertraut() {
        Player admin = admin();
        UUID stranger = UUID.randomUUID();

        listener.handleAdminTrustEntry(admin, lockId + "|" + stranger, ClickType.LEFT);

        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts(admin));
    }

    @Test
    @DisplayName("Rechtsklick auf Kopf: eigener Dialog ADMIN_REMOVE_TRUST, bestaetigt entfernt das Vertrauen ohne Benachrichtigung")
    void kopf_rechtsklickEntfernt() {
        Player admin = admin();

        listener.handleAdminTrustEntry(admin, lockId + "|" + trustedUuid, ClickType.RIGHT);
        ConfirmRequest request = confirms.get(admin.getUniqueId()).orElseThrow();
        assertEquals(ConfirmType.ADMIN_REMOVE_TRUST, request.type());
        assertEquals(lockId + "|" + trustedUuid, request.data());
        assertEquals("ADMIN_LOCK_MANAGE|" + lockId, request.returnTo());
        assertEquals(List.of("confirm-admin-remove-trust-question", "", "confirm-remove-trust-warning"),
                confirmLines(admin, "confirm-admin-remove-trust-title"));
        verify(locks, never()).removeTrustById(any(), anyString(), any());

        listener.handleConfirmAction(admin, true);

        verify(locks).removeTrustById(admin, lockId, trustedUuid);
        assertNoNotification();
        verify(gui).openAdminLockManage(admin, lockId);
    }

    @Test
    @DisplayName("Ohne Admin-Permission: alle Klick-Methoden lehnen ab, kein Dialog, keine Chat-Eingabe, kein Dienst")
    void ohnePermission_klick() {
        Player player = player(UUID.randomUUID(), false);

        listener.requestAdminUnlock(player, lockId);
        listener.startAdminTransferInput(player, lockId);
        listener.startAdminAddTrustInput(player, "VIEW|" + lockId);
        listener.handleAdminTrustEntry(player, lockId + "|" + trustedUuid, ClickType.LEFT);
        listener.handleAdminTrustEntry(player, lockId + "|" + trustedUuid, ClickType.RIGHT);

        assertTrue(confirms.get(player.getUniqueId()).isEmpty());
        assertTrue(pendingChat.isEmpty());
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        verify(gui, never()).openConfirm(any(), anyString(), anyList());
        assertEquals(List.of("keine-berechtigung", "keine-berechtigung", "keine-berechtigung",
                "keine-berechtigung", "keine-berechtigung"), sentTexts(player));
    }

    @Test
    @DisplayName("Ohne Admin-Permission im Dialog: Eintrag entfernt, Dienst nicht aufgerufen, keine Benachrichtigung")
    void ohnePermission_dialog() {
        Player player = player(UUID.randomUUID(), false);

        for (ConfirmType type : List.of(ConfirmType.ADMIN_UNLOCK, ConfirmType.ADMIN_TRANSFER_LOCK,
                ConfirmType.ADMIN_REMOVE_TRUST)) {
            String data = type == ConfirmType.ADMIN_UNLOCK ? lockId : lockId + "|" + newOwnerUuid;
            confirm(player, type, data);
            assertTrue(confirms.get(player.getUniqueId()).isEmpty(), "Dialog bleibt haengen bei " + type);
        }

        verify(locks, never()).unlockByIdWithUndo(any(), anyString());
        verify(locks, never()).transferLock(any(), anyString(), any());
        verify(locks, never()).removeTrustById(any(), anyString(), any());
        assertNoNotification();
        assertEquals(List.of("gui-aktion-fehlgeschlagen", "gui-aktion-fehlgeschlagen", "gui-aktion-fehlgeschlagen"),
                sentTexts(player));
    }

    @Test
    @DisplayName("Sperre fehlt beim Klick: schloss-nicht-gefunden, Fenster zu, kein Dialog")
    void sperreFehlt_klick() {
        Player admin = admin();
        when(locks.getLockFromId("missing")).thenReturn(Optional.empty());

        listener.requestAdminUnlock(admin, "missing");
        listener.startAdminTransferInput(admin, "missing");

        assertTrue(confirms.get(admin.getUniqueId()).isEmpty());
        assertTrue(pendingChat.isEmpty());
        assertEquals(List.of("schloss-nicht-gefunden", "schloss-nicht-gefunden"), sentTexts(admin));
    }

    @Test
    @DisplayName("Der Admin-Weg fragt den Wirkungsbereich nie ab")
    void wirkungsbereich_nichtBeteiligt() {
        Player admin = admin();

        confirm(admin, ConfirmType.ADMIN_UNLOCK, lockId);
        confirm(admin, ConfirmType.ADMIN_TRANSFER_LOCK, lockId + "|" + newOwnerUuid);
        listener.handleAdminTrustEntry(admin, lockId + "|" + trustedUuid, ClickType.LEFT);
        confirm(admin, ConfirmType.ADMIN_REMOVE_TRUST, lockId + "|" + trustedUuid);

        verify(plugin, never()).areas();
    }

    @Test
    @DisplayName("Besitzer-Dialoge bleiben fuer fremde Operatoren gesperrt, auch neben dem Admin-Menue")
    void besitzerWege_unveraendert() {
        Player operator = admin();

        confirms.set(operator.getUniqueId(), new ConfirmRequest(ConfirmType.UNLOCK_ONE, lockId,
                "LOCK_MANAGE|" + lockId, Long.MAX_VALUE));
        listener.handleConfirmAction(operator, true);
        confirms.set(operator.getUniqueId(), new ConfirmRequest(ConfirmType.TRANSFER_LOCK, lockId + "|" + newOwnerUuid,
                "LOCK_MANAGE|" + lockId, Long.MAX_VALUE));
        listener.handleConfirmAction(operator, true);
        listener.setTrustRole(operator, "STOCKER|" + lockId + "|" + trustedUuid);

        verify(locks, never()).unlockByIdWithUndo(any(), anyString());
        verify(locks, never()).transferLock(any(), anyString(), any());
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
    }
}
