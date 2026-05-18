package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlayerSettingsRepository – Whitelist gegen SQL-Injection")
class PlayerSettingsRepositoryWhitelistTest {

    @Test
    @DisplayName("quick_lock ist erlaubt")
    void quickLock_erlaubt() {
        assertDoesNotThrow(() -> PlayerSettingsRepository.requireAllowedField("quick_lock"));
    }

    @Test
    @DisplayName("quick_remove ist erlaubt")
    void quickRemove_erlaubt() {
        assertDoesNotThrow(() -> PlayerSettingsRepository.requireAllowedField("quick_remove"));
    }

    @Test
    @DisplayName("Beliebige andere Felder werden abgelehnt")
    void unbekanntesFeld_wirftAusnahme() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField("password"));
    }

    @Test
    @DisplayName("SQL-Injection-Versuch wird abgelehnt")
    void sqlInjectionVersuch_wirftAusnahme() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField("quick_lock; DROP TABLE locks; --"));
    }

    @Test
    @DisplayName("Leerer String wird abgelehnt")
    void leererString_wirftAusnahme() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField(""));
    }

    @Test
    @DisplayName("null wird abgelehnt")
    void nullFeld_wirftAusnahme() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField(null));
    }

    @Test
    @DisplayName("Case-sensitive: QUICK_LOCK ist NICHT erlaubt")
    void caseSensitive_grossgeschriebenAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSettingsRepository.requireAllowedField("QUICK_LOCK"));
    }
}
