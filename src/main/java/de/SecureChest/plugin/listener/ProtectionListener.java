package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockUtil;
import de.SecureChest.plugin.lock.SecureHopperEntry;
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

import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

public final class ProtectionListener implements Listener {

    private final ChestSecurePlugin plugin;
    private final Worlds worlds;

    public ProtectionListener(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        this.worlds = new Worlds(plugin);
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (!worlds.isEnabled(b.getWorld())) return;
        if (!LockUtil.isLockable(b)) return;

        boolean isChestKey = plugin.items().is(e.getItem(), Items.Type.CHEST_KEY);
        
        boolean isAdminKey = e.getPlayer().hasPermission("chestsecure.admin")
                && plugin.items().is(e.getItem(), Items.Type.ADMIN_KEY);

        
        
        boolean inspectMode = plugin.ui().inspect(e.getPlayer().getUniqueId());
        ItemStack handItem = e.getItem();
        boolean emptyHand = (handItem == null || handItem.getType().isAir());
        if (inspectMode && e.getPlayer().hasPermission("chestsecure.admin") && e.getPlayer().isSneaking() && emptyHand && !isChestKey && !isAdminKey) {
            e.setCancelled(true);
            sendInspectInfo(e.getPlayer(), b);
            return;
        }

        
        if (isChestKey && b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b)) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "Secure Hopper is already protected.");
            return;
        }

        
        if (isChestKey) {
            e.setCancelled(true);

            boolean locked = plugin.locks().isLocked(b);
            if (!locked) {
                boolean ok = plugin.locks().lock(e.getPlayer(), b);
                if (ok) {
                    feedbackOk(e.getPlayer(), "Locked.");
                } else {
                    feedbackFail(e.getPlayer(), "Already locked.");
                }
                return;
            }

            
            boolean quickRemove = plugin.locks().getQuickRemove(e.getPlayer().getUniqueId());
            if (e.getPlayer().isSneaking() || quickRemove) {
                boolean ok = plugin.locks().unlock(e.getPlayer(), b);
                if (ok) feedbackOk(e.getPlayer(), "Unlocked.");
                else feedbackFail(e.getPlayer(), "You cannot unlock this.");
                return;
            }

            feedbackFail(e.getPlayer(), "This is locked.");
            return;
        }

        
        boolean quickLock = plugin.locks().getQuickLock(e.getPlayer().getUniqueId());
        
        boolean isSecureHopper = b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b);
        if (quickLock && emptyHand && !plugin.locks().isLocked(b) && !isSecureHopper && hasChestKey(e.getPlayer())) {
            e.setCancelled(true);
            boolean ok = plugin.locks().lock(e.getPlayer(), b);
            if (ok) feedbackOk(e.getPlayer(), "Locked.");
            else feedbackFail(e.getPlayer(), "Already locked.");
            return;
        }

        
        if (plugin.locks().isLocked(b)) {
            boolean allowed = plugin.locks().canOpen(e.getPlayer(), b, isAdminKey);
            if (!allowed) {
                e.setCancelled(true);
                feedbackFail(e.getPlayer(), "Locked.");
                return;
            }
        }

        
        
        if (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b)) {
            SecureHopperEntry shEntry = plugin.locks().getSecureHopper(b).orElse(null);
            if (shEntry != null) {
                boolean allowed = (e.getPlayer().hasPermission("chestsecure.admin") && isAdminKey)
                        || shEntry.owner().equals(e.getPlayer().getUniqueId());
                if (!allowed) {
                    e.setCancelled(true);
                    feedbackFail(e.getPlayer(), "Protected.");
                    return;
                }
            }
        }
    }


    

    private void sendInspectInfo(Player p, Block b) {
        
        if (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b)) {
            Optional<SecureHopperEntry> shOpt = plugin.locks().getSecureHopper(b);
            if (shOpt.isEmpty()) {
                p.sendMessage(Component.text("[Inspect] Secure Hopper: missing DB record.", NamedTextColor.RED));
                return;
            }
            SecureHopperEntry sh = shOpt.get();
            LockKey bound = sh.boundLock();
            World w = Bukkit.getWorld(bound.worldUuid());
            String wn = (w != null ? w.getName() : bound.worldUuid().toString());
            p.sendMessage(Component.text("[Inspect] Secure Hopper", NamedTextColor.GOLD));
            p.sendMessage(Component.text("Owner: " + nameOf(sh.owner()), NamedTextColor.AQUA));
            p.sendMessage(Component.text("Bound: " + wn + " " + bound.x() + " " + bound.y() + " " + bound.z(), NamedTextColor.AQUA));
            return;
        }

        
        if (!plugin.locks().isLocked(b)) {
            p.sendMessage(Component.text("[Inspect] Not locked.", NamedTextColor.YELLOW));
            return;
        }

        Optional<LockEntry> lockOpt = plugin.locks().getLock(b);
        if (lockOpt.isEmpty()) {
            p.sendMessage(Component.text("[Inspect] Locked, but record missing.", NamedTextColor.RED));
            return;
        }
        LockEntry lock = lockOpt.get();
        LockKey key = lock.key();
        World w2 = Bukkit.getWorld(key.worldUuid());
        String wn2 = (w2 != null ? w2.getName() : key.worldUuid().toString());
        p.sendMessage(Component.text("[Inspect] Locked " + lock.blockType().name(), NamedTextColor.GOLD));
        p.sendMessage(Component.text("Owner: " + nameOf(lock.owner()), NamedTextColor.AQUA));
        p.sendMessage(Component.text("Location: " + wn2 + " " + key.x() + " " + key.y() + " " + key.z(), NamedTextColor.AQUA));
        p.sendMessage(Component.text("Trusted: " + lock.trusted().size(), NamedTextColor.AQUA));
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        if (!worlds.isEnabled(placed.getWorld())) return;
        if (placed.getType() != Material.HOPPER) return;

        ItemStack inHand = e.getItemInHand();
        if (!plugin.items().is(inHand, Items.Type.SECURE_HOPPER)) return;

        
        BlockData data = placed.getBlockData();
        if (!(data instanceof Hopper hopperData)) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "Invalid hopper data.");
            return;
        }
        BlockFace face = hopperData.getFacing();
        Block bound = placed.getRelative(face);

        if (!LockUtil.isLockable(bound) || !plugin.locks().isLocked(bound)) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "You must place it facing your LOCKED container.");
            return;
        }

        
        Optional<LockEntry> lock = plugin.locks().getLock(bound);
        if (lock.isEmpty() || !lock.get().owner().equals(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "You can only bind to your own lock.");
            return;
        }

        boolean ok = plugin.locks().bindSecureHopper(e.getPlayer(), placed, bound);
        if (!ok) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "Failed to bind Secure Hopper.");
            return;
        }

        feedbackOk(e.getPlayer(), "Secure Hopper bound.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutoLockPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        if (!worlds.isEnabled(placed.getWorld())) return;
        if (!plugin.getConfig().getBoolean("auto_lock.enabled", false)) return;
        if (!LockUtil.isLockable(placed)) return;

        
        if (placed.getType() == Material.HOPPER && plugin.items().is(e.getItemInHand(), Items.Type.SECURE_HOPPER)) return;

        boolean sneakOnly = plugin.getConfig().getBoolean("auto_lock.sneak_only", false);
        if (sneakOnly && !e.getPlayer().isSneaking()) return;

        boolean requireKey = plugin.getConfig().getBoolean("auto_lock.require_key", true);
        if (requireKey && !hasChestKey(e.getPlayer())) return;

        if (plugin.locks().isLocked(placed)) return;
        boolean ok = plugin.locks().lock(e.getPlayer(), placed);
        if (ok) {
            feedbackOk(e.getPlayer(), "Auto-locked.");
        }
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Block b = e.getBlock();
        if (!worlds.isEnabled(b.getWorld())) return;
        if (!LockUtil.isLockable(b) && b.getType() != Material.HOPPER) return;

        boolean protectedBlock = plugin.locks().isLocked(b) || plugin.locks().isSecureHopper(b);
        if (!protectedBlock) return;

        if (!plugin.locks().canBreak(e.getPlayer(), b)) {
            e.setCancelled(true);
            feedbackFail(e.getPlayer(), "Protected.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!worlds.isEnabled(b.getWorld())) return;

        boolean locked = LockUtil.isLockable(b) && plugin.locks().isLocked(b);
        boolean secureHopper = b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b);
        if (!locked && !secureHopper) return;

        if (!plugin.locks().canBreak(e.getPlayer(), b)) {
            e.setCancelled(true);
            plugin.locks().logUnauthorizedBreak(e.getPlayer(), b);
            feedbackFail(e.getPlayer(), "You cannot break this.");
            return;
        }

        
        if (e.getPlayer().hasPermission("chestsecure.admin")) {
            if (locked) {
                LockEntry le = plugin.locks().getLock(b).orElse(null);
                if (le != null && !le.owner().equals(e.getPlayer().getUniqueId())) {
                    plugin.locks().logAdminBreak(e.getPlayer(), b);
                }
            } else if (secureHopper) {
                SecureHopperEntry sh = plugin.locks().getSecureHopper(b).orElse(null);
                if (sh != null && !sh.owner().equals(e.getPlayer().getUniqueId())) {
                    plugin.locks().logAdminBreak(e.getPlayer(), b);
                }
            }
        }

        
        if (locked) {
            plugin.locks().unlock(e.getPlayer(), b);
        }
        if (secureHopper) {
            plugin.locks().removeSecureHopper(b);
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), plugin.items().secureHopper());
        }

        feedbackOk(e.getPlayer(), "Removed.");
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!worlds.isEnabled(e.getLocation().getWorld())) return;
        filterProtectedBlocks(e.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!worlds.isEnabled(e.getBlock().getWorld())) return;
        filterProtectedBlocks(e.blockList().iterator());
    }

    private void filterProtectedBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            if (LockUtil.isLockable(b) && plugin.locks().isLocked(b)) {
                it.remove();
                continue;
            }
            if (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b)) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        Block b = e.getBlock();
        if (!worlds.isEnabled(b.getWorld())) return;
        if ((LockUtil.isLockable(b) && plugin.locks().isLocked(b)) || (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Block b = e.getBlock();
        if (b == null) return;
        if (!worlds.isEnabled(b.getWorld())) return;
        if ((LockUtil.isLockable(b) && plugin.locks().isLocked(b)) || (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        Block b = e.getBlock();
        if (!worlds.isEnabled(b.getWorld())) return;
        if ((LockUtil.isLockable(b) && plugin.locks().isLocked(b)) || (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b))) {
            e.setCancelled(true);
        }
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!worlds.isEnabled(e.getBlock().getWorld())) return;
        for (Block b : e.getBlocks()) {
            if ((LockUtil.isLockable(b) && plugin.locks().isLocked(b)) || (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!worlds.isEnabled(e.getBlock().getWorld())) return;
        for (Block b : e.getBlocks()) {
            if ((LockUtil.isLockable(b) && plugin.locks().isLocked(b)) || (b.getType() == Material.HOPPER && plugin.locks().isSecureHopper(b))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent e) {
        Inventory source = e.getSource();
        Inventory dest = e.getDestination();

        Block sourceBlock = inventoryToBlock(source);
        Block destBlock = inventoryToBlock(dest);

        boolean sourceLocked = sourceBlock != null && LockUtil.isLockable(sourceBlock) && plugin.locks().isLocked(sourceBlock);
        boolean destLocked = destBlock != null && LockUtil.isLockable(destBlock) && plugin.locks().isLocked(destBlock);

        if (!sourceLocked && !destLocked) return;

        
        InventoryHolder sh = source.getHolder();
        InventoryHolder dh = dest.getHolder();

        Block hopperBlock = null;
        if (sh instanceof org.bukkit.block.Hopper bh) {
            hopperBlock = bh.getBlock();
        } else if (dh instanceof org.bukkit.block.Hopper bh) {
            hopperBlock = bh.getBlock();
        } else if (sh instanceof HopperMinecart || dh instanceof HopperMinecart) {
            
            e.setCancelled(true);
            return;
        }

        if (hopperBlock == null) {
            
            e.setCancelled(true);
            return;
        }

        
        SecureHopperEntry shEntry = plugin.locks().getSecureHopper(hopperBlock).orElse(null);
        if (shEntry == null) {
            e.setCancelled(true);
            return;
        }

        
        if (sourceLocked) {
            LockKey lk = LockUtil.canonicalKey(sourceBlock);
            if (!lk.asId().equals(shEntry.boundLock().asId())) {
                e.setCancelled(true);
                return;
            }
        }
        if (destLocked) {
            LockKey lk = LockUtil.canonicalKey(destBlock);
            if (!lk.asId().equals(shEntry.boundLock().asId())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private Block inventoryToBlock(Inventory inv) {
        if (inv == null) return null;
        Location loc = inv.getLocation();
        if (loc == null) return null;
        World w = loc.getWorld();
        if (w == null) return null;
        return w.getBlockAt(loc);
    }

    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        Block container = inventoryToBlock(top);
        if (container == null) return;
        if (!worlds.isEnabled(container.getWorld())) return;
        if (!LockUtil.isLockable(container)) return;
        if (!plugin.locks().isLocked(container)) return;

        AccessLevel level = plugin.locks().getAccessLevel(p, container);
        if (level != AccessLevel.VIEW) return;

        
        
        
        
        
        
        
        
        InventoryAction action = e.getAction();
        boolean affectsTop =
                e.getClickedInventory() == top                          
                || e.isShiftClick()                                     
                || action == InventoryAction.COLLECT_TO_CURSOR          
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY    
                || action == InventoryAction.HOTBAR_SWAP                
                || action == InventoryAction.HOTBAR_MOVE_AND_READD;    

        if (affectsTop) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        Block container = inventoryToBlock(top);
        if (container == null) return;
        if (!worlds.isEnabled(container.getWorld())) return;
        if (!LockUtil.isLockable(container)) return;
        if (!plugin.locks().isLocked(container)) return;

        AccessLevel level = plugin.locks().getAccessLevel(p, container);
        if (level != AccessLevel.VIEW) return;

        
        int topSize = top.getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    
    private boolean hasChestKey(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (plugin.items().is(it, Items.Type.CHEST_KEY)) return true;
        }
        return plugin.items().is(p.getInventory().getItemInOffHand(), Items.Type.CHEST_KEY);
    }



    private void feedbackOk(Player p, String msg) {
        p.sendMessage(Component.text(msg, NamedTextColor.GREEN));
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            
            p.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.0, 0), 8, 0.2, 0.4, 0.2, 0.01);
        }
    }

    private void feedbackFail(Player p, String msg) {
        p.sendMessage(Component.text(msg, NamedTextColor.RED));
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        }
        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            p.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.0, 0), 6, 0.1, 0.2, 0.1, 0.01);
        }
    }
}
