package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

final class PingHighlight {

    static final float              SCALE       = 1.02f;
    static final float              OFFSET      = (1.0f - SCALE) / 2.0f;
    static final float              VIEW_RANGE  = 2.0f;
    static final Color              GLOW_COLOR  = Color.fromRGB(0xFFAA00);
    static final Display.Brightness BRIGHTNESS  = new Display.Brightness(15, 15);

    private record ShownDisplay(UUID entityId, int x, int y, int z) {}

    private final ChestSecurePlugin plugin;
    private final LockKey target;
    private final List<ShownDisplay> shownDisplays = new ArrayList<>();
    private boolean spawnFailureLogged;

    PingHighlight(ChestSecurePlugin plugin, LockKey target) {
        this.plugin = plugin;
        this.target = target;
    }

    void update(World world, Player player) {
        List<Block> highlightedBlocks = highlightedBlocks(world);
        if (highlightedBlocks.isEmpty()) {
            remove();
            return;
        }
        if (isShowing(highlightedBlocks)) return;
        remove();
        for (Block block : highlightedBlocks) {
            spawn(world, block, player);
        }
    }

    void remove() {
        for (ShownDisplay shownDisplay : shownDisplays) {
            Entity entity = Bukkit.getEntity(shownDisplay.entityId());
            if (entity != null && entity.isValid()) entity.remove();
        }
        shownDisplays.clear();
    }

    int displayCount() {
        return shownDisplays.size();
    }

    private List<Block> highlightedBlocks(World world) {
        if (!isAreaLoaded(world)) return List.of();
        Block block = world.getBlockAt(target.x(), target.y(), target.z());
        if (!LockUtil.isLockable(block) || plugin.locks().getLock(block).isEmpty()) return List.of();
        Block partner = LockUtil.findDoubleChestPartner(block);
        return partner == null ? List.of(block) : List.of(block, partner);
    }

    private boolean isAreaLoaded(World world) {
        int x = target.x();
        int z = target.z();
        return world.isChunkLoaded(x >> 4, z >> 4)
                && world.isChunkLoaded((x - 1) >> 4, z >> 4)
                && world.isChunkLoaded((x + 1) >> 4, z >> 4)
                && world.isChunkLoaded(x >> 4, (z - 1) >> 4)
                && world.isChunkLoaded(x >> 4, (z + 1) >> 4);
    }

    private boolean isShowing(List<Block> highlightedBlocks) {
        if (shownDisplays.size() != highlightedBlocks.size()) return false;
        for (int index = 0; index < highlightedBlocks.size(); index++) {
            Block block = highlightedBlocks.get(index);
            ShownDisplay shownDisplay = shownDisplays.get(index);
            if (shownDisplay.x() != block.getX() || shownDisplay.y() != block.getY()
                    || shownDisplay.z() != block.getZ()) return false;
            Entity entity = Bukkit.getEntity(shownDisplay.entityId());
            if (entity == null || !entity.isValid()) return false;
        }
        return true;
    }

    private void spawn(World world, Block block, Player player) {
        try {
            BlockData blockData = block.getBlockData();
            Location location = new Location(world, block.getX(), block.getY(), block.getZ());
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setTransformation(new Transformation(
                        new Vector3f(OFFSET, OFFSET, OFFSET), new AxisAngle4f(),
                        new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
                entity.setGlowing(true);
                entity.setGlowColorOverride(GLOW_COLOR);
                entity.setBrightness(BRIGHTNESS);
                entity.setViewRange(VIEW_RANGE);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            shownDisplays.add(new ShownDisplay(display.getUniqueId(), block.getX(), block.getY(), block.getZ()));
            player.showEntity(plugin, display);
        } catch (Exception exception) {
            if (spawnFailureLogged) return;
            spawnFailureLogged = true;
            plugin.getLogger().log(Level.WARNING, "Failed to spawn ping highlight for " + target.asId(), exception);
        }
    }
}
