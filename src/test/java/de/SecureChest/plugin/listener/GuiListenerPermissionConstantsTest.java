package de.SecureChest.plugin.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuiListener – Permission-Konstanten (SEC-13)")
class GuiListenerPermissionConstantsTest {

    @Test
    @DisplayName("Teleport-Permission folgt dem Schema chestsecure.admin.<feature>")
    void teleportPermission_followsAdminNamespace() {
        assertEquals("chestsecure.admin.teleport", GuiListener.PERMISSION_TELEPORT,
                "Permission muss unter chestsecure.admin.* liegen, "
                        + "damit Permission-Plugins sie granular vergeben koennen");
    }

    @Test
    @DisplayName("Permission-String enthaelt keine Tippfehler oder Whitespace")
    void teleportPermission_isCleanString() {
        String permission = GuiListener.PERMISSION_TELEPORT;
        assertFalse(permission.isBlank(), "Permission darf nicht leer sein");
        assertEquals(permission.trim(), permission, "Permission darf keinen Whitespace haben");
        assertFalse(permission.contains(" "), "Permission darf keine Leerzeichen enthalten");
    }
}
