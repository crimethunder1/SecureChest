package de.SecureChest.plugin.sign;

import de.SecureChest.plugin.lock.LockKey;

@FunctionalInterface
public interface NameGroupMemberLookup {

    NameGroupMember memberAt(LockKey field);
}
