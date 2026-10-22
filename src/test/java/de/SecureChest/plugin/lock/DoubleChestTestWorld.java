package de.SecureChest.plugin.lock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class DoubleChestTestWorld {

    private record Position(int x, int y, int z) {}

    private final UUID worldUuid = UUID.randomUUID();
    private final World world = mock(World.class);
    private final Map<Position, Material> materials = new HashMap<>();
    private final Map<Position, BlockData> blockData = new HashMap<>();
    private final Map<Position, Block> blocks = new HashMap<>();

    DoubleChestTestWorld() {
        when(world.getUID()).thenReturn(worldUuid);
        when(world.getName()).thenReturn("world");
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            return blockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        });
    }

    World world() {
        return world;
    }

    Block blockAt(int x, int y, int z) {
        return blocks.computeIfAbsent(new Position(x, y, z), this::createBlock);
    }

    Block setChest(int x, int y, int z, Material material, BlockFace facing, Chest.Type connection) {
        Chest chestData = mock(Chest.class);
        when(chestData.getFacing()).thenReturn(facing);
        when(chestData.getType()).thenReturn(connection);
        Position position = new Position(x, y, z);
        materials.put(position, material);
        blockData.put(position, chestData);
        return blockAt(x, y, z);
    }

    Block setBlock(int x, int y, int z, Material material) {
        Position position = new Position(x, y, z);
        materials.put(position, material);
        blockData.put(position, mock(BlockData.class));
        return blockAt(x, y, z);
    }

    Block setBlock(int x, int y, int z, Material material, BlockData data) {
        Position position = new Position(x, y, z);
        materials.put(position, material);
        blockData.put(position, data);
        return blockAt(x, y, z);
    }

    LockKey keyAt(int x, int y, int z) {
        return new LockKey(worldUuid, x, y, z);
    }

    private Block createBlock(Position position) {
        Block block = mock(Block.class);
        when(block.getType()).thenAnswer(invocation -> materials.getOrDefault(position, Material.AIR));
        when(block.getBlockData()).thenAnswer(invocation -> blockData.get(position));
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(position.x());
        when(block.getY()).thenReturn(position.y());
        when(block.getZ()).thenReturn(position.z());
        when(block.getLocation()).thenAnswer(invocation ->
                new Location(world, position.x(), position.y(), position.z()));
        when(block.getRelative(any(BlockFace.class))).thenAnswer(invocation -> {
            BlockFace face = invocation.getArgument(0);
            return blockAt(position.x() + face.getModX(), position.y() + face.getModY(), position.z() + face.getModZ());
        });
        return block;
    }
}
