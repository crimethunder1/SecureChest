package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.AccessLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Lists the players who have default access to all locks of the viewing owner. */
final class DefaultTrustScreen {

    static final int SLOT_INFO        = 10;
    static final int SLOT_ADD_VIEWER  = 12;
    static final int SLOT_ADD_STOCKER = 14;
    static final int SLOT_COUNT       = 16;
    static final int FIRST_ENTRY_SLOT = 28;
    static final int LAST_ENTRY_SLOT  = 43;
    static final int SLOT_BACK        = 45;

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;

    DefaultTrustScreen(ChestSecurePlugin plugin, GuiHelper helper) {
        this.plugin = plugin;
        this.h      = helper;
    }

    void open(Player player) {
        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.DEFAULT_TRUST, "");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(text("gui-default-trust-title")));
        holder.setInventory(inv);

        Map<UUID, AccessLevel> trusted = plugin.locks().getDefaultTrust(player.getUniqueId());

        inv.setItem(SLOT_INFO, h.buildInfoItem(Material.BOOK, text("gui-default-trust-info-name"),
                lines("gui-default-trust-info-lore")));
        inv.setItem(SLOT_ADD_VIEWER, h.buildButton(Material.SPYGLASS, text("gui-default-trust-add-viewer-name"),
                lines("gui-default-trust-add-viewer-lore"), "default_trust_add", AccessLevel.VIEW.name()));
        inv.setItem(SLOT_ADD_STOCKER, h.buildButton(Material.CHEST, text("gui-default-trust-add-stocker-name"),
                lines("gui-default-trust-add-stocker-lore"), "default_trust_add", AccessLevel.STOCKER.name()));
        inv.setItem(SLOT_COUNT, h.buildInfoItem(Material.NAME_TAG,
                trusted.isEmpty() ? text("gui-default-trust-count-none")
                        : text("gui-default-trust-count", trusted.size()),
                List.of()));

        List<Map.Entry<UUID, AccessLevel>> entries = new ArrayList<>(trusted.entrySet());
        entries.sort(Comparator.comparing(entry -> h.resolvePlayerName(entry.getKey()).toLowerCase()));
        int slot = FIRST_ENTRY_SLOT;
        for (Map.Entry<UUID, AccessLevel> entry : entries) {
            if (slot > LAST_ENTRY_SLOT) break;
            inv.setItem(slot++, buildEntry(entry.getKey(), entry.getValue()));
        }

        inv.setItem(SLOT_BACK, h.buildButton(Material.ARROW, text("gui-back-name"),
                lines("gui-back-settings-lore"), "back_settings", ""));
        h.fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildEntry(UUID uuid, AccessLevel level) {
        AccessLevel toggled = level == AccessLevel.VIEW ? AccessLevel.STOCKER : AccessLevel.VIEW;
        return h.buildPlayerHeadButton(uuid, h.resolvePlayerName(uuid),
                lines("gui-default-trust-entry-lore", label(level), label(toggled)),
                "default_trust_entry", uuid.toString());
    }

    private String label(AccessLevel level) {
        return text(level == AccessLevel.VIEW ? "gui-role-viewer" : "gui-role-stocker");
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
