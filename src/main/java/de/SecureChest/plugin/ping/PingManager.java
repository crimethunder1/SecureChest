package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ChestSecurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingManager {

    private static final double ARRIVAL_RADIUS_BLOCKS             = 5.0;
    private static final long   ACTION_BAR_UPDATE_INTERVAL_TICKS  = 10L;
    private static final long   INITIAL_DELAY_TICKS               = 2L;

    private record ActivePing(Location targetLocation, BukkitTask updateTask) {}

    private final Map<UUID, ActivePing> activePings = new ConcurrentHashMap<>();
    private final ChestSecurePlugin plugin;

    public PingManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public void startPing(Player player, Location targetLocation) {
        stopPing(player);

        UUID playerUuid = player.getUniqueId();

        BukkitTask updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    cancel();
                    activePings.remove(playerUuid);
                    return;
                }

                if (targetLocation.getWorld() == null
                        || !targetLocation.getWorld().equals(onlinePlayer.getWorld())) {
                    onlinePlayer.sendActionBar(buildOtherWorldActionBarMessage());
                    return;
                }

                double distanceToTarget = onlinePlayer.getLocation().distance(targetLocation);
                if (distanceToTarget <= ARRIVAL_RADIUS_BLOCKS) {
                    cancel();
                    activePings.remove(playerUuid);
                    onlinePlayer.sendActionBar(buildArrivalActionBarMessage());
                    return;
                }

                String directionLabel = calculateDirectionLabel(
                        onlinePlayer.getLocation(), targetLocation);
                onlinePlayer.sendActionBar(
                        buildNavigationActionBarMessage(directionLabel, (int) distanceToTarget));
            }
        }.runTaskTimer(plugin, INITIAL_DELAY_TICKS, ACTION_BAR_UPDATE_INTERVAL_TICKS);

        activePings.put(playerUuid, new ActivePing(targetLocation, updateTask));
    }

    public void stopPing(Player player) {
        ActivePing activePing = activePings.remove(player.getUniqueId());
        if (activePing != null) {
            activePing.updateTask().cancel();
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        ActivePing activePing = activePings.remove(playerUuid);
        if (activePing != null) {
            activePing.updateTask().cancel();
        }
    }

    public boolean hasPing(UUID playerUuid) {
        return activePings.containsKey(playerUuid);
    }

    private Component buildNavigationActionBarMessage(String directionLabel, int distanceBlocks) {
        return Component.text("⟶ ", TextColor.color(0x00CFFF))
                .append(Component.text(directionLabel + "  ", NamedTextColor.WHITE))
                .append(Component.text(distanceBlocks + " Blöcke", TextColor.color(0xFFAA00)));
    }

    private Component buildArrivalActionBarMessage() {
        return Component.text(plugin.msg().get("ping-angekommen"), NamedTextColor.GREEN);
    }

    private Component buildOtherWorldActionBarMessage() {
        return Component.text(plugin.msg().get("ping-andere-welt"), NamedTextColor.YELLOW);
    }

    private String calculateDirectionLabel(Location playerLocation, Location targetLocation) {
        double deltaX = targetLocation.getX() - playerLocation.getX();
        double deltaZ = targetLocation.getZ() - playerLocation.getZ();
        double angleDegrees = Math.toDegrees(Math.atan2(deltaX, -deltaZ));
        if (angleDegrees < 0) {
            angleDegrees += 360.0;
        }
        String[] directionLabels = {"↑ N", "↗ NO", "→ O", "↘ SO", "↓ S", "↙ SW", "← W", "↖ NW"};
        int labelIndex = (int) Math.round(angleDegrees / 45.0) % 8;
        return directionLabels[labelIndex];
    }
}
