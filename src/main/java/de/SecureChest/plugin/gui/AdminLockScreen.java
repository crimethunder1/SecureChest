package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import de.SecureChest.plugin.lock.AccessLevel;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class AdminLockScreen {

    static final int SLOT_INFO           = 10;
    static final int SLOT_TRANSFER       = 14;
    static final int SLOT_REMOVE         = 16;
    static final int SLOT_ADD_VIEWER     = 29;
    static final int SLOT_ADD_STOCKER    = 31;
    static final int SLOT_TRUSTED_COUNT  = 35;
    static final int FIRST_TRUSTED_SLOT  = 36;
    static final int LAST_TRUSTED_SLOT   = 44;
    static final int SLOT_CLOSE          = 45;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChestSecurePlugin plugin;
    private final GuiHelper         h;

    AdminLockScreen(ChestSecurePlugin plugin, GuiHelper helper) {
        this.plugin = plugin;
        this.h      = helper;
    }

    void openLockManage(Player player, String lockId) {
        if (!player.hasPermission(Permissions.ADMIN)) {
            player.sendMessage(Component.text(text("keine-berechtigung"), NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        LockEntry lockEntry = plugin.locks().getLockFromId(lockId).orElse(null);
        if (lockEntry == null) {
            player.sendMessage(Component.text(text("schloss-nicht-gefunden"), NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        LockKey key       = lockEntry.key();
        String blockName  = lockEntry.blockType().name().replace('_', ' ');
        World world       = Bukkit.getWorld(key.worldUuid());
        String worldName  = world != null ? world.getName() : "?";
        String ownerName  = h.resolvePlayerName(lockEntry.owner());

        ChestSecureHolder holder = new ChestSecureHolder(ChestSecureHolder.GuiId.ADMIN_LOCK_MANAGE, lockId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(text("gui-admin-lock-manage-title", blockName)));
        holder.setInventory(inv);

        inv.setItem(SLOT_INFO, h.buildInfoItem(lockEntry.blockType(),
                text("gui-admin-lock-manage-info-name", blockName, ownerName),
                lines("gui-lock-manage-info-lore", worldName, key.x(), key.y(), key.z(),
                        DATE_FMT.format(Instant.ofEpochMilli(lockEntry.createdAt())))));

        inv.setItem(SLOT_TRANSFER, h.buildButton(Material.ENDER_PEARL, text("gui-admin-lock-manage-transfer-name"),
                lines("gui-admin-lock-manage-transfer-lore"), "admin_lock_transfer", lockId));
        inv.setItem(SLOT_REMOVE, h.buildButton(Material.BARRIER, text("gui-admin-lock-manage-remove-name"),
                lines("gui-admin-lock-manage-remove-lore"), "admin_lock_remove", lockId));
        inv.setItem(SLOT_ADD_VIEWER, h.buildButton(Material.SPYGLASS, text("gui-admin-lock-manage-add-viewer-name"),
                lines("gui-admin-lock-manage-add-viewer-lore"), "admin_trust_add", "VIEW|" + lockId));
        inv.setItem(SLOT_ADD_STOCKER, h.buildButton(Material.CHEST, text("gui-admin-lock-manage-add-stocker-name"),
                lines("gui-admin-lock-manage-add-stocker-lore"), "admin_trust_add", "STOCKER|" + lockId));

        List<Map.Entry<UUID, AccessLevel>> trustList = lockEntry.trusted().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().name())).toList();
        int trustCount = trustList.size();
        String countLabel = trustCount == 0 ? text("gui-lock-manage-trusted-none")
                : trustCount == 1 ? text("gui-lock-manage-trusted-one", trustCount)
                : trustCount > 9  ? text("gui-lock-manage-trusted-many-limited", trustCount)
                : text("gui-lock-manage-trusted-many", trustCount);
        inv.setItem(SLOT_TRUSTED_COUNT, h.buildInfoItem(Material.NAME_TAG, countLabel, List.of()));

        int trustSlot = FIRST_TRUSTED_SLOT;
        for (Map.Entry<UUID, AccessLevel> trusted : trustList) {
            if (trustSlot > LAST_TRUSTED_SLOT) break;
            inv.setItem(trustSlot++, buildTrustListEntry(trusted.getKey(), trusted.getValue(), lockId));
        }

        inv.setItem(SLOT_CLOSE, h.buildButton(Material.ARROW, text("gui-admin-lock-manage-close-name"),
                lines("gui-admin-lock-manage-close-lore"), "admin_lock_close", ""));
        h.fillEmptySlots(inv, Material.RED_STAINED_GLASS_PANE);
        player.openInventory(inv);
        h.playClickSound(player);
    }

    private ItemStack buildTrustListEntry(UUID uuid, AccessLevel level, String lockId) {
        return h.buildPlayerHeadButton(uuid, h.resolvePlayerName(uuid),
                lines("gui-admin-lock-manage-trusted-entry-lore",
                        accessLevelLabel(level), accessLevelLabel(toggledLevel(level))),
                "admin_trust_entry", lockId + "|" + uuid);
    }

    private static AccessLevel toggledLevel(AccessLevel level) {
        return level == AccessLevel.VIEW ? AccessLevel.STOCKER : AccessLevel.VIEW;
    }

    private String accessLevelLabel(AccessLevel level) {
        return switch (level) {
            case VIEW    -> text("gui-role-viewer");
            case STOCKER -> text("gui-role-stocker");
        };
    }

    private String text(String key, Object... args) {
        return plugin.msg().get(key, args);
    }

    private List<String> lines(String key, Object... args) {
        return plugin.msg().getList(key, args);
    }
}
