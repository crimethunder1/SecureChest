package de.SecureChest.plugin.area;

import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockRemovalBatch;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class AreaService {

    private record AreaState(boolean enabled, List<Area> areas) {

        boolean allows(String worldName, int x, int z) {
            return !enabled || containsAny(areas, worldName, x, z);
        }
    }

    private record Impact(List<LockEntry> locks, List<SecureHopperEntry> secureHoppers,
                          Set<UUID> owners, int uncheckedEntries) {

        static final Impact NONE = new Impact(List.of(), List.of(), Set.of(), 0);

        boolean isEmpty() {
            return locks.isEmpty() && secureHoppers.isEmpty();
        }
    }

    private final AreaStore store;
    private final LockService locks;
    private final AreaEnvironment environment;
    private final Logger logger;

    private boolean enabled;
    private List<Area> areas;

    public AreaService(AreaStore store, LockService locks, AreaEnvironment environment, Logger logger) {
        this.store = store;
        this.locks = locks;
        this.environment = environment;
        this.logger = logger;
        AreaStore.Contents contents = store.load();
        this.enabled = contents.enabled();
        this.areas = contents.areas();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Area> areas() {
        return List.copyOf(areas);
    }

    public Optional<Area> find(String name) {
        if (name == null) return Optional.empty();
        String wantedKey = Area.nameKey(name);
        for (Area area : areas) {
            if (Area.nameKey(area.name()).equals(wantedKey)) return Optional.of(area);
        }
        return Optional.empty();
    }

    public boolean isInside(Block block) {
        if (block == null) return false;
        World world = block.getWorld();
        if (world == null) return false;
        return isInside(world.getName(), block.getX(), block.getZ());
    }

    public boolean isInside(String worldName, int x, int z) {
        return containsAny(areas, worldName, x, z);
    }

    public boolean isLockPlacementAllowed(Block block) {
        if (!enabled) return true;
        if (!isInside(block)) return false;
        Block partner = LockUtil.findDoubleChestPartner(block);
        return partner == null || isInside(partner);
    }

    public AreaChangePreview previewEnable() {
        return preview(new AreaChange.Enable());
    }

    public AreaChangePreview previewDisable() {
        return preview(new AreaChange.Disable());
    }

    public AreaChangePreview previewCreate(String name, String worldName, int firstX, int firstZ, int secondX, int secondZ) {
        return preview(new AreaChange.Create(name, worldName, firstX, firstZ, secondX, secondZ));
    }

    public AreaChangePreview previewResize(String name, int firstX, int firstZ, int secondX, int secondZ) {
        return preview(new AreaChange.Resize(name, firstX, firstZ, secondX, secondZ));
    }

    public AreaChangePreview previewDelete(String name) {
        return preview(new AreaChange.Delete(name));
    }

    public AreaChangePreview preview(AreaChange change) {
        List<AreaValidationError> errors = validate(change);
        if (!errors.isEmpty()) return new AreaChangePreview(change, errors, 0, 0, 0);
        Impact impact = computeImpact(currentState(), targetState(change));
        return new AreaChangePreview(change, List.of(),
                impact.locks().size(), impact.secureHoppers().size(), impact.owners().size());
    }

    public AreaChangeResult apply(AreaChange change) {
        List<AreaValidationError> errors = validate(change);
        if (!errors.isEmpty()) return AreaChangeResult.rejected(change, errors);

        AreaState target = targetState(change);
        Impact impact = computeImpact(currentState(), target);
        enabled = target.enabled();
        areas = target.areas();

        LockRemovalBatch removal = impact.isEmpty()
                ? LockRemovalBatch.EMPTY
                : locks.removeOutsideArea(impact.locks(), impact.secureHoppers());

        Map<UUID, int[]> removedCountsByOwner = new LinkedHashMap<>();
        for (LockEntry lock : removal.locks()) {
            removedCountsByOwner.computeIfAbsent(lock.owner(), owner -> new int[2])[0]++;
        }
        for (SecureHopperEntry secureHopper : removal.secureHoppers()) {
            removedCountsByOwner.computeIfAbsent(secureHopper.owner(), owner -> new int[2])[1]++;
        }
        removedCountsByOwner.forEach((owner, counts) -> environment.notifyOwner(owner, counts[0], counts[1]));

        if (impact.uncheckedEntries() > 0) {
            logger.warning(impact.uncheckedEntries() + " locks or secure hoppers are in worlds that are not loaded."
                    + " They were not checked against the areas and stay unchanged.");
        }
        logger.info("Area change " + change + " applied: " + removal.locks().size() + " locks and "
                + removal.secureHoppers().size() + " secure hoppers removed.");

        String saveError = null;
        try {
            store.save(new AreaStore.Contents(enabled, areas));
        } catch (IOException ex) {
            saveError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            logger.warning("Saving " + AreaStore.FILE_NAME + " failed: " + saveError
                    + " The change stays active until the next restart.");
        }

        return new AreaChangeResult(change, true, List.of(),
                removal.locks().size(), removal.secureHoppers().size(), removedCountsByOwner.size(), saveError);
    }

    private List<AreaValidationError> validate(AreaChange change) {
        List<AreaValidationError> errors = new ArrayList<>();
        switch (change) {
            case AreaChange.Enable _, AreaChange.Disable _ -> { }
            case AreaChange.Create create -> {
                if (!Area.isValidName(create.name())) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.NAME_INVALID, create.name()));
                } else if (find(create.name()).isPresent()) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.NAME_ALREADY_USED, create.name()));
                }
                if (create.worldName() == null || create.worldName().isBlank()) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.WORLD_NAME_MISSING, create.worldName()));
                } else if (!environment.worldExists(create.worldName())) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.WORLD_NOT_FOUND, create.worldName()));
                }
            }
            case AreaChange.Resize resize -> {
                if (find(resize.name()).isEmpty()) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.AREA_NOT_FOUND, resize.name()));
                }
            }
            case AreaChange.Delete delete -> {
                if (find(delete.name()).isEmpty()) {
                    errors.add(new AreaValidationError(AreaValidationError.Code.AREA_NOT_FOUND, delete.name()));
                }
            }
        }
        return errors;
    }

    private AreaState currentState() {
        return new AreaState(enabled, areas);
    }

    private AreaState targetState(AreaChange change) {
        return switch (change) {
            case AreaChange.Enable _ -> new AreaState(true, areas);
            case AreaChange.Disable _ -> new AreaState(false, areas);
            case AreaChange.Create create -> {
                List<Area> changedAreas = new ArrayList<>(areas);
                changedAreas.add(Area.normalized(create.name(), create.worldName(),
                        create.firstX(), create.firstZ(), create.secondX(), create.secondZ()));
                yield new AreaState(enabled, List.copyOf(changedAreas));
            }
            case AreaChange.Resize resize -> {
                Area existing = find(resize.name()).orElseThrow();
                Area resized = existing.withCorners(resize.firstX(), resize.firstZ(), resize.secondX(), resize.secondZ());
                List<Area> changedAreas = new ArrayList<>();
                for (Area area : areas) changedAreas.add(area == existing ? resized : area);
                yield new AreaState(enabled, List.copyOf(changedAreas));
            }
            case AreaChange.Delete delete -> {
                Area existing = find(delete.name()).orElseThrow();
                List<Area> changedAreas = new ArrayList<>(areas);
                changedAreas.remove(existing);
                yield new AreaState(enabled, List.copyOf(changedAreas));
            }
        };
    }

    private Impact computeImpact(AreaState before, AreaState after) {
        if (!after.enabled()) return Impact.NONE;

        List<LockEntry> affectedLocks = new ArrayList<>();
        Set<String> affectedLockIds = new HashSet<>();
        Set<UUID> owners = new LinkedHashSet<>();
        int uncheckedEntries = 0;

        for (LockEntry lock : List.copyOf(locks.getAllCachedLocks())) {
            LockKey key = lock.key();
            String worldName = environment.worldName(key.worldUuid());
            if (worldName == null) {
                uncheckedEntries++;
                continue;
            }
            if (before.allows(worldName, key.x(), key.z()) && !after.allows(worldName, key.x(), key.z())) {
                affectedLocks.add(lock);
                affectedLockIds.add(key.asId());
                owners.add(lock.owner());
            }
        }

        List<SecureHopperEntry> affectedSecureHoppers = new ArrayList<>();
        for (SecureHopperEntry secureHopper : List.copyOf(locks.getAllCachedHoppers())) {
            if (!affectedLockIds.contains(secureHopper.boundLock().asId())) {
                LockKey hopperKey = secureHopper.hopperKey();
                String worldName = environment.worldName(hopperKey.worldUuid());
                if (worldName == null) {
                    uncheckedEntries++;
                    continue;
                }
                boolean leavesArea = before.allows(worldName, hopperKey.x(), hopperKey.z())
                        && !after.allows(worldName, hopperKey.x(), hopperKey.z());
                if (!leavesArea) continue;
            }
            affectedSecureHoppers.add(secureHopper);
            owners.add(secureHopper.owner());
        }

        return new Impact(affectedLocks, affectedSecureHoppers, owners, uncheckedEntries);
    }

    private static boolean containsAny(List<Area> candidates, String worldName, int x, int z) {
        if (worldName == null) return false;
        for (Area area : candidates) {
            if (area.contains(worldName, x, z)) return true;
        }
        return false;
    }
}
