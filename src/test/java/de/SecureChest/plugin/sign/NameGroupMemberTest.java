package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameGroupMember: Mitglieder einer Namensgruppe")
class NameGroupMemberTest {

    private final UUID worldUuid = new UUID(0L, 1L);
    private final UUID owner = new UUID(0L, 10L);

    private LockKey at(int x, int y, int z) {
        return new LockKey(worldUuid, x, y, z);
    }

    @Test
    @DisplayName("Gesperrter Block, Doppelkiste und Secure Hopper belegen ein oder zwei Felder")
    void factories() {
        NameGroupMember block = NameGroupMember.lockedBlock(at(0, 64, 0), owner);
        NameGroupMember chest = NameGroupMember.doubleChest(at(0, 64, 0), at(0, 64, -1), owner);
        NameGroupMember hopper = NameGroupMember.secureHopper(at(3, 64, 0), owner);

        assertEquals(List.of(at(0, 64, 0)), block.fields());
        assertTrue(block.isLockedBlock());
        assertEquals(List.of(at(0, 64, 0), at(0, 64, -1)), chest.fields());
        assertTrue(chest.isLockedBlock());
        assertEquals(NameGroupMember.Kind.SECURE_HOPPER, hopper.kind());
        assertFalse(hopper.isLockedBlock());
        assertEquals(at(0, 64, -1), chest.smallestField());
    }

    @Test
    @DisplayName("Zweites Feld muss seitlich angrenzen, auf gleicher Hoehe und in gleicher Welt")
    void partnerMustTouchAtSide() {
        LockKey otherWorld = new LockKey(new UUID(0L, 2L), 1, 64, 0);

        assertThrows(IllegalArgumentException.class, () -> NameGroupMember.doubleChest(at(0, 64, 0), at(1, 64, 1), owner));
        assertThrows(IllegalArgumentException.class, () -> NameGroupMember.doubleChest(at(0, 64, 0), at(2, 64, 0), owner));
        assertThrows(IllegalArgumentException.class, () -> NameGroupMember.doubleChest(at(0, 64, 0), at(0, 65, 0), owner));
        assertThrows(IllegalArgumentException.class, () -> NameGroupMember.doubleChest(at(0, 64, 0), otherWorld, owner));
        assertThrows(IllegalArgumentException.class, () -> NameGroupMember.doubleChest(at(0, 64, 0), at(0, 64, 0), owner));
    }

    @Test
    @DisplayName("Ungueltige Felder: leer, mehr als zwei, erstes Feld nicht die Position, Secure Hopper mit zwei Feldern")
    void invalidFields() {
        LockKey position = at(0, 64, 0);

        assertThrows(IllegalArgumentException.class, () ->
                new NameGroupMember(position, owner, NameGroupMember.Kind.LOCKED_BLOCK, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new NameGroupMember(position, owner,
                NameGroupMember.Kind.LOCKED_BLOCK, List.of(position, at(1, 64, 0), at(2, 64, 0))));
        assertThrows(IllegalArgumentException.class, () ->
                new NameGroupMember(position, owner, NameGroupMember.Kind.LOCKED_BLOCK, List.of(at(1, 64, 0))));
        assertThrows(IllegalArgumentException.class, () -> new NameGroupMember(position, owner,
                NameGroupMember.Kind.SECURE_HOPPER, List.of(position, at(1, 64, 0))));
        assertThrows(NullPointerException.class, () -> NameGroupMember.lockedBlock(position, null));
    }
}
