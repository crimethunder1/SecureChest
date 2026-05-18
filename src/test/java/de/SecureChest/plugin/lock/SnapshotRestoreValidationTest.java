package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ui.UndoSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SqlLockService.isSnapshotRestorableBy – Owner/Admin-Validierung")
class SnapshotRestoreValidationTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UndoSnapshot snapshotOf(UUID lockOwner, UUID hopperOwner) {
        LockKey lockKey = new LockKey(WORLD, 0, 64, 0);
        LockKey hopperKey = new LockKey(WORLD, 1, 64, 0);
        LockKey boundKey = new LockKey(WORLD, 0, 64, 0);
        long now = System.currentTimeMillis();
        return new UndoSnapshot(
                lockOwner == null
                        ? List.of()
                        : List.of(new UndoSnapshot.LockRow(lockKey, "CHEST", lockOwner, now)),
                List.of(),
                hopperOwner == null
                        ? List.of()
                        : List.of(new UndoSnapshot.SecureHopperRow(hopperKey, hopperOwner, boundKey, now)),
                now,
                now + 60_000L);
    }

    @Test
    @DisplayName("Actor restored eigenen Lock-Snapshot")
    void eigenerLockSnapshot_erlaubt() {
        UUID actor = UUID.randomUUID();
        UndoSnapshot snap = snapshotOf(actor, null);
        assertTrue(SqlLockService.isSnapshotRestorableBy(snap, actor, false));
    }

    @Test
    @DisplayName("Actor lehnt fremden Lock-Snapshot ohne Admin ab")
    void fremderLockSnapshot_ohneAdmin_abgelehnt() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UndoSnapshot snap = snapshotOf(other, null);
        assertFalse(SqlLockService.isSnapshotRestorableBy(snap, actor, false));
    }

    @Test
    @DisplayName("Admin restored fremden Lock-Snapshot")
    void fremderLockSnapshot_mitAdmin_erlaubt() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UndoSnapshot snap = snapshotOf(other, null);
        assertTrue(SqlLockService.isSnapshotRestorableBy(snap, actor, true));
    }

    @Test
    @DisplayName("Hopper-Owner-Check ist symmetrisch zum Lock-Owner-Check")
    void fremderHopperSnapshot_ohneAdmin_abgelehnt() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UndoSnapshot snap = snapshotOf(actor, other);
        assertFalse(SqlLockService.isSnapshotRestorableBy(snap, actor, false));
    }

    @Test
    @DisplayName("Gemischter Snapshot mit nur fremdem Hopper, eigenem Lock → ohne Admin abgelehnt")
    void gemischterSnapshot_einEintragFremd_abgelehnt() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UndoSnapshot snap = snapshotOf(actor, other);
        assertFalse(SqlLockService.isSnapshotRestorableBy(snap, actor, false));
    }

    @Test
    @DisplayName("Leerer Snapshot ist trivial restorable")
    void leererSnapshot_erlaubt() {
        UndoSnapshot snap = snapshotOf(null, null);
        assertTrue(SqlLockService.isSnapshotRestorableBy(snap, UUID.randomUUID(), false));
    }
}
