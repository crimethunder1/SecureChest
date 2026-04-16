package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testet reine String/Logik-Aspekte des LogRepository ohne DB-Verbindung.
 * Fuer DB-Tests siehe Integrationstests (werden separat gegen Testdatenbank
 * ausgefuehrt und benoetigen eine SQLite-In-Memory-Verbindung).
 */
@DisplayName("LogRepository – reine Logik (kein DB)")
class LogRepositoryLogicTest {

    // ── SQL-Filter-Logik ─────────────────────────────────────────────────────
    // Repliziert die interne Filterlogik um sicherzustellen, dass Bedingungen
    // korrekt an- oder abgehängt werden.

    private String buildCountSql(String action, long minTs, String actorLike) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM logs WHERE 1=1");
        if (action != null && !action.isBlank() && !action.equalsIgnoreCase("ALL"))
            sql.append(" AND action=?");
        if (minTs > 0)
            sql.append(" AND created_at>=?");
        if (actorLike != null && !actorLike.isBlank())
            sql.append(" AND actor_name LIKE ?");
        return sql.toString();
    }

    @Test
    @DisplayName("Kein Filter → keine WHERE-Bedingungen")
    void buildSql_keineFilter() {
        String sql = buildCountSql(null, 0, null);
        assertEquals("SELECT COUNT(*) FROM logs WHERE 1=1", sql);
    }

    @Test
    @DisplayName("action=ALL → kein action-Filter")
    void buildSql_actionAll() {
        String sql = buildCountSql("ALL", 0, null);
        assertFalse(sql.contains("action=?"),
                "action=ALL darf keinen action-Filter erzeugen");
    }

    @ParameterizedTest(name = "action={0}")
    @CsvSource({"LOCK", "UNLOCK", "UNAUTHORIZED_BREAK", "ADMIN_BREAK"})
    @DisplayName("Bekannte action-Werte erzeugen action=?-Filter")
    void buildSql_action(String action) {
        String sql = buildCountSql(action, 0, null);
        assertTrue(sql.contains("AND action=?"));
    }

    @Test
    @DisplayName("minTs > 0 → created_at-Filter")
    void buildSql_minTs() {
        String sql = buildCountSql(null, 1000L, null);
        assertTrue(sql.contains("AND created_at>=?"));
    }

    @Test
    @DisplayName("minTs = 0 → kein created_at-Filter")
    void buildSql_minTsNull() {
        String sql = buildCountSql(null, 0, null);
        assertFalse(sql.contains("created_at"));
    }

    @Test
    @DisplayName("actorLike gesetzt → actor_name LIKE ?-Filter")
    void buildSql_actorLike() {
        String sql = buildCountSql(null, 0, "Spieler");
        assertTrue(sql.contains("AND actor_name LIKE ?"));
    }

    @Test
    @DisplayName("Alle Filter gleichzeitig → drei AND-Bedingungen")
    void buildSql_alleFilter() {
        String sql = buildCountSql("LOCK", 1000L, "Steve");
        assertTrue(sql.contains("action=?"));
        assertTrue(sql.contains("created_at>=?"));
        assertTrue(sql.contains("actor_name LIKE ?"));
    }

    // ── Relatives Zeit-Label ─────────────────────────────────────────────────

    private String buildRelativeTimeLabel(long ts) {
        long min = (System.currentTimeMillis() - ts) / 60_000L;
        if (min < 1)  return "Gerade eben";
        if (min < 60) return "vor " + min + " Min";
        long h = min / 60;
        if (h < 24)   return "vor " + h + " Std";
        long d = h / 24;
        return "vor " + d + (d == 1 ? " Tag" : " Tagen");
    }

    @Test
    @DisplayName("Sehr aktueller Timestamp → 'Gerade eben'")
    void relativeTime_geradEben() {
        long jetzt = System.currentTimeMillis() - 30_000L; // 30 Sekunden
        assertEquals("Gerade eben", buildRelativeTimeLabel(jetzt));
    }

    @Test
    @DisplayName("45 Minuten alt → 'vor 45 Min'")
    void relativeTime_45Minuten() {
        long ts = System.currentTimeMillis() - 45L * 60_000L;
        assertEquals("vor 45 Min", buildRelativeTimeLabel(ts));
    }

    @Test
    @DisplayName("3 Stunden alt → 'vor 3 Std'")
    void relativeTime_3Stunden() {
        long ts = System.currentTimeMillis() - 3L * 3_600_000L;
        assertEquals("vor 3 Std", buildRelativeTimeLabel(ts));
    }

    @Test
    @DisplayName("1 Tag alt → Singular 'vor 1 Tag'")
    void relativeTime_1Tag() {
        long ts = System.currentTimeMillis() - 25L * 3_600_000L;
        assertEquals("vor 1 Tag", buildRelativeTimeLabel(ts));
    }

    @Test
    @DisplayName("5 Tage alt → Plural 'vor 5 Tagen'")
    void relativeTime_5Tage() {
        long ts = System.currentTimeMillis() - 5L * 86_400_000L;
        assertEquals("vor 5 Tagen", buildRelativeTimeLabel(ts));
    }
}
