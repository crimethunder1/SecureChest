package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.lock.LogRepository;
import de.SecureChest.plugin.ui.UiStateStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminScreen: Symbol und Beschriftung der Eintraege im Log-Browser")
class AdminScreenLogEntryTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final String DEFAULT_LABEL_KEY = "gui-logs-entry-action-other";

    private record RenderedEntry(String action, Material icon, String label) {}

    private final List<LogRecord> records = new ArrayList<>();
    private final List<RenderedEntry> rendered = new ArrayList<>();
    private MessageService messages;
    private AdminScreen screen;
    private Player player;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        messages = mock(MessageService.class, invocation -> switch (invocation.getMethod().getName()) {
            case "get" -> "gui-logs-entry-name".equals(invocation.getArgument(0))
                    ? invocation.getArgument(1)
                    : invocation.getArgument(0);
            case "getList" -> List.of((String) invocation.getArgument(0));
            default -> null;
        });
        when(plugin.msg()).thenReturn(messages);
        when(plugin.ui()).thenReturn(new UiStateStore());
        LockService locks = mock(LockService.class);
        when(locks.countLogs(any(), anyLong(), any())).thenAnswer(invocation -> records.size());
        when(locks.listLogs(any(), anyLong(), any(), anyInt(), anyInt())).thenAnswer(invocation -> List.copyOf(records));
        when(plugin.locks()).thenReturn(locks);

        GuiHelper helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            if ("log_entry".equals(invocation.getArgument(3))) {
                LogRecord record = records.get(Integer.parseInt(invocation.getArgument(4)));
                rendered.add(new RenderedEntry(record.action(), invocation.getArgument(0), invocation.getArgument(1)));
            }
            return null;
        });

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        screen = new AdminScreen(plugin, helper, mock(GuiManager.class));
    }

    private static List<String> actionConstants() throws IllegalAccessException {
        List<String> actions = new ArrayList<>();
        for (Field field : LogRepository.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) continue;
            if (field.getType() != String.class || !field.getName().startsWith("ACTION_")) continue;
            actions.add((String) field.get(null));
        }
        return actions;
    }

    private List<RenderedEntry> render(List<String> actions) {
        for (String action : actions) {
            records.add(new LogRecord(records.size(), System.currentTimeMillis(), action, UUID.randomUUID(),
                    "Actor", "world", UUID.randomUUID(), 1, 64, 2, null));
        }
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screen.openLogs(player, 0);
        }
        assertEquals(actions.size(), rendered.size(), "Jeder Datensatz muss genau einen Eintrag ergeben");
        return List.copyOf(rendered);
    }

    @Test
    @DisplayName("Jede ACTION_-Konstante aus LogRepository hat einen eigenen Zweig, nicht den Standardzweig")
    void jedeAktionskonstante_eigenerZweig() throws IllegalAccessException {
        List<String> actions = actionConstants();
        assertTrue(actions.size() >= 15, "Erwartet mindestens 15 Konstanten, gefunden: " + actions);

        List<RenderedEntry> entries = render(actions);

        for (RenderedEntry entry : entries) {
            assertNotEquals(Material.PAPER, entry.icon(), "Standardsymbol bei " + entry.action());
            assertNotEquals(DEFAULT_LABEL_KEY, entry.label(), "Standardbeschriftung bei " + entry.action());
            assertTrue(entry.label().startsWith("gui-logs-entry-action-"),
                    "Unerwarteter Schluessel bei " + entry.action() + ": " + entry.label());
        }
        assertEquals(actions.size(), new HashSet<>(entries.stream().map(RenderedEntry::label).toList()).size(),
                "Jede Aktion braucht eine eigene Beschriftung: " + entries);
    }

    @Test
    @DisplayName("Jede Beschriftung einer ACTION_-Konstante steht in de.yml und en.yml, nicht leer und ohne Platzhalter")
    void jedeBeschriftung_inBeidenSprachdateien() throws IllegalAccessException {
        List<RenderedEntry> entries = render(actionConstants());

        for (String language : List.of("de", "en")) {
            YamlConfiguration languageFile = YamlConfiguration.loadConfiguration(
                    LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
            for (RenderedEntry entry : entries) {
                String value = languageFile.getString(entry.label());
                assertNotNull(value, language + ".yml: fehlender Schluessel " + entry.label());
                assertFalse(value.isBlank(), language + ".yml: leerer Wert bei " + entry.label());
                assertFalse(value.contains("{"), language + ".yml: unerwarteter Platzhalter bei " + entry.label());
            }
        }
    }

    @Test
    @DisplayName("LOCK_REMOVE_AREA hat eine eindeutige Beschriftung")
    void lockRemoveArea_eindeutigeBeschriftung() {
        List<RenderedEntry> entries = render(List.of(LogRepository.ACTION_LOCK_REMOVE_AREA));

        assertEquals("gui-logs-entry-action-lock-remove-area", entries.getFirst().label());
        assertEquals(Material.YELLOW_GLAZED_TERRACOTTA, entries.getFirst().icon());
    }

    @Test
    @DisplayName("UNAUTHORIZED_BREAK und ADMIN_BREAK behalten Symbol und Beschriftung")
    void bestehendeAktionen_unveraendert() {
        List<RenderedEntry> entries = render(List.of(
                LogRepository.ACTION_UNAUTHORIZED_BREAK, LogRepository.ACTION_ADMIN_BREAK));

        assertEquals(Material.RED_CONCRETE, entries.get(0).icon());
        assertEquals("gui-logs-entry-action-unauthorized-break", entries.get(0).label());
        assertEquals(Material.ORANGE_CONCRETE, entries.get(1).icon());
        assertEquals("gui-logs-entry-action-admin-break", entries.get(1).label());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCK", "UNLOCK", "SOMETHING_NEW"})
    @DisplayName("Unbekannte Aktion faellt in den Standardzweig: Papier und Rohname")
    void unbekannteAktion_standardzweig(String action) {
        List<RenderedEntry> entries = render(List.of(action));

        assertEquals(Material.PAPER, entries.getFirst().icon());
        assertEquals(DEFAULT_LABEL_KEY, entries.getFirst().label());
        verify(messages).get(eq(DEFAULT_LABEL_KEY), eq(action));
    }
}
