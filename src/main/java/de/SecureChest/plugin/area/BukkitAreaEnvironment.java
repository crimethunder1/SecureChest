package de.SecureChest.plugin.area;

import de.SecureChest.plugin.ChestSecurePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BukkitAreaEnvironment implements AreaEnvironment {

    private final ChestSecurePlugin plugin;

    public BukkitAreaEnvironment(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String worldName(UUID worldUuid) {
        World world = Bukkit.getWorld(worldUuid);
        return world == null ? null : world.getName();
    }

    @Override
    public boolean worldExists(String worldName) {
        return worldName != null && Bukkit.getWorld(worldName) != null;
    }

    @Override
    public void notifyOwner(UUID owner, int removedLocks, int removedSecureHoppers) {
        String message = plugin.msg().get("wirkungsbereich-sperren-entfernt", removedLocks, removedSecureHoppers);
        Player onlineOwner = Bukkit.getPlayer(owner);
        if (onlineOwner != null && onlineOwner.isOnline()) {
            onlineOwner.sendMessage(Component.text(message, NamedTextColor.RED));
        } else {
            plugin.locks().addAlwaysDeliveredNotification(owner, message);
        }
    }
}
