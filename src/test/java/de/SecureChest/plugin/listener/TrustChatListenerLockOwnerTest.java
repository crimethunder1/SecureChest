package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PendingChatAction;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TrustChatListener: Vertrauen per Chat-Eingabe prueft den Besitzer erneut, ohne Admin-Ausnahme")
class TrustChatListenerLockOwnerTest {

    private final UUID ownerUuid = UUID.randomUUID();
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private LockService locks;
    private GuiManager gui;
    private Player target;
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

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(new LockEntry(key, Material.CHEST, ownerUuid, 0L)));
        when(locks.setTrustById(any(), anyString(), any(), any())).thenReturn(true);

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

    private Player onlinePlayer(UUID uuid, boolean operator) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(operator);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        return player;
    }

    private List<String> sentTexts(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Fremder Operator: Vertrauen wird nicht vergeben, Meldung 'fehlgeschlagen', Eingabe beendet")
    void fremderOperator_abgelehnt() {
        UUID operatorUuid = UUID.randomUUID();
        Player operator = onlinePlayer(operatorUuid, true);
        pendingChat.put(operatorUuid, new PendingChatAction.AddTrust(lockId, "STOCKER"));

        listener.handleOnMain(operatorUuid, "Carol");

        verify(locks, never()).setTrustById(any(), anyString(), any(), any());
        assertEquals(List.of("trust-vergeben-fehlgeschlagen"), sentTexts(operator));
        assertFalse(pendingChat.containsKey(operatorUuid));
        verify(gui).openLockManage(operator, lockId);
    }

    @Test
    @DisplayName("Besitzer mit Operator-Rechten: Vertrauen wird wie bisher vergeben")
    void besitzerMitOperator_erlaubt() {
        Player owner = onlinePlayer(ownerUuid, true);
        pendingChat.put(ownerUuid, new PendingChatAction.AddTrust(lockId, "VIEW"));

        listener.handleOnMain(ownerUuid, "Carol");

        verify(locks).setTrustById(owner, lockId, target.getUniqueId(), AccessLevel.VIEW);
        assertEquals(List.of("trust-vergeben"), sentTexts(owner));
    }

    @Test
    @DisplayName("Besitzer ohne Operator-Rechte: Vertrauen wird wie bisher vergeben")
    void besitzerOhneOperator_erlaubt() {
        Player owner = onlinePlayer(ownerUuid, false);
        pendingChat.put(ownerUuid, new PendingChatAction.AddTrust(lockId, "STOCKER"));

        listener.handleOnMain(ownerUuid, "Carol");

        verify(locks).setTrustById(owner, lockId, target.getUniqueId(), AccessLevel.STOCKER);
    }
}
