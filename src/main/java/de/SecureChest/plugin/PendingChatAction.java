package de.SecureChest.plugin;

public sealed interface PendingChatAction permits PendingChatAction.AddTrust, PendingChatAction.GlobalUnlock, PendingChatAction.LogActorFilter {

    record AddTrust(String lockId, String level) implements PendingChatAction {}

    record GlobalUnlock() implements PendingChatAction {}

    /**
     * Set/replace actor filter for the admin logs GUI.
     */
    record LogActorFilter() implements PendingChatAction {}
}
