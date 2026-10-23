package de.SecureChest.plugin.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("ThrottledWarning: hoechstens eine Warnung pro Intervall")
class ThrottledWarningTest {

    private static final long INTERVAL_MILLIS = 60_000L;

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private Logger logger;
    private ThrottledWarning warning;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        warning = new ThrottledWarning(logger, INTERVAL_MILLIS, clock::get);
    }

    private void warnAt(long offsetMillis) {
        clock.set(1_000_000L + offsetMillis);
        warning.warn("fallback");
    }

    private List<String> loggedWarnings(int expectedCount) {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(expectedCount)).warning(messageCaptor.capture());
        return messageCaptor.getAllValues();
    }

    @Test
    @DisplayName("Erster Aufruf schreibt die Warnung ohne Zusatz")
    void ersterAufruf_schreibtWarnungOhneZusatz() {
        warnAt(0);

        assertEquals(List.of("fallback"), loggedWarnings(1));
    }

    @Test
    @DisplayName("Mehrere Aufrufe innerhalb von 60 Sekunden ergeben eine Warnung")
    void mehrereAufrufeInnerhalbIntervall_eineWarnung() {
        warnAt(0);
        warnAt(1);
        warnAt(30_000);
        warnAt(INTERVAL_MILLIS - 1);

        assertEquals(List.of("fallback"), loggedWarnings(1));
    }

    @Test
    @DisplayName("Nach 60 Sekunden wieder eine Warnung mit der Anzahl der unterdrueckten Meldungen")
    void nachIntervall_wiederWarnungMitAnzahl() {
        warnAt(0);
        warnAt(10_000);
        warnAt(20_000);
        warnAt(INTERVAL_MILLIS);

        assertEquals(List.of("fallback",
                        "fallback (2 similar warnings suppressed since the last one)"),
                loggedWarnings(2));
    }

    @Test
    @DisplayName("Intervall zaehlt ab der letzten geschriebenen Warnung, Zaehler startet danach neu")
    void intervallAbLetzterWarnung_zaehlerStartetNeu() {
        warnAt(0);
        warnAt(INTERVAL_MILLIS);
        warnAt(INTERVAL_MILLIS + 1);
        warnAt(2 * INTERVAL_MILLIS - 1);
        warnAt(2 * INTERVAL_MILLIS);

        assertEquals(List.of("fallback",
                        "fallback",
                        "fallback (2 similar warnings suppressed since the last one)"),
                loggedWarnings(3));
    }
}
