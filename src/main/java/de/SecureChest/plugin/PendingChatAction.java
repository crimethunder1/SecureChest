package de.SecureChest.plugin;

public sealed interface PendingChatAction
        permits PendingChatAction.AddTrust,
                PendingChatAction.GlobalUnlock,
                PendingChatAction.LogActorFilter,
                PendingChatAction.TransferLock {

    record AddTrust(String lockId, String level) implements PendingChatAction {}

    record GlobalUnlock() implements PendingChatAction {}

    record LogActorFilter() implements PendingChatAction {}

    record TransferLock(String lockId) implements PendingChatAction {}
}
