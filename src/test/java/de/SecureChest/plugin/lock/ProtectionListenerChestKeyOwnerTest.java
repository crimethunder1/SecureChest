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
import org.bukkit.event.block.BlockBreakEvent;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Chest Key wirkt nur fuer den Besitzer, auch bei Operatoren")
class ProtectionListenerChestKeyOwnerTest {

    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private MessageService messages;
    private Items items;
    private GuiManager gui;
    private UiStateStore uiState;
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
        uiState = new UiStateStore();
        when(fixture.plugin.ui()).thenReturn(uiState);
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
        when(fixture.plugin.names().resolve(operator.getUniqueId())).thenReturn("Operator");
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

    private Block secureHopper(int x, Player owner) {
        Block container = world.setBlock(x, 64, 0, Material.BARREL);
        assertTrue(service.lock(owner, container));
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(BlockFace.DOWN);
        Block hopperBlock = world.setBlock(x, 65, 0, Material.HOPPER, hopperData);
        assertTrue(service.bindSecureHopper(owner, hopperBlock, container));
        return hopperBlock;
    }

    private void holdChestKey(Player player, boolean sneaking) {
        when(items.isHoldingChestKey(player)).thenReturn(true);
        when(player.isSneaking()).thenReturn(sneaking);
    }

    private void holdAdminKey(Player player) {
        when(items.isHoldingAdminKey(player)).thenReturn(true);
    }

    private String lockIdOf(Block block) {
        return service.getLock(block).orElseThrow().key().asId();
    }

    private void assertStillLockedBy(Block block, Player owner) {
        assertTrue(service.isLocked(block));
        assertEquals(owner.getUniqueId(), service.getLock(block).orElseThrow().owner());
    }

    private void assertNoManageMenuScheduled() {
        verify(fixture.scheduler, never()).runTask(eq(fixture.plugin), any(Runnable.class));
        verify(gui, never()).openLockManage(any(), anyString());
    }

    private void runScheduledMainThreadTask() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).runTask(eq(fixture.plugin), taskCaptor.capture());
        taskCaptor.getValue().run();
    }

    @Test
    @DisplayName("Operator mit Chest Key ohne Schleichen auf fremder Kiste: kein Verwaltungsmenue, Meldung fuer Fremde")
    void operator_ohneSchleichen_keinMenue() {
        Block chest = lockedChest(0, alice);
        holdChestKey(operator, false);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gehoert", "Alice");
        assertNoManageMenuScheduled();
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Operator mit Chest Key und Schleichen auf fremder Kiste: kein Entsperren, Meldung fuer Fremde")
    void operator_mitSchleichen_keinEntsperren() {
        Block chest = lockedChest(0, alice);
        holdChestKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, chest));

        verify(messages).get("block-gehoert", "Alice");
        verify(messages, never()).get("block-entsperrt");
        assertStillLockedBy(chest, alice);
        assertFalse(fixture.plugin.undos().hasUndo(operator.getUniqueId()));
    }

    @Test
    @DisplayName("Operator mit Chest Key und Quick Remove auf fremder Kiste: kein Entsperren, Meldung fuer Fremde")
    void operator_quickRemove_keinEntsperren() {
        Block chest = lockedChest(0, alice);
        service.setQuickRemove(operator.getUniqueId(), true);
        holdChestKey(operator, false);

        listener.onPlayerInteract(rightClick(operator, chest));

        verify(messages).get("block-gehoert", "Alice");
        verify(messages, never()).get("block-entsperrt");
        assertNoManageMenuScheduled();
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Operator bekommt dieselbe Rueckmeldung wie ein fremder Spieler ohne Operator-Rechte")
    void operator_gleicheRueckmeldungWieFremder() {
        Block chest = lockedChest(0, alice);
        holdChestKey(operator, true);
        holdChestKey(bob, true);
        PlayerInteractEvent operatorEvent = rightClick(operator, chest);
        PlayerInteractEvent strangerEvent = rightClick(bob, chest);

        listener.onPlayerInteract(operatorEvent);
        listener.onPlayerInteract(strangerEvent);

        verify(operatorEvent).setCancelled(true);
        verify(strangerEvent).setCancelled(true);
        verify(messages, times(2)).get("block-gehoert", "Alice");
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Operator mit Chest Key auf fremdem Secure Hopper, mit und ohne Schleichen: kein Entbinden, Meldung fuer Fremde")
    void operator_fremderSecureHopper_keinEntbinden() {
        Block hopperBlock = secureHopper(0, alice);
        holdChestKey(operator, true);
        listener.onPlayerInteract(rightClick(operator, hopperBlock));
        holdChestKey(operator, false);
        listener.onPlayerInteract(rightClick(operator, hopperBlock));
        holdChestKey(bob, true);
        listener.onPlayerInteract(rightClick(bob, hopperBlock));

        verify(messages, times(3)).get("hopper-bereits-geschuetzt");
        verify(messages, never()).get("hopper-entbunden");
        SecureHopperEntry entry = service.getSecureHopper(hopperBlock).orElseThrow();
        assertEquals(alice.getUniqueId(), entry.owner());
    }

    @Test
    @DisplayName("Operator mit Chest Key auf eigener Sperre ohne Schleichen: Verwaltungsmenue oeffnet sich")
    void operator_eigeneSperre_menue() {
        Block chest = lockedChest(0, operator);
        holdChestKey(operator, false);

        listener.onPlayerInteract(rightClick(operator, chest));
        runScheduledMainThreadTask();

        verify(gui).openLockManage(operator, lockIdOf(chest));
        verify(messages, never()).get(eq("block-gehoert"), any());
    }

    @Test
    @DisplayName("Operator mit Chest Key auf eigener Sperre mit Schleichen: Sperre wird entfernt")
    void operator_eigeneSperre_schleichenEntsperrt() {
        Block chest = lockedChest(0, operator);
        holdChestKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, chest));

        verify(messages).get("block-entsperrt");
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Operator mit Chest Key auf eigener Sperre mit Quick Remove: Sperre wird entfernt")
    void operator_eigeneSperre_quickRemoveEntsperrt() {
        Block chest = lockedChest(0, operator);
        service.setQuickRemove(operator.getUniqueId(), true);
        holdChestKey(operator, false);

        listener.onPlayerInteract(rightClick(operator, chest));

        verify(messages).get("block-entsperrt");
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Besitzer ohne Operator-Rechte: Menue ohne Schleichen, Entsperren mit Schleichen wie bisher")
    void besitzerOhneOperator_unveraendert() {
        Block menuChest = lockedChest(0, alice);
        Block unlockChest = lockedChest(5, alice);
        holdChestKey(alice, false);
        listener.onPlayerInteract(rightClick(alice, menuChest));
        runScheduledMainThreadTask();
        holdChestKey(alice, true);
        listener.onPlayerInteract(rightClick(alice, unlockChest));

        verify(gui).openLockManage(alice, lockIdOf(menuChest));
        verify(messages).get("block-entsperrt");
        assertFalse(service.isLocked(unlockChest));
        assertStillLockedBy(menuChest, alice);
    }

    @Test
    @DisplayName("Besitzer mit Operator-Rechten oeffnet den eigenen Secure Hopper weiterhin per Schleichen")
    void operator_eigenerSecureHopper_entbinden() {
        Block hopperBlock = secureHopper(0, operator);
        holdChestKey(operator, true);

        listener.onPlayerInteract(rightClick(operator, hopperBlock));

        verify(messages).get("hopper-entbunden");
        assertFalse(service.isSecureHopper(hopperBlock));
    }

    @Test
    @DisplayName("Operator mit Admin Key oeffnet fremde Kiste und fremden Secure Hopper weiterhin")
    void operator_adminKey_oeffnet() {
        Block chest = lockedChest(0, alice);
        Block hopperBlock = secureHopper(5, alice);
        holdAdminKey(operator);
        PlayerInteractEvent chestEvent = rightClick(operator, chest);
        PlayerInteractEvent hopperEvent = rightClick(operator, hopperBlock);

        listener.onPlayerInteract(chestEvent);
        listener.onPlayerInteract(hopperEvent);

        verify(chestEvent, never()).setCancelled(anyBoolean());
        verify(hopperEvent, never()).setCancelled(anyBoolean());
        assertEquals(AccessLevel.STOCKER, service.getAccessLevel(operator, chest));
        assertStillLockedBy(chest, alice);
    }

    @Test
    @DisplayName("Operator ohne Admin Key und ohne Chest Key: fremde Kiste bleibt zu wie bisher")
    void operator_ohneSchluessel_bleibtZu() {
        Block chest = lockedChest(0, alice);
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gehoert", "Alice");
    }

    @Test
    @DisplayName("Operator baut fremde gesperrte Kiste ab: erlaubt, mit Admin-Log, Sperre entfernt")
    void operator_abbauen_mitLog() {
        Block chest = lockedChest(0, alice);
        SqlLockService spiedService = spy(service);
        when(fixture.plugin.locks()).thenReturn(spiedService);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(chest);
        when(event.getPlayer()).thenReturn(operator);

        listener.onBlockBreak(event);

        verify(event, never()).setCancelled(true);
        verify(spiedService).logAdminBreak(operator, chest);
        verify(spiedService, never()).logUnauthorizedBreak(any(), any());
        verify(messages).get("lock-entfernt");
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Inspect-Modus: Operator mit leerer Hand und Schleichen sieht die Infos der fremden Sperre")
    void inspectModus_unveraendert() {
        Block chest = lockedChest(0, alice);
        uiState.setInspect(operator.getUniqueId(), true);
        when(operator.isSneaking()).thenReturn(true);
        when(messages.get(eq("inspect-position"), any(), any(), any(), any())).thenReturn("message");
        PlayerInteractEvent event = rightClick(operator, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("inspect-gesperrt", "CHEST");
        verify(messages).get("inspect-besitzer", "Alice");
        verify(messages, never()).get(eq("block-gehoert"), any());
        assertStillLockedBy(chest, alice);
    }
}
