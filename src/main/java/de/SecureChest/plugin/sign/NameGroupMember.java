package de.SecureChest.plugin.sign;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.SecureChest.plugin.lock.LockKey;

public record NameGroupMember(LockKey position, UUID owner, Kind kind, List<LockKey> fields) {

    public enum Kind { LOCKED_BLOCK, SECURE_HOPPER }

    public NameGroupMember {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        fields = List.copyOf(fields);
        if (fields.isEmpty() || fields.size() > 2) {
            throw new IllegalArgumentException("A member occupies one or two fields");
        }
        if (!fields.get(0).equals(position)) {
            throw new IllegalArgumentException("The first field must be the position of the member");
        }
        if (fields.size() == 2) {
            if (kind != Kind.LOCKED_BLOCK) {
                throw new IllegalArgumentException("Only a locked block can occupy two fields");
            }
            if (!NameGroupCalculator.isLateralNeighbour(position, fields.get(1))) {
                throw new IllegalArgumentException("The second field must touch the position at a side");
            }
        }
    }

    public static NameGroupMember lockedBlock(LockKey position, UUID owner) {
        return new NameGroupMember(position, owner, Kind.LOCKED_BLOCK, List.of(position));
    }

    public static NameGroupMember doubleChest(LockKey position, LockKey partner, UUID owner) {
        return new NameGroupMember(position, owner, Kind.LOCKED_BLOCK, List.of(position, partner));
    }

    public static NameGroupMember secureHopper(LockKey position, UUID owner) {
        return new NameGroupMember(position, owner, Kind.SECURE_HOPPER, List.of(position));
    }

    public boolean isLockedBlock() {
        return kind == Kind.LOCKED_BLOCK;
    }

    LockKey smallestField() {
        return fields.stream().min(NameGroup.FIELD_ORDER).orElseThrow();
    }
}
