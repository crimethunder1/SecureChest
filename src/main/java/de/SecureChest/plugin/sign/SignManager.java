package de.SecureChest.plugin.sign;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.ChunkIndex;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.SecureHopperEntry;

import net.kyori.adventure.text.Component;

public final class SignManager implements Listener {

    static final int SPAWN_BATCH_SIZE = 50;
    static final int CHANGE_BATCH_SIZE = 50;
    static final int COVER_CHECK_BATCH_SIZE = 50;
    static final long COVER_CHECK_INTERVAL_TICKS = 100L;

    private record TextChunk(UUID worldUuid, int chunkX, int chunkZ) {

        static TextChunk of(NameGroup group) {
            NameTextPosition position = group.textPosition();
            return new TextChunk(position.worldUuid(), position.chunkX(), position.chunkZ());
        }

        static TextChunk of(LockKey field) {
            return new TextChunk(field.worldUuid(), ChunkIndex.chunkCoordinate(field.x()),
                    ChunkIndex.chunkCoordinate(field.z()));
        }
    }

    private record GroupText(NameGroup group, String ownerName, UUID entityId, boolean hidden, boolean covered) {}

    private final ChestSecurePlugin plugin;
    private final TextFieldCover textFieldCover;

    private final Set<LockKey> changedFields = ConcurrentHashMap.newKeySet();
    private final Set<UUID> changedOwners = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean processingScheduled = new AtomicBoolean();

    private final Map<String, GroupText> groupTexts = new ConcurrentHashMap<>();
    private final Map<LockKey, String> groupIdByField = new ConcurrentHashMap<>();
    private final Map<TextChunk, Set<String>> groupIdsByTextChunk = new ConcurrentHashMap<>();
    private final Map<LockKey, Set<String>> groupIdsByTextField = new ConcurrentHashMap<>();

    private final Deque<String> coverCheckQueue = new ArrayDeque<>();
    private BukkitTask coverCheckTimer;
    private BukkitTask coverCheckBatchTask;

    public SignManager(ChestSecurePlugin plugin) {
        this(plugin, Material::isSolid);
    }

    SignManager(ChestSecurePlugin plugin, Predicate<Material> solidMaterial) {
        this.plugin = plugin;
        this.textFieldCover = new TextFieldCover(solidMaterial);
    }

    public boolean isEnabled() {
        return plugin.ownerNameDisplay().isEnabled();
    }

    private boolean isHideWhenCoveredEnabled() {
        return plugin.ownerNameDisplay().isHideWhenCoveredEnabled();
    }

    private boolean isCoverCheckActive() {
        return isEnabled() && isHideWhenCoveredEnabled();
    }

    private boolean isPluginEnabled() {
        return plugin.isEnabled();
    }

    public void spawnSign(LockKey key, String ownerName) {
        markChanged(key);
    }

    public void removeSign(String lockId) {
        LockKey key;
        try {
            key = LockKey.fromId(lockId);
        } catch (RuntimeException ex) {
            return;
        }
        markChanged(key);
    }

    public void markChanged(LockKey position) {
        if (!isEnabled()) return;
        changedFields.add(position);
        scheduleProcessing();
    }

    public void refreshOwnerSigns(UUID owner) {
        if (!isEnabled()) return;
        changedOwners.add(owner);
        scheduleProcessing();
    }

    boolean hasLiveSign(String groupId) {
        GroupText groupText = groupTexts.get(groupId);
        return groupText != null && isLive(groupText.entityId());
    }

    Set<String> trackedGroupIds() {
        return Set.copyOf(groupTexts.keySet());
    }

    Set<String> hiddenGroupIds() {
        Set<String> groupIds = new HashSet<>();
        for (GroupText groupText : groupTexts.values()) {
            if (groupText.hidden()) groupIds.add(groupText.group().id());
        }
        return groupIds;
    }

    Set<LockKey> trackedTextFields() {
        return Set.copyOf(groupIdsByTextField.keySet());
    }

    Set<LockKey> trackedFields() {
        return Set.copyOf(groupIdByField.keySet());
    }

    int trackedTextChunkCount() {
        return groupIdsByTextChunk.size();
    }

    Set<UUID> trackedEntityIds() {
        Set<UUID> entityIds = new HashSet<>();
        for (GroupText groupText : groupTexts.values()) {
            if (groupText.entityId() != null) entityIds.add(groupText.entityId());
        }
        return entityIds;
    }

    int pendingChangeCount() {
        return changedFields.size() + changedOwners.size();
    }

    int pendingCoverCheckCount() {
        return coverCheckQueue.size();
    }

    public void removeAllSigns() {
        int removed = 0;
        for (String groupId : List.copyOf(groupTexts.keySet())) {
            removeGroup(groupId);
            removed++;
        }
        groupIdByField.clear();
        groupIdsByTextChunk.clear();
        groupIdsByTextField.clear();
        coverCheckQueue.clear();
        plugin.getLogger().info("SignManager: " + removed + " signs removed.");
    }

    public void spawnAllSigns() {
        if (!isEnabled()) return;
        List<LockKey> fields = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            UUID worldUuid = world.getUID();
            for (Chunk chunk : world.getLoadedChunks()) {
                for (LockEntry entry : plugin.locks().getCachedLocksInChunk(worldUuid, chunk.getX(), chunk.getZ())) {
                    fields.add(entry.key());
                }
                for (SecureHopperEntry hopper
                        : plugin.locks().getCachedHoppersInChunk(worldUuid, chunk.getX(), chunk.getZ())) {
                    fields.add(hopper.hopperKey());
                }
            }
        }
        plugin.getLogger().info("SignManager: " + fields.size() + " locks and secure hoppers in loaded chunks"
                + " marked (batches of " + CHANGE_BATCH_SIZE + " per tick).");
        markAllChanged(fields);
    }

    public void refreshAllGroups() {
        if (!isEnabled()) return;
        List<NameGroup> groups = new ArrayList<>();
        for (GroupText groupText : groupTexts.values()) groups.add(groupText.group());
        groups.sort(Comparator.comparing((NameGroup group) -> group.fields().get(0), NameGroup.FIELD_ORDER));
        final int total = groups.size();
        updateInBatches(groups, () -> plugin.getLogger().info("SignManager: " + total
                + " groups checked again, " + trackedEntityIds().size() + " signs shown (batches of "
                + SPAWN_BATCH_SIZE + ")."));
    }

    private void updateInBatches(List<NameGroup> groups, Runnable afterLastBatch) {
        if (groups.isEmpty() || !isPluginEnabled()) return;

        final int total = groups.size();
        for (int batchStart = 0; batchStart < total; batchStart += SPAWN_BATCH_SIZE) {
            final List<NameGroup> batch = List.copyOf(groups.subList(batchStart,
                    Math.min(batchStart + SPAWN_BATCH_SIZE, total)));
            final boolean lastBatch = batchStart + SPAWN_BATCH_SIZE >= total;
            long delay = batchStart / SPAWN_BATCH_SIZE;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isEnabled()) return;
                List<LockKey> fields = new ArrayList<>();
                for (NameGroup group : batch) fields.addAll(group.fields());
                updateGroups(fields);
                if (lastBatch) afterLastBatch.run();
            }, delay);
        }
    }

    public void startCoverCheck() {
        if (coverCheckTimer != null || !isPluginEnabled()) return;
        coverCheckTimer = Bukkit.getScheduler().runTaskTimer(plugin, this::startCoverCheckRound,
                COVER_CHECK_INTERVAL_TICKS, COVER_CHECK_INTERVAL_TICKS);
    }

    public void stopCoverCheck() {
        if (coverCheckTimer != null) {
            coverCheckTimer.cancel();
            coverCheckTimer = null;
        }
        if (coverCheckBatchTask != null) {
            coverCheckBatchTask.cancel();
            coverCheckBatchTask = null;
        }
        coverCheckQueue.clear();
    }

    void startCoverCheckRound() {
        if (!coverCheckQueue.isEmpty() || !isCoverCheckActive()) return;
        for (Map.Entry<TextChunk, Set<String>> entry : groupIdsByTextChunk.entrySet()) {
            TextChunk chunk = entry.getKey();
            World world = Bukkit.getWorld(chunk.worldUuid());
            if (world == null || !world.isChunkLoaded(chunk.chunkX(), chunk.chunkZ())) continue;
            coverCheckQueue.addAll(entry.getValue());
        }
        runCoverCheckBatch();
    }

    private void runCoverCheckBatch() {
        coverCheckBatchTask = null;
        if (!isCoverCheckActive()) {
            coverCheckQueue.clear();
            return;
        }
        for (int checked = 0; checked < COVER_CHECK_BATCH_SIZE && !coverCheckQueue.isEmpty(); checked++) {
            checkCover(coverCheckQueue.poll());
        }
        if (!coverCheckQueue.isEmpty()) {
            if (!isPluginEnabled()) {
                coverCheckQueue.clear();
                return;
            }
            coverCheckBatchTask = Bukkit.getScheduler().runTaskLater(plugin, this::runCoverCheckBatch, 1L);
        }
    }

    private void checkCover(String groupId) {
        GroupText groupText = groupTexts.get(groupId);
        if (groupText == null) return;
        NameTextPosition position = groupText.group().textPosition();
        LockKey textField = position.textField();
        World world = Bukkit.getWorld(textField.worldUuid());
        if (!textFieldCover.isLoaded(world, textField)) return;
        if (textFieldCover.isCovered(world, position.touchedTextFields()) != groupText.covered()) {
            markAllChanged(groupText.group().fields());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!isEnabled() || !isPluginEnabled()) return;
        World world = e.getWorld();
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(cx, cz)) return;
            List<LockKey> changed = new ArrayList<>();
            for (LockEntry entry : plugin.locks().getCachedLocksInChunk(world.getUID(), cx, cz)) {
                LockKey k = entry.key();
                Block block = world.getBlockAt(k.x(), k.y(), k.z());
                if (!LockUtil.isLockable(block)) {
                    plugin.getLogger().info("[SecureChest] Orphan lock at " + k.asId()
                            + " (block type: " + block.getType() + "), cleaning up.");
                    plugin.locks().forceRemoveLockAt(k);
                    continue;
                }
                changed.add(k);
            }
            for (SecureHopperEntry hopper : plugin.locks().getCachedHoppersInChunk(world.getUID(), cx, cz)) {
                changed.add(hopper.hopperKey());
            }
            Set<String> textGroupIds = groupIdsByTextChunk.get(new TextChunk(world.getUID(), cx, cz));
            if (textGroupIds != null) {
                for (String groupId : List.copyOf(textGroupIds)) {
                    GroupText groupText = groupTexts.get(groupId);
                    if (groupText != null) changed.addAll(groupText.group().fields());
                }
            }
            markAllChanged(changed);
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (groupTexts.isEmpty()) return;
        World world = event.getWorld();
        TextChunk unloading = new TextChunk(world.getUID(), event.getChunk().getX(), event.getChunk().getZ());

        Set<String> candidates = new LinkedHashSet<>();
        Set<String> textGroupIds = groupIdsByTextChunk.get(unloading);
        if (textGroupIds != null) candidates.addAll(textGroupIds);
        for (LockEntry entry : plugin.locks().getCachedLocksInChunk(unloading.worldUuid(),
                unloading.chunkX(), unloading.chunkZ())) {
            addGroupOf(candidates, entry.key());
        }
        for (SecureHopperEntry hopper : plugin.locks().getCachedHoppersInChunk(unloading.worldUuid(),
                unloading.chunkX(), unloading.chunkZ())) {
            addGroupOf(candidates, hopper.hopperKey());
        }

        for (String groupId : candidates) {
            GroupText groupText = groupTexts.get(groupId);
            if (groupText != null && !hasLoadedPartOutside(world, groupText.group(), unloading)) {
                unregister(groupId);
            }
        }
    }

    private void addGroupOf(Set<String> candidates, LockKey field) {
        String groupId = groupIdByField.get(field);
        if (groupId != null) candidates.add(groupId);
    }

    private static boolean hasLoadedPartOutside(World world, NameGroup group, TextChunk unloading) {
        if (isLoadedOutside(world, TextChunk.of(group), unloading)) return true;
        for (LockKey field : group.fields()) {
            if (isLoadedOutside(world, TextChunk.of(field), unloading)) return true;
        }
        return false;
    }

    private static boolean isLoadedOutside(World world, TextChunk chunk, TextChunk unloading) {
        return !chunk.equals(unloading) && world.isChunkLoaded(chunk.chunkX(), chunk.chunkZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!LockUtil.isChestFamily(placed.getType())) return;
        markChanged(LockKey.fromLocation(placed.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTextFieldPlace(BlockPlaceEvent event) {
        markTextField(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTextFieldBreak(BlockBreakEvent event) {
        markTextField(event.getBlock());
    }

    private void markTextField(Block block) {
        if (!isCoverCheckActive()) return;
        Set<String> groupIds = groupIdsByTextField.get(LockKey.fromLocation(block.getLocation()));
        if (groupIds == null) return;
        for (String groupId : List.copyOf(groupIds)) {
            GroupText groupText = groupTexts.get(groupId);
            if (groupText != null) markAllChanged(groupText.group().fields());
        }
    }

    public void validateAndCleanOrphans() {
        if (!isEnabled()) return;
        int cleaned = 0;

        for (LockEntry entry : plugin.locks().getAllCachedLocks()) {
            LockKey k = entry.key();
            World world = Bukkit.getWorld(k.worldUuid());
            if (world == null) continue;
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;

            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (!LockUtil.isLockable(block)) {
                plugin.getLogger().info("[SecureChest] Orphan lock (periodic check) at " + k.asId()
                        + " (block type: " + block.getType() + "), cleaning up.");
                plugin.locks().forceRemoveLockAt(k);
                cleaned++;
            }
        }

        for (SecureHopperEntry entry : plugin.locks().getAllCachedHoppers()) {
            LockKey k = entry.hopperKey();
            World world = Bukkit.getWorld(k.worldUuid());
            if (world == null) continue;
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;

            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (block.getType() != Material.HOPPER) {
                plugin.getLogger().info("[SecureChest] Orphan hopper (periodic check) at " + k.asId()
                        + " (block type: " + block.getType() + "), cleaning up.");
                plugin.locks().forceRemoveHopperAt(k);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().info("[SecureChest] Periodic validation: cleaned up " + cleaned + " orphan(s).");
        }
    }

    private void markAllChanged(Collection<LockKey> positions) {
        if (positions.isEmpty() || !isEnabled()) return;
        changedFields.addAll(positions);
        scheduleProcessing();
    }

    private void scheduleProcessing() {
        if (!isPluginEnabled()) return;
        if (!processingScheduled.compareAndSet(false, true)) return;
        try {
            Bukkit.getScheduler().runTask(plugin, this::processChanges);
        } catch (RuntimeException ex) {
            processingScheduled.set(false);
            throw ex;
        }
    }

    private void processChanges() {
        processingScheduled.set(false);
        Set<UUID> owners = new HashSet<>();
        drainInto(changedOwners, owners, Integer.MAX_VALUE);
        if (!isEnabled()) {
            changedFields.clear();
            return;
        }

        if (!owners.isEmpty()) {
            for (GroupText groupText : groupTexts.values()) {
                if (owners.contains(groupText.group().owner())) changedFields.addAll(groupText.group().fields());
            }
        }
        Set<LockKey> fields = new HashSet<>();
        drainInto(changedFields, fields, CHANGE_BATCH_SIZE);
        if (!fields.isEmpty()) updateGroups(fields);
        if (!changedFields.isEmpty() || !changedOwners.isEmpty()) scheduleProcessing();
    }

    private static <T> void drainInto(Set<T> source, Set<T> target, int limit) {
        Iterator<T> iterator = source.iterator();
        while (target.size() < limit && iterator.hasNext()) {
            target.add(iterator.next());
            iterator.remove();
        }
    }

    private void updateGroups(Collection<LockKey> changed) {
        NameGroupCalculator calculator = newCalculator();
        Deque<LockKey> pending = new ArrayDeque<>();
        for (LockKey field : changed) {
            pending.add(field);
            pending.addAll(NameGroupCalculator.lateralNeighbours(field));
        }

        Set<LockKey> visited = new HashSet<>();
        Set<String> previousGroupIds = new LinkedHashSet<>();
        Map<String, NameGroup> currentGroups = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            LockKey field = pending.poll();
            if (!visited.add(field)) continue;

            String previousGroupId = groupIdByField.get(field);
            if (previousGroupId != null && previousGroupIds.add(previousGroupId)) {
                GroupText previous = groupTexts.get(previousGroupId);
                if (previous != null) pending.addAll(previous.group().fields());
            }

            calculator.groupAt(field).ifPresent(group -> {
                if (currentGroups.putIfAbsent(group.id(), group) == null) pending.addAll(group.fields());
            });
        }

        for (String previousGroupId : previousGroupIds) {
            if (!currentGroups.containsKey(previousGroupId)) removeGroup(previousGroupId);
        }
        for (NameGroup group : currentGroups.values()) showGroup(group);
    }

    private NameGroupCalculator newCalculator() {
        return new NameGroupCalculator(new CachedNameGroupMembers(plugin.locks(), this::doubleChestPartner));
    }

    private LockKey doubleChestPartner(LockEntry lock) {
        LockKey key = lock.key();
        World world = Bukkit.getWorld(key.worldUuid());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return null;
        BlockFace partnerFace = LockUtil.doubleChestPartnerFace(world.getBlockAt(key.x(), key.y(), key.z()));
        if (partnerFace == null) return null;
        return new LockKey(key.worldUuid(), key.x() + partnerFace.getModX(), key.y(), key.z() + partnerFace.getModZ());
    }

    private void showGroup(NameGroup group) {
        boolean hideWhenCovered = isHideWhenCoveredEnabled();
        boolean covered = hideWhenCovered && isTextFieldCovered(group);
        boolean shown = NameVisibilityRules.isShown(isEnabled(), plugin.locks().getShowOwnerName(group.owner()),
                hideWhenCovered, covered);
        GroupText previous = unregister(group.id());
        if (!shown) {
            if (previous != null) removeEntity(previous.entityId());
            register(new GroupText(group, null, null, true, covered));
            return;
        }

        String ownerName = ownerName(group.owner());
        UUID entityId;
        if (previous != null && isUnchanged(previous, group, ownerName)) {
            entityId = previous.entityId();
        } else {
            if (previous != null) removeEntity(previous.entityId());
            entityId = spawnText(group, ownerName);
        }
        register(new GroupText(group, ownerName, entityId, false, covered));
    }

    private boolean isTextFieldCovered(NameGroup group) {
        NameTextPosition position = group.textPosition();
        return textFieldCover.isCovered(Bukkit.getWorld(position.worldUuid()), position.touchedTextFields());
    }

    private boolean isUnchanged(GroupText previous, NameGroup group, String ownerName) {
        return Objects.equals(previous.ownerName(), ownerName)
                && previous.group().textPosition().equals(group.textPosition())
                && isLive(previous.entityId());
    }

    private UUID spawnText(NameGroup group, String ownerName) {
        NameTextPosition position = group.textPosition();
        World world = Bukkit.getWorld(position.worldUuid());
        if (world == null) return null;
        if (!world.isChunkLoaded(position.chunkX(), position.chunkZ())) return null;
        try {
            Location loc = new Location(world, position.x(), position.y(), position.z());
            TextDisplay td = world.spawn(loc, TextDisplay.class, entity -> {
                entity.text(
                    Component.text(plugin.msg().get("sign-owner-display", ownerName))
                );
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setDefaultBackground(true);
                entity.setSeeThrough(false);
                entity.setViewRange(0.45f);
            });
            return td.getUniqueId();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to spawn sign for " + group.id(), ex);
            return null;
        }
    }

    private void register(GroupText groupText) {
        String groupId = groupText.group().id();
        groupTexts.put(groupId, groupText);
        for (LockKey field : groupText.group().fields()) groupIdByField.put(field, groupId);
        groupIdsByTextChunk.computeIfAbsent(TextChunk.of(groupText.group()), chunk -> ConcurrentHashMap.newKeySet())
                .add(groupId);
        for (LockKey textField : groupText.group().textPosition().touchedTextFields()) {
            groupIdsByTextField.computeIfAbsent(textField, field -> ConcurrentHashMap.newKeySet()).add(groupId);
        }
    }

    private GroupText unregister(String groupId) {
        GroupText removed = groupTexts.remove(groupId);
        if (removed == null) return null;
        for (LockKey field : removed.group().fields()) groupIdByField.remove(field, groupId);
        groupIdsByTextChunk.computeIfPresent(TextChunk.of(removed.group()), (chunk, groupIds) -> {
            groupIds.remove(groupId);
            return groupIds.isEmpty() ? null : groupIds;
        });
        for (LockKey textField : removed.group().textPosition().touchedTextFields()) {
            groupIdsByTextField.computeIfPresent(textField, (field, groupIds) -> {
                groupIds.remove(groupId);
                return groupIds.isEmpty() ? null : groupIds;
            });
        }
        return removed;
    }

    private void removeGroup(String groupId) {
        GroupText removed = unregister(groupId);
        if (removed != null) removeEntity(removed.entityId());
    }

    private void removeEntity(UUID entityId) {
        if (entityId == null) return;
        Entity e = Bukkit.getEntity(entityId);
        if (e != null && e.isValid()) e.remove();
    }

    private boolean isLive(UUID entityId) {
        if (entityId == null) return false;
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid();
    }

    private String ownerName(UUID uuid) {
        return plugin.names().resolve(uuid);
    }
}
