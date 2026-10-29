package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class FakePlayerInventory {

    public static final int SIZE = 41;
    public static final int OFF_HAND_SLOT = ChestKeyPayment.OFF_HAND_SLOT;
    public static final int MAXIMUM_STACK_SIZE = 64;

    private final ItemStack[] slots = new ItemStack[SIZE];
    private final Map<ItemStack, Boolean> chestKeyStacks = new IdentityHashMap<>();
    private final Map<ItemStack, Integer> amounts = new IdentityHashMap<>();
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private int heldSlot;

    public FakePlayerInventory(Player player) {
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenAnswer(invocation -> heldSlot);
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots[(int) invocation.getArgument(0)]);
        doAnswer(invocation -> {
            slots[(int) invocation.getArgument(0)] = invocation.getArgument(1);
            return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(inventory.getContents()).thenAnswer(invocation -> slots.clone());
        when(inventory.addItem(any(ItemStack[].class))).thenAnswer(invocation -> {
            for (Object added : invocation.getArguments()) {
                for (int slot = 0; slot < 36; slot++) {
                    if (slots[slot] == null) {
                        slots[slot] = (ItemStack) added;
                        break;
                    }
                }
            }
            return new HashMap<Integer, ItemStack>();
        });
    }

    public PlayerInventory inventory() {
        return inventory;
    }

    public void holdSlot(int slot) {
        heldSlot = slot;
    }

    public ItemStack putChestKeys(int slot, int amount) {
        return put(slot, stack(true, amount));
    }

    public ItemStack putPlainTripwireHooks(int slot, int amount) {
        return put(slot, stack(false, amount));
    }

    public void answerChestKeyChecks(Items items, Player player) {
        when(items.is(nullable(ItemStack.class), eq(Items.Type.CHEST_KEY)))
                .thenAnswer(invocation -> isChestKey(invocation.getArgument(0)));
        when(items.hasChestKeyInInventory(player)).thenAnswer(invocation -> chestKeyCount() > 0);
    }

    public boolean isChestKey(ItemStack stack) {
        return stack != null && amounts.getOrDefault(stack, 0) > 0 && chestKeyStacks.getOrDefault(stack, false);
    }

    public ItemStack item(int slot) {
        return slots[slot];
    }

    public int amount(int slot) {
        ItemStack stack = slots[slot];
        return stack == null ? 0 : amounts.getOrDefault(stack, 0);
    }

    public int chestKeyCount() {
        int count = 0;
        for (ItemStack stack : slots) {
            if (isChestKey(stack)) count += amounts.get(stack);
        }
        return count;
    }

    private ItemStack put(int slot, ItemStack stack) {
        slots[slot] = stack;
        return stack;
    }

    private ItemStack stack(boolean chestKey, int amount) {
        ItemStack stack = mock(ItemStack.class);
        chestKeyStacks.put(stack, chestKey);
        amounts.put(stack, amount);
        when(stack.getType()).thenReturn(Material.TRIPWIRE_HOOK);
        when(stack.getAmount()).thenAnswer(invocation -> amounts.get(stack));
        doAnswer(invocation -> {
            amounts.put(stack, invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(anyInt());
        when(stack.isEmpty()).thenAnswer(invocation -> amounts.get(stack) <= 0);
        when(stack.getMaxStackSize()).thenReturn(MAXIMUM_STACK_SIZE);
        when(stack.asQuantity(anyInt())).thenAnswer(invocation -> stack(chestKey, invocation.getArgument(0)));
        when(stack.isSimilar(nullable(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && chestKeyStacks.containsKey(other)
                    && chestKeyStacks.get(other).equals(chestKey);
        });
        return stack;
    }
}
