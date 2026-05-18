package de.SecureChest.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Permissions – zentrale Permission-Konstanten")
class PermissionsConstantsTest {

    @Test
    @DisplayName("USE folgt dem Schema chestsecure.<feature>")
    void use_korrekteNamespace() {
        assertEquals("chestsecure.use", Permissions.USE);
    }

    @Test
    @DisplayName("ADMIN folgt dem Schema chestsecure.<feature>")
    void admin_korrekteNamespace() {
        assertEquals("chestsecure.admin", Permissions.ADMIN);
    }

    @Test
    @DisplayName("ADMIN_TELEPORT folgt dem Schema chestsecure.admin.<feature>")
    void adminTeleport_korrekteNamespace() {
        assertEquals("chestsecure.admin.teleport", Permissions.ADMIN_TELEPORT);
    }

    @Test
    @DisplayName("Alle Permissions sind eindeutig (keine Duplikate)")
    void alle_eindeutig() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != String.class) continue;
            String value = (String) field.get(null);
            assertTrue(values.add(value),
                    "Duplikat: " + field.getName() + " = " + value);
        }
    }

    @Test
    @DisplayName("Alle Permissions sind kleingeschrieben und ohne Whitespace")
    void alle_lowercaseUndOhneWhitespace() throws IllegalAccessException {
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != String.class) continue;
            String value = (String) field.get(null);
            assertEquals(value.toLowerCase(), value, field.getName());
            assertFalse(value.contains(" "), field.getName());
            assertFalse(value.isBlank(), field.getName());
        }
    }
}
