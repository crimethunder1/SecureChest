package de.SecureChest.plugin.names;

import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PlayerNameService: Laden, Beitritt und Altbestand")
class PlayerNameServiceTest {

    private static final long NOW = 1_700_000_000_000L;

    private final UUID alex = UUID.randomUUID();
    private final UUID steve = UUID.randomUUID();
    private final UUID trustedPlayer = UUID.randomUUID();

    private final Map<UUID, String> onlineNames = new HashMap<>();
    private final Map<UUID, String> serverCacheNames = new HashMap<>();
    private final AtomicInteger serverCacheCalls = new AtomicInteger();
    private final List<UUID> refreshedOwners = new ArrayList<>();

    private RecordingPlayerNameStore store;
    private QueuedExecutor async;
    private QueuedExecutor main;
    private PlayerNameService service;

    @BeforeEach
    void setUp() {
        store = new RecordingPlayerNameStore();
        async = new QueuedExecutor(true);
        main = new QueuedExecutor(false);
        service = new PlayerNameService(
                store,
                onlineNames::get,
                uuid -> {
                    serverCacheCalls.incrementAndGet();
                    return serverCacheNames.get(uuid);
                },
                async,
                main,
                refreshedOwners::add,
                () -> NOW,
                mock(Logger.class));
    }

    private void loadFromStore() {
        service.loadAsync();
        async.runAll();
        main.runAll();
    }

    @Test
    @DisplayName("Laden: gespeicherte Namen stehen danach im Speicher")
    void loadFillsStoredNames() {
        store.rows.put(alex, "Alex");

        loadFromStore();

        assertEquals("Alex", service.resolve(alex));
    }

    @Test
    @DisplayName("Laden: leere Namen aus der Datenbank werden ignoriert")
    void loadIgnoresBlankNames() {
        store.rows.put(alex, " ");

        loadFromStore();

        assertEquals(PlayerNameResolver.shortUuid(alex), service.resolve(alex));
    }

    @Test
    @DisplayName("Laden: ein Name aus einem Beitritt waehrend des Ladens wird nicht ueberschrieben")
    void loadDoesNotOverwriteNameFromJoin() {
        store.rows.put(alex, "OldAlex");
        service.loadAsync();
        async.runAll();

        service.recordJoin(alex, "NewAlex");
        main.runAll();

        assertEquals("NewAlex", service.resolve(alex));
    }

    @Test
    @DisplayName("afterLoad: Aufgabe laeuft erst nach dem Laden, danach sofort")
    void afterLoadRunsAfterLoading() {
        List<String> order = new ArrayList<>();
        service.loadAsync();
        service.afterLoad(() -> order.add("first"));
        service.afterLoad(() -> order.add("second"));

        async.runAll();
        assertTrue(order.isEmpty());
        main.runAll();
        assertEquals(List.of("first", "second"), order);

        service.afterLoad(() -> order.add("third"));
        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    @DisplayName("afterLoad: Aufgabe laeuft auch, wenn das Laden fehlschlaegt")
    void afterLoadRunsWhenLoadingFails() {
        store.loadFailure = new IllegalStateException("database broken");
        List<String> order = new ArrayList<>();
        service.afterLoad(() -> order.add("task"));

        loadFromStore();

        assertEquals(List.of("task"), order);
    }

    @Test
    @DisplayName("Keine Datenbankabfrage auf dem Hauptthread: Laden nur im asynchronen Task, Aufloesen nur aus dem Speicher")
    void noDatabaseQueryOnMainThread() {
        store.rows.put(alex, "Alex");

        service.loadAsync();
        assertEquals(PlayerNameResolver.shortUuid(alex), service.resolve(alex));
        assertTrue(store.loadAllInsideAsync.isEmpty());
        assertEquals(1, async.pending());

        async.runAll();
        main.runAll();
        for (int i = 0; i < 100; i++) {
            service.resolve(alex);
            service.resolve(steve);
        }

        assertEquals(List.of(true), store.loadAllInsideAsync);
        assertTrue(store.saves.isEmpty());
        assertEquals(0, async.pending());
    }

    @Test
    @DisplayName("Servercache: Ergebnis wird je UUID nur einmal abgefragt, auch wenn es leer ist")
    void serverCacheLookupIsRemembered() {
        serverCacheNames.put(alex, "Alex");

        service.resolve(alex);
        service.resolve(alex);
        service.resolve(steve);
        service.resolve(steve);

        assertEquals(2, serverCacheCalls.get());
    }

    @Test
    @DisplayName("Beitritt ohne gespeicherten Namen: Speicher, Datenbank und Texte werden aktualisiert")
    void joinWithoutStoredNameSavesAndRefreshes() {
        loadFromStore();

        service.recordJoin(alex, "Alex");

        assertEquals(List.of(new RecordingPlayerNameStore.SavedName(alex, "Alex", NOW)), store.saves);
        assertEquals(List.of(alex), refreshedOwners);
        assertEquals("Alex", service.resolve(alex));
    }

    @Test
    @DisplayName("Beitritt mit neuem Namen: Speicher, Datenbank und Texte werden aktualisiert")
    void joinWithNewNameSavesAndRefreshes() {
        store.rows.put(alex, "OldAlex");
        loadFromStore();

        service.recordJoin(alex, "NewAlex");

        assertEquals(List.of(new RecordingPlayerNameStore.SavedName(alex, "NewAlex", NOW)), store.saves);
        assertEquals(List.of(alex), refreshedOwners);
        assertEquals("NewAlex", service.resolve(alex));
    }

    @Test
    @DisplayName("Beitritt mit gleichem Namen: kein erneutes Schreiben, keine Aktualisierung der Texte")
    void joinWithSameNameDoesNothing() {
        store.rows.put(alex, "Alex");
        loadFromStore();

        service.recordJoin(alex, "Alex");

        assertTrue(store.saves.isEmpty());
        assertTrue(refreshedOwners.isEmpty());
    }

    @Test
    @DisplayName("Beitritt mit leerem Namen wird ignoriert")
    void joinWithBlankNameIgnored() {
        loadFromStore();

        service.recordJoin(alex, " ");
        service.recordJoin(alex, null);

        assertTrue(store.saves.isEmpty());
        assertTrue(refreshedOwners.isEmpty());
    }

    @Test
    @DisplayName("Beitritt verwirft den gemerkten Servercache-Namen, nach dem Verlassen gilt der neue Name")
    void joinForgetsRememberedServerCacheName() {
        serverCacheNames.put(alex, "OldAlex");
        loadFromStore();
        assertEquals("OldAlex", service.resolve(alex));

        onlineNames.put(alex, "NewAlex");
        service.recordJoin(alex, "NewAlex");
        assertEquals("NewAlex", service.resolve(alex));
        onlineNames.remove(alex);
        serverCacheNames.remove(alex);

        assertEquals("NewAlex", service.resolve(alex));
        assertEquals(2, serverCacheCalls.get());
    }

    @Test
    @DisplayName("Altbestand: fehlende Namen werden aus dem Servercache nachgezogen und gespeichert")
    void backfillAddsMissingNames() {
        serverCacheNames.put(alex, "Alex");
        serverCacheNames.put(steve, "Steve");
        loadFromStore();

        int added = service.backfillFromServerCache(List.of(alex, steve));

        assertEquals(2, added);
        assertEquals(1, store.saveAllCalls);
        assertEquals(List.of(
                new RecordingPlayerNameStore.SavedName(alex, "Alex", PlayerNameService.UNKNOWN_LAST_SEEN),
                new RecordingPlayerNameStore.SavedName(steve, "Steve", PlayerNameService.UNKNOWN_LAST_SEEN)),
                store.saves);
        serverCacheNames.clear();
        assertEquals("Alex", service.resolve(alex));
        assertEquals("Steve", service.resolve(steve));
    }

    @Test
    @DisplayName("Altbestand: vorhandene Namen werden nicht ueberschrieben und nicht abgefragt")
    void backfillKeepsExistingNames() {
        store.rows.put(alex, "StoredAlex");
        serverCacheNames.put(alex, "CachedAlex");
        loadFromStore();

        int added = service.backfillFromServerCache(List.of(alex));

        assertEquals(0, added);
        assertTrue(store.saves.isEmpty());
        assertEquals(0, serverCacheCalls.get());
        assertEquals("StoredAlex", store.rows.get(alex));
    }

    @Test
    @DisplayName("Altbestand: unbekannte und leere Namen im Servercache werden uebersprungen")
    void backfillSkipsUnknownNames() {
        serverCacheNames.put(steve, "");
        loadFromStore();

        int added = service.backfillFromServerCache(List.of(alex, steve));

        assertEquals(0, added);
        assertTrue(store.saves.isEmpty());
        assertEquals(PlayerNameResolver.shortUuid(alex), service.resolve(alex));
    }

    @Test
    @DisplayName("playersOf: Besitzer und vertraute Spieler ohne Doppelte")
    void playersOfCollectsOwnersAndTrustedPlayers() {
        LockEntry first = mock(LockEntry.class);
        when(first.owner()).thenReturn(alex);
        when(first.trusted()).thenReturn(Map.of(trustedPlayer, AccessLevel.VIEW));
        LockEntry second = mock(LockEntry.class);
        when(second.owner()).thenReturn(alex);
        when(second.trusted()).thenReturn(Map.of(steve, AccessLevel.STOCKER, trustedPlayer, AccessLevel.VIEW));

        assertEquals(Set.of(alex, steve, trustedPlayer), PlayerNameService.playersOf(List.of(first, second)));
    }
}
