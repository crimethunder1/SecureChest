package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogRepository – Action-Konstanten (SEC-14)")
class LogRepositoryActionConstantsTest {

    @Test
    @DisplayName("Alle Action-Konstanten sind eindeutig (keine Duplikate)")
    void aktionsKonstanten_sindEindeutig() throws IllegalAccessException {
        Set<String> values = new HashSet<>();
        for (Field field : LogRepository.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getName().startsWith("ACTION_")) continue;
            String value = (String) field.get(null);
            assertTrue(values.add(value),
                    "Duplikat-Action-Konstante: " + field.getName() + " = " + value);
        }
    }

    @Test
    @DisplayName("Bekannte Konstanten haben die erwarteten String-Werte")
    void bekannteKonstanten_haveExpectedValues() {
        assertEquals("LOCK_CREATE",           LogRepository.ACTION_LOCK_CREATE);
        assertEquals("LOCK_REMOVE",           LogRepository.ACTION_LOCK_REMOVE);
        assertEquals("LOCK_REMOVE_ADMIN",     LogRepository.ACTION_LOCK_REMOVE_ADMIN);
        assertEquals("LOCK_TRANSFER",         LogRepository.ACTION_LOCK_TRANSFER);
        assertEquals("LOCK_UNLOCK_ALL_SELF",  LogRepository.ACTION_LOCK_UNLOCK_ALL_SELF);
        assertEquals("LOCK_UNLOCK_ALL_ADMIN", LogRepository.ACTION_LOCK_UNLOCK_ALL_ADMIN);
        assertEquals("TRUST_SET",             LogRepository.ACTION_TRUST_SET);
        assertEquals("TRUST_REMOVE",          LogRepository.ACTION_TRUST_REMOVE);
        assertEquals("HOPPER_BIND",           LogRepository.ACTION_HOPPER_BIND);
        assertEquals("HOPPER_REMOVE",         LogRepository.ACTION_HOPPER_REMOVE);
        assertEquals("UNDO_RESTORE",          LogRepository.ACTION_UNDO_RESTORE);
        assertEquals("INSPECT_TOGGLE",        LogRepository.ACTION_INSPECT_TOGGLE);
        assertEquals("LOCK_REMOVE_AREA",      LogRepository.ACTION_LOCK_REMOVE_AREA);
    }

    @Test
    @DisplayName("Alle Action-Konstanten matchen das UPPERCASE_SNAKE_CASE-Schema")
    void aktionsKonstanten_matchenSchema() throws IllegalAccessException {
        for (Field field : LogRepository.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getName().startsWith("ACTION_")) continue;
            String value = (String) field.get(null);
            assertTrue(value.matches("[A-Z][A-Z0-9_]+"),
                    "Action-Konstante " + field.getName() + " = '" + value
                            + "' muss UPPERCASE_SNAKE_CASE sein");
        }
    }

    @Test
    @DisplayName("Bestehende Konstanten aus v1.4.x sind unveraendert")
    void bestehendeKonstanten_unveraendert() {
        assertEquals("UNAUTHORIZED_BREAK", LogRepository.ACTION_UNAUTHORIZED_BREAK,
                "Konstante darf sich nicht aendern – sonst werden alte DB-Eintraege unauffindbar");
        assertEquals("ADMIN_BREAK", LogRepository.ACTION_ADMIN_BREAK,
                "Konstante darf sich nicht aendern – sonst werden alte DB-Eintraege unauffindbar");
    }
}
