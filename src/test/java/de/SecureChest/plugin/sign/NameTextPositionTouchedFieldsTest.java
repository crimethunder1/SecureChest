package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NameTextPosition und TextFieldCover: alle Felder, die der Mittelpunkt des Textes beruehrt")
class NameTextPositionTouchedFieldsTest {

    private static final int Y = 64;
    private final UUID worldUuid = new UUID(0L, 1L);

    private LockKey at(int x, int z) {
        return new LockKey(worldUuid, x, Y, z);
    }

    private LockKey above(int x, int z) {
        return new LockKey(worldUuid, x, Y + 1, z);
    }

    private Set<LockKey> touched(LockKey... fields) {
        return Set.copyOf(NameTextPosition.above(List.of(fields)).touchedTextFields());
    }

    private List<LockKey> rectangle(int fromX, int fromZ, int sizeX, int sizeZ) {
        List<LockKey> fields = new ArrayList<>();
        for (int x = fromX; x < fromX + sizeX; x++) {
            for (int z = fromZ; z < fromZ + sizeZ; z++) fields.add(at(x, z));
        }
        return fields;
    }

    @Test
    @DisplayName("Ein Feld: genau das Feld darueber, wie das bisherige Textfeld")
    void singleField() {
        NameTextPosition position = NameTextPosition.above(List.of(at(5, 7)));

        assertEquals(List.of(above(5, 7)), position.touchedTextFields());
        assertEquals(position.textField(), position.touchedTextFields().getFirst());
    }

    @Test
    @DisplayName("Doppelkiste in x: beide Felder ueber den Haelften")
    void doubleChestAlongX() {
        assertEquals(Set.of(above(0, 0), above(1, 0)), touched(at(0, 0), at(1, 0)));
    }

    @Test
    @DisplayName("Doppelkiste in z: beide Felder ueber den Haelften")
    void doubleChestAlongZ() {
        assertEquals(Set.of(above(3, 4), above(3, 5)), touched(at(3, 4), at(3, 5)));
    }

    @Test
    @DisplayName("2x2: der Mittelpunkt liegt auf einer Ecke und beruehrt vier Felder")
    void twoByTwo() {
        NameTextPosition position = NameTextPosition.above(rectangle(0, 0, 2, 2));

        assertEquals(Set.of(above(0, 0), above(1, 0), above(0, 1), above(1, 1)),
                Set.copyOf(position.touchedTextFields()));
        assertEquals(4, position.touchedTextFields().size());
    }

    @Test
    @DisplayName("3x3: Mittelpunkt in der Feldmitte, ein Feld")
    void threeByThree() {
        assertEquals(List.of(above(1, 1)), NameTextPosition.above(rectangle(0, 0, 3, 3)).touchedTextFields());
    }

    @Test
    @DisplayName("1x3 in x und in z: ein Feld ueber dem mittleren Block")
    void oneByThree() {
        assertEquals(List.of(above(1, 0)), NameTextPosition.above(rectangle(0, 0, 3, 1)).touchedTextFields());
        assertEquals(List.of(above(0, 1)), NameTextPosition.above(rectangle(0, 0, 1, 3)).touchedTextFields());
    }

    @Test
    @DisplayName("2x3: zwei Felder in x, eins in z")
    void twoByThree() {
        assertEquals(Set.of(above(0, 1), above(1, 1)), Set.copyOf(
                NameTextPosition.above(rectangle(0, 0, 2, 3)).touchedTextFields()));
    }

    @Test
    @DisplayName("Negative Koordinaten: Doppelkiste bei -2 und -1, 2x2 um den Nullpunkt")
    void negativeCoordinates() {
        assertEquals(Set.of(above(-2, -5), above(-1, -5)), touched(at(-2, -5), at(-1, -5)));
        assertEquals(Set.of(above(-1, -1), above(0, -1), above(-1, 0), above(0, 0)),
                Set.copyOf(NameTextPosition.above(rectangle(-1, -1, 2, 2)).touchedTextFields()));
    }

    @Test
    @DisplayName("Grenze von Chunks: beruehrte Felder in zwei Chunks, der Chunk des Textes bleibt der des groesseren Feldes")
    void chunkBorder() {
        NameTextPosition alongX = NameTextPosition.above(List.of(at(15, 0), at(16, 0)));
        NameTextPosition acrossZero = NameTextPosition.above(List.of(at(-1, 0), at(0, 0)));

        assertEquals(Set.of(above(15, 0), above(16, 0)), Set.copyOf(alongX.touchedTextFields()));
        assertEquals(1, alongX.chunkX());
        assertEquals(Set.of(above(-1, 0), above(0, 0)), Set.copyOf(acrossZero.touchedTextFields()));
        assertEquals(0, acrossZero.chunkX());
    }

    @Test
    @DisplayName("Die Hoehe der beruehrten Felder ist die Hoehe der Gruppe plus 1")
    void heightOfTouchedFields() {
        for (LockKey field : NameTextPosition.above(rectangle(0, 0, 2, 2)).touchedTextFields()) {
            assertEquals(Y + 1, field.y());
        }
    }

    @Test
    @DisplayName("TextFieldCover: ein festes Feld unter mehreren reicht, ohne festes Feld nicht ueberdeckt")
    void coverOfSeveralFields() {
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        TextFieldCover cover = new TextFieldCover(Set.of(Material.STONE)::contains);
        List<LockKey> fields = List.of(above(0, 0), above(1, 0));

        assertFalse(cover.isCovered(world, fields));
        assertFalse(cover.isCovered(world, List.of()));

        when(world.getBlockAt(0, Y + 1, 0)).thenReturn(stone);
        assertTrue(cover.isCovered(world, fields));
    }

    @Test
    @DisplayName("TextFieldCover: ein Feld in einem nicht geladenen Chunk zaehlt als nicht belegt und wird nicht gelesen")
    void unloadedNeighbourFieldIsNotCovered() {
        World world = mock(World.class);
        when(world.isChunkLoaded(1, 0)).thenReturn(true);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(16, Y + 1, 0)).thenReturn(air);
        TextFieldCover cover = new TextFieldCover(Set.of(Material.STONE)::contains);

        assertFalse(cover.isCovered(world, List.of(above(15, 0), above(16, 0))));
        verify(world, never()).getBlockAt(15, Y + 1, 0);
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
