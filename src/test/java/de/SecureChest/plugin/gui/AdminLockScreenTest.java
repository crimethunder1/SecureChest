package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AdminLockScreen: Admin-Verwaltungsmenue einer einzelnen Sperre")
class AdminLockScreenTest {

    private final UUID ownerUuid = UUID.randomUUID();
    private final UUID viewerUuid = UUID.randomUUID();
    private final UUID stockerUuid = UUID.randomUUID();
    private final Map<ItemStack, String> builtItems = new HashMap<>();
    private final Map<Integer, String> slots = new LinkedHashMap<>();
    private final Map<Integer, String> contexts = new HashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private GuiHelper helper;
    private LockService locks;
    private AdminLockScreen screen;
    private String lockId;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.msg()).thenReturn(ScreenActionRecorder.keyEchoingMessages());
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);

        LockKey key = new LockKey(UUID.randomUUID(), 10, 64, -20);
        lockId = key.asId();
        LockEntry entry = mock(LockEntry.class);
        when(entry.key()).thenReturn(key);
        when(entry.blockType()).thenReturn(Material.CHEST);
        when(entry.owner()).thenReturn(ownerUuid);
        when(entry.trusted()).thenReturn(Map.of(viewerUuid, AccessLevel.VIEW, stockerUuid, AccessLevel.STOCKER));
        when(locks.getLockFromId(lockId)).thenReturn(Optional.of(entry));

        helper = mock(GuiHelper.class);
        when(helper.resolvePlayerName(any())).thenAnswer(invocation -> "name-" + invocation.getArgument(0));
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation ->
                item(invocation.getArgument(3), invocation.getArgument(4)));
        when(helper.buildPlayerHeadButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation ->
                item(invocation.getArgument(3), invocation.getArgument(4)));
        when(helper.buildInfoItem(any(), any(), any())).thenAnswer(invocation -> item("info", ""));
        screen = new AdminLockScreen(plugin, helper);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(invocation -> {
                    Inventory inventory = mock(Inventory.class);
                    doAnswer(setItem -> {
                        int slot = setItem.getArgument(0);
                        ItemStack stack = setItem.getArgument(1);
                        String[] actionAndContext = builtItems.get(stack).split("#", 2);
                        slots.put(slot, actionAndContext[0]);
                        contexts.put(slot, actionAndContext[1]);
                        return null;
                    }).when(inventory).setItem(anyInt(), any());
                    ChestSecureHolder holder = invocation.getArgument(0);
                    assertEquals(ChestSecureHolder.GuiId.ADMIN_LOCK_MANAGE, holder.id());
                    assertEquals(lockId, holder.context());
                    return inventory;
                });
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private ItemStack item(String action, String context) {
        ItemStack stack = mock(ItemStack.class);
        builtItems.put(stack, action + "#" + (context == null ? "" : context));
        return stack;
    }

    private Player player(boolean admin) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(admin);
        return player;
    }

    private String sentText(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        return ((TextComponent) captor.getValue()).content();
    }

    @Test
    @DisplayName("Admin: Menue mit Info, Uebertragen, Entfernen, Vertrauen per Name, Koepfen und Schliessen in festen Slots")
    void admin_slots() {
        Player admin = player(true);

        screen.openLockManage(admin, lockId);

        verify(admin).openInventory(any(Inventory.class));
        assertEquals("info", slots.get(AdminLockScreen.SLOT_INFO));
        assertEquals("admin_lock_transfer", slots.get(AdminLockScreen.SLOT_TRANSFER));
        assertEquals("admin_lock_remove", slots.get(AdminLockScreen.SLOT_REMOVE));
        assertEquals("admin_trust_add", slots.get(AdminLockScreen.SLOT_ADD_VIEWER));
        assertEquals("VIEW|" + lockId, contexts.get(AdminLockScreen.SLOT_ADD_VIEWER));
        assertEquals("admin_trust_add", slots.get(AdminLockScreen.SLOT_ADD_STOCKER));
        assertEquals("STOCKER|" + lockId, contexts.get(AdminLockScreen.SLOT_ADD_STOCKER));
        assertEquals("info", slots.get(AdminLockScreen.SLOT_TRUSTED_COUNT));
        assertEquals("admin_lock_close", slots.get(AdminLockScreen.SLOT_CLOSE));
        assertEquals(lockId, contexts.get(AdminLockScreen.SLOT_TRANSFER));
        assertEquals(lockId, contexts.get(AdminLockScreen.SLOT_REMOVE));
        assertEquals(List.of(10, 14, 16, 29, 31, 35, 36, 37, 45), List.copyOf(slots.keySet()));
    }

    @Test
    @DisplayName("Vertrauensliste: Koepfe ab Slot 36, sortiert nach Stufe, Kontext Sperre und Spieler")
    void admin_koepfe() {
        screen.openLockManage(player(true), lockId);

        assertEquals("admin_trust_entry", slots.get(AdminLockScreen.FIRST_TRUSTED_SLOT));
        assertEquals(lockId + "|" + stockerUuid, contexts.get(AdminLockScreen.FIRST_TRUSTED_SLOT));
        assertEquals("admin_trust_entry", slots.get(AdminLockScreen.FIRST_TRUSTED_SLOT + 1));
        assertEquals(lockId + "|" + viewerUuid, contexts.get(AdminLockScreen.FIRST_TRUSTED_SLOT + 1));
        verify(helper).buildPlayerHeadButton(eq(viewerUuid), eq("name-" + viewerUuid),
                eq(List.of("gui-admin-lock-manage-trusted-entry-lore")), eq("admin_trust_entry"), anyString());
    }

    @Test
    @DisplayName("Name des Besitzers kommt aus der Namensaufloesung")
    void admin_besitzername() {
        screen.openLockManage(player(true), lockId);

        verify(helper).resolvePlayerName(ownerUuid);
    }

    @Test
    @DisplayName("Ohne Admin-Permission: keine-berechtigung, kein Fenster, keine Knoepfe")
    void ohnePermission_abgelehnt() {
        Player player = player(false);

        screen.openLockManage(player, lockId);

        assertEquals("keine-berechtigung", sentText(player));
        verify(player, never()).openInventory(any(Inventory.class));
        verify(player).closeInventory();
        verifyNoInteractions(helper, locks);
    }

    @Test
    @DisplayName("Sperre fehlt: schloss-nicht-gefunden, kein Fenster")
    void sperreFehlt() {
        Player admin = player(true);
        when(locks.getLockFromId("missing")).thenReturn(Optional.empty());

        screen.openLockManage(admin, "missing");

        assertEquals("schloss-nicht-gefunden", sentText(admin));
        verify(admin, never()).openInventory(any(Inventory.class));
        verifyNoInteractions(helper);
    }
}
