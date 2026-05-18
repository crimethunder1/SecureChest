package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.util.BookUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

public final class LogRepository {

    public static final String UNKNOWN_WORLD_UUID = "00000000-0000-0000-0000-000000000000";

    public static final String ACTION_UNAUTHORIZED_BREAK = "UNAUTHORIZED_BREAK";
    public static final String ACTION_ADMIN_BREAK        = "ADMIN_BREAK";

    public static final String ACTION_LOCK_CREATE           = "LOCK_CREATE";
    public static final String ACTION_LOCK_REMOVE           = "LOCK_REMOVE";
    public static final String ACTION_LOCK_REMOVE_ADMIN     = "LOCK_REMOVE_ADMIN";
    public static final String ACTION_LOCK_TRANSFER         = "LOCK_TRANSFER";
    public static final String ACTION_LOCK_UNLOCK_ALL_SELF  = "LOCK_UNLOCK_ALL_SELF";
    public static final String ACTION_LOCK_UNLOCK_ALL_ADMIN = "LOCK_UNLOCK_ALL_ADMIN";
    public static final String ACTION_TRUST_SET             = "TRUST_SET";
    public static final String ACTION_TRUST_REMOVE          = "TRUST_REMOVE";
    public static final String ACTION_HOPPER_BIND           = "HOPPER_BIND";
    public static final String ACTION_HOPPER_REMOVE         = "HOPPER_REMOVE";
    public static final String ACTION_UNDO_RESTORE          = "UNDO_RESTORE";
    public static final String ACTION_INSPECT_TOGGLE        = "INSPECT_TOGGLE";

    private final ChestSecurePlugin plugin;
    private final DatabaseManager db;

    public LogRepository(ChestSecurePlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
    }

    public void insertLog(String action, Player actor, Block target, String extra) {
        long now = Instant.now().toEpochMilli();
        final String actorUuid = actor.getUniqueId().toString();
        final String actorName = actor.getName();
        World w = target.getWorld();
        final String worldName = w != null ? w.getName() : "unknown";
        final String worldUuid = w != null ? w.getUID().toString() : UNKNOWN_WORLD_UUID;
        final int bx = target.getX(), by = target.getY(), bz = target.getZ();

        persistLog(now, action, actorUuid, actorName, worldName, worldUuid, bx, by, bz, extra);
    }

    public void insertLog(String action, Player actor, LockKey key, String extra) {
        long now = Instant.now().toEpochMilli();
        final String actorUuid = actor.getUniqueId().toString();
        final String actorName = actor.getName();
        World w = Bukkit.getWorld(key.worldUuid());
        final String worldName = w != null ? w.getName() : "unknown";
        final String worldUuid = key.worldUuid().toString();

        persistLog(now, action, actorUuid, actorName, worldName, worldUuid, key.x(), key.y(), key.z(), extra);
    }

    public void insertLogAtActor(String action, Player actor, String extra) {
        insertLog(action, actor, actor.getLocation().getBlock(), extra);
    }

    private void persistLog(long now, String action,
                            String actorUuid, String actorName,
                            String worldName, String worldUuid,
                            int x, int y, int z, String extra) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO logs(created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra)"
                         + " VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, now);      ps.setString(2, action);
                ps.setString(3, actorUuid); ps.setString(4, actorName);
                ps.setString(5, worldName); ps.setString(6, worldUuid);
                ps.setInt(7, x);         ps.setInt(8, y);     ps.setInt(9, z);
                ps.setString(10, extra);
                ps.executeUpdate();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "DB log insert fehlgeschlagen", ex);
            }
        });
    }

    public ItemStack createLogsBook(int maxEntries) {
        List<String> lines = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT created_at,action,actor_name,world_name,x,y,z"
                     + " FROM logs ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, maxEntries));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(BookUtil.formatLogLine(
                            rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4),
                            rs.getInt(5), rs.getInt(6), rs.getInt(7)));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB Log-Buch lesen fehlgeschlagen", ex);
        }
        if (lines.isEmpty()) lines.add("Noch keine Logs vorhanden.");
        return BookUtil.buildLogsBook(lines);
    }

    public List<LogRecord> listLogs(String actionFilter, long minCreatedAt,
                                    String actorNameLike, int offset, int limit) {
        List<LogRecord> out    = new ArrayList<>();
        StringBuilder   sql    = buildLogSqlBase(actionFilter, minCreatedAt, actorNameLike);
        List<Object>    params = buildLogParams(actionFilter, minCreatedAt, actorNameLike);

        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapLogRecord(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB listLogs fehlgeschlagen", ex);
        }
        return out;
    }

    public int countLogs(String actionFilter, long minCreatedAt, String actorNameLike) {
        StringBuilder sql    = new StringBuilder("SELECT COUNT(*) FROM logs WHERE 1=1");
        List<Object>  params = buildLogParams(actionFilter, minCreatedAt, actorNameLike);
        appendLogFilters(sql, actionFilter, minCreatedAt, actorNameLike);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB countLogs fehlgeschlagen", ex);
        }
        return 0;
    }

    public Optional<LogRecord> getLogById(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra"
                     + " FROM logs WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapLogRecord(rs));
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB getLogById fehlgeschlagen", ex);
            return Optional.empty();
        }
    }

    public ItemStack createAdminReportBook() {
        List<String> lines  = new ArrayList<>();
        int totalLocks = 0, totalHoppers = 0, logs24h = 0, logs7d = 0, unauth24h = 0, admin24h = 0;
        long now  = System.currentTimeMillis();
        long h24  = now - 86_400_000L;
        long d7   = now - 604_800_000L;

        try (Connection c = db.getConnection()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM locks")) {
                if (rs.next()) totalLocks = rs.getInt(1);
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM secure_hoppers")) {
                if (rs.next()) totalHoppers = rs.getInt(1);
            }
            logs24h   = countLogsRaw(c, "ALL",                h24);
            logs7d    = countLogsRaw(c, "ALL",                d7);
            unauth24h = countLogsRaw(c, "UNAUTHORIZED_BREAK", h24);
            admin24h  = countLogsRaw(c, "ADMIN_BREAK",        h24);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "DB Admin-Report fehlgeschlagen", ex);
        }

        lines.add("ChestSecure Admin Report");
        lines.add(""); lines.add("Gesamt:");
        lines.add("- Locks: "          + totalLocks);
        lines.add("- Secure Hopper: "  + totalHoppers);
        lines.add(""); lines.add("Logs:");
        lines.add("- Letzte 24h: "     + logs24h);
        lines.add("- Letzte 7 Tage: "  + logs7d);
        lines.add("- Unauth (24h): "   + unauth24h);
        lines.add("- Admin (24h): "    + admin24h);
        lines.add(""); lines.add("Top Unauth (7d):");

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT actor_name,COUNT(*) AS c FROM logs"
                     + " WHERE created_at>=? AND action='UNAUTHORIZED_BREAK'"
                     + " GROUP BY actor_name ORDER BY c DESC LIMIT 10")) {
            ps.setLong(1, d7);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) { any = true; lines.add("- " + rs.getString(1) + ": " + rs.getInt(2)); }
                if (!any) lines.add("- keine");
            }
        } catch (SQLException ex) { lines.add("- (Fehler)"); }

        return BookUtil.buildBook("ChestSecure Report", "ChestSecure Report", "ChestSecure", lines);
    }

    private int countLogsRaw(Connection c, String action, long minTs) throws SQLException {
        String sql = "SELECT COUNT(*) FROM logs WHERE created_at>=?"
                + (action.equalsIgnoreCase("ALL") ? "" : " AND action=?");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, minTs);
            if (!action.equalsIgnoreCase("ALL")) ps.setString(2, action);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private StringBuilder buildLogSqlBase(String action, long minCreatedAt, String actorNameLike) {
        StringBuilder sql = new StringBuilder(
                "SELECT id,created_at,action,actor_uuid,actor_name,world_name,world_uuid,x,y,z,extra"
                + " FROM logs WHERE 1=1");
        appendLogFilters(sql, action, minCreatedAt, actorNameLike);
        return sql;
    }

    private List<Object> buildLogParams(String action, long minCreatedAt, String actorNameLike) {
        List<Object> params = new ArrayList<>();
        if (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL"))
            params.add(action);
        if (minCreatedAt > 0)
            params.add(minCreatedAt);
        if (actorNameLike != null && !actorNameLike.isBlank())
            params.add("%" + actorNameLike + "%");
        return params;
    }

    private void appendLogFilters(StringBuilder sql, String action, long minCreatedAt, String actorNameLike) {
        if (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL"))
            sql.append(" AND action=?");
        if (minCreatedAt > 0)
            sql.append(" AND created_at>=?");
        if (actorNameLike != null && !actorNameLike.isBlank())
            sql.append(" AND actor_name LIKE ?");
    }

    private LogRecord mapLogRecord(ResultSet rs) throws SQLException {
        return new LogRecord(
                rs.getLong(1), rs.getLong(2), rs.getString(3),
                UUID.fromString(rs.getString(4)), rs.getString(5), rs.getString(6),
                UUID.fromString(rs.getString(7)), rs.getInt(8), rs.getInt(9), rs.getInt(10),
                rs.getString(11));
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if      (p instanceof String  s) ps.setString(i + 1, s);
            else if (p instanceof Long    l) ps.setLong(i + 1, l);
            else if (p instanceof Integer n) ps.setInt(i + 1, n);
            else                             ps.setObject(i + 1, p);
        }
    }
}
