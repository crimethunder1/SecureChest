package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.util.Items;
import java.util.function.BooleanSupplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class ChestKeyPayment {

    static final int OFF_HAND_SLOT = 40;

    private static final ChestKeyPayment FREE = new ChestKeyPayment(null, -1, null);

    private final Player player;
    private final int slot;
    private final ItemStack takenKey;
    private boolean settled;

    private ChestKeyPayment(Player player, int slot, ItemStack takenKey) {
        this.player = player;
        this.slot = slot;
        this.takenKey = takenKey;
    }

    static ChestKeyPayment free() {
        return FREE;
    }

    static ChestKeyPayment take(ChestSecurePlugin plugin, Player player) {
        if (!plugin.chestKeyConsumption().isEnabled() || player.getGameMode() == GameMode.CREATIVE) return FREE;
        PlayerInventory inventory = player.getInventory();
        int slot = chestKeySlot(plugin.items(), inventory);
        if (slot < 0) return null;
        ItemStack stack = inventory.getItem(slot);
        ItemStack takenKey = stack.asQuantity(1);
        int remainingAmount = stack.getAmount() - 1;
        if (remainingAmount > 0) {
            stack.setAmount(remainingAmount);
            inventory.setItem(slot, stack);
        } else {
            inventory.setItem(slot, null);
        }
        return new ChestKeyPayment(player, slot, takenKey);
    }

    static int chestKeySlot(Items items, PlayerInventory inventory) {
        int heldSlot = inventory.getHeldItemSlot();
        if (items.is(inventory.getItem(heldSlot), Items.Type.CHEST_KEY)) return heldSlot;
        if (items.is(inventory.getItem(OFF_HAND_SLOT), Items.Type.CHEST_KEY)) return OFF_HAND_SLOT;
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            if (items.is(contents[index], Items.Type.CHEST_KEY)) return index;
        }
        return -1;
    }

    boolean spendOn(BooleanSupplier lockAction) {
        boolean locked = false;
        try {
            locked = lockAction.getAsBoolean();
            return locked;
        } finally {
            if (!locked) refund();
        }
    }

    private void refund() {
        if (takenKey == null || settled) return;
        settled = true;
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.isEmpty()) {
            inventory.setItem(slot, takenKey);
            return;
        }
        if (current.isSimilar(takenKey) && current.getAmount() < current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + 1);
            inventory.setItem(slot, current);
            return;
        }
        inventory.addItem(takenKey).values().forEach(
                leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
