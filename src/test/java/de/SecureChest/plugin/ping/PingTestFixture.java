package de.SecureChest.plugin.ping;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.PluginConfig;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class PingTestFixture implements AutoCloseable {

    static final int TARGET_X = 101;
    static final int TARGET_Y = 64;
    static final int TARGET_Z = 205;

    private record ScheduledPing(Runnable runnable, BukkitTask task) {}

    final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    final ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
    final PluginConfig config = mock(PluginConfig.class);
    final LockService locks = mock(LockService.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final Logger logger = mock(Logger.class);
    final UUID worldId = UUID.randomUUID();
    final World world = mock(World.class);
    final World otherWorld = mock(World.class);
    final LockKey target = new LockKey(worldId, TARGET_X, TARGET_Y, TARGET_Z);
    final AtomicLong clock = new AtomicLong(1_000_000L);
    final AtomicBoolean chunkLoaded = new AtomicBoolean(true);
    final Block targetBlock;
    final Chest targetBlockData;
    final List<BlockDisplay> spawnedDisplays = new ArrayList<>();
    final List<Location> spawnLocations = new ArrayList<>();
    final Set<Entity> invalidEntities = new HashSet<>();
    final PingManager manager;

    private final List<ScheduledPing> scheduledPings = new ArrayList<>();
    private final Set<BukkitTask> cancelledTasks = new HashSet<>();
    private final Map<UUID, Entity> entities = new HashMap<>();
    private final List<BlockDisplay> displayPool = new ArrayList<>();
    private RuntimeException spawnFailure;

    PingTestFixture() {
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.msg()).thenReturn(argumentEchoingMessages());
        when(plugin.cfg()).thenReturn(config);
        when(plugin.locks()).thenReturn(locks);
        when(config.pingTimeoutSeconds()).thenReturn(300);
        when(config.pingArrivedSeconds()).thenReturn(15);
        when(config.pingHighlightEnabled()).thenReturn(true);

        when(world.getUID()).thenReturn(worldId);
        when(otherWorld.getUID()).thenReturn(UUID.randomUUID());
        bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class)))
                .thenAnswer(invocation -> entities.get(invocation.getArgument(0, UUID.class)));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(invocation -> chunkLoaded.get());

        targetBlockData = chestData(BlockFace.NORTH, Chest.Type.SINGLE);
        targetBlock = block(TARGET_X, TARGET_Y, TARGET_Z, Material.CHEST, targetBlockData);
        when(world.getBlockAt(TARGET_X, TARGET_Y, TARGET_Z)).thenReturn(targetBlock);
        lockPresent(true);

        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    BukkitTask task = mock(BukkitTask.class);
                    doAnswer(cancel -> cancelledTasks.add(task)).when(task).cancel();
                    scheduledPings.add(new ScheduledPing(invocation.getArgument(1, Runnable.class), task));
                    return task;
                });

        for (int index = 0; index < 12; index++) {
            displayPool.add(createDisplay());
        }
        when(world.spawn(any(Location.class), eq(BlockDisplay.class), anyConfigurator()))
                .thenAnswer(invocation -> {
                    if (spawnFailure != null) throw spawnFailure;
                    BlockDisplay display = displayPool.remove(0);
                    spawnLocations.add(invocation.getArgument(0, Location.class));
                    @SuppressWarnings("unchecked")
                    Consumer<BlockDisplay> configurator = invocation.getArgument(2, Consumer.class);
                    configurator.accept(display);
                    entities.put(display.getUniqueId(), display);
                    spawnedDisplays.add(display);
                    return display;
                });

        manager = new PingManager(plugin, clock::get);
    }

    @Override
    public void close() {
        bukkit.close();
    }

    private static Consumer<? super BlockDisplay> anyConfigurator() {
        return any();
    }

    private static MessageService argumentEchoingMessages() {
        return mock(MessageService.class, invocation -> {
            if (!invocation.getMethod().getName().equals("get")) return null;
            Object[] arguments = invocation.getArguments();
            StringBuilder text = new StringBuilder(String.valueOf(arguments[0]));
            for (int index = 1; index < arguments.length; index++) {
                text.append('|').append(arguments[index]);
            }
            return text.toString();
        });
    }

    private BlockDisplay createDisplay() {
        BlockDisplay display = mock(BlockDisplay.class);
        UUID displayId = UUID.randomUUID();
        when(display.getUniqueId()).thenReturn(displayId);
        when(display.isValid()).thenAnswer(invocation -> !invalidEntities.contains(display));
        doAnswer(invocation -> invalidEntities.add(display)).when(display).remove();
        return display;
    }

    static Chest chestData(BlockFace facing, Chest.Type connection) {
        Chest chestData = mock(Chest.class);
        when(chestData.getFacing()).thenReturn(facing);
        when(chestData.getType()).thenReturn(connection);
        return chestData;
    }

    static Block block(int x, int y, int z, Material material, org.bukkit.block.data.BlockData blockData) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(blockData);
        return block;
    }

    Block makeDoubleChest() {
        when(targetBlockData.getType()).thenReturn(Chest.Type.LEFT);
        Chest partnerData = chestData(BlockFace.NORTH, Chest.Type.RIGHT);
        Block partner = block(TARGET_X + 1, TARGET_Y, TARGET_Z, Material.CHEST, partnerData);
        when(targetBlock.getRelative(BlockFace.EAST)).thenReturn(partner);
        return partner;
    }

    void lockPresent(boolean present) {
        when(locks.getLock(targetBlock)).thenReturn(present
                ? Optional.of(new LockEntry(target, Material.CHEST, UUID.randomUUID(), 0L))
                : Optional.empty());
    }

    void failSpawns(RuntimeException failure) {
        spawnFailure = failure;
    }

    Player player() {
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        moveTo(player, TARGET_X + 0.5, TARGET_Y, TARGET_Z + 0.5 - 20.0, 0.0f);
        return player;
    }

    void moveTo(Player player, double x, double y, double z, float yaw) {
        when(player.getLocation()).thenReturn(new Location(world, x, y, z, yaw, 0.0f));
    }

    void goOffline(Player player) {
        when(player.isOnline()).thenReturn(false);
    }

    void tick() {
        for (ScheduledPing scheduledPing : List.copyOf(scheduledPings)) {
            if (!cancelledTasks.contains(scheduledPing.task())) scheduledPing.runnable().run();
        }
    }

    void advanceSeconds(double seconds) {
        clock.addAndGet((long) (seconds * 1000.0));
    }

    int scheduledCount() {
        return scheduledPings.size();
    }

    BukkitTask task(int index) {
        return scheduledPings.get(index).task();
    }

    boolean isCancelled(int index) {
        return cancelledTasks.contains(task(index));
    }

    static String text(Component component) {
        return ((TextComponent) component).content();
    }

    String lastActionBar(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeast(1)).sendActionBar(captor.capture());
        return text(captor.getValue());
    }

    Component lastActionBarComponent(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeast(1)).sendActionBar(captor.capture());
        return captor.getValue();
    }

    List<String> chatMessages(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(text(component));
        return texts;
    }
}
