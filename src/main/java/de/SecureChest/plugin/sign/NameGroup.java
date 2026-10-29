package de.SecureChest.plugin.sign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

import de.SecureChest.plugin.lock.LockKey;

public record NameGroup(String id, UUID owner, List<LockKey> fields, List<NameGroupMember> members,
                        NameTextPosition textPosition) {

    public static final Comparator<LockKey> FIELD_ORDER = Comparator.comparing(LockKey::worldUuid)
            .thenComparingInt(LockKey::y)
            .thenComparingInt(LockKey::x)
            .thenComparingInt(LockKey::z);

    public NameGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(textPosition, "textPosition");
        fields = List.copyOf(fields);
        members = List.copyOf(members);
    }

    static NameGroup of(Collection<NameGroupMember> members) {
        if (members.isEmpty()) throw new IllegalArgumentException("A group needs at least one member");
        List<NameGroupMember> sortedMembers = new ArrayList<>(members);
        sortedMembers.sort(Comparator.comparing(NameGroupMember::smallestField, FIELD_ORDER));
        TreeSet<LockKey> sortedFields = new TreeSet<>(FIELD_ORDER);
        for (NameGroupMember member : sortedMembers) sortedFields.addAll(member.fields());
        List<LockKey> fields = List.copyOf(sortedFields);
        return new NameGroup(fields.get(0).asId(), sortedMembers.get(0).owner(), fields, sortedMembers,
                NameTextPosition.above(fields));
    }
}
