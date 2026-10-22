package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.listener.ProtectionListener;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Kiste neben gesperrter Kiste platzieren")
class ProtectionListenerDoubleChestPlacementTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);

        Block aliceChest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, aliceChest));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private BlockPlaceEvent placeEvent(Player player, Block placed) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(placed);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void enableAutoLock() {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        when(fixture.config.autoLockSneakOnly()).thenReturn(false);
        when(fixture.config.autoLockRequireKey()).thenReturn(false);
    }

    @Test
    @DisplayName("Fremder Spieler, neue Kiste schon LEFT, Nachbar noch SINGLE: Platzierung abgebrochen")
    void fremderSpieler_neueKisteLeft_abgebrochen() {
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        BlockPlaceEvent event = placeEvent(bob, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("Fremder Spieler, beide Kisten noch SINGLE: Platzierung abgebrochen")
    void fremderSpieler_beideSingle_abgebrochen() {
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        BlockPlaceEvent event = placeEvent(bob, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("Fremder Spieler, Anbau bei groesseren Koordinaten als RIGHT: Platzierung abgebrochen")
    void fremderSpieler_groessereKoordinatenRight_abgebrochen() {
        Block placed = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        BlockPlaceEvent event = placeEvent(bob, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("Eigentuemer darf neben der eigenen Sperre platzieren, in beiden Zustaenden")
    void eigentuemer_erlaubt() {
        Block placedLeft = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        BlockPlaceEvent leftEvent = placeEvent(alice, placedLeft);
        listener.onChestPlaceAdjacentToForeignLock(leftEvent);
        verify(leftEvent, never()).setCancelled(anyBoolean());

        Block placedSingle = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        BlockPlaceEvent singleEvent = placeEvent(alice, placedSingle);
        listener.onChestPlaceAdjacentToForeignLock(singleEvent);
        verify(singleEvent, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("Admin ist von der Pruefung ausgenommen")
    void admin_ausgenommen() {
        Player admin = fixture.player("Admin", true);
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        BlockPlaceEvent event = placeEvent(admin, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("Neue Kiste zeigt mit RIGHT von der fremden Einzelkiste weg: nicht abgebrochen")
    void neueKisteZeigtWeg_nichtAbgebrochen() {
        world.setChest(-2, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        BlockPlaceEvent event = placeEvent(bob, placed);

        listener.onChestPlaceAdjacentToForeignLock(event);

        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("Auto-Lock, neue Kiste schon LEFT: keine zweite Sperre")
    void autoLock_neueKisteLeft_keineZweiteSperre() {
        enableAutoLock();
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);

        listener.onBlockPlaceAutoLock(placeEvent(alice, placed));

        assertEquals(List.of(world.keyAt(0, 64, 0)),
                service.getAllCachedLocks().stream().map(LockEntry::key).toList());
        assertEquals(1, service.countLocks(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Auto-Lock, beide Kisten noch SINGLE: keine zweite Sperre")
    void autoLock_beideSingle_keineZweiteSperre() {
        enableAutoLock();
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);

        listener.onBlockPlaceAutoLock(placeEvent(alice, placed));

        assertEquals(List.of(world.keyAt(0, 64, 0)),
                service.getAllCachedLocks().stream().map(LockEntry::key).toList());
        assertEquals(1, service.countLocks(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Auto-Lock ohne gesperrten Nachbarn sperrt die neue Kiste")
    void autoLock_ohneGesperrtenNachbarn_sperrt() {
        enableAutoLock();
        Block placed = world.setChest(10, 64, 10, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);

        listener.onBlockPlaceAutoLock(placeEvent(alice, placed));

        assertEquals(2, service.countLocks(alice.getUniqueId()));
        assertTrue(service.isLocked(placed));
    }
}
