package de.SecureChest.plugin.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameVisibilityRules: Wahrheitstabelle fuer die Sichtbarkeit eines Namenstextes")
class NameVisibilityRulesTest {

    private static final boolean[] BOTH = {true, false};

    @ParameterizedTest(name = "global {0}, Besitzer {1}, Admin-Schalter {2}, ueberdeckt {3}: gezeigt {4}")
    @CsvSource({
            "true,  true,  true,  true,  false",
            "true,  true,  true,  false, true",
            "true,  true,  false, true,  true",
            "true,  true,  false, false, true",
            "true,  false, true,  true,  false",
            "true,  false, true,  false, false",
            "true,  false, false, true,  false",
            "true,  false, false, false, false",
            "false, true,  true,  true,  false",
            "false, true,  true,  false, false",
            "false, true,  false, true,  false",
            "false, true,  false, false, false",
            "false, false, true,  true,  false",
            "false, false, true,  false, false",
            "false, false, false, true,  false",
            "false, false, false, false, false"
    })
    @DisplayName("Alle 16 Kombinationen")
    void truthTable(boolean displayEnabled, boolean ownerShowsName, boolean hideWhenCovered,
                    boolean textFieldCovered, boolean expected) {
        assertEquals(expected,
                NameVisibilityRules.isShown(displayEnabled, ownerShowsName, hideWhenCovered, textFieldCovered));
    }

    @Test
    @DisplayName("Globale Anzeige aus hat Vorrang: in keiner Kombination wird ein Name gezeigt")
    void globalDisplayOffWins() {
        for (boolean owner : BOTH) {
            for (boolean hide : BOTH) {
                for (boolean covered : BOTH) {
                    assertFalse(NameVisibilityRules.isShown(false, owner, hide, covered));
                }
            }
        }
    }

    @Test
    @DisplayName("Admin-Schalter aus: die Abdeckung spielt keine Rolle, es entscheidet der Besitzer")
    void hideSwitchOffIgnoresCover() {
        for (boolean owner : BOTH) {
            assertEquals(NameVisibilityRules.isShown(true, owner, false, true),
                    NameVisibilityRules.isShown(true, owner, false, false));
            assertEquals(owner, NameVisibilityRules.isShown(true, owner, false, true));
        }
    }
}
