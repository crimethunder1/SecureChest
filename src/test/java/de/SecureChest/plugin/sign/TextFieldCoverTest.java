package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Textfeld eines Namenstextes und Erkennung fest belegt")
class TextFieldCoverTest {

    private static final Set<Material> SOLID_ON_SERVER =
            Set.of(Material.STONE, Material.CHEST, Material.SMOOTH_STONE_SLAB, Material.GLASS);

    private final UUID worldUuid = new UUID(0L, 1L);
    private World world;
    private TextFieldCover cover;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldUuid);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        cover = new TextFieldCover(SOLID_ON_SERVER::contains);
    }

    private LockKey at(int x, int y, int z) {
        return new LockKey(worldUuid, x, y, z);
    }

    private LockKey textFieldAbove(LockKey... fields) {
        return NameTextPosition.above(List.of(fields)).textField();
    }

    private Block blockAt(LockKey position, Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(world.getBlockAt(position.x(), position.y(), position.z())).thenReturn(block);
        return block;
    }

    @Test
    @DisplayName("Ein Feld mit positiven Koordinaten: Textfeld direkt ueber dem Block")
    void singleFieldPositive() {
        assertEquals(at(5, 65, 7), textFieldAbove(at(5, 64, 7)));
    }

    @Test
    @DisplayName("Negative Koordinaten: floor statt Abschneiden, auch fuer die Hoehe")
    void negativeCoordinates() {
        assertEquals(at(-1, -19, -16), textFieldAbove(at(-1, -20, -16)));
        assertEquals(at(-1, -19, -16), textFieldAbove(at(-1, -20, -16), at(-2, -20, -16)));
        assertEquals(at(-17, 65, -1), textFieldAbove(at(-17, 64, -1)));
    }

    @Test
    @DisplayName("Weltgrenzen in der Hoehe: unterste und oberste Ebene")
    void heightLimits() {
        assertEquals(at(0, -63, 0), textFieldAbove(at(0, -64, 0)));
        assertEquals(at(0, 320, 0), textFieldAbove(at(0, 319, 0)));
    }

    @Test
    @DisplayName("Doppelkiste: Mittelpunkt auf der Grenze der Haelften, Textfeld ist das Feld ueber der groesseren Haelfte")
    void doubleChest() {
        assertEquals(at(1, 65, 0), textFieldAbove(at(0, 64, 0), at(1, 64, 0)));
        assertEquals(at(3, 65, 5), textFieldAbove(at(3, 64, 4), at(3, 64, 5)));
        assertEquals(at(-1, 65, 0), textFieldAbove(at(-2, 64, 0), at(-1, 64, 0)));
        assertEquals(at(0, 65, -1), textFieldAbove(at(0, 64, -2), at(0, 64, -1)));
    }

    @Test
    @DisplayName("Das Textfeld liegt im Chunk des Textes")
    void textFieldInChunkOfText() {
        List<List<LockKey>> groups = List.of(
                List.of(at(15, 64, 0), at(16, 64, 0)),
                List.of(at(-1, 64, -1)),
                List.of(at(-17, 64, 3), at(-16, 64, 3)),
                List.of(at(0, 64, 31), at(0, 64, 32)));
        for (List<LockKey> fields : groups) {
            NameTextPosition position = NameTextPosition.above(fields);
            LockKey textField = position.textField();
            assertEquals(position.chunkX(), textField.x() >> 4, fields.toString());
            assertEquals(position.chunkZ(), textField.z() >> 4, fields.toString());
        }
    }

    @ParameterizedTest(name = "{0}: fest belegt {1}")
    @CsvSource({
            "AIR,               false",
            "STONE,             true",
            "CHEST,             true",
            "SMOOTH_STONE_SLAB, true",
            "WATER,             false",
            "GLASS,             true",
            "SHORT_GRASS,       false",
            "TORCH,             false"
    })
    @DisplayName("Erkennung mit gemockten Bloecken im Textfeld")
    void coverByMaterial(Material material, boolean expected) {
        LockKey textField = at(5, 65, 7);
        blockAt(textField, material);

        assertEquals(expected, cover.isCovered(world, textField));
        verify(world).getBlockAt(5, 65, 7);
    }

    @Test
    @DisplayName("Nicht geladener Chunk: nicht belegt, kein Blockzugriff, kein Laden")
    void unloadedChunkIsNotCovered() {
        LockKey textField = at(-1, 65, -17);
        when(world.isChunkLoaded(-1, -2)).thenReturn(false);
        blockAt(textField, Material.STONE);

        assertFalse(cover.isLoaded(world, textField));
        assertFalse(cover.isCovered(world, textField));
        verify(world, times(2)).isChunkLoaded(-1, -2);
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Welt nicht geladen: nicht belegt")
    void missingWorldIsNotCovered() {
        LockKey textField = at(0, 65, 0);

        assertFalse(cover.isLoaded(null, textField));
        assertFalse(cover.isCovered(null, textField));
    }
}
