package de.SecureChest.plugin.sign;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import de.SecureChest.plugin.lock.LockKey;

public final class NameGroupCalculator {

    public static final int MAXIMUM_GROUP_FIELDS = 64;

    public static final int TILE_SIZE = 7;

    private record Tile(UUID worldUuid, int y, int tileX, int tileZ) {

        static Tile of(NameGroupMember member) {
            LockKey anchor = member.smallestField();
            return new Tile(anchor.worldUuid(), anchor.y(),
                    Math.floorDiv(anchor.x(), TILE_SIZE), Math.floorDiv(anchor.z(), TILE_SIZE));
        }
    }

    private final NameGroupMemberLookup lookup;
    private final Map<LockKey, Optional<NameGroup>> resolvedFields = new HashMap<>();
    private final Set<LockKey> fieldsOfLargeAreas = new HashSet<>();

    public NameGroupCalculator(NameGroupMemberLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public Optional<NameGroup> groupAt(LockKey field) {
        Optional<NameGroup> resolved = resolvedFields.get(field);
        if (resolved != null) return resolved;

        NameGroupMember member = lookup.memberAt(field);
        if (member == null) return Optional.empty();

        List<NameGroupMember> groupMembers = fieldsOfLargeAreas.contains(field) ? null : connectedArea(member);
        if (groupMembers == null) groupMembers = tilePiece(member);

        Optional<NameGroup> group = groupMembers.stream().anyMatch(NameGroupMember::isLockedBlock)
                ? Optional.of(NameGroup.of(groupMembers))
                : Optional.empty();
        for (NameGroupMember groupMember : groupMembers) {
            for (LockKey memberField : groupMember.fields()) resolvedFields.put(memberField, group);
        }
        resolvedFields.putIfAbsent(field, group);
        return group;
    }

    private List<NameGroupMember> connectedArea(NameGroupMember start) {
        Map<LockKey, NameGroupMember> visited = new LinkedHashMap<>();
        Deque<NameGroupMember> pending = new ArrayDeque<>();
        visited.put(start.position(), start);
        pending.add(start);
        int fieldCount = start.fields().size();
        boolean large = fieldCount > MAXIMUM_GROUP_FIELDS;

        while (!large && !pending.isEmpty()) {
            NameGroupMember current = pending.poll();
            for (NameGroupMember neighbour : neighbours(current, candidate -> true)) {
                if (visited.containsKey(neighbour.position())) continue;
                visited.put(neighbour.position(), neighbour);
                pending.add(neighbour);
                fieldCount += neighbour.fields().size();
                if (fieldCount > MAXIMUM_GROUP_FIELDS || isInLargeArea(neighbour)) {
                    large = true;
                    break;
                }
            }
        }

        if (!large) return List.copyOf(visited.values());
        for (NameGroupMember member : visited.values()) fieldsOfLargeAreas.addAll(member.fields());
        return null;
    }

    private List<NameGroupMember> tilePiece(NameGroupMember start) {
        Tile tile = Tile.of(start);
        Map<LockKey, NameGroupMember> visited = new LinkedHashMap<>();
        Deque<NameGroupMember> pending = new ArrayDeque<>();
        visited.put(start.position(), start);
        pending.add(start);

        while (!pending.isEmpty()) {
            NameGroupMember current = pending.poll();
            for (NameGroupMember neighbour : neighbours(current, candidate -> Tile.of(candidate).equals(tile))) {
                if (visited.putIfAbsent(neighbour.position(), neighbour) == null) pending.add(neighbour);
            }
        }
        return List.copyOf(visited.values());
    }

    private List<NameGroupMember> neighbours(NameGroupMember member, Predicate<NameGroupMember> accepted) {
        List<NameGroupMember> neighbours = new ArrayList<>();
        for (LockKey field : member.fields()) {
            for (LockKey neighbourField : lateralNeighbours(field)) {
                if (member.fields().contains(neighbourField)) continue;
                NameGroupMember candidate = lookup.memberAt(neighbourField);
                if (candidate == null || candidate.position().equals(member.position())) continue;
                if (!candidate.owner().equals(member.owner())) continue;
                if (!accepted.test(candidate) || neighbours.contains(candidate)) continue;
                neighbours.add(candidate);
            }
        }
        return neighbours;
    }

    private boolean isInLargeArea(NameGroupMember member) {
        for (LockKey field : member.fields()) {
            if (fieldsOfLargeAreas.contains(field)) return true;
        }
        return false;
    }

    static List<LockKey> lateralNeighbours(LockKey field) {
        UUID worldUuid = field.worldUuid();
        return List.of(
                new LockKey(worldUuid, field.x() + 1, field.y(), field.z()),
                new LockKey(worldUuid, field.x() - 1, field.y(), field.z()),
                new LockKey(worldUuid, field.x(), field.y(), field.z() + 1),
                new LockKey(worldUuid, field.x(), field.y(), field.z() - 1));
    }

    static boolean isLateralNeighbour(LockKey first, LockKey second) {
        if (!first.worldUuid().equals(second.worldUuid()) || first.y() != second.y()) return false;
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z()) == 1;
    }
}
