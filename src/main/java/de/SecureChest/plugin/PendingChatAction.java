<<<<<<< HEAD
package de.SecureChest.plugin;

public sealed interface PendingChatAction permits PendingChatAction.AddTrust, PendingChatAction.GlobalUnlock, PendingChatAction.LogActorFilter {

    record AddTrust(String lockId, String level) implements PendingChatAction {}

    record GlobalUnlock() implements PendingChatAction {}

    record LogActorFilter() implements PendingChatAction {}
}
=======
package de.SecureChest.plugin;

public sealed interface PendingChatAction permits PendingChatAction.AddTrust, PendingChatAction.GlobalUnlock, PendingChatAction.LogActorFilter {

    record AddTrust(String lockId, String level) implements PendingChatAction {}

    record GlobalUnlock() implements PendingChatAction {}

    /**
     * Set/replace actor filter for the admin logs GUI.
     */
    record LogActorFilter() implements PendingChatAction {}
}
>>>>>>> a213fa6b4069e01c09e863d0744f947774f9fe82
