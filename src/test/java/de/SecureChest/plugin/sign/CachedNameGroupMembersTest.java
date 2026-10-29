package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockCacheManager;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CachedNameGroupMembers: Mitglieder aus Cache und Chunk-Index, Doppelkisten aus dem Blockzustand")
class CachedNameGroupMembersTest {

    private final UUID worldUuid = new UUID(0L, 1L);
    private final UUID alice = new UUID(0L, 10L);
    private final UUID bob = new UUID(0L, 20L);

    private LockCacheManager cache;
    private LockService locks;
    private Map<LockKey, LockKey> partners;
    private int partnerQueries;
    private CachedNameGroupMembers members;

    @BeforeEach
    void setUp() {
        cache = new LockCacheManager();
        locks = mock(LockService.class);
        when(locks.getCachedLocksInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.locksInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(locks.getCachedHoppersInChunk(any(), anyInt(), anyInt())).thenAnswer(invocation ->
                cache.hoppersInChunk(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        partners = new HashMap<>();
        members = new CachedNameGroupMembers(locks, lock -> {
            partnerQueries++;
            return partners.get(lock.key());
        });
    }

    private LockKey at(int x, int z) {
        return new LockKey(worldUuid, x, 64, z);
    }

    private void lock(int x, int z, UUID owner) {
        cache.putLock(at(x, z).asId(), new LockEntry(at(x, z), Material.CHEST, owner, 0L));
    }

    private void hopper(int x, int z, UUID owner) {
        cache.putHopper(at(x, z).asId(), new SecureHopperEntry(at(x, z), owner, at(x, z - 1)));
    }

    @Test
    @DisplayName("Gesperrter Block und Secure Hopper werden mit Besitzer und einem Feld gefunden")
    void lockAndHopper() {
        lock(0, 0, alice);
        hopper(1, 0, bob);

        assertEquals(NameGroupMember.lockedBlock(at(0, 0), alice), members.memberAt(at(0, 0)));
        assertEquals(NameGroupMember.secureHopper(at(1, 0), bob), members.memberAt(at(1, 0)));
        assertNull(members.memberAt(at(2, 0)));
    }

    @Test
    @DisplayName("Sperre und Secure Hopper an derselben Stelle: die Sperre zaehlt")
    void lockWinsOverHopper() {
        lock(0, 0, alice);
        hopper(0, 0, alice);

        assertEquals(NameGroupMember.Kind.LOCKED_BLOCK, members.memberAt(at(0, 0)).kind());
    }

    @Test
    @DisplayName("Doppelkiste: die zweite Haelfte ohne eigenen Eintrag gehoert zum selben Mitglied")
    void doubleChestPartnerField() {
        lock(4, 4, alice);
        partners.put(at(4, 4), at(4, 5));

        NameGroupMember member = members.memberAt(at(4, 5));

        assertEquals(List.of(at(4, 4), at(4, 5)), member.fields());
        assertSame(member, members.memberAt(at(4, 4)));
    }

    @Test
    @DisplayName("Ohne Blockzustand (Chunk nicht geladen) gilt ein Feld")
    void withoutBlockStateOneField() {
        lock(4, 4, alice);

        assertEquals(List.of(at(4, 4)), members.memberAt(at(4, 4)).fields());
        assertNull(members.memberAt(at(4, 5)));
    }

    @Test
    @DisplayName("Partnerfeld mit eigenem Eintrag: beide bleiben einzelne Mitglieder")
    void partnerWithOwnEntryStaysSingle() {
        lock(0, 0, alice);
        lock(1, 0, alice);
        partners.put(at(0, 0), at(1, 0));
        partners.put(at(1, 0), at(0, 0));

        assertEquals(List.of(at(0, 0)), members.memberAt(at(0, 0)).fields());
        assertEquals(List.of(at(1, 0)), members.memberAt(at(1, 0)).fields());
    }

    @Test
    @DisplayName("Partner, der nicht seitlich angrenzt, wird ignoriert")
    void partnerNotAdjacentIsIgnored() {
        lock(0, 0, alice);
        partners.put(at(0, 0), at(5, 5));

        assertEquals(List.of(at(0, 0)), members.memberAt(at(0, 0)).fields());
    }

    @Test
    @DisplayName("Doppelkiste ueber eine Chunk-Grenze: die Haelfte im Nachbarchunk wird gefunden")
    void doubleChestAcrossChunkBorder() {
        lock(15, 3, alice);
        partners.put(at(15, 3), at(16, 3));

        NameGroupMember member = members.memberAt(at(16, 3));

        assertNotNull(member);
        assertEquals(List.of(at(15, 3), at(16, 3)), member.fields());
        verify(locks).getCachedLocksInChunk(worldUuid, 0, 0);
        verify(locks).getCachedLocksInChunk(worldUuid, 1, 0);
    }

    @Test
    @DisplayName("Jeder Chunk wird pro Berechnung nur einmal aus dem Index gelesen, der Blockzustand einmal pro Sperre")
    void chunkReadOnce() {
        lock(2, 2, alice);
        lock(3, 2, alice);

        for (int round = 0; round < 3; round++) {
            members.memberAt(at(2, 2));
            members.memberAt(at(3, 2));
            members.memberAt(at(8, 8));
        }

        verify(locks, times(1)).getCachedLocksInChunk(worldUuid, 0, 0);
        verify(locks, times(1)).getCachedHoppersInChunk(worldUuid, 0, 0);
        verify(locks, times(1)).getCachedLocksInChunk(any(), anyInt(), anyInt());
        assertEquals(2, partnerQueries);
    }

    @Test
    @DisplayName("Secure Hopper fragen keinen Blockzustand ab")
    void hopperNeedsNoBlockState() {
        hopper(0, 0, alice);

        members.memberAt(at(0, 0));

        assertEquals(0, partnerQueries);
    }
}
