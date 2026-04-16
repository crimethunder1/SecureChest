package de.SecureChest.plugin.util;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Worlds {

    private final Set<String> enabled = new HashSet<>();

    public Worlds(ChestSecurePlugin plugin) {
        List<String> list = plugin.cfg().enabledWorlds();
        if (list != null) {
            for (String w : list) {
                if (w != null && !w.isBlank()) enabled.add(w.trim());
            }
        }
    }

    public boolean isEnabled(World world) {
        if (world == null) return false;
        if (enabled.isEmpty()) return true;
        return enabled.contains(world.getName());
    }
}
