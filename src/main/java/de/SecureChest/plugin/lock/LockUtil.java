package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

import java.util.Objects;

public final class LockUtil {

    private static final BlockFace[] LATERAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

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
        Material type = b.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            Block partner = findConnectedChest(b);
            if (partner != null) {
                Location ownLocation = b.getLocation();
                Location partnerLocation = partner.getLocation();
                return isLessOrEqual(ownLocation, partnerLocation) ? b : partner;
            }
        }
        return b;
    }

    public static LockKey canonicalKey(Block b) {
        Block canon = canonicalBlock(b);
        return LockKey.fromLocation(canon.getLocation());
    }

    private static Block findConnectedChest(Block block) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return null;

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Chest ownChestData)) return null;
        if (ownChestData.getType() == Chest.Type.SINGLE) return null;

        BlockFace ownFacing = ownChestData.getFacing();
        Chest.Type ownConnection = ownChestData.getType();

        for (BlockFace face : LATERAL_FACES) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != type) continue;

            BlockData neighborData = neighbor.getBlockData();
            if (!(neighborData instanceof Chest neighborChestData)) continue;
            if (neighborChestData.getFacing() != ownFacing) continue;
            if (neighborChestData.getType() == Chest.Type.SINGLE) continue;
            if (neighborChestData.getType() == ownConnection) continue;

            return neighbor;
        }
        return null;
    }

    public static boolean wouldFormDoubleChestWith(Block placedBlock, Block neighborBlock, BlockFace face) {
        Material placedType = placedBlock.getType();
        if (placedType != Material.CHEST && placedType != Material.TRAPPED_CHEST) return false;
        if (neighborBlock.getType() != placedType) return false;

        BlockData placedData = placedBlock.getBlockData();
        BlockData neighborData = neighborBlock.getBlockData();
        if (!(placedData instanceof Chest placedChestData)) return false;
        if (!(neighborData instanceof Chest neighborChestData)) return false;

        BlockFace placedFacing = placedChestData.getFacing();
        if (placedFacing != neighborChestData.getFacing()) return false;

        if (!isConnectionAxis(placedFacing, face)) return false;

        Chest.Type placedConnection = placedChestData.getType();
        Chest.Type neighborConnection = neighborChestData.getType();

        boolean alreadyMerged = placedConnection != Chest.Type.SINGLE
                && neighborConnection != Chest.Type.SINGLE
                && placedConnection != neighborConnection;

        boolean vanillaWillMerge = placedConnection == Chest.Type.SINGLE
                && neighborConnection == Chest.Type.SINGLE;

        return alreadyMerged || vanillaWillMerge;
    }

    private static boolean isConnectionAxis(BlockFace chestFacing, BlockFace candidateFace) {
        return switch (chestFacing) {
            case NORTH, SOUTH -> candidateFace == BlockFace.EAST || candidateFace == BlockFace.WEST;
            case EAST,  WEST  -> candidateFace == BlockFace.NORTH || candidateFace == BlockFace.SOUTH;
            default -> false;
        };
    }

    public static BlockFace[] lateralFaces() {
        return LATERAL_FACES.clone();
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
