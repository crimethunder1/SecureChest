package de.SecureChest.plugin.util;

import de.SecureChest.plugin.ChestSecurePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class Items {

    public enum Type {
        CHEST_KEY,
        ADMIN_KEY,
        SECURE_HOPPER
    }

    private final ChestSecurePlugin plugin;

    public Items(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack chestKey() {
        ItemStack it = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Chest Key"));
        meta.lore(List.of(
                Component.text("Right-click a container to lock it."),
                Component.text("Sneak-right-click with Quick Remove to unlock."),
                Component.text("Used for crafting Secure Hopper.")
        ));
        mark(meta, Type.CHEST_KEY);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack adminKey() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Admin Key"));
        meta.lore(List.of(
                Component.text("Admins can open any locked container."),
                Component.text("Not craftable.")
        ));
        mark(meta, Type.ADMIN_KEY);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack secureHopper() {
        ItemStack it = new ItemStack(Material.HOPPER);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Secure Hopper"));
        meta.lore(List.of(
                Component.text("Only Secure Hoppers can move items"),
                Component.text("into/from locked containers."),
                Component.text("Place it facing your locked container to bind it.")
        ));
        mark(meta, Type.SECURE_HOPPER);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    public boolean is(ItemStack stack, Type type) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        String val = meta.getPersistentDataContainer().get(plugin.keyItemType(), PersistentDataType.STRING);
        return val != null && val.equals(type.name());
    }

    public Type readType(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        String val = meta.getPersistentDataContainer().get(plugin.keyItemType(), PersistentDataType.STRING);
        if (val == null) return null;
        try {
            return Type.valueOf(val);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void mark(ItemMeta meta, Type type) {
        meta.getPersistentDataContainer().set(plugin.keyItemType(), PersistentDataType.STRING, type.name());
    }
}
