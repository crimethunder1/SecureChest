package de.SecureChest.plugin.names;

import de.SecureChest.plugin.lock.LockEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerNameService {

    public static final long UNKNOWN_LAST_SEEN = 0L;

    private final PlayerNameStore store;
    private final Function<UUID, String> serverCacheLookup;
    private final Executor asyncExecutor;
    private final Executor mainThreadExecutor;
    private final Consumer<UUID> ownerSignRefresher;
    private final LongSupplier clock;
    private final Logger logger;
    private final PlayerNameResolver resolver;

    private final Map<UUID, String> storedNames = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> serverCacheNames = new ConcurrentHashMap<>();
    private final List<Runnable> afterLoadTasks = new ArrayList<>();
    private volatile boolean loaded;

    public PlayerNameService(PlayerNameStore store,
                             Function<UUID, String> onlineLookup,
                             Function<UUID, String> serverCacheLookup,
                             Executor asyncExecutor,
                             Executor mainThreadExecutor,
                             Consumer<UUID> ownerSignRefresher,
                             LongSupplier clock,
                             Logger logger) {
        this.store              = store;
        this.serverCacheLookup  = serverCacheLookup;
        this.asyncExecutor      = asyncExecutor;
        this.mainThreadExecutor = mainThreadExecutor;
        this.ownerSignRefresher = ownerSignRefresher;
        this.clock              = clock;
        this.logger             = logger;
        this.resolver = new PlayerNameResolver(onlineLookup, this::serverCacheName, storedNames::get);
    }

    public String resolve(UUID playerUuid) {
        return resolver.resolve(playerUuid);
    }

    public void loadAsync() {
        asyncExecutor.execute(() -> {
            Map<UUID, String> loadedNames;
            try {
                loadedNames = store.loadAll();
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Loading player names failed", ex);
                loadedNames = Map.of();
            }
            final Map<UUID, String> result = loadedNames;
            mainThreadExecutor.execute(() -> finishLoading(result));
        });
    }

    private void finishLoading(Map<UUID, String> loadedNames) {
        loadedNames.forEach((playerUuid, name) -> {
            if (PlayerNameResolver.isUsable(name)) storedNames.putIfAbsent(playerUuid, name);
        });
        loaded = true;
        logger.info("Player names: " + storedNames.size() + " names loaded.");
        List<Runnable> tasks = List.copyOf(afterLoadTasks);
        afterLoadTasks.clear();
        tasks.forEach(Runnable::run);
    }

    public void afterLoad(Runnable task) {
        if (loaded) {
            task.run();
            return;
        }
        afterLoadTasks.add(task);
    }

    public void recordJoin(UUID playerUuid, String currentName) {
        if (!PlayerNameResolver.isUsable(currentName)) return;
        serverCacheNames.remove(playerUuid);
        String previousName = storedNames.put(playerUuid, currentName);
        if (currentName.equals(previousName)) return;
        store.save(playerUuid, currentName, clock.getAsLong());
        ownerSignRefresher.accept(playerUuid);
    }

    public int backfillFromServerCache(Collection<UUID> playerUuids) {
        Map<UUID, String> added = new LinkedHashMap<>();
        for (UUID playerUuid : playerUuids) {
            if (storedNames.containsKey(playerUuid)) continue;
            String name = serverCacheName(playerUuid);
            if (name == null) continue;
            if (storedNames.putIfAbsent(playerUuid, name) == null) added.put(playerUuid, name);
        }
        store.saveAll(added, UNKNOWN_LAST_SEEN);
        if (!added.isEmpty()) {
            logger.info("Player names: " + added.size() + " names taken from the server cache.");
        }
        return added.size();
    }

    public static Set<UUID> playersOf(Collection<LockEntry> locks) {
        Set<UUID> players = new LinkedHashSet<>();
        for (LockEntry lock : locks) {
            players.add(lock.owner());
            players.addAll(lock.trusted().keySet());
        }
        return players;
    }

    private String serverCacheName(UUID playerUuid) {
        return serverCacheNames.computeIfAbsent(playerUuid,
                uuid -> Optional.ofNullable(serverCacheLookup.apply(uuid)).filter(PlayerNameResolver::isUsable))
                .orElse(null);
    }
}
