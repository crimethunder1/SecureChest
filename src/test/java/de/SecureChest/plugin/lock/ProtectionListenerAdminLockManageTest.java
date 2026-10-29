package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Admin Key mit Schleichen oeffnet das Admin-Verwaltungsmenue")
class ProtectionListenerAdminLockManageTest {

    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private MessageService messages;
    private Items items;
    private GuiManager gui;
    private Player alice;
    private Player bob;
    private Player operator;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        gui = mock(GuiManager.class);
        when(fixture.plugin.gui()).thenReturn(gui);
        when(fixture.plugin.confirms()).thenReturn(new ConfirmManager());
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.plugin.keySecureHopperBlock()).thenReturn(SECURE_HOPPER_MARK);
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
        operator = fixture.player("Operator", true);
        when(fixture.plugin.names().resolve(alice.getUniqueId())).thenReturn("Alice");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private PlayerInteractEvent rightClick(Player player, Block block) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private Block lockedChest(int x, Player owner) {
        Block chest = world.setChest(x, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(owner, chest));
        return chest;
    }

    private void holdAdminKey(Player player, boolean sneaking) {
        when(items.isHoldingAdminKey(player)).thenReturn(true);
        when(player.isSneaking()).thenReturn(sneaking);
    }

    private void runScheduledMainThreadTask() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).runTask(eq(fixture.plugin), taskCaptor.capture());
        taskCaptor.getValue().run();
    }

    private void assertNoAdminMenu() {
        verify(fixture.scheduler, never()).runTask(eq(fixture.plugin), any(Runnable.class));
        verify(gui, never()).openAdminLockManage(any(), anyString());
    }

    private void assertStillLockedBy(Block block, Player owner) {
        assertTrue(service.isLocked(block));
        assertEquals(owner.getUniqueId(), service.getLock(block).orElseThrow().owner());
    }

    @Test
    @DisplayName("Fremde Kiste: Admin Key und Schleichen bricht das Oeffnen ab und oeffnet das Admin-Menue der Sperre")
    void fremdeKiste_menue() {
        Block chest = lockedChest(0, alice);
        holdAdminKey(operator, true);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);
        runScheduledMainThreadTask();

        verify(event).setCancelled(true);
        verify(gui).openAdminLockManage(operator, world.keyAt(0, 64, 0).asId());
        verify(gui, never()).openLockManage(any(), anyString());
        verify(messages, never()).get(eq("block-gehoert"), any());
        assertStillLockedBy(chest, alice);
        assertFalse(fixture.plugin.undos().hasUndo(operator.getUniqueId()));
    }

    @Test
    @DisplayName("Eigene Kiste: Admin Key und Schleichen oeffnet ebenfalls das Admin-Menue")
    void eigeneKiste_menue() {
        Block chest = lockedChest(0, operator);
        holdAdminKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, chest));
        runScheduledMainThreadTask();

        verify(gui).openAdminLockManage(operator, world.keyAt(0, 64, 0).asId());
        assertStillLockedBy(chest, operator);
    }

    @Test
    @DisplayName("Doppelkiste: beide Haelften fuehren zur kanonischen Sperre")
    void doppelkiste_kanonischeSperre() {
        Block canonicalHalf = world.setChest(5, 64, 5, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        Block otherHalf = world.setChest(6, 64, 5, Material.CHEST, BlockFace.SOUTH, Chest.Type.LEFT);
        assertTrue(service.lock(alice, otherHalf));
        holdAdminKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, otherHalf));
        listener.onPlayerInteract(rightClick(operator, canonicalHalf));

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, times(2)).runTask(eq(fixture.plugin), taskCaptor.capture());
        taskCaptor.getAllValues().forEach(Runnable::run);
        verify(gui, times(2)).openAdminLockManage(operator, world.keyAt(5, 64, 5).asId());
    }

    @Test
    @DisplayName("Gesperrter normaler Trichter: Admin-Menue wie bei einer Kiste")
    void gesperrterTrichter_menue() {
        Block hopper = world.setBlock(0, 64, 0, Material.HOPPER, mock(Hopper.class));
        assertTrue(service.lock(alice, hopper));
        holdAdminKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, hopper));
        runScheduledMainThreadTask();

        verify(gui).openAdminLockManage(operator, world.keyAt(0, 64, 0).asId());
    }

    @Test
    @DisplayName("Secure Hopper: kein Admin-Menue, das bisherige Oeffnen mit dem Admin Key bleibt")
    void secureHopper_bisherigesVerhalten() {
        Block container = world.setBlock(0, 64, 0, Material.BARREL);
        assertTrue(service.lock(alice, container));
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(BlockFace.DOWN);
        Block hopperBlock = world.setBlock(0, 65, 0, Material.HOPPER, hopperData);
        assertTrue(service.bindSecureHopper(alice, hopperBlock, container));
        holdAdminKey(operator, true);
        PlayerInteractEvent event = rightClick(operator, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertNoAdminMenu();
    }

    @Test
    @DisplayName("Admin Key ohne Schleichen: Container oeffnet wie bisher, kein Menue")
    void ohneSchleichen_oeffnet() {
        Block chest = lockedChest(0, alice);
        holdAdminKey(operator, false);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertNoAdminMenu();
    }

    @Test
    @DisplayName("Schleichen ohne Admin Key: kein Menue, fremde Kiste bleibt zu")
    void ohneAdminKey_bleibtZu() {
        Block chest = lockedChest(0, alice);
        when(operator.isSneaking()).thenReturn(true);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gehoert", "Alice");
        assertNoAdminMenu();
    }

    @Test
    @DisplayName("Ohne Admin-Permission: auch bei erkanntem Admin Key kein Menue, Meldung fuer Fremde")
    void ohnePermission_keinMenue() {
        Block chest = lockedChest(0, alice);
        holdAdminKey(bob, true);
        PlayerInteractEvent event = rightClick(bob, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gehoert", "Alice");
        assertNoAdminMenu();
    }

    @Test
    @DisplayName("Ungesperrter Block: Admin Key mit Schleichen tut nichts")
    void ungesperrt_nichts() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        holdAdminKey(operator, true);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertNoAdminMenu();
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Chest Key und Admin Key zugleich: der Chest-Key-Weg gilt, fremde Kiste bleibt fuer den Operator wirkungslos")
    void chestKeyUndAdminKey_chestKeyGewinnt() {
        Block chest = lockedChest(0, alice);
        holdAdminKey(operator, true);
        when(items.isHoldingChestKey(operator)).thenReturn(true);

        listener.onPlayerInteract(rightClick(operator, chest));

        verify(messages).get("block-gehoert", "Alice");
        assertNoAdminMenu();
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Chest Key mit Schleichen fuer Fremde und Operatoren weiterhin wirkungslos")
    void chestKey_fremdeUndOperatorWirkungslos() {
        Block chest = lockedChest(0, alice);
        for (Player player : new Player[] {bob, operator}) {
            when(items.isHoldingChestKey(player)).thenReturn(true);
            when(player.isSneaking()).thenReturn(true);
            listener.onPlayerInteract(rightClick(player, chest));
        }

        verify(messages, times(2)).get("block-gehoert", "Alice");
        verify(messages, never()).get("block-entsperrt");
        assertNoAdminMenu();
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Welt nicht aktiviert: Admin-Menue fuer die vorhandene Sperre trotzdem erreichbar")
    void weltNichtAktiv_menue() {
        Block chest = lockedChest(0, alice);
        when(fixture.plugin.worlds().isEnabled(any())).thenReturn(false);
        holdAdminKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, chest));
        runScheduledMainThreadTask();

        verify(gui).openAdminLockManage(operator, world.keyAt(0, 64, 0).asId());
    }

    @Test
    @DisplayName("Zweites Ereignis fuer die Nebenhand im selben Klick: nur ein Menue")
    void doppeltesEreignis_einMenue() {
        Block chest = lockedChest(0, alice);
        when(fixture.plugin.cfg().lockActionCooldownMs()).thenReturn(500L);
        holdAdminKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, chest));
        PlayerInteractEvent second = rightClick(operator, chest);
        listener.onPlayerInteract(second);

        verify(second).setCancelled(true);
        runScheduledMainThreadTask();
    }
}
