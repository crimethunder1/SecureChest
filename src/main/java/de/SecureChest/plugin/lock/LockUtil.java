package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Objects;

public final class LockUtil {

    private LockUtil() {}

    public static boolean isLockable(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        if (m == Material.CHEST) return true;
        if (m == Material.TRAPPED_CHEST) return true;
        if (m == Material.BARREL) return true;
        if (m == Material.HOPPER) return true;
        return m.name().endsWith("SHULKER_BOX");
    }

    public static Block canonicalBlock(Block b) {
        Objects.requireNonNull(b, "block");
        Material t = b.getType();
        if (t == Material.CHEST || t == Material.TRAPPED_CHEST) {
            Block other = findConnectedChest(b);
            if (other != null) {
                Location a = b.getLocation();
                Location c = other.getLocation();
                return isLessOrEqual(a, c) ? b : other;
            }
        }
        return b;
    }

    public static LockKey canonicalKey(Block b) {
        Block canon = canonicalBlock(b);
        return LockKey.fromLocation(canon.getLocation());
    }

    private static Block findConnectedChest(Block b) {
        Material type = b.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return null;

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block rel = b.getRelative(face);
            if (rel.getType() == type) return rel;
        }
        return null;
    }

    private static boolean isLessOrEqual(Location a, Location b) {
        if (a.getBlockX() != b.getBlockX()) return a.getBlockX() < b.getBlockX();
        if (a.getBlockY() != b.getBlockY()) return a.getBlockY() < b.getBlockY();
        return a.getBlockZ() <= b.getBlockZ();
    }

    public static String worldName(World world) {
        return world == null ? "world" : world.getName();
    }
}
