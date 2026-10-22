package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LockUtil: wouldFormDoubleChestWith in beiden moeglichen Platzierungszustaenden")
class LockUtilPlacementStateTest {

    private Block mockChest(Material material, BlockFace facing, Chest.Type connection) {
        Block block = mock(Block.class);
        Chest data = mock(Chest.class);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(data);
        when(data.getFacing()).thenReturn(facing);
        when(data.getType()).thenReturn(connection);
        return block;
    }

    @Test
    @DisplayName("Neue Kiste LEFT, Nachbar SINGLE auf der Partnerseite: Verbindung erkannt")
    void left_singleNachbarAufPartnerseite_verbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Neue Kiste RIGHT, Nachbar SINGLE auf der Partnerseite: Verbindung erkannt")
    void right_singleNachbarAufPartnerseite_verbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.WEST));
    }

    @Test
    @DisplayName("Achse z: LEFT mit Blickrichtung EAST verbindet nach SOUTH, RIGHT nach NORTH")
    void achseZ_partnerseiteErkannt() {
        Block neighbor = mockChest(Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.SINGLE);
        Block placedLeft = mockChest(Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.LEFT);
        Block placedRight = mockChest(Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.RIGHT);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedLeft, neighbor, BlockFace.SOUTH));
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedRight, neighbor, BlockFace.NORTH));
        assertFalse(LockUtil.wouldFormDoubleChestWith(placedLeft, neighbor, BlockFace.NORTH));
        assertFalse(LockUtil.wouldFormDoubleChestWith(placedRight, neighbor, BlockFace.SOUTH));
    }

    @Test
    @DisplayName("Neue Kiste LEFT, Nachbar SINGLE auf der Gegenseite: keine Verbindung")
    void left_singleNachbarAufGegenseite_nichtVerbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.WEST));
    }

    @Test
    @DisplayName("Neue Kiste LEFT, Nachbar SINGLE mit anderer Blickrichtung: keine Verbindung")
    void left_singleNachbarAndereBlickrichtung_nichtVerbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.SOUTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Neue Kiste LEFT, Nachbar SINGLE aus anderem Material: keine Verbindung")
    void left_singleNachbarAnderesMaterial_nichtVerbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Neue Kiste SINGLE, Nachbar bereits LEFT: keine Verbindung")
    void single_nachbarBereitsVerbunden_nichtVerbunden() {
        Block placed = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Anbau bei kleineren Koordinaten: beide Serverzustaende liefern Verbindung")
    void anbauKleinereKoordinaten_beideZustaende() {
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedStillSingle = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedAlreadyLeft = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedStillSingle, neighbor, BlockFace.EAST));
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedAlreadyLeft, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Anbau bei groesseren Koordinaten: beide Serverzustaende liefern Verbindung")
    void anbauGroessereKoordinaten_beideZustaende() {
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedStillSingle = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedAlreadyRight = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedStillSingle, neighbor, BlockFace.WEST));
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedAlreadyRight, neighbor, BlockFace.WEST));
    }
}
