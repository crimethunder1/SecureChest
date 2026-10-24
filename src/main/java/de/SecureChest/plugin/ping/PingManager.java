package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.lock.LockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class PingManager {

    static final double ARRIVAL_RADIUS_BLOCKS             = 5.0;
    static final long   ACTION_BAR_UPDATE_INTERVAL_TICKS  = 10L;
    static final long   INITIAL_DELAY_TICKS               = 2L;
    static final double TARGET_CENTER_OFFSET              = 0.5;

    private static final class ActivePing {
        private final UUID playerUuid;
        private final LockKey target;
        private final long startedAtMillis;
        private final long timeoutMillis;
        private final long arrivedLingerMillis;
        private final PingHighlight highlight;
        private long arrivedAtMillis = -1L;
        private BukkitTask updateTask;

        private ActivePing(UUID playerUuid, LockKey target, long startedAtMillis, long timeoutMillis,
                           long arrivedLingerMillis, PingHighlight highlight) {
            this.playerUuid = playerUuid;
            this.target = target;
            this.startedAtMillis = startedAtMillis;
            this.timeoutMillis = timeoutMillis;
            this.arrivedLingerMillis = arrivedLingerMillis;
            this.highlight = highlight;
        }

        private boolean isTimedOut(long nowMillis) {
            return timeoutMillis > 0 && nowMillis - startedAtMillis >= timeoutMillis;
        }

        private void markArrived(long nowMillis) {
            if (arrivedAtMillis < 0) arrivedAtMillis = nowMillis;
        }

        private boolean isArrivalLingerOver(long nowMillis) {
            return arrivedAtMillis >= 0 && nowMillis - arrivedAtMillis >= arrivedLingerMillis;
        }

        private void updateHighlight(World targetWorld, Player player) {
            if (highlight != null) highlight.update(targetWorld, player);
        }

        private void removeHighlight() {
            if (highlight != null) highlight.remove();
        }
    }

    private final Map<UUID, ActivePing> activePings = new ConcurrentHashMap<>();
    private final ChestSecurePlugin plugin;
    private final LongSupplier clock;

    public PingManager(ChestSecurePlugin plugin) {
        this(plugin, System::currentTimeMillis);
    }

    PingManager(ChestSecurePlugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    public void startPing(Player player, LockKey target) {
        stopPing(player);

        PluginConfig config = plugin.cfg();
        ActivePing activePing = new ActivePing(player.getUniqueId(), target, clock.getAsLong(),
                config.pingTimeoutSeconds() * 1000L,
                config.pingArrivedSeconds() * 1000L,
                config.pingHighlightEnabled() ? new PingHighlight(plugin, target) : null);

        activePing.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updatePing(activePing);
            }
        }.runTaskTimer(plugin, INITIAL_DELAY_TICKS, ACTION_BAR_UPDATE_INTERVAL_TICKS);

        activePings.put(activePing.playerUuid, activePing);
    }

    public void stopPing(Player player) {
        ActivePing activePing = activePings.get(player.getUniqueId());
        if (activePing != null) {
            endPing(activePing);
        }
    }

    public boolean isPinging(UUID playerUuid, LockKey target) {
        ActivePing activePing = activePings.get(playerUuid);
        return activePing != null && activePing.target.equals(target);
    }

    public void stopAll() {
        for (ActivePing activePing : List.copyOf(activePings.values())) {
            endPing(activePing);
        }
    }

    int highlightDisplayCount(UUID playerUuid) {
        ActivePing activePing = activePings.get(playerUuid);
        return activePing == null || activePing.highlight == null ? 0 : activePing.highlight.displayCount();
    }

    private void endPing(ActivePing activePing) {
        activePings.remove(activePing.playerUuid, activePing);
        if (activePing.updateTask != null) {
            activePing.updateTask.cancel();
        }
        activePing.removeHighlight();
    }

    private void updatePing(ActivePing activePing) {
        Player onlinePlayer = Bukkit.getPlayer(activePing.playerUuid);
        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
            endPing(activePing);
            return;
        }

        long nowMillis = clock.getAsLong();
        if (activePing.isTimedOut(nowMillis)) {
            endPing(activePing);
            onlinePlayer.sendMessage(Component.text(plugin.msg().get("ping-timed-out"), NamedTextColor.YELLOW));
            return;
        }

        World targetWorld = Bukkit.getWorld(activePing.target.worldUuid());
        if (targetWorld == null) {
            endPing(activePing);
            onlinePlayer.sendMessage(Component.text(plugin.msg().get("ping-world-unloaded"), NamedTextColor.RED));
            return;
        }

        if (!targetWorld.equals(onlinePlayer.getWorld())) {
            activePing.removeHighlight();
            onlinePlayer.sendActionBar(buildOtherWorldActionBarMessage());
            return;
        }

        Location playerLocation = onlinePlayer.getLocation();
        PingDirection direction = PingDirection.calculate(
                playerLocation.getX(), playerLocation.getY(), playerLocation.getZ(), playerLocation.getYaw(),
                activePing.target.x() + TARGET_CENTER_OFFSET,
                activePing.target.y() + TARGET_CENTER_OFFSET,
                activePing.target.z() + TARGET_CENTER_OFFSET);

        if (direction.distance() <= ARRIVAL_RADIUS_BLOCKS) {
            activePing.markArrived(nowMillis);
            onlinePlayer.sendActionBar(buildArrivalActionBarMessage());
        } else {
            onlinePlayer.sendActionBar(buildNavigationActionBarMessage(direction));
        }

        if (activePing.isArrivalLingerOver(nowMillis)) {
            endPing(activePing);
            return;
        }
        activePing.updateHighlight(targetWorld, onlinePlayer);
    }

    private Component buildNavigationActionBarMessage(PingDirection direction) {
        String directionLabel = plugin.msg().get(directionLabelKey(direction.sector()));
        int distanceBlocks = direction.distanceBlocks();
        int heightBlocks = Math.abs(direction.heightDifferenceBlocks());
        return Component.text(switch (direction.heightHint()) {
            case NONE  -> plugin.msg().get("ping-navigation-relative", directionLabel, distanceBlocks);
            case ABOVE -> plugin.msg().get("ping-navigation-relative-above", directionLabel, distanceBlocks, heightBlocks);
            case BELOW -> plugin.msg().get("ping-navigation-relative-below", directionLabel, distanceBlocks, heightBlocks);
        });
    }

    private Component buildArrivalActionBarMessage() {
        return Component.text(plugin.msg().get("ping-angekommen"), NamedTextColor.GREEN);
    }

    private Component buildOtherWorldActionBarMessage() {
        return Component.text(plugin.msg().get("ping-andere-welt"), NamedTextColor.YELLOW);
    }

    static String directionLabelKey(PingDirection.Sector sector) {
        return switch (sector) {
            case AHEAD        -> "ping-direction-ahead";
            case AHEAD_RIGHT  -> "ping-direction-ahead-right";
            case RIGHT        -> "ping-direction-right";
            case BEHIND_RIGHT -> "ping-direction-behind-right";
            case BEHIND       -> "ping-direction-behind";
            case BEHIND_LEFT  -> "ping-direction-behind-left";
            case LEFT         -> "ping-direction-left";
            case AHEAD_LEFT   -> "ping-direction-ahead-left";
        };
    }
}
