package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockUtil: Partner-Ermittlung, kanonischer Schluessel und Schluessel-Kandidaten")
class LockUtilChestPartnerTest {

    private final DoubleChestTestWorld world = new DoubleChestTestWorld();

    private static Chest.Type opposite(Chest.Type type) {
        return type == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
    }

    @ParameterizedTest(name = "{0} {1}: Partner bei dx={2}, dz={3}")
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
    @DisplayName("Partner liegt auf der Seite, die sich aus Typ und Blickrichtung ergibt")
    void partner_folgtAusTypUndBlickrichtung(BlockFace facing, Chest.Type type, int deltaX, int deltaZ) {
        Block chest = world.setChest(10, 64, 10, Material.CHEST, facing, type);
        Block expectedPartner = world.setChest(10 + deltaX, 64, 10 + deltaZ, Material.CHEST, facing, opposite(type));
        assertSame(expectedPartner, LockUtil.findDoubleChestPartner(chest));
        assertSame(chest, LockUtil.findDoubleChestPartner(expectedPartner));
    }

    @ParameterizedTest(name = "{0} {1}: Kiste auf der Gegenseite wird ignoriert")
    @CsvSource({
            "NORTH, LEFT,  -1,  0",
            "NORTH, RIGHT,  1,  0",
            "EAST,  LEFT,   0, -1",
            "EAST,  RIGHT,  0,  1"
    })
    @DisplayName("Kiste mit passendem Typ auf der falschen Seite ist kein Partner")
    void kisteAufFalscherSeite_istKeinPartner(BlockFace facing, Chest.Type type, int deltaX, int deltaZ) {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, facing, type);
        world.setChest(deltaX, 64, deltaZ, Material.CHEST, facing, opposite(type));
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Einzelkiste hat keinen Partner")
    void einzelkiste_hatKeinenPartner() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Nachbar mit anderer Blickrichtung ist kein Partner")
    void andereBlickrichtung_istKeinPartner() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Nachbar aus anderem Material ist kein Partner")
    void anderesMaterial_istKeinPartner() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Nachbar mit gleichem Typ ist kein Partner")
    void gleicherTyp_istKeinPartner() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Platzierungszustand: neue Kiste LEFT, Nachbar noch SINGLE, Nachbar ist Partner")
    void platzierungszustand_singleNachbarIstPartner() {
        Block placed = world.setChest(-1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block neighbor = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertSame(neighbor, LockUtil.findDoubleChestPartner(placed));
        assertNull(LockUtil.findDoubleChestPartner(neighbor));
    }

    @Test
    @DisplayName("Fass hat keinen Partner")
    void keineKiste_hatKeinenPartner() {
        Block barrel = world.setBlock(0, 64, 0, Material.BARREL);
        assertNull(LockUtil.findDoubleChestPartner(barrel));
    }

    @Test
    @DisplayName("Kistenwand Blickrichtung NORTH: jede Haelfte findet ihren eigenen Partner")
    void kistenwandNorth_richtigePartner() {
        Block firstLeft = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block firstRight = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        Block secondLeft = world.setChest(2, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block secondRight = world.setChest(3, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertSame(firstLeft, LockUtil.findDoubleChestPartner(firstRight));
        assertSame(secondRight, LockUtil.findDoubleChestPartner(secondLeft));
        assertEquals(world.keyAt(0, 64, 0), LockUtil.canonicalKey(firstLeft));
        assertEquals(world.keyAt(0, 64, 0), LockUtil.canonicalKey(firstRight));
        assertEquals(world.keyAt(2, 64, 0), LockUtil.canonicalKey(secondLeft));
        assertEquals(world.keyAt(2, 64, 0), LockUtil.canonicalKey(secondRight));
    }

    @Test
    @DisplayName("Kistenwand Blickrichtung EAST: Schluessel bleibt in der eigenen Doppelkiste")
    void kistenwandEast_schluesselBleibtInEigenerDoppelkiste() {
        Block firstLeft = world.setChest(5, 64, -2, Material.CHEST, BlockFace.EAST, Chest.Type.LEFT);
        Block firstRight = world.setChest(5, 64, -1, Material.CHEST, BlockFace.EAST, Chest.Type.RIGHT);
        Block secondLeft = world.setChest(5, 64, 0, Material.CHEST, BlockFace.EAST, Chest.Type.LEFT);
        Block secondRight = world.setChest(5, 64, 1, Material.CHEST, BlockFace.EAST, Chest.Type.RIGHT);

        assertEquals(world.keyAt(5, 64, -2), LockUtil.canonicalKey(firstLeft));
        assertEquals(world.keyAt(5, 64, -2), LockUtil.canonicalKey(firstRight));
        assertEquals(world.keyAt(5, 64, 0), LockUtil.canonicalKey(secondLeft));
        assertEquals(world.keyAt(5, 64, 0), LockUtil.canonicalKey(secondRight));
    }

    @Test
    @DisplayName("canonicalBlock Achse x: kleinere x-Koordinate gewinnt")
    void canonicalBlock_achseX() {
        Block left = world.setChest(4, 70, 9, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block right = world.setChest(5, 70, 9, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertSame(left, LockUtil.canonicalBlock(left));
        assertSame(left, LockUtil.canonicalBlock(right));
    }

    @Test
    @DisplayName("canonicalBlock Achse z: kleinere z-Koordinate gewinnt")
    void canonicalBlock_achseZ() {
        Block left = world.setChest(3, 70, 7, Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.LEFT);
        Block right = world.setChest(3, 70, 8, Material.TRAPPED_CHEST, BlockFace.EAST, Chest.Type.RIGHT);
        assertSame(left, LockUtil.canonicalBlock(left));
        assertSame(left, LockUtil.canonicalBlock(right));
        assertEquals(world.keyAt(3, 70, 7), LockUtil.canonicalKey(right));
    }

    @Test
    @DisplayName("canonicalKey mit negativen x-Koordinaten")
    void canonicalKey_negativesX() {
        Block right = world.setChest(-4, 64, -20, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        Block left = world.setChest(-3, 64, -20, Material.CHEST, BlockFace.SOUTH, Chest.Type.LEFT);
        assertEquals(world.keyAt(-4, 64, -20), LockUtil.canonicalKey(left));
        assertEquals(world.keyAt(-4, 64, -20), LockUtil.canonicalKey(right));
    }

    @Test
    @DisplayName("canonicalKey mit negativen z-Koordinaten")
    void canonicalKey_negativesZ() {
        Block left = world.setChest(-7, -30, -10, Material.CHEST, BlockFace.WEST, Chest.Type.LEFT);
        Block right = world.setChest(-7, -30, -11, Material.CHEST, BlockFace.WEST, Chest.Type.RIGHT);
        assertEquals(world.keyAt(-7, -30, -11), LockUtil.canonicalKey(left));
        assertEquals(world.keyAt(-7, -30, -11), LockUtil.canonicalKey(right));
    }

    @Test
    @DisplayName("canonicalKey einer Einzelkiste und eines Fasses ist die eigene Position")
    void canonicalKey_ohnePartner_eigenePosition() {
        Block chest = world.setChest(1, 64, 1, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        Block barrel = world.setBlock(0, 64, 1, Material.BARREL);
        assertEquals(world.keyAt(1, 64, 1), LockUtil.canonicalKey(chest));
        assertEquals(world.keyAt(0, 64, 1), LockUtil.canonicalKey(barrel));
    }

    @Test
    @DisplayName("Kandidaten einer Einzelkiste: nur die eigene Position")
    void kandidaten_einzelkiste() {
        Block chest = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertEquals(List.of(world.keyAt(0, 64, 0)), LockUtil.storedKeyCandidates(chest));
    }

    @Test
    @DisplayName("Kandidaten der kanonischen Haelfte: eigene Position, dann Partner")
    void kandidaten_kanonischeHaelfte() {
        Block left = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertEquals(List.of(world.keyAt(0, 64, 0), world.keyAt(1, 64, 0)), LockUtil.storedKeyCandidates(left));
    }

    @Test
    @DisplayName("Kandidaten der anderen Haelfte: kanonischer Schluessel, dann eigene Position")
    void kandidaten_andereHaelfte() {
        world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block right = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertEquals(List.of(world.keyAt(0, 64, 0), world.keyAt(1, 64, 0)), LockUtil.storedKeyCandidates(right));
    }

    @Test
    @DisplayName("Kandidaten eines Fasses: nur die eigene Position")
    void kandidaten_fass() {
        Block barrel = world.setBlock(0, 64, 0, Material.BARREL);
        assertEquals(List.of(world.keyAt(0, 64, 0)), LockUtil.storedKeyCandidates(barrel));
    }
}
