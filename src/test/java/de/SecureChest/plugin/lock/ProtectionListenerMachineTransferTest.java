package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Trichter, Werfer und Secure Hopper an gesperrten Oefen und Braustaenden")
class ProtectionListenerMachineTransferTest {

    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private MessageService messages;
    private Items items;
    private ProtectionListener listener;
    private Player alice;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        when(fixture.plugin.gui()).thenReturn(mock(GuiManager.class));
        when(fixture.plugin.confirms()).thenReturn(new ConfirmManager());
        when(fixture.plugin.keySecureHopperBlock()).thenReturn(SECURE_HOPPER_MARK);
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Block machine(int x, int y, int z, Material type, boolean locked) {
        Block block = world.setBlock(x, y, z, type);
        if (locked) assertTrue(service.lock(alice, block));
        return block;
    }

    private Block hopper(int x, int y, int z, BlockFace facing) {
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(facing);
        return world.setBlock(x, y, z, Material.HOPPER, hopperData);
    }

    private Inventory inventoryAt(Block block, InventoryHolder holder) {
        Location location = new Location(world.world(), block.getX(), block.getY(), block.getZ());
        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(location);
        when(inventory.getHolder()).thenReturn(holder);
        return inventory;
    }

    private Inventory hopperInventory(Block hopperBlock) {
        org.bukkit.block.Hopper holder = mock(org.bukkit.block.Hopper.class);
        when(holder.getBlock()).thenReturn(hopperBlock);
        return inventoryAt(hopperBlock, holder);
    }

    private Inventory machineInventory(Block block) {
        InventoryHolder holder = switch (block.getType()) {
            case BREWING_STAND -> mock(BrewingStand.class);
            case DROPPER -> mock(Dropper.class);
            case DISPENSER -> mock(Dispenser.class);
            default -> mock(Furnace.class);
        };
        return inventoryAt(block, holder);
    }

    private InventoryMoveItemEvent move(Inventory source, Inventory destination) {
        InventoryMoveItemEvent event = mock(InventoryMoveItemEvent.class);
        when(event.getSource()).thenReturn(source);
        when(event.getDestination()).thenReturn(destination);
        listener.onInventoryMoveItem(event);
        return event;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Normaler Trichter ueber und neben dem gesperrten Block darf nicht einfuellen")
    void plainHopperCannotFeed(Material type) {
        Block target = machine(0, 64, 0, type, true);
        Block above = hopper(0, 65, 0, BlockFace.DOWN);
        Block side = hopper(1, 64, 0, BlockFace.WEST);

        verify(move(hopperInventory(above), machineInventory(target))).setCancelled(true);
        verify(move(hopperInventory(side), machineInventory(target))).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Gebundener Secure Hopper darf in den gesperrten Block einfuellen, von oben und von der Seite")
    void boundSecureHopperMayFeed(Material type) {
        Block target = machine(0, 64, 0, type, true);
        Block above = hopper(0, 65, 0, BlockFace.DOWN);
        Block side = hopper(1, 64, 0, BlockFace.WEST);
        assertTrue(service.bindSecureHopper(alice, above, target));
        assertTrue(service.bindSecureHopper(alice, side, target));

        verify(move(hopperInventory(above), machineInventory(target)), never()).setCancelled(anyBoolean());
        verify(move(hopperInventory(side), machineInventory(target)), never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Secure Hopper, der an einen anderen Behaelter gebunden ist, darf nicht einfuellen")
    void secureHopperBoundElsewhereCannotFeed(Material type) {
        Block target = machine(0, 64, 0, type, true);
        Block otherContainer = machine(1, 63, 0, Material.BARREL, true);
        Block side = hopper(1, 64, 0, BlockFace.DOWN);
        assertTrue(service.bindSecureHopper(alice, side, otherContainer));

        verify(move(hopperInventory(side), machineInventory(target))).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Trichter darunter darf weder Ergebnis noch Brennstoff herausholen, auch kein Secure Hopper mit anderem Ziel")
    void hopperBelowCannotExtract(Material type) {
        Block source = machine(0, 64, 0, type, true);
        Block plainBelow = hopper(0, 63, 0, BlockFace.DOWN);
        verify(move(machineInventory(source), hopperInventory(plainBelow))).setCancelled(true);

        Block secondSource = machine(5, 64, 0, type, true);
        Block ownBarrel = machine(6, 63, 0, Material.BARREL, true);
        Block secureBelow = hopper(5, 63, 0, BlockFace.EAST);
        assertTrue(service.bindSecureHopper(alice, secureBelow, ownBarrel));
        verify(move(machineInventory(secondSource), hopperInventory(secureBelow))).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Werfer und Spender schieben nicht in einen gesperrten Ofen oder Braustand")
    void dropperCannotPushIntoLockedMachine(Material type) {
        Block target = machine(0, 64, 0, type, true);
        Block dropper = machine(1, 64, 0, Material.DROPPER, false);
        Block dispenser = machine(-1, 64, 0, Material.DISPENSER, false);

        verify(move(machineInventory(dropper), machineInventory(target))).setCancelled(true);
        verify(move(machineInventory(dispenser), machineInventory(target))).setCancelled(true);
    }

    @Test
    @DisplayName("Gesperrter Werfer schiebt nicht in einen ungesperrten Behaelter, wie bei Kisten ohne Secure Hopper")
    void lockedDropperCannotPushOut() {
        Block dropper = machine(0, 64, 0, Material.DROPPER, true);
        Block chest = machine(1, 64, 0, Material.CHEST, false);

        verify(move(machineInventory(dropper), machineInventory(chest))).setCancelled(true);
    }

    @Test
    @DisplayName("Trichterlore wird an gesperrten Oefen immer abgebrochen")
    void hopperMinecartIsCancelled() {
        Block furnace = machine(0, 64, 0, Material.FURNACE, true);
        Block rail = world.setBlock(0, 63, 0, Material.RAIL);
        Inventory minecart = inventoryAt(rail, mock(HopperMinecart.class));

        verify(move(machineInventory(furnace), minecart)).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "DROPPER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Ungesperrte Bloecke: Trichter und Werfer laufen ohne Eingriff")
    void unlockedMachinesAreUntouched(Material type) {
        Block target = machine(0, 64, 0, type, false);
        Block above = hopper(0, 65, 0, BlockFace.DOWN);
        Block below = hopper(0, 63, 0, BlockFace.DOWN);

        verify(move(hopperInventory(above), machineInventory(target)), never()).setCancelled(anyBoolean());
        verify(move(machineInventory(target), hopperInventory(below)), never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND"})
    @DisplayName("Chest Key mit Schleichen bindet einen Trichter ueber dem eigenen gesperrten Block als Secure Hopper")
    void chestKeyBindsHopperAboveMachine(Material type) {
        Block target = machine(0, 64, 0, type, true);
        Block above = hopper(0, 65, 0, BlockFace.DOWN);
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(true);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(above);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getPlayer()).thenReturn(alice);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("hopper-gebunden");
        SecureHopperEntry entry = service.getSecureHopper(above).orElseThrow();
        assertEquals(service.getLock(target).orElseThrow().key(), entry.boundLock());
        assertFalse(service.isLocked(above));
        verify(move(hopperInventory(above), machineInventory(target)), never()).setCancelled(anyBoolean());
    }
}
