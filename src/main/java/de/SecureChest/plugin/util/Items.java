package de.SecureChest.plugin.util;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
        meta.displayName(Component.text(plugin.msg().get("item-chest-key-name")));
        meta.lore(List.of(
                Component.text(plugin.msg().get("item-chest-key-lore-1")),
                Component.text(plugin.msg().get("item-chest-key-lore-2")),
                Component.text(plugin.msg().get("item-chest-key-lore-3"))
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
        meta.displayName(Component.text(plugin.msg().get("item-admin-key-name")));
        meta.lore(List.of(
                Component.text(plugin.msg().get("item-admin-key-lore-1")),
                Component.text(plugin.msg().get("item-admin-key-lore-2"))
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
        meta.displayName(Component.text(plugin.msg().get("item-secure-hopper-name")));
        meta.lore(List.of(
                Component.text(plugin.msg().get("item-secure-hopper-lore-1")),
                Component.text(plugin.msg().get("item-secure-hopper-lore-2")),
                Component.text(plugin.msg().get("item-secure-hopper-lore-3"))
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

    public boolean isHoldingChestKey(org.bukkit.entity.Player player) {
        return is(player.getInventory().getItemInMainHand(), Type.CHEST_KEY)
                || is(player.getInventory().getItemInOffHand(), Type.CHEST_KEY);
    }

    public boolean isHoldingAdminKey(org.bukkit.entity.Player player) {
        if (!player.hasPermission(Permissions.ADMIN)) return false;
        return is(player.getInventory().getItemInMainHand(), Type.ADMIN_KEY)
                || is(player.getInventory().getItemInOffHand(), Type.ADMIN_KEY);
    }

    public boolean hasChestKeyInInventory(org.bukkit.entity.Player player) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (is(item, Type.CHEST_KEY)) return true;
        }
        return false;
    }
}
