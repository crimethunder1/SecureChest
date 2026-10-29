package de.SecureChest.plugin.names;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.db.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerNameRepository implements PlayerNameStore {

    private static final String UPSERT_SQL =
            "INSERT OR REPLACE INTO player_names(uuid,name,last_seen) VALUES(?,?,?)";

    private final ChestSecurePlugin plugin;
    private final DatabaseManager db;

    public PlayerNameRepository(ChestSecurePlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
    }

    @Override
    public Map<UUID, String> loadAll() {
        Map<UUID, String> names = new HashMap<>();
        if (Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("[SecureChest] Player names requested on the main thread, "
                    + "the database is not read there.");
            return names;
        }
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid,name FROM player_names")) {
            while (rs.next()) {
                String uuidText = rs.getString(1);
                try {
                    names.put(UUID.fromString(uuidText), rs.getString(2));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[SecureChest] Invalid UUID in player_names skipped: " + uuidText);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB loading player names failed", ex);
        }
        return names;
    }

    @Override
    public void save(UUID playerUuid, String name, long lastSeen) {
        saveAll(Map.of(playerUuid, name), lastSeen);
    }

    @Override
    public void saveAll(Map<UUID, String> names, long lastSeen) {
        if (names.isEmpty()) return;
        final Map<UUID, String> snapshot = new LinkedHashMap<>(names);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {
                    for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
                        ps.setString(1, entry.getKey().toString());
                        ps.setString(2, entry.getValue());
                        ps.setLong(3, lastSeen);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB saving " + snapshot.size() + " player name(s) failed", ex);
            }
        });
    }
}
