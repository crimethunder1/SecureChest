package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Geteilte Hilfsmethoden fuer alle GUI-Screen-Klassen.
 * Kein Screen-spezifischer Code – nur generische Builder und Formatter.
 */
public final class GuiHelper {

    protected final ChestSecurePlugin plugin;

    public GuiHelper(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Item-Builder ─────────────────────────────────────────────────────────

    public ItemStack buildButton(Material material, String name, List<String> lore,
                                 String action, String context) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty())
            meta.lore(lore.stream().map(Component::text).toList());
        meta.getPersistentDataContainer().set(
                plugin.keyGuiAction(), PersistentDataType.STRING, action);
        if (context != null && !context.isEmpty())
            meta.getPersistentDataContainer().set(
                    plugin.keyGuiContext(), PersistentDataType.STRING, context);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildInfoItem(Material material, String name, List<String> lore) {
        return buildButton(material, name, lore, "noop", "");
    }

    public ItemStack buildToggleButton(String label, boolean enabled,
                                       String action, String description) {
        Material pane = enabled ? Material.LIME_STAINED_GLASS_PANE
                                : Material.RED_STAINED_GLASS_PANE;
        net.kyori.adventure.text.Component displayName =
                Component.text(label + " — ")
                        .append(Component.text(enabled ? "AN" : "AUS",
                                enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        ItemStack item = new ItemStack(pane);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(displayName);
        List<net.kyori.adventure.text.Component> loreCmp = new ArrayList<>();
        if (description != null && !description.isBlank())
            loreCmp.add(Component.text(formatInfoLine(description)));
        loreCmp.add(Component.text(formatActionLine("Klicken zum Umschalten")));
        meta.lore(loreCmp);
        meta.getPersistentDataContainer().set(
                plugin.keyGuiAction(), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildPlayerHeadButton(UUID playerUuid, String displayName,
                                           List<String> lore, String action, String context) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta  base = item.getItemMeta();
        if (base instanceof SkullMeta skull) {
            org.bukkit.OfflinePlayer offline =
                    Bukkit.getOfflinePlayerIfCached(playerUuid.toString());
            if (offline == null) {
                Player online = Bukkit.getPlayer(playerUuid);
                if (online != null) offline = online;
            }
            if (offline != null) skull.setOwningPlayer(offline);
            skull.displayName(Component.text(displayName));
            skull.lore(lore.stream().map(Component::text).toList());
            skull.getPersistentDataContainer().set(
                    plugin.keyGuiAction(), PersistentDataType.STRING, action);
            if (context != null && !context.isEmpty())
                skull.getPersistentDataContainer().set(
                        plugin.keyGuiContext(), PersistentDataType.STRING, context);
            item.setItemMeta(skull);
            return item;
        }
        return buildButton(Material.PLAYER_HEAD, displayName, lore, action, context);
    }

    // ── Inventar-Helfer ──────────────────────────────────────────────────────

    public void fillEmptySlots(Inventory inventory, Material glassMaterial) {
        ItemStack pane = new ItemStack(glassMaterial);
        ItemMeta  meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, pane);
        }
    }

    // ── Spieler-Name ─────────────────────────────────────────────────────────

    public String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (cached != null && cached.getName() != null) return cached.getName();
        return uuid.toString().substring(0, 8) + "…";
    }

    // ── Sound ────────────────────────────────────────────────────────────────

    public void playClickSound(Player player) {
        if (plugin.cfg().soundsEnabled())
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    // ── Formatter ────────────────────────────────────────────────────────────

    public static String formatInfoLine(String text)    { return "§8" + text; }
    public static String formatHintLine(String text)    { return "§e" + text; }
    public static String formatActionLine(String text)  { return "§b" + text; }
    public static String formatWarningLine(String text) { return "§c" + text; }
}
