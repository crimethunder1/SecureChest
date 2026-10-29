package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.db.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public final class PlayerSettingsRepository {

    private static final Set<String> ALLOWED_FIELDS = Set.of("quick_lock", "quick_remove", "show_owner_name");

    private final ChestSecurePlugin plugin;
    private final DatabaseManager db;

    public PlayerSettingsRepository(ChestSecurePlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
    }

    static void requireAllowedField(String field) {
        if (field == null || !ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unbekanntes Setting-Feld: " + field);
        }
    }

    public boolean loadSetting(UUID player, String field) {
        requireAllowedField(field);
        if (Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("[SecureChest] loadSetting('" + field
                    + "') on the main thread for " + player
                    + ", returning the default. Settings will be loaded shortly.");

            return false;
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + field + " FROM player_settings WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB loadSetting failed for " + player, ex);
        }
        return false;
    }

    public Optional<Boolean> loadShowOwnerName(UUID player) {
        if (Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("[SecureChest] loadShowOwnerName on the main thread for " + player
                    + ", the database is not read there.");
            return Optional.empty();
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT show_owner_name FROM player_settings WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return Optional.of(!rs.next() || rs.getInt(1) == 1);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB loadShowOwnerName failed for " + player, ex);
        }
        return Optional.empty();
    }

    public Map<UUID, Boolean> loadAllShowOwnerName() {
        Map<UUID, Boolean> values = new HashMap<>();
        if (Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("[SecureChest] Owner name settings requested on the main thread, "
                    + "the database is not read there.");
            return values;
        }
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT player_uuid,show_owner_name FROM player_settings")) {
            while (rs.next()) {
                String uuidText = rs.getString(1);
                try {
                    values.put(UUID.fromString(uuidText), rs.getInt(2) == 1);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[SecureChest] Invalid UUID in player_settings skipped: " + uuidText);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB loading owner name settings failed, all names are shown", ex);
            return new HashMap<>();
        }
        return values;
    }

    public void saveSetting(UUID player, String field, boolean value) {
        requireAllowedField(field);
        ensureSettingsRow(player);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE player_settings SET " + field + "=? WHERE player_uuid=?")) {
            ps.setInt(1, value ? 1 : 0);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB saveSetting failed for " + player, ex);
        }
    }

    public void ensureSettingsRow(UUID player) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO player_settings(player_uuid,quick_lock,quick_remove)"
                     + " VALUES(?,0,0)")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "DB ensureSettingsRow failed for " + player, ex);
        }
    }

    public void addNotification(UUID ownerUuid, String message) {
        if (!plugin.cfg().notificationsEnabled()) return;
        long now = Instant.now().toEpochMilli();
        int maxPerPlayer = plugin.cfg().notificationsMaxPerPlayer();
        final String ownerStr = ownerUuid.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection()) {
                c.setAutoCommit(false);

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO notifications(owner_uuid,message,created_at) VALUES(?,?,?)")) {
                    ps.setString(1, ownerStr); ps.setString(2, message); ps.setLong(3, now);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM notifications WHERE owner_uuid=? AND always_deliver=0 AND id NOT IN"
                        + " (SELECT id FROM notifications WHERE owner_uuid=? AND always_deliver=0"
                        + " ORDER BY id DESC LIMIT ?)")) {
                    ps.setString(1, ownerStr); ps.setString(2, ownerStr); ps.setInt(3, maxPerPlayer);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB addNotification failed for " + ownerStr, ex);
            }
        });
    }

    public void addAlwaysDeliveredNotification(UUID ownerUuid, String message) {
        long now = Instant.now().toEpochMilli();
        final String ownerStr = ownerUuid.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO notifications(owner_uuid,message,created_at,always_deliver) VALUES(?,?,?,1)")) {
                ps.setString(1, ownerStr); ps.setString(2, message); ps.setLong(3, now);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB addAlwaysDeliveredNotification failed for " + ownerStr, ex);
            }
        });
    }

    public List<Notification> fetchAndClearNotifications(UUID playerUuid) {
        return fetchAndClear(playerUuid, false);
    }

    public List<Notification> fetchAndClearAlwaysDeliveredNotifications(UUID playerUuid) {
        return fetchAndClear(playerUuid, true);
    }

    private List<Notification> fetchAndClear(UUID playerUuid, boolean onlyAlwaysDelivered) {
        List<Notification> out = new ArrayList<>();
        final String uuidStr = playerUuid.toString();
        final String filter = onlyAlwaysDelivered ? " AND always_deliver=1" : "";

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,message,created_at FROM notifications WHERE owner_uuid=?" + filter + " ORDER BY id ASC")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Notification(rs.getLong(1), playerUuid, rs.getString(2), rs.getLong(3)));
                    }
                }
            }
            if (!out.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM notifications WHERE owner_uuid=? AND id<=?" + filter)) {
                    ps.setString(1, uuidStr);
                    ps.setLong(2, out.get(out.size() - 1).id());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB fetchAndClearNotifications failed for " + playerUuid, ex);
        }
        return out;
    }
}
