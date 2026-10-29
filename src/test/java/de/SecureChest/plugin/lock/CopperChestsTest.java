package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.FakePlayerInventory;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.sign.SignManager;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Kupferkisten: Sperren, Schutz, Fenster, Doppelkisten, Anbau, Oxidation und Statistik")
class CopperChestsTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private MessageService messages;
    private SignManager signs;
    private Items items;
    private ChestKeyConsumptionSetting consumption;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private ProtectionListener listener;
    private Player alice;
    private Player bob;
    private Player viewer;
    private Player stocker;
    private FakePlayerInventory aliceInventory;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        signs = fixture.plugin.signs();
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        when(fixture.plugin.gui()).thenReturn(mock(GuiManager.class));
        when(fixture.plugin.confirms()).thenReturn(new ConfirmManager());
        consumption = mock(ChestKeyConsumptionSetting.class);
        when(consumption.isEnabled()).thenReturn(true);
        when(fixture.plugin.chestKeyConsumption()).thenReturn(consumption);
        listener = new ProtectionListener(fixture.plugin, clock::get);

        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
        viewer = fixture.player("Viewer", false);
        stocker = fixture.player("Stocker", false);
        when(alice.getGameMode()).thenReturn(GameMode.SURVIVAL);
        aliceInventory = new FakePlayerInventory(alice);
        aliceInventory.answerChestKeyChecks(items, alice);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Block singleChest(Material type) {
        return world.setChest(0, 64, 0, type, BlockFace.NORTH, Chest.Type.SINGLE);
    }

    private Block lockedSingleChest(Material type) {
        Block block = singleChest(type);
        assertTrue(service.lock(alice, block), "Sperre fuer " + type);
        return block;
    }

    private String lockId(Block block) {
        return service.getLock(block).orElseThrow().key().asId();
    }

    private void trustViewerAndStocker(Block block) {
        assertTrue(service.setTrustById(alice, lockId(block), viewer.getUniqueId(), AccessLevel.VIEW));
        assertTrue(service.setTrustById(alice, lockId(block), stocker.getUniqueId(), AccessLevel.STOCKER));
    }

    private PlayerInteractEvent rightClick(Player player, Block block) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private BlockBreakEvent breakBy(Player player, Block block) {
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private BlockPlaceEvent placeEvent(Player player, Block placed) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void holdChestKey(int amount, boolean sneaking) {
        aliceInventory.putChestKeys(0, amount);
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(sneaking);
    }

    private void enableAutoLock() {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        when(fixture.config.autoLockSneakOnly()).thenReturn(false);
        when(fixture.config.autoLockRequireKey()).thenReturn(false);
    }

    private InventoryView chestWindow(double x, double z, int size) {
        Inventory top = mock(Inventory.class);
        when(top.getLocation()).thenReturn(new Location(world.world(), x, 64, z));
        when(top.getSize()).thenReturn(size);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(mock(Inventory.class));
        return view;
    }

    private InventoryOpenEvent open(Player player, InventoryView view) {
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        return event;
    }

    private InventoryClickEvent topClick(Player player, InventoryView view, ClickType clickType, InventoryAction action) {
        Inventory topInventory = view.getTopInventory();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(topInventory);
        when(event.getClick()).thenReturn(clickType);
        when(event.isShiftClick()).thenReturn(clickType.isShiftClick());
        when(event.getAction()).thenReturn(action);
        when(event.getRawSlot()).thenReturn(3);
        return event;
    }

    private InventoryDragEvent drag(Player player, InventoryView view) {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(2));
        return event;
    }

    private Block lockedCopperDoubleChest(Material leftType, Material rightType) {
        Block original = world.setChest(0, 64, 0, rightType, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, original));
        world.setChest(0, 64, 0, rightType, BlockFace.NORTH, Chest.Type.RIGHT);
        return world.setChest(-1, 64, 0, leftType, BlockFace.NORTH, Chest.Type.LEFT);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Sperren und Entsperren ueber den Dienst, mit Typ, Namenstext und Entfernen des Textes")
    void lockAndUnlock(Material type) {
        Block block = lockedSingleChest(type);
        LockEntry entry = service.getLock(block).orElseThrow();

        assertEquals(type, entry.blockType());
        assertEquals(alice.getUniqueId(), entry.owner());
        assertEquals(world.keyAt(0, 64, 0), entry.key());
        verify(signs).spawnSign(world.keyAt(0, 64, 0), "Alice");
        assertFalse(service.lock(bob, block), "zweite Sperre darf nicht gelingen");

        assertTrue(service.unlockByIdWithUndo(alice, entry.key().asId()));
        assertFalse(service.isLocked(block));
        verify(signs).removeSign(entry.key().asId());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Chest Key mit Schleichen sperrt und verbraucht genau einen Key, erneut entsperrt ohne Verbrauch")
    void chestKeyLockConsumesKeyAndUnlocks(Material type) {
        holdChestKey(3, true);
        Block block = singleChest(type);

        PlayerInteractEvent lockClick = rightClick(alice, block);
        listener.onPlayerInteract(lockClick);

        verify(lockClick).setCancelled(true);
        assertTrue(service.isLocked(block));
        assertEquals(type, service.getLock(block).orElseThrow().blockType());
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("block-gesperrt");

        clock.addAndGet(10_000L);
        listener.onPlayerInteract(rightClick(alice, block));

        assertFalse(service.isLocked(block));
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("block-entsperrt");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Rechtsklick: Fremder wird abgewiesen, Viewer und Besitzer duerfen oeffnen")
    void rightClickAccess(Material type) {
        Block block = lockedSingleChest(type);
        trustViewerAndStocker(block);

        PlayerInteractEvent strangerClick = rightClick(bob, block);
        PlayerInteractEvent viewerClick = rightClick(viewer, block);
        PlayerInteractEvent ownerClick = rightClick(alice, block);
        listener.onPlayerInteract(strangerClick);
        listener.onPlayerInteract(viewerClick);
        listener.onPlayerInteract(ownerClick);

        verify(strangerClick).setCancelled(true);
        verify(messages).get(eq("block-gehoert"), any());
        verify(viewerClick, never()).setCancelled(true);
        verify(ownerClick, never()).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Abbauen: Fremder wird abgewiesen, der Besitzer entfernt die Sperre und den Namenstext")
    void breaking(Material type) {
        Block block = lockedSingleChest(type);
        String id = lockId(block);

        BlockBreakEvent strangerBreak = breakBy(bob, block);
        listener.onBlockBreak(strangerBreak);
        verify(strangerBreak).setCancelled(true);
        assertTrue(service.isLocked(block));

        BlockBreakEvent ownerBreak = breakBy(alice, block);
        listener.onBlockBreak(ownerBreak);
        verify(ownerBreak, never()).setCancelled(true);
        assertFalse(service.isLocked(block));
        verify(signs).removeSign(id);
        verify(messages).get("lock-entfernt");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Explosionen lassen gesperrte Kupferkisten stehen, ungesperrte bleiben in der Liste")
    void explosions(Material type) {
        Block locked = lockedSingleChest(type);
        Block unlocked = world.setChest(5, 64, 0, type, BlockFace.NORTH, Chest.Type.SINGLE);
        Block stone = world.setBlock(6, 64, 3, Material.STONE);

        List<Block> entityBlocks = new ArrayList<>(List.of(locked, unlocked, stone));
        EntityExplodeEvent entityExplosion = mock(EntityExplodeEvent.class);
        when(entityExplosion.blockList()).thenReturn(entityBlocks);
        listener.onEntityExplode(entityExplosion);

        List<Block> blockBlocks = new ArrayList<>(List.of(locked, unlocked, stone));
        BlockExplodeEvent blockExplosion = mock(BlockExplodeEvent.class);
        when(blockExplosion.blockList()).thenReturn(blockBlocks);
        listener.onBlockExplode(blockExplosion);

        assertEquals(List.of(unlocked, stone), entityBlocks);
        assertEquals(List.of(unlocked, stone), blockBlocks);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Feuer und Kolben werden an gesperrten Kupferkisten abgebrochen")
    void fireAndPistons(Material type) {
        Block block = lockedSingleChest(type);
        Block piston = world.setBlock(0, 64, 3, Material.PISTON);

        BlockBurnEvent burn = mock(BlockBurnEvent.class);
        when(burn.getBlock()).thenReturn(block);
        BlockIgniteEvent ignite = mock(BlockIgniteEvent.class);
        when(ignite.getBlock()).thenReturn(block);
        BlockPistonExtendEvent extend = mock(BlockPistonExtendEvent.class);
        when(extend.getBlock()).thenReturn(piston);
        when(extend.getBlocks()).thenReturn(List.of(block));
        BlockPistonRetractEvent retract = mock(BlockPistonRetractEvent.class);
        when(retract.getBlock()).thenReturn(piston);
        when(retract.getBlocks()).thenReturn(List.of(block));

        listener.onBlockBurn(burn);
        listener.onBlockIgnite(ignite);
        listener.onPistonExtend(extend);
        listener.onPistonRetract(retract);

        verify(burn).setCancelled(true);
        verify(ignite).setCancelled(true);
        verify(extend).setCancelled(true);
        verify(retract).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Fenster: Fremder wird abgewiesen, Viewer darf nichts bewegen, Stocker und Besitzer schon")
    void windowProtection(Material type) {
        Block block = lockedSingleChest(type);
        trustViewerAndStocker(block);
        InventoryView view = chestWindow(0, 0, 27);

        InventoryOpenEvent strangerOpen = open(bob, view);
        InventoryOpenEvent viewerOpen = open(viewer, view);
        InventoryOpenEvent stockerOpen = open(stocker, view);
        listener.onInventoryOpen(strangerOpen);
        listener.onInventoryOpen(viewerOpen);
        listener.onInventoryOpen(stockerOpen);
        verify(strangerOpen).setCancelled(true);
        verify(viewerOpen, never()).setCancelled(anyBoolean());
        verify(stockerOpen, never()).setCancelled(anyBoolean());

        InventoryClickEvent viewerTake = topClick(viewer, view, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        InventoryClickEvent viewerShift = topClick(viewer, view, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        InventoryClickEvent stockerTake = topClick(stocker, view, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        InventoryClickEvent ownerTake = topClick(alice, view, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        InventoryClickEvent strangerTake = topClick(bob, view, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(viewerTake);
        listener.onInventoryClick(viewerShift);
        listener.onInventoryClick(stockerTake);
        listener.onInventoryClick(ownerTake);
        listener.onInventoryClick(strangerTake);
        verify(viewerTake).setCancelled(true);
        verify(viewerShift).setCancelled(true);
        verify(stockerTake, never()).setCancelled(anyBoolean());
        verify(ownerTake, never()).setCancelled(anyBoolean());
        verify(strangerTake).setCancelled(true);

        InventoryDragEvent viewerDrag = drag(viewer, view);
        InventoryDragEvent stockerDrag = drag(stocker, view);
        listener.onInventoryDrag(viewerDrag);
        listener.onInventoryDrag(stockerDrag);
        verify(viewerDrag).setCancelled(true);
        verify(stockerDrag, never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Auto-Lock sperrt eine einzeln platzierte Kupferkiste")
    void autoLock(Material type) {
        enableAutoLock();
        Block block = singleChest(type);

        listener.onBlockPlaceAutoLock(placeEvent(alice, block));

        assertTrue(service.isLocked(block));
        assertEquals(type, service.getLock(block).orElseThrow().blockType());
        verify(messages).get("automatisch-gesperrt");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Doppelkiste aus zwei gleichen Kupferkisten: eine Sperre, Zugriff und Schutz von beiden Haelften")
    void doubleChestOfSameStage(Material type) {
        Block addedHalf = lockedCopperDoubleChest(type, type);
        Block originalHalf = world.blockAt(0, 64, 0);
        trustViewerAndStocker(originalHalf);

        assertEquals(world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertEquals(world.keyAt(0, 64, 0), service.getLock(originalHalf).orElseThrow().key());
        assertFalse(service.lock(bob, addedHalf));
        assertEquals(1, service.getAllCachedLocks().size());

        PlayerInteractEvent strangerClick = rightClick(bob, addedHalf);
        PlayerInteractEvent viewerClick = rightClick(viewer, addedHalf);
        listener.onPlayerInteract(strangerClick);
        listener.onPlayerInteract(viewerClick);
        verify(strangerClick).setCancelled(true);
        verify(viewerClick, never()).setCancelled(true);

        BlockBreakEvent strangerBreak = breakBy(bob, addedHalf);
        listener.onBlockBreak(strangerBreak);
        verify(strangerBreak).setCancelled(true);

        InventoryView doubleWindow = chestWindow(-0.5, 0.5, 54);
        InventoryOpenEvent strangerOpen = open(bob, doubleWindow);
        listener.onInventoryOpen(strangerOpen);
        verify(strangerOpen).setCancelled(true);
        InventoryClickEvent viewerTake = topClick(viewer, doubleWindow, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(viewerTake);
        verify(viewerTake).setCancelled(true);
    }

    @ParameterizedTest(name = "{0} {1}: Anbau bei dx={2}, dz={3}")
    @CsvSource({
            "NORTH, LEFT,   1,  0",
            "NORTH, RIGHT, -1,  0",
            "SOUTH, LEFT,  -1,  0",
            "SOUTH, RIGHT,  1,  0",
            "EAST,  LEFT,   0,  1",
            "EAST,  RIGHT,  0, -1",
            "WEST,  LEFT,   0, -1",
            "WEST,  RIGHT,  0,  1"
    })
    @DisplayName("Doppelkiste aus Kupferkisten in allen Blickrichtungen: Sperre unter einer Haelfte, von beiden gefunden")
    void doubleChestInAllDirections(BlockFace facing, Chest.Type connection, int deltaX, int deltaZ) {
        Chest.Type partnerConnection = connection == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
        Block locked = world.setChest(10, 64, 10, Material.COPPER_CHEST, facing, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, locked));
        world.setChest(10, 64, 10, Material.COPPER_CHEST, facing, connection);
        Block added = world.setChest(10 + deltaX, 64, 10 + deltaZ, Material.COPPER_CHEST, facing, partnerConnection);

        assertTrue(service.isLocked(added));
        assertEquals(world.keyAt(10, 64, 10), service.getLock(added).orElseThrow().key());
        assertEquals(world.keyAt(10, 64, 10), service.getLock(locked).orElseThrow().key());
        assertFalse(service.lock(alice, added));
        assertEquals(1, service.countLocks(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Anbau einer Kupferkiste an eine fremde gesperrte Kupferkiste wird abgelehnt, auch bei anderer Stufe")
    void attachingToForeignCopperChestIsCancelled() {
        lockedSingleChest(Material.COPPER_CHEST);

        BlockPlaceEvent sameStage = placeEvent(bob,
                world.setChest(-1, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT));
        listener.onChestPlaceAdjacentToForeignLock(sameStage);
        verify(sameStage).setCancelled(true);

        BlockPlaceEvent otherStage = placeEvent(bob,
                world.setChest(-1, 64, 0, Material.WAXED_OXIDIZED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.SINGLE));
        listener.onChestPlaceAdjacentToForeignLock(otherStage);
        verify(otherStage).setCancelled(true);
        verify(messages, times(2)).get("chest-neben-fremdem-lock");
    }

    @Test
    @DisplayName("Anbau einer Kupferkiste an die eigene gesperrte Kupferkiste: erlaubt, die Sperre gilt fuer beide Haelften")
    void attachingToOwnCopperChestBindsLock() {
        enableAutoLock();
        lockedSingleChest(Material.EXPOSED_COPPER_CHEST);
        Block placed = world.setChest(-1, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        BlockPlaceEvent event = placeEvent(alice, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);
        listener.onBlockPlaceAutoLock(event);
        world.setChest(0, 64, 0, Material.EXPOSED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        verify(event, never()).setCancelled(anyBoolean());
        assertEquals(List.of(world.keyAt(0, 64, 0)), service.getAllCachedLocks().stream().map(LockEntry::key).toList());
        assertEquals(world.keyAt(0, 64, 0), service.getLock(placed).orElseThrow().key());
        assertEquals(alice.getUniqueId(), service.getLock(placed).orElseThrow().owner());
    }

    @Test
    @DisplayName("Mischfall: Kupferkiste neben fremder gesperrter Kiste und Kiste neben fremder Kupferkiste werden nicht abgelehnt")
    void copperNextToForeignChestIsNotCancelled() {
        lockedSingleChest(Material.CHEST);
        BlockPlaceEvent copperNextToChest = placeEvent(bob,
                world.setChest(-1, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.SINGLE));
        listener.onChestPlaceAdjacentToForeignLock(copperNextToChest);
        verify(copperNextToChest, never()).setCancelled(anyBoolean());

        Block foreignCopper = world.setChest(20, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, foreignCopper));
        BlockPlaceEvent chestNextToCopper = placeEvent(bob,
                world.setChest(19, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE));
        listener.onChestPlaceAdjacentToForeignLock(chestNextToCopper);
        verify(chestNextToCopper, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("Admin ist von der Anbau-Pruefung ausgenommen, auch bei Kupferkisten")
    void adminIsExemptForCopperChests() {
        lockedSingleChest(Material.COPPER_CHEST);
        Player admin = fixture.player("Admin", true);
        BlockPlaceEvent event = placeEvent(admin,
                world.setChest(-1, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT));

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest(name = "gesperrt als COPPER_CHEST, jetzt {0}")
    @EnumSource(value = Material.class, names = {
            "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST"})
    @DisplayName("Oxidation und Wachsen: die Sperre bleibt gueltig, gespeichertes Material bleibt unveraendert")
    void lockSurvivesMaterialChange(Material current) {
        Block block = lockedSingleChest(Material.COPPER_CHEST);
        trustViewerAndStocker(block);
        world.setChest(0, 64, 0, current, BlockFace.NORTH, Chest.Type.SINGLE);

        assertTrue(LockUtil.isLockable(block));
        assertTrue(service.isLocked(block));
        LockEntry entry = service.getLock(block).orElseThrow();
        assertEquals(Material.COPPER_CHEST, entry.blockType());
        assertEquals(alice.getUniqueId(), entry.owner());
        assertFalse(service.lock(bob, block));

        PlayerInteractEvent strangerClick = rightClick(bob, block);
        listener.onPlayerInteract(strangerClick);
        verify(strangerClick).setCancelled(true);
        InventoryOpenEvent strangerOpen = open(bob, chestWindow(0, 0, 27));
        listener.onInventoryOpen(strangerOpen);
        verify(strangerOpen).setCancelled(true);

        LockStats stats = service.getLockStats(alice.getUniqueId());
        assertEquals(1, stats.chestCount());
    }

    @Test
    @DisplayName("Oxidation nur einer Haelfte: die Doppelkiste bleibt verbunden, die Sperre gilt fuer beide Haelften")
    void doubleChestWithOneOxidizedHalfKeepsLock() {
        Block addedHalf = lockedCopperDoubleChest(Material.COPPER_CHEST, Material.COPPER_CHEST);
        world.setChest(-1, 64, 0, Material.EXPOSED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block originalHalf = world.blockAt(0, 64, 0);

        assertSame(originalHalf, LockUtil.findDoubleChestPartner(addedHalf));
        assertEquals(world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertEquals(world.keyAt(0, 64, 0), service.getLock(originalHalf).orElseThrow().key());

        world.setChest(0, 64, 0, Material.WAXED_WEATHERED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertEquals(world.keyAt(0, 64, 0), service.getLock(addedHalf).orElseThrow().key());
        assertEquals(Material.COPPER_CHEST, service.getLock(addedHalf).orElseThrow().blockType());
    }

    @Test
    @DisplayName("Statistik zaehlt Kupferkisten jeder Stufe als Kisten, Redstone-Kisten bleiben getrennt")
    void statisticsCountCopperChestsAsChests() {
        List<Material> types = List.of(Material.CHEST, Material.TRAPPED_CHEST, Material.COPPER_CHEST,
                Material.EXPOSED_COPPER_CHEST, Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST, Material.WAXED_WEATHERED_COPPER_CHEST,
                Material.WAXED_OXIDIZED_COPPER_CHEST, Material.BARREL);
        for (int index = 0; index < types.size(); index++) {
            Block block = world.setChest(index * 3, 64, 0, types.get(index), BlockFace.NORTH, Chest.Type.SINGLE);
            assertTrue(service.lock(alice, block), types.get(index).name());
        }

        LockStats stats = service.getLockStats(alice.getUniqueId());

        assertEquals(11, stats.totalLocks());
        assertEquals(9, stats.chestCount());
        assertEquals(1, stats.trappedChestCount());
        assertEquals(1, stats.barrelCount());
        assertEquals(0, stats.shulkerCount());
    }
}
