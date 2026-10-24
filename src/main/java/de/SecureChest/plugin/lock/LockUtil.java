package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Hopper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        Block partner = findDoubleChestPartner(b);
        if (partner == null) return b;
        Location ownLocation = b.getLocation();
        Location partnerLocation = partner.getLocation();
        return isLessOrEqual(ownLocation, partnerLocation) ? b : partner;
    }

    public static LockKey canonicalKey(Block b) {
        Block canon = canonicalBlock(b);
        return LockKey.fromLocation(canon.getLocation());
    }

    public static List<LockKey> storedKeyCandidates(Block block) {
        Objects.requireNonNull(block, "block");
        LockKey own = LockKey.fromLocation(block.getLocation());
        Block partner = findDoubleChestPartner(block);
        if (partner == null) return List.of(own);

        LockKey partnerKey = LockKey.fromLocation(partner.getLocation());
        boolean ownIsCanonical = isLessOrEqual(block.getLocation(), partner.getLocation());
        return ownIsCanonical ? List.of(own, partnerKey) : List.of(partnerKey, own);
    }

    public static Block findDoubleChestPartner(Block block) {
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return null;

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Chest ownChestData)) return null;

        Chest.Type ownConnection = ownChestData.getType();
        BlockFace ownFacing = ownChestData.getFacing();
        BlockFace partnerFace = partnerFace(ownFacing, ownConnection);
        if (partnerFace == null) return null;

        Block neighbor = block.getRelative(partnerFace);
        if (neighbor.getType() != type) return null;

        BlockData neighborData = neighbor.getBlockData();
        if (!(neighborData instanceof Chest neighborChestData)) return null;
        if (neighborChestData.getFacing() != ownFacing) return null;
        if (neighborChestData.getType() == ownConnection) return null;

        return neighbor;
    }

    public static Block chestKeyHopperBindTarget(LockService locks, Block hopperBlock, UUID playerUuid) {
        if (hopperBlock == null || hopperBlock.getType() != Material.HOPPER) return null;
        if (locks.isLocked(hopperBlock) || locks.isSecureHopper(hopperBlock)) return null;
        if (!(hopperBlock.getBlockData() instanceof Hopper hopperData)) return null;

        Block targetBlock = hopperBlock.getRelative(hopperData.getFacing());
        if (!isLockable(targetBlock)) return null;

        LockEntry targetLock = locks.getLock(targetBlock).orElse(null);
        if (targetLock == null || !targetLock.owner().equals(playerUuid)) return null;
        return targetBlock;
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

        boolean placedAlreadyPointsToSingleNeighbor = placedConnection != Chest.Type.SINGLE
                && neighborConnection == Chest.Type.SINGLE
                && face == partnerFace(placedFacing, placedConnection);

        return alreadyMerged || vanillaWillMerge || placedAlreadyPointsToSingleNeighbor;
    }

    private static BlockFace partnerFace(BlockFace facing, Chest.Type connection) {
        if (connection == Chest.Type.LEFT) return rotateClockwise(facing);
        if (connection == Chest.Type.RIGHT) return rotateCounterClockwise(facing);
        return null;
    }

    private static BlockFace rotateClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> null;
        };
    }

    private static BlockFace rotateCounterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> null;
        };
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
