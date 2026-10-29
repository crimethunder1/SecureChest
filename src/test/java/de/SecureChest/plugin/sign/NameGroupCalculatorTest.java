package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameGroupCalculator: Gruppen gesperrter Bloecke fuer die Namenstexte")
class NameGroupCalculatorTest {

    private static final int Y = 64;

    private final UUID worldUuid = new UUID(0L, 1L);
    private final UUID alice = new UUID(0L, 10L);
    private final UUID bob = new UUID(0L, 20L);

    private static final class MemberMap implements NameGroupMemberLookup {
        private final Map<LockKey, NameGroupMember> membersByField = new HashMap<>();
        private int lookups;

        MemberMap add(NameGroupMember member) {
            for (LockKey field : member.fields()) membersByField.put(field, member);
            return this;
        }

        @Override
        public NameGroupMember memberAt(LockKey field) {
            lookups++;
            return membersByField.get(field);
        }

        Set<LockKey> fields() {
            return Set.copyOf(membersByField.keySet());
        }
    }

    private LockKey at(int x, int z) {
        return new LockKey(worldUuid, x, Y, z);
    }

    private NameGroupMember lock(int x, int z) {
        return NameGroupMember.lockedBlock(at(x, z), alice);
    }

    private NameGroupMember hopper(int x, int z) {
        return NameGroupMember.secureHopper(at(x, z), alice);
    }

    private NameGroupMember chest(int x, int z, int partnerX, int partnerZ) {
        return NameGroupMember.doubleChest(at(x, z), at(partnerX, partnerZ), alice);
    }

    private MemberMap area(int fromX, int fromZ, int sizeX, int sizeZ) {
        MemberMap members = new MemberMap();
        for (int x = fromX; x < fromX + sizeX; x++) {
            for (int z = fromZ; z < fromZ + sizeZ; z++) members.add(lock(x, z));
        }
        return members;
    }

    private static Map<String, NameGroup> groupsOf(NameGroupMemberLookup lookup, Collection<LockKey> queryOrder) {
        NameGroupCalculator calculator = new NameGroupCalculator(lookup);
        Map<String, NameGroup> groups = new TreeMap<>();
        for (LockKey field : queryOrder) {
            calculator.groupAt(field).ifPresent(group -> groups.putIfAbsent(group.id(), group));
        }
        return groups;
    }

    private static Map<String, NameGroup> groupsOf(MemberMap members) {
        List<LockKey> fields = new ArrayList<>(members.fields());
        fields.sort(NameGroup.FIELD_ORDER);
        return groupsOf(members, fields);
    }

    private static Map<String, List<LockKey>> fieldsById(Map<String, NameGroup> groups) {
        Map<String, List<LockKey>> result = new TreeMap<>();
        groups.forEach((id, group) -> result.put(id, group.fields()));
        return result;
    }

    private static void assertPartition(Map<String, NameGroup> groups, Set<LockKey> expectedFields) {
        Set<LockKey> seen = new HashSet<>();
        for (NameGroup group : groups.values()) {
            assertTrue(group.fields().size() <= NameGroupCalculator.MAXIMUM_GROUP_FIELDS,
                    "Gruppe " + group.id() + " hat " + group.fields().size() + " Felder");
            for (LockKey field : group.fields()) assertTrue(seen.add(field), "Feld doppelt: " + field.asId());
        }
        assertEquals(expectedFields, seen);
    }

    @Test
    @DisplayName("Ein Block: eine Gruppe mit Kennung aus dem Feld, Besitzer und Text mittig darueber")
    void singleBlock() {
        NameGroup group = new NameGroupCalculator(new MemberMap().add(lock(3, -4))).groupAt(at(3, -4)).orElseThrow();

        assertEquals(at(3, -4).asId(), group.id());
        assertEquals(alice, group.owner());
        assertEquals(List.of(at(3, -4)), group.fields());
        assertEquals(1, group.members().size());
        assertEquals(new NameTextPosition(worldUuid, 3.5, Y + 1.35, -3.5), group.textPosition());
    }

    @Test
    @DisplayName("Zwei Bloecke nebeneinander: eine Gruppe, Text zwischen den Bloecken")
    void twoBlocksSideBySide() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(lock(1, 0)));

        assertEquals(1, groups.size());
        NameGroup group = groups.get(at(0, 0).asId());
        assertEquals(List.of(at(0, 0), at(1, 0)), group.fields());
        assertEquals(1.0, group.textPosition().x(), 0.0);
        assertEquals(0.5, group.textPosition().z(), 0.0);
    }

    @Test
    @DisplayName("2x2: eine Gruppe mit vier Feldern")
    void twoByTwo() {
        Map<String, NameGroup> groups = groupsOf(area(0, 0, 2, 2));

        assertEquals(1, groups.size());
        NameGroup group = groups.values().iterator().next();
        assertEquals(4, group.fields().size());
        assertEquals(1.0, group.textPosition().x(), 0.0);
        assertEquals(1.0, group.textPosition().z(), 0.0);
    }

    @Test
    @DisplayName("3x3: eine Gruppe mit neun Feldern, Text ueber dem mittleren Block")
    void threeByThree() {
        Map<String, NameGroup> groups = groupsOf(area(10, 20, 3, 3));

        assertEquals(1, groups.size());
        NameGroup group = groups.get(at(10, 20).asId());
        assertEquals(9, group.fields().size());
        assertEquals(9, group.members().size());
        assertEquals(new NameTextPosition(worldUuid, 11.5, Y + 1.35, 21.5), group.textPosition());
    }

    @Test
    @DisplayName("L-Form: eine Gruppe, Mittelpunkt ist der Durchschnitt der Feldmitten")
    void lShape() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(lock(1, 0)).add(lock(0, 1)));

        assertEquals(1, groups.size());
        NameGroup group = groups.values().iterator().next();
        assertEquals(3, group.fields().size());
        assertEquals(1.0 / 3 + 0.5, group.textPosition().x(), 1e-9);
        assertEquals(1.0 / 3 + 0.5, group.textPosition().z(), 1e-9);
    }

    @Test
    @DisplayName("Lange L-Form: der Mittelpunkt darf ueber einem leeren Feld liegen")
    void lShapeCenterOverEmptyField() {
        MemberMap members = new MemberMap();
        for (int x = 0; x < 5; x++) members.add(lock(x, 0));
        for (int z = 1; z < 5; z++) members.add(lock(0, z));

        NameGroup group = groupsOf(members).values().iterator().next();

        assertEquals(9, group.fields().size());
        assertEquals(10.0 / 9 + 0.5, group.textPosition().x(), 1e-9);
        assertEquals(10.0 / 9 + 0.5, group.textPosition().z(), 1e-9);
    }

    @Test
    @DisplayName("Luecke: zwei getrennte Gruppen")
    void gap() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(lock(2, 0)));

        assertEquals(Set.of(at(0, 0).asId(), at(2, 0).asId()), groups.keySet());
    }

    @Test
    @DisplayName("Diagonale Nachbarn bilden keine gemeinsame Gruppe")
    void diagonalIsNoGroup() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(lock(1, 1)));

        assertEquals(2, groups.size());
    }

    @Test
    @DisplayName("Verschiedene Besitzer nebeneinander: getrennte Gruppen mit eigenem Besitzer")
    void differentOwnersAreSeparate() {
        MemberMap members = new MemberMap().add(lock(0, 0)).add(NameGroupMember.lockedBlock(at(1, 0), bob));

        Map<String, NameGroup> groups = groupsOf(members);

        assertEquals(alice, groups.get(at(0, 0).asId()).owner());
        assertEquals(bob, groups.get(at(1, 0).asId()).owner());
    }

    @Test
    @DisplayName("Gestapelte Bloecke auf verschiedenen Hoehen: getrennte Gruppen")
    void differentHeightsAreSeparate() {
        LockKey lower = at(0, 0);
        LockKey upper = new LockKey(worldUuid, 0, Y + 1, 0);
        MemberMap members = new MemberMap().add(NameGroupMember.lockedBlock(lower, alice))
                .add(NameGroupMember.lockedBlock(upper, alice));

        Map<String, NameGroup> groups = groupsOf(members, List.of(lower, upper));

        assertEquals(Set.of(lower.asId(), upper.asId()), groups.keySet());
        assertEquals(Y + 1 + 1.35, groups.get(upper.asId()).textPosition().y(), 0.0);
    }

    @Test
    @DisplayName("Gleiche Koordinaten in verschiedenen Welten: getrennte Gruppen")
    void differentWorldsAreSeparate() {
        LockKey first = at(0, 0);
        LockKey second = new LockKey(new UUID(0L, 2L), 1, Y, 0);
        LockKey secondNeighbour = new LockKey(new UUID(0L, 2L), 0, Y, 0);
        MemberMap members = new MemberMap().add(NameGroupMember.lockedBlock(first, alice))
                .add(NameGroupMember.lockedBlock(second, alice))
                .add(NameGroupMember.lockedBlock(secondNeighbour, alice));

        Map<String, NameGroup> groups = groupsOf(members, List.of(first, second));

        assertEquals(2, groups.size());
        assertEquals(List.of(first), groups.get(first.asId()).fields());
        assertEquals(2, groups.get(secondNeighbour.asId()).fields().size());
    }

    @Test
    @DisplayName("Doppelkiste in x-Richtung: ein Mitglied mit zwei Feldern, Text genau zwischen den Haelften")
    void doubleChestAlongX() {
        NameGroup group = new NameGroupCalculator(new MemberMap().add(chest(4, 7, 5, 7))).groupAt(at(5, 7)).orElseThrow();

        assertEquals(List.of(at(4, 7), at(5, 7)), group.fields());
        assertEquals(1, group.members().size());
        assertEquals(5.0, group.textPosition().x(), 0.0);
        assertEquals(7.5, group.textPosition().z(), 0.0);
    }

    @Test
    @DisplayName("Doppelkiste in z-Richtung: Text genau zwischen den Haelften")
    void doubleChestAlongZ() {
        NameGroup group = new NameGroupCalculator(new MemberMap().add(chest(0, 0, 0, 1))).groupAt(at(0, 0)).orElseThrow();

        assertEquals(0.5, group.textPosition().x(), 0.0);
        assertEquals(1.0, group.textPosition().z(), 0.0);
    }

    @Test
    @DisplayName("Doppelkiste unter der groesseren Haelfte gespeichert: Kennung bleibt das kleinste Feld")
    void doubleChestStoredOnLargerHalf() {
        NameGroup group = new NameGroupCalculator(new MemberMap().add(chest(1, 0, 0, 0))).groupAt(at(1, 0)).orElseThrow();

        assertEquals(at(0, 0).asId(), group.id());
        assertEquals(1.0, group.textPosition().x(), 0.0);
    }

    @Test
    @DisplayName("Doppelkiste neben einer weiteren Kiste: eine Gruppe mit drei Feldern")
    void doubleChestNextToChest() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(chest(0, 0, 1, 0)).add(lock(2, 0)));

        assertEquals(1, groups.size());
        NameGroup group = groups.get(at(0, 0).asId());
        assertEquals(3, group.fields().size());
        assertEquals(2, group.members().size());
        assertEquals(1.5, group.textPosition().x(), 0.0);
    }

    @Test
    @DisplayName("Secure Hopper neben einem gesperrten Block gehoert zur Gruppe")
    void secureHopperNextToLockBelongsToGroup() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(hopper(1, 0)));

        assertEquals(1, groups.size());
        NameGroup group = groups.get(at(0, 0).asId());
        assertEquals(List.of(at(0, 0), at(1, 0)), group.fields());
        assertEquals(List.of(NameGroupMember.Kind.LOCKED_BLOCK, NameGroupMember.Kind.SECURE_HOPPER),
                group.members().stream().map(NameGroupMember::kind).toList());
        assertEquals(1.0, group.textPosition().x(), 0.0);
    }

    @Test
    @DisplayName("Secure Hopper allein oder nur mit anderen Secure Hoppern: keine Gruppe, kein Text")
    void secureHoppersAloneHaveNoGroup() {
        MemberMap members = new MemberMap().add(hopper(0, 0)).add(hopper(1, 0)).add(hopper(10, 10));
        NameGroupCalculator calculator = new NameGroupCalculator(members);

        assertTrue(calculator.groupAt(at(0, 0)).isEmpty());
        assertTrue(calculator.groupAt(at(1, 0)).isEmpty());
        assertTrue(calculator.groupAt(at(10, 10)).isEmpty());
    }

    @Test
    @DisplayName("Secure Hopper zwischen zwei gesperrten Gruppen verbindet sie zu einer")
    void secureHopperConnectsTwoGroups() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(hopper(1, 0)).add(lock(2, 0)));

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(at(0, 0).asId()).fields().size());
    }

    @Test
    @DisplayName("Kette aus Secure Hoppern an einem gesperrten Block gehoert vollstaendig zur Gruppe")
    void secureHopperChainBelongsToGroup() {
        Map<String, NameGroup> groups = groupsOf(new MemberMap().add(lock(0, 0)).add(hopper(1, 0)).add(hopper(2, 0)));

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(at(0, 0).asId()).fields().size());
    }

    @Test
    @DisplayName("Secure Hopper eines anderen Besitzers gehoert nicht zur Gruppe")
    void foreignSecureHopperIsNotMember() {
        MemberMap members = new MemberMap().add(lock(0, 0)).add(NameGroupMember.secureHopper(at(1, 0), bob));
        NameGroupCalculator calculator = new NameGroupCalculator(members);

        assertEquals(List.of(at(0, 0)), calculator.groupAt(at(0, 0)).orElseThrow().fields());
        assertTrue(calculator.groupAt(at(1, 0)).isEmpty());
    }

    @Test
    @DisplayName("Feld ohne Mitglied: keine Gruppe")
    void emptyField() {
        assertTrue(new NameGroupCalculator(new MemberMap()).groupAt(at(0, 0)).isEmpty());
    }

    @Test
    @DisplayName("64 Felder (8x8): genau eine Gruppe")
    void sixtyFourFieldsAreOneGroup() {
        Map<String, NameGroup> groups = groupsOf(area(0, 0, 8, 8));

        assertEquals(1, groups.size());
        assertEquals(64, groups.values().iterator().next().fields().size());
    }

    @Test
    @DisplayName("65 Felder: Zerlegung in mehrere Gruppen mit hoechstens 64 Feldern, jedes Feld genau einmal")
    void sixtyFiveFieldsAreSplit() {
        MemberMap members = area(0, 0, 8, 8).add(lock(8, 0));

        Map<String, NameGroup> groups = groupsOf(members);

        assertTrue(groups.size() > 1);
        assertPartition(groups, members.fields());
    }

    @Test
    @DisplayName("Zerlegung nach festen 7x7-Kacheln: eine Reihe aus 65 Bloecken ergibt zehn Gruppen")
    void splitFollowsFixedTiles() {
        MemberMap members = area(0, 0, 65, 1);

        Map<String, NameGroup> groups = groupsOf(members);

        assertEquals(10, groups.size());
        assertEquals(List.of(at(0, 0), at(1, 0), at(2, 0), at(3, 0), at(4, 0), at(5, 0), at(6, 0)),
                groups.get(at(0, 0).asId()).fields());
        assertEquals(2, groups.get(at(63, 0).asId()).fields().size());
        assertPartition(groups, members.fields());
    }

    @Test
    @DisplayName("Zerlegung ist deterministisch: gleiche Gruppen unabhaengig von Abfrage- und Einfuegereihenfolge")
    void splitIsDeterministic() {
        List<NameGroupMember> memberList = new ArrayList<>();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                if ((x + z) % 7 == 3) continue;
                memberList.add(x % 5 == 0 ? hopper(x, z) : lock(x, z));
            }
        }
        MemberMap ordered = new MemberMap();
        memberList.forEach(ordered::add);
        List<NameGroupMember> shuffledMembers = new ArrayList<>(memberList);
        Collections.shuffle(shuffledMembers, new Random(42));
        MemberMap shuffled = new MemberMap();
        shuffledMembers.forEach(shuffled::add);

        List<LockKey> sortedFields = new ArrayList<>(ordered.fields());
        sortedFields.sort(NameGroup.FIELD_ORDER);
        List<LockKey> reversedFields = new ArrayList<>(sortedFields);
        Collections.reverse(reversedFields);
        List<LockKey> randomFields = new ArrayList<>(sortedFields);
        Collections.shuffle(randomFields, new Random(7));

        Map<String, List<LockKey>> expected = fieldsById(groupsOf(ordered, sortedFields));
        assertTrue(expected.size() > 1);
        assertEquals(expected, fieldsById(groupsOf(ordered, reversedFields)));
        assertEquals(expected, fieldsById(groupsOf(shuffled, randomFields)));
        NameGroup single = new NameGroupCalculator(shuffled).groupAt(at(13, 17)).orElseThrow();
        assertEquals(expected.get(single.id()), single.fields());
    }

    @Test
    @DisplayName("Gleicher Endstand nach verschiedenen Aenderungsreihenfolgen ergibt dieselben Gruppen")
    void sameStateAfterDifferentChangeOrders() {
        MemberMap first = new MemberMap();
        MemberMap second = new MemberMap();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 9; z++) first.add(lock(x, z));
        }
        for (int z = 8; z >= 0; z--) {
            for (int x = 11; x >= 0; x--) second.add(lock(x, z));
        }
        second.add(lock(20, 20));
        second.membersByField.remove(at(20, 20));

        assertEquals(fieldsById(groupsOf(first)), fieldsById(groupsOf(second)));
    }

    @Test
    @DisplayName("Doppelkisten-Haelften werden bei der Zerlegung nie getrennt")
    void doubleChestHalvesStayTogether() {
        MemberMap members = new MemberMap();
        for (int x = 5; x < 29; x += 2) {
            for (int z = 0; z < 12; z++) members.add(chest(x, z, x + 1, z));
        }
        for (int z = 0; z < 12; z += 2) members.add(chest(29, z, 29, z + 1));

        Map<String, NameGroup> groups = groupsOf(members);

        assertTrue(groups.size() > 1);
        assertPartition(groups, members.fields());
        for (NameGroup group : groups.values()) {
            for (NameGroupMember member : group.members()) {
                assertTrue(group.fields().containsAll(member.fields()), "getrennte Doppelkiste in " + group.id());
            }
        }
    }

    @Test
    @DisplayName("Obergrenze haelt auch bei dicht gepackten Doppelkisten ueber Kachelgrenzen")
    void capHoldsForDenseDoubleChests() {
        MemberMap members = new MemberMap();
        for (int x = 0; x < 28; x += 2) {
            for (int z = 0; z < 28; z++) members.add(chest(x + 6, z, x + 7, z));
        }
        for (int x = 6; x < 34; x++) members.add(chest(x, 28, x, 29));

        Map<String, NameGroup> groups = groupsOf(members);

        assertPartition(groups, members.fields());
        int largest = groups.values().stream().mapToInt(group -> group.fields().size()).max().orElseThrow();
        assertTrue(largest <= 63, "groesste Gruppe " + largest);
    }

    @Test
    @DisplayName("Negative Koordinaten: 2x2 um den Ursprung ist eine Gruppe mit Mittelpunkt 0/0")
    void negativeCoordinates() {
        Map<String, NameGroup> groups = groupsOf(area(-1, -1, 2, 2));

        assertEquals(1, groups.size());
        NameGroup group = groups.get(at(-1, -1).asId());
        assertEquals(0.0, group.textPosition().x(), 0.0);
        assertEquals(0.0, group.textPosition().z(), 0.0);
    }

    @Test
    @DisplayName("Negative Koordinaten: Kacheln mit floorDiv, x = -1 und x = 0 liegen in verschiedenen Gruppen")
    void negativeCoordinatesSplitWithFloorDivision() {
        MemberMap members = area(-35, 0, 70, 1);

        Map<String, NameGroup> groups = groupsOf(members);

        assertPartition(groups, members.fields());
        assertEquals(List.of(at(-7, 0), at(-6, 0), at(-5, 0), at(-4, 0), at(-3, 0), at(-2, 0), at(-1, 0)),
                groups.get(at(-7, 0).asId()).fields());
        assertTrue(groups.containsKey(at(0, 0).asId()));
    }

    @Test
    @DisplayName("Grenze der lokalen Suche: in einem Feld aus 40000 Bloecken liest eine Abfrage nur wenige hundert Felder")
    void localSearchIsBounded() {
        MemberMap members = area(0, 0, 200, 200);
        NameGroupCalculator calculator = new NameGroupCalculator(members);

        Optional<NameGroup> group = calculator.groupAt(at(100, 100));

        assertTrue(group.isPresent());
        assertTrue(group.get().fields().contains(at(100, 100)));
        assertTrue(group.get().fields().size() <= NameGroupCalculator.MAXIMUM_GROUP_FIELDS);
        assertTrue(members.lookups <= 600, "Abfragen: " + members.lookups);
    }

    @Test
    @DisplayName("Bei Erreichen der Grenze: Kachel ohne gesperrten Block bekommt keinen Text")
    void tileWithoutLockedBlockInLargeAreaHasNoGroup() {
        MemberMap members = new MemberMap();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) members.add(x == 0 && z == 0 ? lock(x, z) : hopper(x, z));
        }
        NameGroupCalculator calculator = new NameGroupCalculator(members);

        NameGroup withLock = calculator.groupAt(at(0, 0)).orElseThrow();
        assertEquals(49, withLock.fields().size());
        assertTrue(calculator.groupAt(at(8, 8)).isEmpty());
    }

    @Test
    @DisplayName("Wiederholte Abfragen derselben Gruppe kommen aus dem Speicher der Berechnung")
    void repeatedQueriesAreMemoized() {
        MemberMap members = area(0, 0, 3, 3);
        NameGroupCalculator calculator = new NameGroupCalculator(members);
        NameGroup first = calculator.groupAt(at(1, 1)).orElseThrow();
        int lookupsAfterFirst = members.lookups;

        for (LockKey field : first.fields()) assertSame(first, calculator.groupAt(field).orElseThrow());

        assertEquals(lookupsAfterFirst, members.lookups);
    }
}
