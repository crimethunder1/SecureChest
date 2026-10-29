package de.SecureChest.plugin.lock;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LockUtil: sperrbare Blocktypen")
class LockUtilLockableTypesTest {

    private static Block blockOf(Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        return block;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Neue Typen sind sperrbar")
    void newTypesAreLockable(Material material) {
        assertTrue(LockUtil.isLockableType(material));
        assertTrue(LockUtil.isLockable(blockOf(material)));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "CHEST", "TRAPPED_CHEST", "BARREL", "HOPPER", "SHULKER_BOX", "WHITE_SHULKER_BOX", "PURPLE_SHULKER_BOX",
            "BLACK_SHULKER_BOX"})
    @DisplayName("Bisherige Typen bleiben sperrbar")
    void existingTypesStayLockable(Material material) {
        assertTrue(LockUtil.isLockableType(material));
        assertTrue(LockUtil.isLockable(blockOf(material)));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "ENDER_CHEST", "DECORATED_POT", "BOOKSHELF", "CHISELED_BOOKSHELF", "LECTERN", "JUKEBOX",
            "CRAFTING_TABLE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "SMITHING_TABLE", "ENCHANTING_TABLE",
            "CAMPFIRE", "CAULDRON", "BEACON", "STONE", "AIR", "FURNACE_MINECART", "HOPPER_MINECART", "CHEST_MINECART"})
    @DisplayName("Ausgeschlossene Typen und Nicht-Container sind nicht sperrbar")
    void excludedTypesAreNotLockable(Material material) {
        assertFalse(LockUtil.isLockableType(material));
        assertFalse(LockUtil.isLockable(blockOf(material)));
    }

    @Test
    @DisplayName("Kein Block und kein Material sind nicht sperrbar")
    void nullIsNotLockable() {
        assertFalse(LockUtil.isLockable(null));
        assertFalse(LockUtil.isLockableType(null));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "FURNACE", "BLAST_FURNACE", "SMOKER", "DROPPER", "DISPENSER", "BREWING_STAND", "CRAFTER"})
    @DisplayName("Neue Typen haben keinen Doppelkisten-Partner und nur ihre eigene Position als Schluessel")
    void newTypesAreSingleBlocks(Material material) {
        DoubleChestTestWorld world = new DoubleChestTestWorld();
        Block block = world.setBlock(0, 64, 0, material);
        world.setBlock(1, 64, 0, material);
        world.setBlock(-1, 64, 0, material);

        assertNull(LockUtil.findDoubleChestPartner(block));
        assertNull(LockUtil.doubleChestPartnerFace(block));
        assertSame(block, LockUtil.canonicalBlock(block));
        assertEquals(List.of(world.keyAt(0, 64, 0)), LockUtil.storedKeyCandidates(block));
        assertFalse(LockUtil.wouldFormDoubleChestWith(block, world.blockAt(1, 64, 0), BlockFace.EAST));
    }
}
