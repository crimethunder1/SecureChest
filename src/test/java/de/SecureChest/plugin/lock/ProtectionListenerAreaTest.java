package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Rueckmeldung ausserhalb des Wirkungsbereichs")
class ProtectionListenerAreaTest {

    private static final String OUTSIDE_MESSAGE_KEY = "wirkungsbereich-hier-nicht-aktiv";

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private MessageService messages;
    private Items items;
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
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);

        assertTrue(fixture.areas.apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10)).applied());
        assertTrue(fixture.areas.apply(new AreaChange.Enable()).applied());
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

    private BlockPlaceEvent placeEvent(Player player, Block placed) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private Block hopper(int x, int y, int z, BlockFace facing) {
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(facing);
        return world.setBlock(x, y, z, Material.HOPPER, hopperData);
    }

    @Test
    @DisplayName("Chest Key mit Schleichen ausserhalb: neue Meldung statt 'bereits gesperrt', keine Sperre")
    void chestKeyDirekt_ausserhalb() {
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(true);
        Block chest = world.setBlock(50, 64, 50, Material.BARREL);
        PlayerInteractEvent event = rightClick(alice, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get(OUTSIDE_MESSAGE_KEY);
        verify(messages, never()).get("block-bereits-gesperrt");
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Chest Key ohne Schleichen ausserhalb: kein Bestaetigungsdialog, neue Meldung")
    void chestKeyMitDialog_ausserhalb() {
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        Block chest = world.setBlock(50, 64, 50, Material.BARREL);

        listener.onPlayerInteract(rightClick(alice, chest));

        verify(messages).get(OUTSIDE_MESSAGE_KEY);
        verify(fixture.plugin, never()).confirms();
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Chest Key innerhalb sperrt wie bisher")
    void chestKey_innerhalb() {
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(true);
        Block chest = world.setBlock(5, 64, 5, Material.BARREL);

        listener.onPlayerInteract(rightClick(alice, chest));

        verify(messages).get("block-gesperrt");
        verify(messages, never()).get(OUTSIDE_MESSAGE_KEY);
        assertTrue(service.isLocked(chest));
    }

    @Test
    @DisplayName("Quick Lock ausserhalb greift nicht: Oeffnen nicht abgebrochen, keine Meldung, keine Sperre")
    void quickLock_ausserhalb() {
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);
        Block chest = world.setBlock(50, 64, 50, Material.BARREL);
        PlayerInteractEvent event = rightClick(alice, chest);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        verify(messages, never()).get(anyString());
        verify(alice, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Quick Lock ausserhalb verbraucht keinen Aktions-Cooldown")
    void quickLock_ausserhalbOhneCooldown() {
        when(fixture.config.lockActionCooldownMs()).thenReturn(60_000L);
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);
        Block outsideChest = world.setBlock(50, 64, 50, Material.BARREL);
        Block insideChest = world.setBlock(5, 64, 5, Material.BARREL);

        listener.onPlayerInteract(rightClick(alice, outsideChest));
        listener.onPlayerInteract(rightClick(alice, insideChest));

        assertFalse(service.isLocked(outsideChest));
        assertTrue(service.isLocked(insideChest));
    }

    @Test
    @DisplayName("Quick Lock innerhalb sperrt wie bisher")
    void quickLock_innerhalb() {
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);
        Block chest = world.setBlock(5, 64, 5, Material.BARREL);
        PlayerInteractEvent event = rightClick(alice, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gesperrt");
        verify(messages, never()).get(OUTSIDE_MESSAGE_KEY);
        assertTrue(service.isLocked(chest));
    }

    @Test
    @DisplayName("Quick Lock bei ausgeschaltetem Wirkungsbereich sperrt ueberall")
    void quickLock_schalterAus() {
        assertTrue(fixture.areas.apply(new AreaChange.Disable()).applied());
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);
        Block chest = world.setBlock(50, 64, 50, Material.BARREL);
        PlayerInteractEvent event = rightClick(alice, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("block-gesperrt");
        verify(messages, never()).get(OUTSIDE_MESSAGE_KEY);
        assertTrue(service.isLocked(chest));
    }

    @Test
    @DisplayName("Chest Key in der Hand ausserhalb bei aktivem Quick Lock: weiterhin neue Meldung, keine Sperre")
    void chestKeyMitQuickLock_ausserhalb() {
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        Block chest = world.setBlock(50, 64, 50, Material.BARREL);
        PlayerInteractEvent event = rightClick(alice, chest);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get(OUTSIDE_MESSAGE_KEY);
        verify(messages, never()).get("block-bereits-gesperrt");
        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Secure Hopper ausserhalb platzieren: abgebrochen mit neuer Meldung, keine Bindung")
    void secureHopper_ausserhalb() {
        Block container = world.setBlock(10, 64, 5, Material.BARREL);
        assertTrue(service.lock(alice, container));
        Block placedHopper = hopper(11, 64, 5, BlockFace.WEST);
        BlockPlaceEvent event = placeEvent(alice, placedHopper);
        when(event.getItemInHand()).thenReturn(mock(ItemStack.class));
        when(items.is(any(), any())).thenReturn(true);

        listener.onBlockPlace(event);

        verify(event).setCancelled(true);
        verify(messages).get(OUTSIDE_MESSAGE_KEY);
        verify(messages, never()).get("hopper-bind-fehlgeschlagen");
        assertTrue(service.getAllCachedHoppers().isEmpty());
    }

    @Test
    @DisplayName("Secure Hopper innerhalb platzieren bindet wie bisher")
    void secureHopper_innerhalb() {
        Block container = world.setBlock(5, 64, 5, Material.BARREL);
        assertTrue(service.lock(alice, container));
        BlockPlaceEvent event = placeEvent(alice, hopper(5, 65, 5, BlockFace.DOWN));
        when(event.getItemInHand()).thenReturn(mock(ItemStack.class));
        when(items.is(any(), any())).thenReturn(true);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
        verify(messages).get("hopper-gebunden");
        assertEquals(1, service.getAllCachedHoppers().size());
    }

    @Test
    @DisplayName("Auto-Lock ausserhalb wird still uebersprungen")
    void autoLock_ausserhalbStill() {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        when(fixture.config.autoLockSneakOnly()).thenReturn(false);
        when(fixture.config.autoLockRequireKey()).thenReturn(false);
        Block placed = world.setBlock(50, 64, 50, Material.BARREL);

        listener.onBlockPlaceAutoLock(placeEvent(alice, placed));

        assertFalse(service.isLocked(placed));
        verify(messages, never()).get(anyString());
        verify(alice, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Auto-Lock innerhalb sperrt wie bisher")
    void autoLock_innerhalb() {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        Block placed = world.setBlock(5, 64, 5, Material.BARREL);

        listener.onBlockPlaceAutoLock(placeEvent(alice, placed));

        assertTrue(service.isLocked(placed));
        verify(messages).get("automatisch-gesperrt");
    }
}
