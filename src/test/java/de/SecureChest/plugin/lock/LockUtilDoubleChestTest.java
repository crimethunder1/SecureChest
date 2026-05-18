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

@DisplayName("LockUtil – Double-Chest-Validierung gegen Chest-Steal")
class LockUtilDoubleChestTest {

    private Block mockChest(Material material, BlockFace facing, Chest.Type connection) {
        Block block = mock(Block.class);
        Chest data = mock(Chest.class);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(data);
        when(data.getFacing()).thenReturn(facing);
        when(data.getType()).thenReturn(connection);
        return block;
    }

    // ── Single+Single (Vanilla wird beim Place mergen) ────────────────────────

    @Test
    @DisplayName("Single+Single, Facing NORTH, Nachbar EAST → Vanilla mergt → als Double erkannt")
    void singleSingle_facingNorth_neighborEast_wirdAlsDouble() {
        Block placed   = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Single+Single, Facing NORTH, Nachbar NORTH → falsche Achse, kein Merge")
    void singleSingle_facingNorth_neighborNorth_falscheAchse() {
        Block placed   = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.NORTH),
                "Chest mit Facing NORTH verbindet sich nur entlang EAST/WEST-Achse");
    }

    @Test
    @DisplayName("Single+Single, Facing EAST, Nachbar NORTH → Vanilla mergt → als Double erkannt")
    void singleSingle_facingEast_neighborNorth_wirdAlsDouble() {
        Block placed   = mockChest(Material.CHEST, BlockFace.EAST, Chest.Type.SINGLE);
        Block neighbor = mockChest(Material.CHEST, BlockFace.EAST, Chest.Type.SINGLE);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.NORTH));
    }

    @Test
    @DisplayName("Single+Single mit unterschiedlichem Facing → kein Merge")
    void singleSingle_unterschiedlichesFacing_keinMerge() {
        Block placed   = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block neighbor = mockChest(Material.CHEST, BlockFace.SOUTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    // ── Bereits gemerged (LEFT/RIGHT) ─────────────────────────────────────────

    @Test
    @DisplayName("Placed LEFT trifft Neighbor RIGHT auf korrekter Achse → Double Chest")
    void leftPlusRight_korrekteAchse_istDouble() {
        Block placed   = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Beide LEFT → keine gueltige Double Chest")
    void beideGleicheSeite_nichtVerbunden() {
        Block placed   = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    // ── Material-Mismatch ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Verschiedene Materials (CHEST vs TRAPPED_CHEST) → nicht verbunden")
    void verschiedeneMaterials_nichtVerbunden() {
        Block placed   = mockChest(Material.CHEST,         BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = mockChest(Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    @Test
    @DisplayName("Nicht-Chest-Material → niemals Double")
    void nichtChest_nichtVerbunden() {
        Block placed   = mock(Block.class);
        Block neighbor = mock(Block.class);
        when(placed.getType()).thenReturn(Material.BARREL);
        when(neighbor.getType()).thenReturn(Material.BARREL);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placed, neighbor, BlockFace.EAST));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lateralFaces() liefert genau N/S/E/W")
    void lateralFaces_korrekt() {
        BlockFace[] faces = LockUtil.lateralFaces();
        assertEquals(4, faces.length);
        assertTrue(java.util.Arrays.asList(faces).containsAll(
                java.util.List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)));
    }
}
