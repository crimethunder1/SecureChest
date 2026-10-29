package de.SecureChest.plugin.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class CooldownNotice {

    private final long intervalMillis;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastNoticeTimes = new ConcurrentHashMap<>();

    CooldownNotice(long intervalMillis, LongSupplier clock) {
        this.intervalMillis = intervalMillis;
        this.clock = clock;
    }

    boolean shouldNotify(UUID playerUuid) {
        long now = clock.getAsLong();
        Long lastNoticeTime = lastNoticeTimes.get(playerUuid);
        if (lastNoticeTime != null && now - lastNoticeTime < intervalMillis) return false;
        lastNoticeTimes.put(playerUuid, now);
        return true;
    }

    void forget(UUID playerUuid) {
        lastNoticeTimes.remove(playerUuid);
    }
}
