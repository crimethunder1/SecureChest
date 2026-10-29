package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LockUtil: Kupferkisten in der Kistenfamilie, Partner-Suche und Mischfaelle")
class LockUtilCopperChestTest {

    static final List<Material> COPPER_CHESTS = List.of(
            Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST, Material.WEATHERED_COPPER_CHEST,
            Material.OXIDIZED_COPPER_CHEST, Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
            Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST);

    private final DoubleChestTestWorld world = new DoubleChestTestWorld();

    static List<Arguments> copperPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (Material first : COPPER_CHESTS) {
            for (Material second : COPPER_CHESTS) pairs.add(Arguments.of(first, second));
        }
        return pairs;
    }

    static List<Arguments> copperWithOtherChest() {
        List<Arguments> pairs = new ArrayList<>();
        for (Material copper : COPPER_CHESTS) {
            pairs.add(Arguments.of(copper, Material.CHEST));
            pairs.add(Arguments.of(copper, Material.TRAPPED_CHEST));
        }
        return pairs;
    }

    private static Chest.Type opposite(Chest.Type type) {
        return type == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Jede Kupferkiste ist sperrbar und gehoert zur Kistenfamilie")
    void copperChestIsLockableAndInChestFamily(Material type) {
        assertTrue(LockUtil.isLockableType(type));
        assertTrue(LockUtil.isChestFamily(type));
        assertTrue(LockUtil.isLockable(world.setBlock(0, 64, 0, type)));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"CHEST", "TRAPPED_CHEST"})
    @DisplayName("Kiste und Redstone-Kiste gehoeren weiter zur Kistenfamilie")
    void chestAndTrappedChestAreInChestFamily(Material type) {
        assertTrue(LockUtil.isChestFamily(type));
        assertTrue(LockUtil.isLockableType(type));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "ENDER_CHEST", "BARREL", "HOPPER", "SHULKER_BOX", "FURNACE", "CRAFTER", "CHEST_MINECART",
            "COPPER_BLOCK", "WAXED_COPPER_BLOCK", "OXIDIZED_COPPER", "COPPER_BULB", "COPPER_GOLEM_STATUE",
            "COPPER_DOOR", "COPPER_GRATE", "STONE", "AIR"})
    @DisplayName("Endertruhe, andere Behaelter und Kupferbloecke ohne Inventar gehoeren nicht zur Kistenfamilie")
    void otherTypesAreNotInChestFamily(Material type) {
        assertFalse(LockUtil.isChestFamily(type));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "ENDER_CHEST", "CHEST_MINECART", "COPPER_BLOCK", "WAXED_COPPER_BLOCK", "OXIDIZED_COPPER",
            "COPPER_BULB", "COPPER_GOLEM_STATUE", "COPPER_DOOR", "COPPER_GRATE", "STONE", "AIR"})
    @DisplayName("Endertruhe und Nicht-Container bleiben nicht sperrbar")
    void nonContainersAreNotLockable(Material type) {
        assertFalse(LockUtil.isLockableType(type));
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("null ist weder sperrbar noch Teil der Kistenfamilie")
    void nullIsNothing(Material type) {
        assertFalse(LockUtil.isChestFamily(type));
        assertFalse(LockUtil.isLockableType(type));
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
    @DisplayName("Doppelkiste aus zwei Kupferkisten: Partner, Seite, kanonischer Schluessel und Kandidaten")
    void copperDoubleChestInAllDirections(BlockFace facing, Chest.Type type, int deltaX, int deltaZ) {
        Block chest = world.setChest(10, 64, 10, Material.COPPER_CHEST, facing, type);
        Block partner = world.setChest(10 + deltaX, 64, 10 + deltaZ, Material.COPPER_CHEST, facing, opposite(type));

        assertSame(partner, LockUtil.findDoubleChestPartner(chest));
        assertSame(chest, LockUtil.findDoubleChestPartner(partner));
        assertEquals(deltaX, LockUtil.doubleChestPartnerFace(chest).getModX());
        assertEquals(deltaZ, LockUtil.doubleChestPartnerFace(chest).getModZ());

        LockKey expectedCanonical = deltaX + deltaZ < 0 ? world.keyAt(10 + deltaX, 64, 10 + deltaZ) : world.keyAt(10, 64, 10);
        assertEquals(expectedCanonical, LockUtil.canonicalKey(chest));
        assertEquals(expectedCanonical, LockUtil.canonicalKey(partner));
        assertEquals(expectedCanonical, LockUtil.storedKeyCandidates(chest).get(0));
        assertEquals(expectedCanonical, LockUtil.storedKeyCandidates(partner).get(0));
        assertEquals(2, LockUtil.storedKeyCandidates(chest).size());
    }

    @ParameterizedTest(name = "{0} neben {1}")
    @MethodSource("copperPairs")
    @DisplayName("Mischfall Oxidationsstufen: zwei Kupferkisten mit passenden Blockzustaenden sind Partner, auch bei verschiedener Stufe")
    void copperChestsOfAnyStagePair(Material first, Material second) {
        Block left = world.setChest(0, 64, 0, first, BlockFace.NORTH, Chest.Type.LEFT);
        Block right = world.setChest(1, 64, 0, second, BlockFace.NORTH, Chest.Type.RIGHT);

        assertSame(right, LockUtil.findDoubleChestPartner(left));
        assertSame(left, LockUtil.findDoubleChestPartner(right));
        assertEquals(world.keyAt(0, 64, 0), LockUtil.canonicalKey(right));
    }

    @ParameterizedTest(name = "{0} neben {1}")
    @MethodSource("copperWithOtherChest")
    @DisplayName("Mischfall Kupferkiste neben Kiste oder Redstone-Kiste: kein Partner, auch bei passenden Blockzustaenden")
    void copperChestNextToOtherChestIsNoPartner(Material copper, Material other) {
        Block left = world.setChest(0, 64, 0, copper, BlockFace.NORTH, Chest.Type.LEFT);
        Block right = world.setChest(1, 64, 0, other, BlockFace.NORTH, Chest.Type.RIGHT);

        assertNull(LockUtil.findDoubleChestPartner(left));
        assertNull(LockUtil.findDoubleChestPartner(right));
        assertEquals(world.keyAt(1, 64, 0), LockUtil.canonicalKey(right));
        assertEquals(List.of(world.keyAt(1, 64, 0)), LockUtil.storedKeyCandidates(right));
    }

    @Test
    @DisplayName("Kiste neben Redstone-Kiste bleibt ohne Partner wie bisher")
    void chestNextToTrappedChestStaysWithoutPartner() {
        Block left = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.TRAPPED_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);

        assertNull(LockUtil.findDoubleChestPartner(left));
    }

    @Test
    @DisplayName("Kupferkiste mit anderer Blickrichtung oder gleichem Typ ist kein Partner")
    void copperChestWithWrongStateIsNoPartner() {
        Block chest = world.setChest(0, 64, 0, Material.COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        world.setChest(1, 64, 0, Material.EXPOSED_COPPER_CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        assertNull(LockUtil.findDoubleChestPartner(chest));

        world.setChest(1, 64, 0, Material.EXPOSED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        assertNull(LockUtil.findDoubleChestPartner(chest));
    }

    @Test
    @DisplayName("Einzelne Kupferkiste hat keinen Partner und keine Partnerseite")
    void singleCopperChestHasNoPartner() {
        Block chest = world.setChest(0, 64, 0, Material.WAXED_COPPER_CHEST, BlockFace.EAST, Chest.Type.SINGLE);
        world.setChest(0, 64, 1, Material.WAXED_COPPER_CHEST, BlockFace.EAST, Chest.Type.SINGLE);

        assertNull(LockUtil.findDoubleChestPartner(chest));
        assertNull(LockUtil.doubleChestPartnerFace(chest));
        assertEquals(List.of(world.keyAt(0, 64, 0)), LockUtil.storedKeyCandidates(chest));
    }

    @ParameterizedTest(name = "{0} neben {1}")
    @MethodSource("copperPairs")
    @DisplayName("Platzierung: Kupferkiste neben Kupferkiste jeder Stufe wird als Doppelkiste erkannt")
    void placementOfCopperChestsFormsDoubleChest(Material placedType, Material neighborType) {
        Block neighbor = world.setChest(1, 64, 0, neighborType, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedSingle = world.setChest(0, 64, 0, placedType, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedSingle, neighbor, BlockFace.EAST));

        Block placedLeft = world.setChest(0, 64, 0, placedType, BlockFace.NORTH, Chest.Type.LEFT);
        assertTrue(LockUtil.wouldFormDoubleChestWith(placedLeft, neighbor, BlockFace.EAST));
    }

    @ParameterizedTest(name = "{0} neben {1}")
    @MethodSource("copperWithOtherChest")
    @DisplayName("Platzierung: Kupferkiste neben Kiste oder Redstone-Kiste ist keine Doppelkiste, in beide Richtungen")
    void placementOfCopperChestNextToOtherChestFormsNothing(Material copper, Material other) {
        Block otherChest = world.setChest(1, 64, 0, other, BlockFace.NORTH, Chest.Type.SINGLE);
        Block placedCopper = world.setChest(0, 64, 0, copper, BlockFace.NORTH, Chest.Type.SINGLE);
        assertFalse(LockUtil.wouldFormDoubleChestWith(placedCopper, otherChest, BlockFace.EAST));
        assertFalse(LockUtil.wouldFormDoubleChestWith(otherChest, placedCopper, BlockFace.WEST));
    }
}
