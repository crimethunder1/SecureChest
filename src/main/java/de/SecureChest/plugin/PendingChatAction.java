package de.SecureChest.plugin;

public sealed interface PendingChatAction
        permits PendingChatAction.AddTrust,
                PendingChatAction.GlobalUnlock,
                PendingChatAction.LogActorFilter,
                PendingChatAction.TransferLock,
                PendingChatAction.AreaCreateName,
                PendingChatAction.AreaCreateCoordinates,
                PendingChatAction.AreaResizeCoordinates,
                PendingChatAction.ChestKeyAmount,
                PendingChatAction.AdminAddTrust,
                PendingChatAction.AdminTransferLock {

    record AddTrust(String lockId, String level) implements PendingChatAction {}

    record GlobalUnlock() implements PendingChatAction {}

    record LogActorFilter() implements PendingChatAction {}

    record TransferLock(String lockId) implements PendingChatAction {}

    record AreaCreateName() implements PendingChatAction {}

    record AreaCreateCoordinates(String name) implements PendingChatAction {}

    record AreaResizeCoordinates(String name) implements PendingChatAction {}

    record ChestKeyAmount() implements PendingChatAction {}

    record AdminAddTrust(String lockId, String level) implements PendingChatAction {}

    record AdminTransferLock(String lockId) implements PendingChatAction {}
}
