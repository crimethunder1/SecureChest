package de.SecureChest.plugin.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CooldownNotice: hoechstens eine Meldung je Spieler und Intervall")
class CooldownNoticeTest {

    private final AtomicLong clock = new AtomicLong(10_000L);
    private final CooldownNotice notice = new CooldownNotice(2000L, clock::get);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    @DisplayName("Die erste Meldung kommt sofort")
    void firstNotice() {
        assertTrue(notice.shouldNotify(alice));
    }

    @Test
    @DisplayName("Innerhalb von 2 Sekunden keine zweite Meldung, ab 2 Sekunden wieder eine")
    void intervalBlocksSecondNotice() {
        assertTrue(notice.shouldNotify(alice));
        clock.addAndGet(1999L);
        assertFalse(notice.shouldNotify(alice));
        clock.addAndGet(1L);
        assertTrue(notice.shouldNotify(alice));
        assertFalse(notice.shouldNotify(alice));
    }

    @Test
    @DisplayName("Unterdrueckte Klicks verlaengern das Intervall nicht")
    void suppressedClicksDoNotExtendInterval() {
        assertTrue(notice.shouldNotify(alice));
        for (int click = 0; click < 19; click++) {
            clock.addAndGet(100L);
            assertFalse(notice.shouldNotify(alice));
        }
        clock.addAndGet(100L);
        assertTrue(notice.shouldNotify(alice));
    }

    @Test
    @DisplayName("Getrennt je Spieler")
    void separatePerPlayer() {
        assertTrue(notice.shouldNotify(alice));
        assertTrue(notice.shouldNotify(bob));
        assertFalse(notice.shouldNotify(alice));
        assertFalse(notice.shouldNotify(bob));
    }

    @Test
    @DisplayName("Vergessen (Verlassen des Servers) erlaubt sofort wieder eine Meldung")
    void forgetResets() {
        assertTrue(notice.shouldNotify(alice));
        notice.forget(alice);
        assertTrue(notice.shouldNotify(alice));
    }
}
