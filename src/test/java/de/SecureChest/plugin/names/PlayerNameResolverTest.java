package de.SecureChest.plugin.names;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlayerNameResolver: Reihenfolge der Namensquellen")
class PlayerNameResolverTest {

    private static final UUID PLAYER = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");

    private final Map<UUID, String> onlineNames = new HashMap<>();
    private final Map<UUID, String> serverCacheNames = new HashMap<>();
    private final Map<UUID, String> storedNames = new HashMap<>();
    private final AtomicInteger serverCacheCalls = new AtomicInteger();
    private final AtomicInteger storedCalls = new AtomicInteger();

    private PlayerNameResolver resolver() {
        Function<UUID, String> serverCache = uuid -> {
            serverCacheCalls.incrementAndGet();
            return serverCacheNames.get(uuid);
        };
        Function<UUID, String> stored = uuid -> {
            storedCalls.incrementAndGet();
            return storedNames.get(uuid);
        };
        return new PlayerNameResolver(onlineNames::get, serverCache, stored);
    }

    @Test
    @DisplayName("Online-Name hat Vorrang vor allen anderen Quellen")
    void onlineNameWins() {
        onlineNames.put(PLAYER, "Online");
        serverCacheNames.put(PLAYER, "Cached");
        storedNames.put(PLAYER, "Stored");

        assertEquals("Online", resolver().resolve(PLAYER));
        assertEquals(0, serverCacheCalls.get());
        assertEquals(0, storedCalls.get());
    }

    @Test
    @DisplayName("Offline: Name aus dem Servercache")
    void serverCacheNameWhenOffline() {
        serverCacheNames.put(PLAYER, "Cached");

        assertEquals("Cached", resolver().resolve(PLAYER));
        assertEquals(0, storedCalls.get());
    }

    @Test
    @DisplayName("Servercache hat Vorrang vor dem gespeicherten Namen")
    void serverCacheBeforeStoredName() {
        serverCacheNames.put(PLAYER, "Cached");
        storedNames.put(PLAYER, "Stored");

        assertEquals("Cached", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Offline ohne Servercache: gespeicherter Name")
    void storedNameWhenOfflineAndNotCached() {
        storedNames.put(PLAYER, "Stored");

        assertEquals("Stored", resolver().resolve(PLAYER));
        assertEquals(1, serverCacheCalls.get());
    }

    @Test
    @DisplayName("Keine Quelle kennt den Namen: gekuerzte UUID")
    void shortUuidWhenNothingKnown() {
        assertEquals("12345678…", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Leerer Name aus dem Servercache wird uebersprungen")
    void emptyServerCacheNameSkipped() {
        serverCacheNames.put(PLAYER, "");
        storedNames.put(PLAYER, "Stored");

        assertEquals("Stored", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Name aus Leerzeichen aus dem Servercache wird uebersprungen")
    void blankServerCacheNameSkipped() {
        serverCacheNames.put(PLAYER, "   ");
        storedNames.put(PLAYER, "Stored");

        assertEquals("Stored", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Leerer Online-Name wird uebersprungen")
    void emptyOnlineNameSkipped() {
        onlineNames.put(PLAYER, "");
        serverCacheNames.put(PLAYER, "Cached");

        assertEquals("Cached", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Leerer gespeicherter Name fuehrt zur gekuerzten UUID")
    void emptyStoredNameFallsBackToShortUuid() {
        storedNames.put(PLAYER, "");

        assertEquals("12345678…", resolver().resolve(PLAYER));
    }

    @Test
    @DisplayName("Gekuerzte UUID entspricht dem bisherigen Text: erste acht Zeichen und Auslassungszeichen")
    void shortUuidMatchesPreviousText() {
        UUID randomPlayer = UUID.randomUUID();

        assertEquals(randomPlayer.toString().substring(0, 8) + "…", PlayerNameResolver.shortUuid(randomPlayer));
        assertEquals(9, PlayerNameResolver.shortUuid(randomPlayer).length());
    }

    @Test
    @DisplayName("isUsable: null, leer und Leerzeichen sind unbrauchbar")
    void isUsable() {
        assertFalse(PlayerNameResolver.isUsable(null));
        assertFalse(PlayerNameResolver.isUsable(""));
        assertFalse(PlayerNameResolver.isUsable("  "));
        assertTrue(PlayerNameResolver.isUsable("Alex"));
    }
}
