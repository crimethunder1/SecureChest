package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ChestSecurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the compass-based ping navigation system.
 *
 * When a player pings a storage location:
 *  - A lodestone compass is placed into the first free hotbar slot.
 *  - If all hotbar slots are occupied, slot 8 is temporarily displaced;
 *    the displaced item is stored and returned 1:1 when the ping ends.
 *  - A repeating task sends an action-bar distance indicator every 0.5 s.
 *  - When the player comes within 5 blocks the compass is removed and the
 *    original item (if any was displaced) is restored exactly.
 *  - Ping is also cleaned up on player disconnect.
 */
public final class PingManager {

    private static final double ARRIVE_RADIUS   = 5.0;
    private static final long   CHECK_INTERVAL  = 10L;   // ticks (~0.5 s)
    private static final long   START_DELAY     = 5L;    // ticks

    // -----------------------------------------------------------------------

    private record PingState(
            Location     target,
            int          compassSlot,
            ItemStack    displaced,   // may be null if slot was empty
            BukkitTask   task
    ) {}

    private final Map<UUID, PingState> active = new ConcurrentHashMap<>();
    private final ChestSecurePlugin    plugin;

    public PingManager(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Start (or restart) a ping for {@code player} pointing at {@code target}. */
    public void startPing(Player player, Location target) {
        // Cancel any previous ping first (restores displaced item if needed)
        cancelPing(player);

        int slot;
        ItemStack displaced = null;

        // Find a free hotbar slot (0-8); prefer empty slots.
        int freeSlot = findFreeHotbarSlot(player);
        if (freeSlot >= 0) {
            slot = freeSlot;
        } else {
            // All slots occupied – displace slot 8 temporarily.
            slot      = 8;
            displaced = player.getInventory().getItem(slot);
            if (displaced != null) displaced = displaced.clone();
        }

        // Build the tracking compass.
        ItemStack compass = buildCompass(target);

        // Place compass (overwrites displaced slot if needed).
        player.getInventory().setItem(slot, compass);

        final int          finalSlot      = slot;
        final ItemStack    finalDisplaced = displaced;
        final UUID         uuid           = player.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player online = Bukkit.getPlayer(uuid);

                // Player offline → clean up silently (no item restore possible here,
                // onQuit already handles it via cancelPing).
                if (online == null || !online.isOnline()) {
                    cancel();
                    active.remove(uuid);
                    return;
                }

                // Check that the compass is still in the expected slot.
                ItemStack current = online.getInventory().getItem(finalSlot);
                if (current == null || current.getType() != Material.COMPASS) {
                    // Player removed the compass manually – end ping without restoring.
                    cancel();
                    active.remove(uuid);
                    return;
                }

                // Update action-bar.
                if (target.getWorld() != null && target.getWorld().equals(online.getWorld())) {
                    double dist = online.getLocation().distance(target);

                    if (dist <= ARRIVE_RADIUS) {
                        // Arrived!
                        cancel();
                        restoreSlot(online, finalSlot, finalDisplaced);
                        active.remove(uuid);
                        online.sendActionBar(
                            Component.text("✔ Storage erreicht! Kompass entfernt.", NamedTextColor.GREEN)
                        );
                        return;
                    }

                    String direction = bearingLabel(online.getLocation(), target);
                    online.sendActionBar(
                        Component.text("⟶ Storage ", NamedTextColor.AQUA)
                            .append(Component.text(direction + "  ", NamedTextColor.WHITE))
                            .append(Component.text((int) dist + " Blöcke", NamedTextColor.GOLD))
                    );
                } else {
                    online.sendActionBar(
                        Component.text("⟶ Storage befindet sich in einer anderen Welt",
                            NamedTextColor.YELLOW)
                    );
                }
            }
        }.runTaskTimer(plugin, START_DELAY, CHECK_INTERVAL);

        active.put(uuid, new PingState(target, slot, displaced, task));
    }

    /**
     * Cancel an active ping for {@code player}.
     * Restores any displaced item and removes the compass.
     */
    public void cancelPing(Player player) {
        PingState state = active.remove(player.getUniqueId());
        if (state == null) return;

        state.task().cancel();

        // Only restore if player is still online (quit is handled separately by
        // PlayerQuitListener which calls this before the inventory is saved).
        if (player.isOnline()) {
            ItemStack current = player.getInventory().getItem(state.compassSlot());
            if (current != null && current.getType() == Material.COMPASS) {
                restoreSlot(player, state.compassSlot(), state.displaced());
            }
        }
    }

    /** Called by PlayerQuitListener to clean up state without inventory manipulation. */
    public void onPlayerQuit(UUID uuid) {
        PingState state = active.remove(uuid);
        if (state != null) state.task().cancel();
    }

    public boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ItemStack buildCompass(Location target) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta  = (CompassMeta) compass.getItemMeta();

        meta.displayName(
            Component.text("⟶ Storage-Kompass", NamedTextColor.AQUA)
        );
        meta.lore(java.util.List.of(
            Component.text("Zeigt auf dein gesichertes Lager.", NamedTextColor.GRAY),
            Component.text("Wird bei Ankunft (5 Blöcke) entfernt.", NamedTextColor.DARK_GRAY)
        ));

        // Use lodestone mode (no actual lodestone needed; setLodestoneTracked(false)).
        if (target.getWorld() != null) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
        }

        compass.setItemMeta(meta);
        return compass;
    }

    /** Replace {@code slot} with {@code item} (null → air / remove). */
    private void restoreSlot(Player player, int slot, ItemStack item) {
        player.getInventory().setItem(slot, item);
    }

    /** Returns the first empty hotbar slot index (0-8), or -1 if all are occupied. */
    private int findFreeHotbarSlot(Player player) {
        for (int i = 0; i < 9; i++) {
            ItemStack it = player.getInventory().getItem(i);
            if (it == null || it.getType().isAir()) return i;
        }
        return -1;
    }

    /**
     * Returns a short Unicode direction label (e.g. "↗ NO") based on the
     * yaw from {@code from} to {@code to}.
     */
    private String bearingLabel(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        // atan2 in Minecraft coords: Z+ is south, X+ is east.
        double angleDeg = Math.toDegrees(Math.atan2(dx, -dz));
        if (angleDeg < 0) angleDeg += 360.0;

        // 8-point compass
        String[] labels  = { "↑ N", "↗ NO", "→ O", "↘ SO", "↓ S", "↙ SW", "← W", "↖ NW" };
        int      index   = (int) Math.round(angleDeg / 45.0) % 8;
        return labels[index];
    }
}
