package de.SecureChest.plugin.lock;

import java.util.function.LongSupplier;
import java.util.logging.Logger;

final class ThrottledWarning {

    private final Logger       logger;
    private final long         intervalMillis;
    private final LongSupplier clock;

    private boolean hasWarned;
    private long    lastWarningAt;
    private int     suppressedCount;

    ThrottledWarning(Logger logger, long intervalMillis, LongSupplier clock) {
        this.logger         = logger;
        this.intervalMillis = intervalMillis;
        this.clock          = clock;
    }

    synchronized void warn(String message) {
        long now = clock.getAsLong();
        if (hasWarned && now - lastWarningAt < intervalMillis) {
            suppressedCount++;
            return;
        }
        String suppressedSuffix = suppressedCount > 0
                ? " (" + suppressedCount + " similar warnings suppressed since the last one)"
                : "";
        logger.warning(message + suppressedSuffix);
        hasWarned       = true;
        lastWarningAt   = now;
        suppressedCount = 0;
    }
}
