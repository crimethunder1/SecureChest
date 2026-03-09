package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.util.Items;
import de.SecureChest.plugin.util.Worlds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionListener implements Listener {

    private static final long LOCK_ACTION_COOLDOWN_MS        = 1_000L;
    private static final long BREAK_NOTIFICATION_COOLDOWN_MS = 30_000L;

    private final ChestSecurePlugin plugin;
    private final Worlds worlds;

    private final Map<UUID, Long>   lockActionCooldowns       = new ConcurrentHashMap<>();
    private final Map<String, Long> breakNotificationCooldowns = new ConcurrentHashMap<>();

    public ProtectionListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        this.worlds = new Worlds(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (!worlds.isEnabled(block.getWorld())) return;
        if (!LockUtil.isLockable(block)) return;

        Player player = event.getPlayer();
        ItemStack heldItem = event.getItem();
        boolean isHandEmpty        = (heldItem == null || heldItem.getType().isAir());
        boolean isHoldingChestKey  = plugin.items().is(heldItem, Items.Type.CHEST_KEY);
        boolean isHoldingAdminKey  = player.hasPermission("chestsecure.admin")
                && plugin.items().is(heldItem, Items.Type.ADMIN_KEY);
        boolean isInspectModeActive = plugin.ui().inspect(player.getUniqueId());

        if (isInspectModeActive && player.hasPermission("chestsecure.admin") && player.isSneaking() && isHandEmpty) {
            event.setCancelled(true);
            displayInspectInfo(player, block);
            return;
        }

        if (isHoldingChestKey && block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block)) {
            event.setCancelled(true);
            sendFailFeedback(player, "Dieses Secure Hopper ist bereits geschützt.");
            return;
        }

        if (isHoldingChestKey) {
            event.setCancelled(true);
            if (!consumeActionCooldown(player.getUniqueId())) return;

            boolean isBlockLocked = plugin.locks().isLocked(block);
            if (!isBlockLocked) {
                processLockRequest(player, block);
            } else {
                processUnlockRequest(player, block);
            }
            return;
        }

        boolean isQuickLockEnabled = plugin.locks().getQuickLock(player.getUniqueId());
        boolean isBlockASecureHopper = block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block);
        if (isQuickLockEnabled && isHandEmpty && !plugin.locks().isLocked(block) && !isBlockASecureHopper && playerHasChestKeyInInventory(player)) {
            event.setCancelled(true);
            if (!consumeActionCooldown(player.getUniqueId())) return;
            lockBlockDirectly(player, block);
            return;
        }

        if (plugin.locks().isLocked(block)) {
            boolean playerMayOpen = plugin.locks().canOpen(player, block, isHoldingAdminKey);
            if (!playerMayOpen) {
                event.setCancelled(true);
                String ownerName = plugin.locks().getLock(block)
                        .map(entry -> resolvePlayerName(entry.owner()))
                        .orElse("jemandem");
                sendFailFeedback(player, "Dieser Block gehört " + ownerName + ".");
                return;
            }
        }

        if (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block)) {
            SecureHopperEntry secureHopperEntry = plugin.locks().getSecureHopper(block).orElse(null);
            if (secureHopperEntry != null) {
                boolean playerMayOpenHopper = (player.hasPermission("chestsecure.admin") && isHoldingAdminKey)
                        || secureHopperEntry.owner().equals(player.getUniqueId());
                if (!playerMayOpenHopper) {
                    event.setCancelled(true);
                    sendFailFeedback(player, "Dieses Secure Hopper gehört " + resolvePlayerName(secureHopperEntry.owner()) + ".");
                }
            }
        }
    }

    private boolean consumeActionCooldown(UUID playerUuid) {
        long currentTime    = System.currentTimeMillis();
        Long lastActionTime = lockActionCooldowns.get(playerUuid);
        if (lastActionTime != null && (currentTime - lastActionTime) < LOCK_ACTION_COOLDOWN_MS) {
            return false;
        }
        lockActionCooldowns.put(playerUuid, currentTime);
        return true;
    }

    private void processLockRequest(Player player, Block block) {
        boolean isQuickLockEnabled = plugin.locks().getQuickLock(player.getUniqueId());
        if (isQuickLockEnabled || player.isSneaking()) {
            lockBlockDirectly(player, block);
        } else {
            scheduleLockConfirmationGui(player, block);
        }
    }

    private void lockBlockDirectly(Player player, Block block) {
        boolean success = plugin.locks().lock(player, block);
        if (success) {
            sendSuccessFeedback(player, "Block gesperrt.");
        } else {
            sendFailFeedback(player, "Block ist bereits gesperrt.");
        }
    }

    private void scheduleLockConfirmationGui(Player player, Block block) {
        LockKey blockKey = LockKey.fromLocation(block.getLocation());
        int ttlSeconds   = plugin.getConfig().getInt("ui.confirm_timeout_seconds", 15);
        long expiresAt   = System.currentTimeMillis() + ttlSeconds * 1000L;
        plugin.confirms().set(player.getUniqueId(), new ConfirmRequest(
                ConfirmType.LOCK_BLOCK,
                blockKey.asId(),
                "CLOSE",
                expiresAt
        ));
        String blockTypeName = block.getType().name().replace('_', ' ');
        Bukkit.getScheduler().runTask(plugin, () ->
            plugin.gui().openConfirm(player, "Block sperren",
                List.of(
                    "Diesen " + blockTypeName + " sperren?",
                    "",
                    "Tipp: Schalte Quick Lock ein,",
                    "um diesen Schritt zu überspringen."
                )
            )
        );
    }

    private void processUnlockRequest(Player player, Block block) {
        Optional<LockEntry> lockEntryOptional = plugin.locks().getLock(block);
        if (lockEntryOptional.isEmpty()) {
            sendFailFeedback(player, "Lock-Eintrag nicht gefunden.");
            return;
        }
        LockEntry lockEntry = lockEntryOptional.get();
        boolean playerOwnsLock  = lockEntry.owner().equals(player.getUniqueId());
        boolean playerIsAdmin   = player.hasPermission("chestsecure.admin");
        boolean playerCanUnlock = playerOwnsLock || playerIsAdmin;

        if (!playerCanUnlock) {
            sendFailFeedback(player, "Dieser Block gehört " + resolvePlayerName(lockEntry.owner()) + ".");
            return;
        }

        boolean isQuickRemoveEnabled = plugin.locks().getQuickRemove(player.getUniqueId());
        String lockId = lockEntry.key().asId();
        if (isQuickRemoveEnabled || player.isSneaking()) {
            boolean success = plugin.locks().unlockByIdWithUndo(player, lockId);
            if (success) {
                sendSuccessFeedback(player, "Block entsperrt. Rückgängig: /cs");
            } else {
                sendFailFeedback(player, "Entsperren fehlgeschlagen.");
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.gui().openLockManage(player, lockId));
        }
    }

    private void displayInspectInfo(Player player, Block block) {
        if (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block)) {
            Optional<SecureHopperEntry> hopperEntryOptional = plugin.locks().getSecureHopper(block);
            if (hopperEntryOptional.isEmpty()) {
                player.sendMessage(Component.text("[Inspect] Secure Hopper: DB-Eintrag fehlt.", NamedTextColor.RED));
                return;
            }
            SecureHopperEntry hopperEntry = hopperEntryOptional.get();
            LockKey boundKey = hopperEntry.boundLock();
            World boundWorld = Bukkit.getWorld(boundKey.worldUuid());
            String worldName = boundWorld != null ? boundWorld.getName() : boundKey.worldUuid().toString();
            player.sendMessage(Component.text("[Inspect] Secure Hopper", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Besitzer: " + resolvePlayerName(hopperEntry.owner()), NamedTextColor.AQUA));
            player.sendMessage(Component.text("Ziel: " + worldName + " " + boundKey.x() + " " + boundKey.y() + " " + boundKey.z(), NamedTextColor.AQUA));
            return;
        }

        if (!plugin.locks().isLocked(block)) {
            player.sendMessage(Component.text("[Inspect] Kein Lock.", NamedTextColor.YELLOW));
            return;
        }

        Optional<LockEntry> lockEntryOptional = plugin.locks().getLock(block);
        if (lockEntryOptional.isEmpty()) {
            player.sendMessage(Component.text("[Inspect] Gesperrt, aber Eintrag fehlt.", NamedTextColor.RED));
            return;
        }
        LockEntry lockEntry = lockEntryOptional.get();
        LockKey lockKey = lockEntry.key();
        World lockWorld = Bukkit.getWorld(lockKey.worldUuid());
        String lockWorldName = lockWorld != null ? lockWorld.getName() : lockKey.worldUuid().toString();
        player.sendMessage(Component.text("[Inspect] Gesperrt: " + lockEntry.blockType().name(), NamedTextColor.GOLD));
        player.sendMessage(Component.text("Besitzer: " + resolvePlayerName(lockEntry.owner()), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Position: " + lockWorldName + " " + lockKey.x() + " " + lockKey.y() + " " + lockKey.z(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("Vertraute: " + lockEntry.trusted().size(), NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        if (!worlds.isEnabled(placedBlock.getWorld())) return;
        if (placedBlock.getType() != Material.HOPPER) return;

        ItemStack heldItem = event.getItemInHand();
        if (!plugin.items().is(heldItem, Items.Type.SECURE_HOPPER)) return;

        BlockData blockData = placedBlock.getBlockData();
        if (!(blockData instanceof Hopper hopperData)) {
            event.setCancelled(true);
            sendFailFeedback(event.getPlayer(), "Ungültige Hopper-Daten.");
            return;
        }
        BlockFace facingDirection = hopperData.getFacing();
        Block targetBlock = placedBlock.getRelative(facingDirection);

        if (!LockUtil.isLockable(targetBlock) || !plugin.locks().isLocked(targetBlock)) {
            event.setCancelled(true);
            sendFailFeedback(event.getPlayer(), "Platziere ihn so, dass er auf deinen GESPERRTEN Behälter zeigt.");
            return;
        }

        Optional<LockEntry> targetLockEntry = plugin.locks().getLock(targetBlock);
        if (targetLockEntry.isEmpty() || !targetLockEntry.get().owner().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendFailFeedback(event.getPlayer(), "Du kannst nur an deinen eigenen Lock binden.");
            return;
        }

        boolean bindingSuccess = plugin.locks().bindSecureHopper(event.getPlayer(), placedBlock, targetBlock);
        if (!bindingSuccess) {
            event.setCancelled(true);
            sendFailFeedback(event.getPlayer(), "Secure Hopper konnte nicht gebunden werden.");
            return;
        }
        sendSuccessFeedback(event.getPlayer(), "Secure Hopper gebunden.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceAutoLock(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        if (!worlds.isEnabled(placedBlock.getWorld())) return;
        if (!plugin.getConfig().getBoolean("auto_lock.enabled", false)) return;
        if (!LockUtil.isLockable(placedBlock)) return;

        if (placedBlock.getType() == Material.HOPPER && plugin.items().is(event.getItemInHand(), Items.Type.SECURE_HOPPER)) return;

        boolean sneakOnlyMode = plugin.getConfig().getBoolean("auto_lock.sneak_only", false);
        if (sneakOnlyMode && !event.getPlayer().isSneaking()) return;

        boolean requiresKey = plugin.getConfig().getBoolean("auto_lock.require_key", true);
        if (requiresKey && !playerHasChestKeyInInventory(event.getPlayer())) return;

        if (plugin.locks().isLocked(placedBlock)) return;

        boolean autoLockSuccess = plugin.locks().lock(event.getPlayer(), placedBlock);
        if (autoLockSuccess) {
            sendSuccessFeedback(event.getPlayer(), "Automatisch gesperrt.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (!worlds.isEnabled(block.getWorld())) return;
        if (!LockUtil.isLockable(block) && block.getType() != Material.HOPPER) return;

        boolean isProtected = plugin.locks().isLocked(block) || plugin.locks().isSecureHopper(block);
        if (!isProtected) return;

        if (!plugin.locks().canBreak(event.getPlayer(), block)) {
            event.setCancelled(true);
            sendFailFeedback(event.getPlayer(), "Du kannst diesen geschützten Block nicht abbauen.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!worlds.isEnabled(block.getWorld())) return;

        boolean isLockedBlock   = LockUtil.isLockable(block) && plugin.locks().isLocked(block);
        boolean isSecureHopper  = block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block);
        if (!isLockedBlock && !isSecureHopper) return;

        if (!plugin.locks().canBreak(event.getPlayer(), block)) {
            event.setCancelled(true);
            plugin.locks().logUnauthorizedBreak(event.getPlayer(), block);

            String ownerName = plugin.locks().getLock(block)
                    .map(entry -> resolvePlayerName(entry.owner()))
                    .orElseGet(() -> plugin.locks().getSecureHopper(block)
                            .map(entry -> resolvePlayerName(entry.owner()))
                            .orElse("jemandem"));
            sendFailFeedback(event.getPlayer(), "Dieser Block gehört " + ownerName + ".");
            sendBreakAttemptNotification(event.getPlayer(), block, isLockedBlock, isSecureHopper);
            return;
        }

        if (event.getPlayer().hasPermission("chestsecure.admin")) {
            if (isLockedBlock) {
                LockEntry lockEntry = plugin.locks().getLock(block).orElse(null);
                if (lockEntry != null && !lockEntry.owner().equals(event.getPlayer().getUniqueId())) {
                    plugin.locks().logAdminBreak(event.getPlayer(), block);
                }
            } else if (isSecureHopper) {
                SecureHopperEntry hopperEntry = plugin.locks().getSecureHopper(block).orElse(null);
                if (hopperEntry != null && !hopperEntry.owner().equals(event.getPlayer().getUniqueId())) {
                    plugin.locks().logAdminBreak(event.getPlayer(), block);
                }
            }
        }

        if (isLockedBlock) {
            String lockId = plugin.locks().getLock(block).map(entry -> entry.key().asId()).orElse(null);
            if (lockId != null) {
                plugin.locks().unlockByIdWithUndo(event.getPlayer(), lockId);
            }
        }
        if (isSecureHopper) {
            plugin.locks().removeSecureHopper(block);
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.items().secureHopper());
        }

        if (isLockedBlock) {
            sendSuccessFeedback(event.getPlayer(), "Lock entfernt. Rückgängig: /cs");
        } else {
            sendSuccessFeedback(event.getPlayer(), "Secure Hopper entfernt.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!worlds.isEnabled(event.getLocation().getWorld())) return;
        removeProtectedBlocksFromList(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!worlds.isEnabled(event.getBlock().getWorld())) return;
        removeProtectedBlocksFromList(event.blockList().iterator());
    }

    private void removeProtectedBlocksFromList(Iterator<Block> blockIterator) {
        while (blockIterator.hasNext()) {
            Block block = blockIterator.next();
            if (LockUtil.isLockable(block) && plugin.locks().isLocked(block)) {
                blockIterator.remove();
                continue;
            }
            if (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block)) {
                blockIterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (!worlds.isEnabled(block.getWorld())) return;
        if ((LockUtil.isLockable(block) && plugin.locks().isLocked(block))
                || (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block == null) return;
        if (!worlds.isEnabled(block.getWorld())) return;
        if ((LockUtil.isLockable(block) && plugin.locks().isLocked(block))
                || (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (!worlds.isEnabled(block.getWorld())) return;
        if ((LockUtil.isLockable(block) && plugin.locks().isLocked(block))
                || (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!worlds.isEnabled(event.getBlock().getWorld())) return;
        for (Block block : event.getBlocks()) {
            if ((LockUtil.isLockable(block) && plugin.locks().isLocked(block))
                    || (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!worlds.isEnabled(event.getBlock().getWorld())) return;
        for (Block block : event.getBlocks()) {
            if ((LockUtil.isLockable(block) && plugin.locks().isLocked(block))
                    || (block.getType() == Material.HOPPER && plugin.locks().isSecureHopper(block))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory sourceInventory      = event.getSource();
        Inventory destinationInventory = event.getDestination();
        Block sourceBlock      = inventoryToBlock(sourceInventory);
        Block destinationBlock = inventoryToBlock(destinationInventory);

        boolean sourceIsLocked      = sourceBlock != null && LockUtil.isLockable(sourceBlock) && plugin.locks().isLocked(sourceBlock);
        boolean destinationIsLocked = destinationBlock != null && LockUtil.isLockable(destinationBlock) && plugin.locks().isLocked(destinationBlock);

        if (!sourceIsLocked && !destinationIsLocked) return;

        InventoryHolder sourceHolder      = sourceInventory.getHolder();
        InventoryHolder destinationHolder = destinationInventory.getHolder();

        Block hopperBlock = null;
        if (sourceHolder instanceof org.bukkit.block.Hopper blockHopper) {
            hopperBlock = blockHopper.getBlock();
        } else if (destinationHolder instanceof org.bukkit.block.Hopper blockHopper) {
            hopperBlock = blockHopper.getBlock();
        } else if (sourceHolder instanceof HopperMinecart || destinationHolder instanceof HopperMinecart) {
            event.setCancelled(true);
            return;
        }

        if (hopperBlock == null) {
            event.setCancelled(true);
            return;
        }

        SecureHopperEntry hopperEntry = plugin.locks().getSecureHopper(hopperBlock).orElse(null);
        if (hopperEntry == null) {
            event.setCancelled(true);
            return;
        }

        if (sourceIsLocked) {
            LockKey sourceLockKey = LockUtil.canonicalKey(sourceBlock);
            if (!sourceLockKey.asId().equals(hopperEntry.boundLock().asId())) {
                event.setCancelled(true);
                return;
            }
        }
        if (destinationIsLocked) {
            LockKey destinationLockKey = LockUtil.canonicalKey(destinationBlock);
            if (!destinationLockKey.asId().equals(hopperEntry.boundLock().asId())) {
                event.setCancelled(true);
            }
        }
    }

    private Block inventoryToBlock(Inventory inventory) {
        if (inventory == null) return null;
        Location location = inventory.getLocation();
        if (location == null) return null;
        World world = location.getWorld();
        if (world == null) return null;
        return world.getBlockAt(location);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Block containerBlock = inventoryToBlock(topInventory);
        if (containerBlock == null) return;
        if (!worlds.isEnabled(containerBlock.getWorld())) return;
        if (!LockUtil.isLockable(containerBlock)) return;
        if (!plugin.locks().isLocked(containerBlock)) return;

        AccessLevel accessLevel = plugin.locks().getAccessLevel(player, containerBlock);

        if (accessLevel == null) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }
        if (accessLevel != AccessLevel.VIEW) return;

        InventoryAction clickAction = event.getAction();
        boolean affectsTopInventory =
                event.getClickedInventory() == topInventory
                || event.isShiftClick()
                || clickAction == InventoryAction.COLLECT_TO_CURSOR
                || clickAction == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || clickAction == InventoryAction.HOTBAR_SWAP
                || clickAction == InventoryAction.HOTBAR_MOVE_AND_READD;

        if (affectsTopInventory) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        Block containerBlock = inventoryToBlock(topInventory);
        if (containerBlock == null) return;
        if (!worlds.isEnabled(containerBlock.getWorld())) return;
        if (!LockUtil.isLockable(containerBlock)) return;
        if (!plugin.locks().isLocked(containerBlock)) return;

        AccessLevel accessLevel = plugin.locks().getAccessLevel(player, containerBlock);

        if (accessLevel == null) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }
        if (accessLevel != AccessLevel.VIEW) return;

        int topInventorySize = topInventory.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventorySize) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
    }

    private boolean playerHasChestKeyInInventory(Player player) {
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (plugin.items().is(itemStack, Items.Type.CHEST_KEY)) return true;
        }
        return plugin.items().is(player.getInventory().getItemInOffHand(), Items.Type.CHEST_KEY);
    }

    private void sendBreakAttemptNotification(Player attacker, Block block, boolean isLockedBlock, boolean isSecureHopper) {
        if (!plugin.getConfig().getBoolean("notifications.enabled", true)) return;

        UUID ownerUuid        = null;
        String blockTypeName  = block.getType().name().replace("_", " ").toLowerCase();

        if (isLockedBlock) {
            Optional<de.SecureChest.plugin.lock.LockEntry> lockOpt = plugin.locks().getLock(block);
            if (lockOpt.isPresent()) ownerUuid = lockOpt.get().owner();
        } else if (isSecureHopper) {
            Optional<SecureHopperEntry> hopperOpt = plugin.locks().getSecureHopper(block);
            if (hopperOpt.isPresent()) ownerUuid = hopperOpt.get().owner();
        }
        if (ownerUuid == null || ownerUuid.equals(attacker.getUniqueId())) return;

        String cooldownKey = attacker.getUniqueId() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        long currentTime = System.currentTimeMillis();
        Long lastNotificationTime = breakNotificationCooldowns.get(cooldownKey);
        if (lastNotificationTime != null && (currentTime - lastNotificationTime) < BREAK_NOTIFICATION_COOLDOWN_MS) return;
        breakNotificationCooldowns.put(cooldownKey, currentTime);

        if (breakNotificationCooldowns.size() > 500) {
            breakNotificationCooldowns.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > BREAK_NOTIFICATION_COOLDOWN_MS);
        }

        World blockWorld = block.getWorld();
        String locationString = (blockWorld != null ? blockWorld.getName() : "?") + " " + block.getX() + " " + block.getY() + " " + block.getZ();
        String notificationMessage = attacker.getName() + " hat versucht, dein " + blockTypeName + " bei " + locationString + " zu zerstören";

        final UUID finalOwnerUuid    = ownerUuid;
        final String finalMessage    = notificationMessage;

        Player onlineOwner = Bukkit.getPlayer(ownerUuid);
        if (onlineOwner != null && onlineOwner.isOnline()) {
            onlineOwner.sendMessage(
                Component.text("⚠ [ChestSecure] ", NamedTextColor.RED)
                    .append(Component.text(finalMessage, NamedTextColor.YELLOW))
            );
        } else {
            plugin.locks().addNotification(finalOwnerUuid, finalMessage);
        }
    }

    private String resolvePlayerName(UUID playerUuid) {
        Player onlinePlayer = Bukkit.getPlayer(playerUuid);
        if (onlinePlayer != null) return onlinePlayer.getName();
        org.bukkit.OfflinePlayer cachedPlayer = Bukkit.getOfflinePlayerIfCached(playerUuid.toString());
        if (cachedPlayer != null && cachedPlayer.getName() != null) return cachedPlayer.getName();
        return playerUuid.toString().substring(0, 8);
    }

    private void sendSuccessFeedback(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0, 0), 8, 0.2, 0.4, 0.2, 0.01);
        }
    }

    private void sendFailFeedback(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1.0, 0), 6, 0.1, 0.2, 0.1, 0.01);
        }
    }
}
