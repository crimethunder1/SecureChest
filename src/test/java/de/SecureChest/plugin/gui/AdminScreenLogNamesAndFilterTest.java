package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LogRecord;
import de.SecureChest.plugin.lock.LogRepository;
import de.SecureChest.plugin.names.PlayerNameService;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UiStateStore.LogsActionFilter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AdminScreen: Namen statt UUIDs in den Notizen und weitere Aktionen im Filter des Log-Browsers")
class AdminScreenLogNamesAndFilterTest {

    private static final Path LANGUAGE_FOLDER = Path.of("src", "main", "resources", "lang");
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final List<LogRecord> records = new ArrayList<>();
    private final List<List<String>> renderedLore = new ArrayList<>();
    private ChestSecurePlugin plugin;
    private LockService locks;
    private AdminScreen screen;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(ChestSecurePlugin.class);
        MessageService messages = mock(MessageService.class, invocation -> switch (invocation.getMethod().getName()) {
            case "get" -> "gui-logs-entry-note".equals(invocation.getArgument(0))
                    ? "note:" + invocation.getArgument(1)
                    : invocation.getArgument(0);
            case "getList" -> List.of((String) invocation.getArgument(0));
            default -> null;
        });
        when(plugin.msg()).thenReturn(messages);
        when(plugin.ui()).thenReturn(new UiStateStore());
        PlayerNameService names = mock(PlayerNameService.class);
        when(names.resolve(ALICE)).thenReturn("Alice");
        when(names.resolve(BOB)).thenReturn("Bob");
        when(plugin.names()).thenReturn(names);
        locks = mock(LockService.class);
        when(locks.countLogs(any(), anyLong(), any())).thenAnswer(invocation -> records.size());
        when(locks.listLogs(any(), anyLong(), any(), anyInt(), anyInt())).thenAnswer(invocation -> List.copyOf(records));
        when(plugin.locks()).thenReturn(locks);

        GuiHelper helper = mock(GuiHelper.class);
        when(helper.buildButton(any(), any(), any(), anyString(), any())).thenAnswer(invocation -> {
            if ("log_entry".equals(invocation.getArgument(3))) renderedLore.add(List.copyOf(invocation.getArgument(2)));
            return null;
        });

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(Permissions.ADMIN)).thenReturn(true);
        screen = new AdminScreen(plugin, helper, mock(GuiManager.class));
    }

    private void addRecord(String action, String extra) {
        records.add(new LogRecord(records.size(), System.currentTimeMillis(), action, UUID.randomUUID(),
                "Actor", "world", UUID.randomUUID(), 1, 64, 2, extra));
    }

    private void openLogs() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(invocation -> mock(Inventory.class));
            screen.openLogs(player, 0);
        }
    }

    private String noteLine(int index) {
        return renderedLore.get(index).stream().filter(line -> line.startsWith("note:")).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Notizen zeigen Namen fuer alte und neue Eintraege, die gespeicherten Notizen bleiben unveraendert")
    void notesShowNamesWithoutChangingStoredData() {
        String transferNote = "from=" + ALICE + ",to=" + BOB;
        String areaNote = "owner=" + BOB;
        String trustNote = "target=" + BOB + ",level=VIEW,target_owner=" + ALICE;
        addRecord(LogRepository.ACTION_LOCK_TRANSFER_ADMIN, transferNote);
        addRecord(LogRepository.ACTION_LOCK_REMOVE_AREA, areaNote);
        addRecord(LogRepository.ACTION_TRUST_SET_ADMIN, trustNote);
        addRecord(LogRepository.ACTION_LOCK_REMOVE_ADMIN, "target_owner=kein-uuid");

        openLogs();

        assertEquals("note:from=Alice,to=Bob", noteLine(0));
        assertEquals("note:owner=Bob", noteLine(1));
        assertEquals("note:target=Bob,level=VIEW,target_owner=Alice", noteLine(2));
        assertEquals("note:target_owner=kein-uuid", noteLine(3));
        assertEquals(transferNote, records.get(0).extra());
        assertEquals(areaNote, records.get(1).extra());
        assertEquals(trustNote, records.get(2).extra());
        verify(locks).countLogs(eq("ALL"), anyLong(), any());
        verify(locks).listLogs(eq("ALL"), anyLong(), any(), anyInt(), anyInt());
        verifyNoMoreInteractions(locks);
    }

    @Test
    @DisplayName("Eintrag ohne Notiz: keine Notizzeile, keine Namensaufloesung")
    void entryWithoutNote() {
        addRecord(LogRepository.ACTION_LOCK_CREATE, null);

        openLogs();

        assertTrue(renderedLore.getFirst().stream().noneMatch(line -> line.startsWith("note:")));
        verifyNoMoreInteractions(plugin.names());
    }

    @Test
    @DisplayName("Filter kennt LOCK_REMOVE_AREA, LOCK_TRANSFER_ADMIN, TRUST_SET_ADMIN und TRUST_REMOVE_ADMIN")
    void filterKnowsNewActions() {
        Map<LogsActionFilter, String> expected = Map.of(
                LogsActionFilter.LOCK_REMOVE_AREA, LogRepository.ACTION_LOCK_REMOVE_AREA,
                LogsActionFilter.LOCK_TRANSFER_ADMIN, LogRepository.ACTION_LOCK_TRANSFER_ADMIN,
                LogsActionFilter.TRUST_SET_ADMIN, LogRepository.ACTION_TRUST_SET_ADMIN,
                LogsActionFilter.TRUST_REMOVE_ADMIN, LogRepository.ACTION_TRUST_REMOVE_ADMIN);

        expected.forEach((filter, action) -> assertEquals(action, filter.databaseValue(), filter.name()));
        assertEquals("ALL", LogsActionFilter.ALL.databaseValue());
        assertEquals(LogRepository.ACTION_UNAUTHORIZED_BREAK, LogsActionFilter.UNAUTHORIZED_BREAK.databaseValue());
        assertEquals(LogRepository.ACTION_ADMIN_BREAK, LogsActionFilter.ADMIN_BREAK.databaseValue());
    }

    @Test
    @DisplayName("Umschalten laeuft durch alle sieben Filter und kommt bei ALL wieder an")
    void cycleVisitsAllFilters() {
        List<LogsActionFilter> visited = new ArrayList<>();
        LogsActionFilter current = LogsActionFilter.ALL;
        do {
            visited.add(current);
            current = current.next();
        } while (current != LogsActionFilter.ALL);

        assertEquals(List.of(LogsActionFilter.values()), visited);
        assertEquals(7, visited.size());
    }

    @Test
    @DisplayName("Jede Filterbeschriftung steht in de.yml und en.yml, eindeutig, nicht leer und ohne Platzhalter")
    void filterLabelsInBothLanguageFiles() {
        List<String> labelKeys = new ArrayList<>();
        for (LogsActionFilter filter : LogsActionFilter.values()) labelKeys.add(filter.labelKey());
        assertEquals(labelKeys.size(), labelKeys.stream().distinct().count());

        for (String language : List.of("de", "en")) {
            YamlConfiguration languageFile = YamlConfiguration.loadConfiguration(
                    LANGUAGE_FOLDER.resolve(language + ".yml").toFile());
            for (String key : labelKeys) {
                String value = languageFile.getString(key);
                assertNotNull(value, language + ".yml: fehlender Schluessel " + key);
                assertFalse(value.isBlank(), language + ".yml: leerer Wert bei " + key);
                assertFalse(value.contains("{"), language + ".yml: unerwarteter Platzhalter bei " + key);
            }
        }
    }

    @Test
    @DisplayName("Ein neuer Filter fragt die Datenbank mit seinem Aktionswert ab")
    void selectedFilterIsUsedForQuery() {
        plugin.ui().logs(player.getUniqueId()).action = LogsActionFilter.TRUST_REMOVE_ADMIN;

        openLogs();

        verify(locks).countLogs(eq(LogRepository.ACTION_TRUST_REMOVE_ADMIN), anyLong(), any());
        verify(locks).listLogs(eq(LogRepository.ACTION_TRUST_REMOVE_ADMIN), anyLong(), any(), anyInt(), anyInt());
    }
}
