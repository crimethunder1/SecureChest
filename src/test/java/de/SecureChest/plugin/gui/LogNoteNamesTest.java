package de.SecureChest.plugin.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogNoteNames: UUIDs in Log-Notizen werden fuer die Anzeige durch Namen ersetzt")
class LogNoteNamesTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Function<UUID, String> NAMES = Map.of(ALICE, "Alice", BOB, "Bob")::get;

    @ParameterizedTest
    @ValueSource(strings = {"target_owner", "from", "to", "target", "owner"})
    @DisplayName("Jeder Notiz-Schluessel mit einer UUID bekommt den Namen")
    void everyKeyIsReplaced(String key) {
        assertEquals(key + "=Alice", LogNoteNames.withNames(key + "=" + ALICE, NAMES));
    }

    @Test
    @DisplayName("Mehrere UUIDs in einer Notiz, andere Werte bleiben stehen")
    void severalUuidsInOneNote() {
        String note = "from=" + ALICE + ",to=" + BOB + ",unbound_foreign_hoppers=2";

        assertEquals("from=Alice,to=Bob,unbound_foreign_hoppers=2", LogNoteNames.withNames(note, NAMES));
    }

    @Test
    @DisplayName("Admin-Vertrauen: Spieler, Stufe und Besitzer")
    void adminTrustNote() {
        String note = "target=" + BOB + ",level=STOCKER,target_owner=" + ALICE;

        assertEquals("target=Bob,level=STOCKER,target_owner=Alice", LogNoteNames.withNames(note, NAMES));
    }

    @Test
    @DisplayName("Globales Entsperren durch einen Admin: Zaehler bleiben, Besitzer wird ersetzt")
    void unlockAllAdminNote() {
        assertEquals("locks=3,hoppers=1,target_owner=Bob",
                LogNoteNames.withNames("locks=3,hoppers=1,target_owner=" + BOB, NAMES));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "owner=not-a-uuid",
            "target=1234",
            "from=11111111-2222-3333-4444-55555555555",
            "to=11111111-2222-3333-4444-5555555555556",
            "owner=11111111-2222-3333-4444-555555555555x",
            "owner= 11111111-2222-3333-4444-555555555555",
            "co_owner=11111111-2222-3333-4444-555555555555",
            "player=11111111-2222-3333-4444-555555555555"})
    @DisplayName("Ungueltige Werte und unbekannte Schluessel bleiben unveraendert")
    void invalidValuesStay(String note) {
        assertEquals(note, LogNoteNames.withNames(note, NAMES));
    }

    @ParameterizedTest
    @ValueSource(strings = {"locks=3,hoppers=0", "enabled=true", "LOCK_BLOCK", "bound=world:1:64:2"})
    @DisplayName("Notiz ohne UUID bleibt unveraendert")
    void noteWithoutUuid(String note) {
        assertEquals(note, LogNoteNames.withNames(note, NAMES));
    }

    @Test
    @DisplayName("Leere und fehlende Notiz bleiben, wie sie sind")
    void emptyAndMissingNote() {
        assertNull(LogNoteNames.withNames(null, NAMES));
        assertEquals("", LogNoteNames.withNames("", NAMES));
    }

    @Test
    @DisplayName("Liefert die Aufloesung keinen Namen, bleibt die UUID stehen")
    void unresolvedUuidStays() {
        UUID unknown = UUID.fromString("99999999-8888-7777-6666-555555555555");

        assertEquals("from=" + unknown + ",to=Bob",
                LogNoteNames.withNames("from=" + unknown + ",to=" + BOB, NAMES));
    }

    @Test
    @DisplayName("Grossgeschriebene UUID wird erkannt, target_owner wird nicht als owner doppelt ersetzt")
    void upperCaseAndCompoundKey() {
        String note = "target_owner=" + ALICE.toString().toUpperCase();

        assertEquals("target_owner=Alice", LogNoteNames.withNames(note, NAMES));
    }

    @Test
    @DisplayName("Namen mit Sonderzeichen fuer regulaere Ausdruecke werden wortwoertlich eingesetzt")
    void namesAreInsertedLiterally() {
        assertEquals("owner=$1\\x", LogNoteNames.withNames("owner=" + ALICE, uuid -> "$1\\x"));
    }
}
