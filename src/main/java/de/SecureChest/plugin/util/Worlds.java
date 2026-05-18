package de.SecureChest.plugin.util;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Worlds {

    private final ChestSecurePlugin plugin;
    private volatile Set<String> enabledWorldNames;

    public Worlds(ChestSecurePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        Set<String> rebuilt = new HashSet<>();
        List<String> configured = plugin.cfg().enabledWorlds();
        if (configured != null) {
            for (String name : configured) {
                if (name != null && !name.isBlank()) rebuilt.add(name.trim());
            }
        }
        this.enabledWorldNames = Collections.unmodifiableSet(rebuilt);
    }

    public boolean isEnabled(World world) {
        if (world == null) return false;
        Set<String> snapshot = enabledWorldNames;
        if (snapshot.isEmpty()) return true;
        return snapshot.contains(world.getName());
    }
}
