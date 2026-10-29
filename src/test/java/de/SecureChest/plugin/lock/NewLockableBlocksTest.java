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
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Neue sperrbare Bloecke: Sperren, Entsperren, Abbauen, Schutz, Namenstext und Chest-Key-Verbrauch")
class NewLockableBlocksTest {

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
        when(alice.getGameMode()).thenReturn(GameMode.SURVIVAL);
        aliceInventory = new FakePlayerInventory(alice);
        aliceInventory.answerChestKeyChecks(items, alice);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Block machine(Material type) {
        return world.setBlock(0, 64, 0, type);
    }

    private Block lockedMachine(Material type) {
        Block block = machine(type);
        assertTrue(service.lock(alice, block), "Sperre fuer " + type);
        return block;
    }

    private String lockId(Block block) {
        return service.getLock(block).orElseThrow().key().asId();
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

    private void holdChestKey(int amount, boolean sneaking) {
        aliceInventory.putChestKeys(0, amount);
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(sneaking);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Sperren und Entsperren ueber den Dienst, mit Typ, Namenstext und Entfernen des Textes")
    void lockAndUnlock(Material type) {
        Block block = lockedMachine(type);
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
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Chest Key mit Schleichen sperrt und verbraucht bei eingeschaltetem Verbrauch genau einen Key")
    void chestKeyLockConsumesKey(Material type) {
        holdChestKey(3, true);
        Block block = machine(type);
        PlayerInteractEvent event = rightClick(alice, block);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        assertTrue(service.isLocked(block));
        assertEquals(type, service.getLock(block).orElseThrow().blockType());
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("block-gesperrt");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Chest Key mit Schleichen bei ausgeschaltetem Verbrauch sperrt ohne Verbrauch")
    void chestKeyLockWithoutConsumption(Material type) {
        when(consumption.isEnabled()).thenReturn(false);
        holdChestKey(3, true);
        Block block = machine(type);

        listener.onPlayerInteract(rightClick(alice, block));

        assertTrue(service.isLocked(block));
        assertEquals(3, aliceInventory.amount(0));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Chest Key mit Schleichen am eigenen gesperrten Block entsperrt ohne Verbrauch")
    void chestKeyUnlock(Material type) {
        Block block = lockedMachine(type);
        holdChestKey(3, true);

        listener.onPlayerInteract(rightClick(alice, block));

        assertFalse(service.isLocked(block));
        assertEquals(3, aliceInventory.amount(0));
        verify(messages).get("block-entsperrt");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Rechtsklick: Fremder wird abgewiesen, Viewer und Besitzer duerfen oeffnen")
    void rightClickAccess(Material type) {
        Block block = lockedMachine(type);
        Player viewer = fixture.player("Viewer", false);
        assertTrue(service.setTrustById(alice, lockId(block), viewer.getUniqueId(), AccessLevel.VIEW));

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
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Abbauen: Fremder wird abgewiesen, der Besitzer entfernt die Sperre und den Namenstext")
    void breaking(Material type) {
        Block block = lockedMachine(type);
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
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Explosionen lassen gesperrte Bloecke stehen, ungesperrte Bloecke bleiben in der Liste")
    void explosions(Material type) {
        Block locked = lockedMachine(type);
        Block unlocked = world.setBlock(5, 64, 0, type);
        Block stone = world.setBlock(6, 64, 0, Material.STONE);

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
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Feuer und Kolben werden an gesperrten Bloecken abgebrochen")
    void fireAndPistons(Material type) {
        Block block = lockedMachine(type);
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
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Auto-Lock sperrt neue Typen beim Platzieren")
    void autoLock(Material type) {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        Block block = machine(type);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(alice);

        listener.onBlockPlaceAutoLock(event);

        assertTrue(service.isLocked(block));
        verify(messages).get("automatisch-gesperrt");
    }

    @Test
    @DisplayName("Statistik zaehlt jeden neuen Typ einzeln, bisherige Zaehler bleiben unberuehrt")
    void statistics() {
        List<Material> types = List.of(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.DROPPER,
                Material.DISPENSER, Material.BREWING_STAND, Material.CRAFTER, Material.FURNACE, Material.CHEST);
        for (int index = 0; index < types.size(); index++) {
            assertTrue(service.lock(alice, world.setBlock(index * 3, 64, 0, types.get(index))));
        }

        LockStats stats = service.getLockStats(alice.getUniqueId());

        assertEquals(9, stats.totalLocks());
        assertEquals(2, stats.furnaceCount());
        assertEquals(1, stats.blastFurnaceCount());
        assertEquals(1, stats.smokerCount());
        assertEquals(1, stats.dropperCount());
        assertEquals(1, stats.dispenserCount());
        assertEquals(1, stats.brewingStandCount());
        assertEquals(1, stats.crafterCount());
        assertEquals(1, stats.chestCount());
        assertEquals(0, stats.trappedChestCount());
        assertEquals(0, stats.barrelCount());
        assertEquals(0, stats.shulkerCount());
        assertEquals(0, stats.hopperCount());
    }
}
