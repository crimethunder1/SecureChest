package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.ui.UndoManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: Rueckgaengig schreibt den Schnappschuss vollstaendig und schuetzt neuere Sperren")
class SqlLockServiceUndoTest {

    private record ExecutedRow(String sql, Map<Integer, Object> parameters) {}

    private LockServiceTestFixture fixture;
    private SqlLockService service;
    private Player alice;
    private Player bob;
    private Player carol;
    private Block chest;
    private String lockId;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        service = fixture.service;
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
        carol = fixture.player("Carol", false);
        Block standingBlock = fixture.world.blockAt(3, 64, 3);
        for (Player player : List.of(alice, bob, carol)) {
            Location location = mock(Location.class);
            when(location.getBlock()).thenReturn(standingBlock);
            when(player.getLocation()).thenReturn(location);
        }
        chest = fixture.world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, chest));
        lockId = service.getLock(chest).orElseThrow().key().asId();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private List<ExecutedRow> runAsyncTasksAfterThisPoint(Runnable action) throws SQLException {
        clearInvocations(fixture.scheduler);
        action.run();
        List<ExecutedRow> executed = new ArrayList<>();
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            Map<Integer, Object> parameters = new HashMap<>();
            PreparedStatement statement = mock(PreparedStatement.class);
            doAnswer(call -> parameters.put(call.getArgument(0), call.getArgument(1)))
                    .when(statement).setString(anyInt(), any());
            doAnswer(call -> parameters.put(call.getArgument(0), call.getArgument(1)))
                    .when(statement).setInt(anyInt(), anyInt());
            doAnswer(call -> parameters.put(call.getArgument(0), call.getArgument(1)))
                    .when(statement).setLong(anyInt(), anyLong());
            doAnswer(call -> executed.add(new ExecutedRow(sql, Map.copyOf(parameters))))
                    .when(statement).addBatch();
            doAnswer(call -> {
                executed.add(new ExecutedRow(sql, Map.copyOf(parameters)));
                return 1;
            }).when(statement).executeUpdate();
            when(statement.executeBatch()).thenReturn(new int[0]);
            return statement;
        });
        doReturn(connection).when(fixture.database).getConnection();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), taskCaptor.capture());
        for (Runnable task : taskCaptor.getAllValues()) task.run();
        return executed;
    }

    private static List<ExecutedRow> rowsStartingWith(List<ExecutedRow> rows, String sqlPrefix) {
        return rows.stream().filter(row -> row.sql().startsWith(sqlPrefix)).toList();
    }

    @Test
    @DisplayName("Rueckgaengig nach Uebertragen: Cache und Datenbank bekommen den alten Besitzer, die Zeile wird ersetzt")
    void transferUndo_replacesRowWithPreviousOwner() throws SQLException {
        assertTrue(service.setTrustById(alice, lockId, carol.getUniqueId(), AccessLevel.VIEW));
        assertTrue(service.transferLock(alice, lockId, bob.getUniqueId()));

        List<ExecutedRow> executed = runAsyncTasksAfterThisPoint(() -> assertTrue(service.restoreUndo(alice)));

        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
        assertEquals(AccessLevel.VIEW, service.getLock(chest).orElseThrow().trusted().get(carol.getUniqueId()));
        List<ExecutedRow> replaced = rowsStartingWith(executed, "INSERT OR REPLACE INTO locks");
        assertEquals(1, replaced.size());
        assertEquals(alice.getUniqueId().toString(), replaced.getFirst().parameters().get(6));
        assertTrue(rowsStartingWith(executed, "INSERT OR IGNORE INTO locks").isEmpty());

        int deleteTrust = executed.indexOf(rowsStartingWith(executed, "DELETE FROM trusted").getFirst());
        int insertTrust = executed.indexOf(rowsStartingWith(executed, "INSERT OR REPLACE INTO trusted").getFirst());
        assertTrue(deleteTrust < insertTrust, "Vertrauen der Position wird vor dem Einfuegen geleert");
        assertEquals(carol.getUniqueId().toString(), executed.get(insertTrust).parameters().get(5));
    }

    @Test
    @DisplayName("Rueckgaengig nach Entsperren: Sperre zurueck, Datenbank ersetzt statt ignoriert")
    void unlockUndo_stillWorks() throws SQLException {
        assertTrue(service.unlockByIdWithUndo(alice, lockId));
        assertFalse(service.isLocked(chest));

        List<ExecutedRow> executed = runAsyncTasksAfterThisPoint(() -> assertTrue(service.restoreUndo(alice)));

        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
        assertEquals(1, rowsStartingWith(executed, "INSERT OR REPLACE INTO locks").size());
        assertFalse(fixture.plugin.undos().hasUndo(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Rueckgaengig nach globalem Entsperren: alle Sperren des Besitzers kommen zurueck")
    void unlockAllUndo_stillWorks() {
        Block second = fixture.world.setBlock(5, 64, 5, Material.BARREL);
        assertTrue(service.lock(alice, second));
        assertTrue(service.unlockAllWithUndo(alice, alice.getUniqueId()));
        assertEquals(0, service.countLocks(alice.getUniqueId()));

        assertTrue(service.restoreUndo(alice));

        assertEquals(2, service.countLocks(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Fremde neuere Sperre an der Position nach dem Entsperren: Rueckgaengig abgelehnt, nichts ueberschrieben")
    void unlockUndo_deniedForNewerForeignLock() {
        assertTrue(service.unlockByIdWithUndo(alice, lockId));
        assertTrue(service.lock(bob, chest));

        assertFalse(service.restoreUndo(alice));

        assertEquals(bob.getUniqueId(), service.getLock(chest).orElseThrow().owner());
        assertTrue(fixture.plugin.undos().hasUndo(alice.getUniqueId()));
    }

    @Test
    @DisplayName("Nach Uebertragen neu gesperrt von einem Dritten: Rueckgaengig abgelehnt")
    void transferUndo_deniedForNewerForeignLock() {
        assertTrue(service.transferLock(alice, lockId, bob.getUniqueId()));
        assertTrue(service.unlockByIdWithUndo(bob, lockId));
        assertTrue(service.lock(carol, chest));

        assertFalse(service.restoreUndo(alice));

        assertEquals(carol.getUniqueId(), service.getLock(chest).orElseThrow().owner());
    }

    @Test
    @DisplayName("Nach Uebertragen vom neuen Besitzer entsperrt: leere Position, Rueckgaengig erlaubt")
    void transferUndo_allowedForEmptyPosition() {
        assertTrue(service.transferLock(alice, lockId, bob.getUniqueId()));
        assertTrue(service.unlockByIdWithUndo(bob, lockId));

        assertTrue(service.restoreUndo(alice));

        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
    }

    @Test
    @DisplayName("Globales Entsperren, danach eine Position fremd gesperrt: das ganze Rueckgaengig wird abgelehnt")
    void unlockAllUndo_deniedWhenOnePositionTaken() {
        Block second = fixture.world.setBlock(5, 64, 5, Material.BARREL);
        assertTrue(service.lock(alice, second));
        assertTrue(service.unlockAllWithUndo(alice, alice.getUniqueId()));
        assertTrue(service.lock(bob, second));

        assertFalse(service.restoreUndo(alice));

        assertFalse(service.isLocked(chest));
        assertEquals(bob.getUniqueId(), service.getLock(second).orElseThrow().owner());
    }

    @Test
    @DisplayName("Speicherfehler beim Rueckgaengig nach Uebertragen: der Cache zeigt wieder den neuen Besitzer")
    void transferUndo_databaseFailureRestoresTransferredLock() {
        assertTrue(service.transferLock(alice, lockId, bob.getUniqueId()));
        clearInvocations(fixture.scheduler);
        assertTrue(service.restoreUndo(alice));
        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
        ArgumentCaptor<Runnable> asyncCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), asyncCaptor.capture());

        for (Runnable task : asyncCaptor.getAllValues()) task.run();
        ArgumentCaptor<Runnable> mainCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTask(eq(fixture.plugin), mainCaptor.capture());
        for (Runnable task : mainCaptor.getAllValues()) task.run();

        assertEquals(bob.getUniqueId(), service.getLock(chest).orElseThrow().owner());
    }
}
