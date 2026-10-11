package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageService – Farbcodes und Platzhalter")
class MessageServiceTest {

    private static final Path BUNDLED_RESOURCES = Path.of("src", "main", "resources");

    private Method translateColorCodes;

    @TempDir
    Path dataFolder;

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

    private static PluginConfig configWithLanguage(String languageLine) {
        return new PluginConfig(YamlConfiguration.loadConfiguration(new StringReader(languageLine)));
    }

    private ChestSecurePlugin mockPlugin(PluginConfig config) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        when(plugin.cfg()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("MessageServiceTest"));
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            Path bundledFile = BUNDLED_RESOURCES.resolve(invocation.<String>getArgument(0));
            return Files.isRegularFile(bundledFile) ? Files.newInputStream(bundledFile) : null;
        });
        doAnswer(invocation -> {
            String resourcePath = invocation.getArgument(0);
            boolean replace = invocation.getArgument(1);
            Path target = dataFolder.resolve(resourcePath);
            if (Files.exists(target) && !replace) return null;
            Files.createDirectories(target.getParent());
            Files.copy(BUNDLED_RESOURCES.resolve(resourcePath), target, StandardCopyOption.REPLACE_EXISTING);
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());
        return plugin;
    }

    private void writeDataFile(String relativePath, String content) throws IOException {
        Path target = dataFolder.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sprache en liefert englische Texte und legt beide Sprachdateien an")
    void language_en_liefertEnglisch() {
        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: en")));

        assertEquals("Block locked.", service.get("block-gesperrt"));
        assertEquals("This block belongs to Steve.", service.get("block-gehoert", "Steve"));
        assertTrue(Files.isRegularFile(dataFolder.resolve("lang/en.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("lang/de.yml")));
    }

    @Test
    @DisplayName("Sprache de liefert deutsche Texte")
    void language_de_liefertDeutsch() {
        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: de")));

        assertEquals("Block gesperrt.", service.get("block-gesperrt"));
        assertEquals("§fHilfe", service.get("gui-main-help-name"));
    }

    @Test
    @DisplayName("Sprachwert wird getrimmt und ohne Gross- und Kleinschreibung ausgewertet")
    void language_trimUndGrossschreibung() {
        PluginConfig config = configWithLanguage("language: \"  DE \"");

        assertEquals("de", config.language());
        assertEquals("Block gesperrt.", new MessageService(mockPlugin(config)).get("block-gesperrt"));
    }

    @Test
    @DisplayName("Ungueltiger Sprachwert faellt auf en zurueck")
    void language_ungueltig_faelltAufEnglischZurueck() {
        PluginConfig config = configWithLanguage("language: fr");

        assertEquals("en", config.language());
        assertEquals("Block locked.", new MessageService(mockPlugin(config)).get("block-gesperrt"));
    }

    @Test
    @DisplayName("Fehlender Sprachschluessel in der config.yml ergibt en")
    void language_fehlt_ergibtEnglisch() {
        assertEquals("en", configWithLanguage("").language());
    }

    @Test
    @DisplayName("Fehlender Schluessel in der Datei im Plugin-Ordner kommt aus der JAR-Datei")
    void fehlenderSchluessel_kommtAusJar() throws IOException {
        writeDataFile("lang/de.yml", "block-gesperrt: \"Eigener Text\"\n");

        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: de")));

        assertEquals("Eigener Text", service.get("block-gesperrt"));
        assertEquals("Block ist bereits gesperrt.", service.get("block-bereits-gesperrt"));
        assertEquals("block-gesperrt: \"Eigener Text\"\n",
                Files.readString(dataFolder.resolve("lang/de.yml"), StandardCharsets.UTF_8),
                "Bestehende Sprachdateien duerfen nicht ueberschrieben werden");
    }

    @Test
    @DisplayName("Unbekannter Schluessel liefert [schluessel]")
    void unbekannterSchluessel_liefertPlatzhalter() {
        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: en")));

        assertEquals("[does-not-exist]", service.get("does-not-exist"));
        assertEquals(List.of("[does-not-exist]"), service.getList("does-not-exist"));
    }

    @Test
    @DisplayName("getList liefert Lore-Zeilen mit Farbcodes und Platzhaltern")
    void getList_liefertZeilen() {
        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: de")));

        List<String> lore = service.getList("gui-main-locks-lore", 5);

        assertEquals(List.of("§8Schlösser anzeigen und verwalten", "§85 Schloss/Schlösser vorhanden"), lore);
    }

    @Test
    @DisplayName("getList auf einen einfachen Text liefert eine Zeile")
    void getList_einfacherText_liefertEineZeile() {
        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: en")));

        assertEquals(List.of("§aStorage reached!"), service.getList("ping-angekommen"));
    }

    @Test
    @DisplayName("Alte messages.yml wird nach lang/de.yml migriert und umbenannt")
    void migration_messagesYml_nachLangDe() throws IOException {
        writeDataFile("messages.yml", "block-gesperrt: \"Alter angepasster Text\"\n");

        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: de")));

        assertFalse(Files.exists(dataFolder.resolve("messages.yml")));
        assertTrue(Files.isRegularFile(dataFolder.resolve("messages.yml.old")));
        assertEquals("block-gesperrt: \"Alter angepasster Text\"\n",
                Files.readString(dataFolder.resolve("lang/de.yml"), StandardCharsets.UTF_8));
        assertEquals("Alter angepasster Text", service.get("block-gesperrt"));
        assertEquals("§fHilfe", service.get("gui-main-help-name"));
    }

    @Test
    @DisplayName("Migration entfaellt, wenn lang/de.yml schon existiert")
    void migration_entfaelltBeiVorhandenerDatei() throws IOException {
        writeDataFile("messages.yml", "block-gesperrt: \"Alt\"\n");
        writeDataFile("lang/de.yml", "block-gesperrt: \"Neu\"\n");

        MessageService service = new MessageService(mockPlugin(configWithLanguage("language: de")));

        assertTrue(Files.isRegularFile(dataFolder.resolve("messages.yml")));
        assertFalse(Files.exists(dataFolder.resolve("messages.yml.old")));
        assertEquals("Neu", service.get("block-gesperrt"));
    }

    @Test
    @DisplayName("reload() uebernimmt eine geaenderte Sprache")
    void reload_wechseltSprache() {
        ChestSecurePlugin plugin = mockPlugin(configWithLanguage("language: en"));
        MessageService service = new MessageService(plugin);
        assertEquals("Block locked.", service.get("block-gesperrt"));

        when(plugin.cfg()).thenReturn(configWithLanguage("language: de"));
        service.reload();

        assertEquals("Block gesperrt.", service.get("block-gesperrt"));
    }
}
