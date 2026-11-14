package de.SecureChest.plugin.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessResolverTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void noTrustAndNoDefaultsMeansNoAccess() {
        assertNull(AccessResolver.resolve(null, null, player));
        assertNull(AccessResolver.resolve(null, Map.of(), player));
    }

    @Test
    void defaultAppliesWhenTheLockHasNoTrustForThePlayer() {
        assertEquals(AccessLevel.VIEW,
                AccessResolver.resolve(null, Map.of(player, AccessLevel.VIEW), player));
        assertEquals(AccessLevel.STOCKER,
                AccessResolver.resolve(null, Map.of(player, AccessLevel.STOCKER), player));
    }

    @Test
    void defaultOfAnotherPlayerDoesNotApply() {
        assertNull(AccessResolver.resolve(null, Map.of(UUID.randomUUID(), AccessLevel.STOCKER), player));
    }

    @Test
    void trustOnTheLockTakesPriorityOverTheDefault() {
        assertEquals(AccessLevel.VIEW,
                AccessResolver.resolve(AccessLevel.VIEW, Map.of(player, AccessLevel.STOCKER), player));
        assertEquals(AccessLevel.STOCKER,
                AccessResolver.resolve(AccessLevel.STOCKER, Map.of(player, AccessLevel.VIEW), player));
    }
}
