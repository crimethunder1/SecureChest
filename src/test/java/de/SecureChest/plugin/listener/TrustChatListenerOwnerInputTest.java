package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TrustChatListener: Besitzer-Eingaben fragen nach einem Tippfehler erneut")
class TrustChatListenerOwnerInputTest {

    private final UUID ownerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private final ConfirmManager confirms = new ConfirmManager();
    private MockedStatic<Bukkit> bukkit;
    private LockService locks;
    private GuiManager gui;
    private Player owner;
    private Player target;
    private TrustChatListener listener;
    private String lockId;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        when(plugin.pendingChat()).thenReturn(pendingChat);
        when(plugin.confirms()).thenReturn(confirms);
        PluginConfig config = mock(PluginConfig.class);
        when(config.confirmTimeoutSeconds()).thenReturn(30);
        when(plugin.cfg()).thenReturn(config);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(new LockEntry(key, Material.CHEST, ownerUuid, 0L)));
        when(locks.setTrustById(any(), anyString(), any(), any())).thenReturn(true);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerUuid);
        bukkit.when(() -> Bukkit.getPlayer(ownerUuid)).thenReturn(owner);
        target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getName()).thenReturn("Carol");
        bukkit.when(() -> Bukkit.getPlayerExact("Carol")).thenReturn(target);
        listener = new TrustChatListener(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private List<String> sentTexts(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    private void chat(PendingChatAction action, String... messages) {
        pendingChat.put(ownerUuid, action);
        for (String message : messages) listener.handleOnMain(ownerUuid, message);
    }

    @Test
    @DisplayName("Vertrauen: ungueltige Eingabe und unbekannter Spieler fragen erneut, die Eingabe bleibt offen")
    void addTrust_typoAsksAgain() {
        PendingChatAction action = new PendingChatAction.AddTrust(lockId, "STOCKER");

        chat(action, "Ca rol!", "Carla");

        assertEquals(List.of("trust-eingabe-ungueltig", "chat-prompt-add-trust",
                "trust-spieler-nicht-gefunden", "chat-prompt-add-trust"), sentTexts(owner));
        assertEquals(action, pendingChat.get(ownerUuid));
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        verify(gui, never()).openLockManage(any(), anyString());
    }

    @Test
    @DisplayName("Vertrauen: nach dem Tippfehler vergibt der richtige Name das Vertrauen und beendet die Eingabe")
    void addTrust_correctNameAfterTypo() {
        chat(new PendingChatAction.AddTrust(lockId, "VIEW"), "Carla", "Carol");

        verify(locks).setTrustById(owner, lockId, target.getUniqueId(), AccessLevel.VIEW);
        assertFalse(pendingChat.containsKey(ownerUuid));
        assertEquals("trust-vergeben", sentTexts(owner).getLast());
        verify(gui).openLockManage(owner, lockId);
    }

    @Test
    @DisplayName("Vertrauen an fremder Sperre (keine Berechtigung): sofort beendet, auch bei einem Tippfehler")
    void addTrust_foreignLockEnds() {
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(
                new LockEntry(LockKey.fromId(lockId), Material.CHEST, UUID.randomUUID(), 0L)));

        chat(new PendingChatAction.AddTrust(lockId, "STOCKER"), "Ca rol!");

        assertEquals(List.of("trust-vergeben-fehlgeschlagen"), sentTexts(owner));
        assertFalse(pendingChat.containsKey(ownerUuid));
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Uebertragen: ungueltige Eingabe und unbekannter Spieler fragen erneut, keine Bestaetigung")
    void transfer_typoAsksAgain() {
        PendingChatAction action = new PendingChatAction.TransferLock(lockId);

        chat(action, "x", "Carla");

        assertEquals(List.of("trust-eingabe-ungueltig", "chat-prompt-transfer",
                "trust-spieler-nicht-gefunden", "chat-prompt-transfer"), sentTexts(owner));
        assertEquals(action, pendingChat.get(ownerUuid));
        assertTrue(confirms.get(ownerUuid).isEmpty());
        verify(gui, never()).openConfirm(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("Uebertragen: nach dem Tippfehler oeffnet der richtige Name die Bestaetigung und beendet die Eingabe")
    void transfer_correctNameAfterTypo() {
        chat(new PendingChatAction.TransferLock(lockId), "Carla", "Carol");

        assertFalse(pendingChat.containsKey(ownerUuid));
        assertEquals(ConfirmType.TRANSFER_LOCK, confirms.get(ownerUuid).orElseThrow().type());
        assertEquals(lockId + "|" + target.getUniqueId(), confirms.get(ownerUuid).orElseThrow().data());
        verify(gui).openConfirm(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("'cancel' beendet beide Besitzer-Eingaben weiterhin zentral, auch nach einem Tippfehler")
    void cancelEndsBoth() {
        chat(new PendingChatAction.AddTrust(lockId, "STOCKER"), "Carla", "cancel");
        assertFalse(pendingChat.containsKey(ownerUuid));
        chat(new PendingChatAction.TransferLock(lockId), "CANCEL");
        assertFalse(pendingChat.containsKey(ownerUuid));

        assertEquals(List.of("trust-spieler-nicht-gefunden", "chat-prompt-add-trust",
                "trust-abgebrochen", "trust-abgebrochen"), sentTexts(owner));
        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        assertTrue(confirms.get(ownerUuid).isEmpty());
    }

    @Test
    @DisplayName("Unbekannte Stufe: Eingabe wird wie bisher beendet")
    void addTrust_invalidLevelEnds() {
        chat(new PendingChatAction.AddTrust(lockId, "OWNER"), "Carol");

        assertEquals(List.of("trust-ungueltige-stufe"), sentTexts(owner));
        assertFalse(pendingChat.containsKey(ownerUuid));
    }
}
