package de.SecureChest.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageService – Farbcodes und Platzhalter")
class MessageServiceTest {

    private Method translateColorCodes;

    @BeforeEach
    void setUp() throws Exception {
        translateColorCodes = MessageService.class
                .getDeclaredMethod("translateColorCodes", String.class);
        translateColorCodes.setAccessible(true);
    }

    private String translate(String input) throws Exception {
        return (String) translateColorCodes.invoke(null, input);
    }

    @Test
    @DisplayName("&a wird zu §a konvertiert")
    void translateColorCodes_kleinbuchstabe() throws Exception {
        assertEquals("§aGrün", translate("&aGrün"));
    }

    @Test
    @DisplayName("&C (Großbuchstabe) wird zu §c konvertiert")
    void translateColorCodes_grossbuchstabe() throws Exception {
        assertEquals("§cRot", translate("&CRot"));
    }

    @Test
    @DisplayName("Mehrere Codes im selben String werden alle konvertiert")
    void translateColorCodes_mehrfach() throws Exception {
        String result = translate("&aGrün &cRot &eGelb");
        assertEquals("§aGrün §cRot §eGelb", result);
    }

    @Test
    @DisplayName("& ohne gültigen Code-Buchstabe bleibt unverändert")
    void translateColorCodes_ungueltigesAmpersand() throws Exception {
        assertEquals("&xyz bleibt", translate("&xyz bleibt"));
    }

    @Test
    @DisplayName("Ziffern 0-9 als Farbcodes werden konvertiert")
    void translateColorCodes_ziffern() throws Exception {
        assertEquals("§0§9", translate("&0&9"));
    }

    @Test
    @DisplayName("Null-Eingabe liefert leeren String")
    void translateColorCodes_null() throws Exception {
        assertEquals("", translate(null));
    }

    @Test
    @DisplayName("Leerer String bleibt leer")
    void translateColorCodes_leerString() throws Exception {
        assertEquals("", translate(""));
    }

    @Test
    @DisplayName("Platzhalter {0} wird durch erstes Argument ersetzt")
    void platzhalter_einzel() {
        String raw = "Hallo {0}!";
        String result = raw.replace("{0}", "Welt");
        assertEquals("Hallo Welt!", result);
    }

    @Test
    @DisplayName("Mehrere Platzhalter {0},{1},{2} werden korrekt ersetzt")
    void platzhalter_mehrere() {
        String raw = "{0} hat {1} Schlösser in {2}.";
        String result = raw
                .replace("{0}", "Spieler")
                .replace("{1}", "5")
                .replace("{2}", "Overworld");
        assertEquals("Spieler hat 5 Schlösser in Overworld.", result);
    }

    @Test
    @DisplayName("Fehlender Platzhalter {1} wenn nur {0} vorhanden bleibt erhalten")
    void platzhalter_fehlend() {
        String raw = "{0} hat {1} Schlösser.";
        String result = raw.replace("{0}", "Spieler");

        assertTrue(result.contains("{1}"), "Unreplaced placeholder should remain visible");
    }

    @Test
    @DisplayName("Null-Argument wird zu leerem String")
    void platzhalter_nullArgument() {
        String raw = "Besitzer: {0}";
        String result = raw.replace("{0}", String.valueOf((Object) null));
        assertEquals("Besitzer: null", result);
    }

    private String renderLikeService(String raw, Object... args) throws Exception {
        String translated = (String) translateColorCodes.invoke(null, raw);
        for (int i = 0; i < args.length; i++) {
            translated = translated.replace("{" + i + "}",
                    args[i] == null ? "" : args[i].toString());
        }
        return translated;
    }

    @Test
    @DisplayName("User-Input mit &c im Argument wird NICHT als Color-Code interpretiert")
    void userInput_keineColorCodeInjection() throws Exception {
        String raw = "Spieler: {0}";
        String result = renderLikeService(raw, "&cBoeserName");
        assertEquals("Spieler: &cBoeserName", result);
        assertFalse(result.contains("§c"),
                "User-Input darf keine §-Codes injizieren");
    }

    @Test
    @DisplayName("Color-Codes im Template werden uebersetzt, im Argument bleiben sie roh")
    void templateCodes_uebersetzt_argumentRoh() throws Exception {
        String raw = "&aOK: &f{0}";
        String result = renderLikeService(raw, "&kverschleiert");
        assertEquals("§aOK: §f&kverschleiert", result);
    }
}
