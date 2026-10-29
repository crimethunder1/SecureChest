package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.area.AreaService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: eigene Log-Aktionen fuer Aenderungen eines Admins an fremden Sperren")
class SqlLockServiceAdminLogTest {

    private record LoggedAction(String action, String extra) {}

    private LockServiceTestFixture fixture;
    private SqlLockService service;
    private Player alice;
    private Player bob;
    private Player operator;
    private Block chest;
    private String lockId;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        service = fixture.service;
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
        operator = fixture.player("Operator", true);
        when(fixture.config.undoSeconds()).thenReturn(60);
        Block operatorBlock = fixture.world.blockAt(3, 64, 3);
        Location operatorLocation = mock(Location.class);
        when(operatorLocation.getBlock()).thenReturn(operatorBlock);
        when(operator.getLocation()).thenReturn(operatorLocation);
        chest = fixture.world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, chest));
        lockId = service.getLock(chest).orElseThrow().key().asId();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private List<LoggedAction> loggedActions() throws SQLException {
        List<LoggedAction> logged = new ArrayList<>();
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            if (sql.startsWith("INSERT INTO logs")) {
                String[] row = new String[1];
                doAnswer(setString -> {
                    int index = setString.getArgument(0);
                    String value = setString.getArgument(1);
                    if (index == 2) row[0] = value;
                    if (index == 10) logged.add(new LoggedAction(row[0], value));
                    return null;
                }).when(statement).setString(anyInt(), any());
            }
            return statement;
        });
        doReturn(connection).when(fixture.database).getConnection();
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), taskCaptor.capture());
        List<Runnable> tasks = taskCaptor.getAllValues();
        for (int index = 1; index < tasks.size(); index++) tasks.get(index).run();
        return logged;
    }

    private LoggedAction lastLogged() throws SQLException {
        List<LoggedAction> logged = loggedActions();
        return logged.getLast();
    }

    @Test
    @DisplayName("Neue Konstanten haben feste Werte")
    void konstanten() {
        assertEquals("LOCK_TRANSFER_ADMIN", LogRepository.ACTION_LOCK_TRANSFER_ADMIN);
        assertEquals("TRUST_SET_ADMIN", LogRepository.ACTION_TRUST_SET_ADMIN);
        assertEquals("TRUST_REMOVE_ADMIN", LogRepository.ACTION_TRUST_REMOVE_ADMIN);
    }

    @Test
    @DisplayName("Entfernen durch Admin: LOCK_REMOVE_ADMIN mit target_owner, Undo fuer den Admin")
    void entfernen_admin() throws SQLException {
        assertTrue(service.unlockByIdWithUndo(operator, lockId));

        assertEquals(new LoggedAction(LogRepository.ACTION_LOCK_REMOVE_ADMIN, "target_owner=" + alice.getUniqueId()),
                lastLogged());
        assertFalse(service.isLocked(chest));
        assertTrue(fixture.plugin.undos().hasUndo(operator.getUniqueId()));
    }

    @Test
    @DisplayName("Rueckgaengig nach dem Entfernen durch einen Admin stellt die Sperre fuer den Besitzer wieder her")
    void entfernen_admin_undo() {
        assertTrue(service.unlockByIdWithUndo(operator, lockId));

        assertTrue(service.restoreUndo(operator));

        assertEquals(alice.getUniqueId(), service.getLock(chest).orElseThrow().owner());
    }

    @Test
    @DisplayName("Entfernen durch den Besitzer: LOCK_REMOVE wie bisher")
    void entfernen_besitzer() throws SQLException {
        assertTrue(service.unlockByIdWithUndo(alice, lockId));

        assertEquals(new LoggedAction(LogRepository.ACTION_LOCK_REMOVE, null), lastLogged());
    }

    @Test
    @DisplayName("Uebertragen durch Admin: LOCK_TRANSFER_ADMIN mit altem und neuem Besitzer")
    void uebertragen_admin() throws SQLException {
        assertTrue(service.transferLock(operator, lockId, bob.getUniqueId()));

        assertEquals(new LoggedAction(LogRepository.ACTION_LOCK_TRANSFER_ADMIN,
                "from=" + alice.getUniqueId() + ",to=" + bob.getUniqueId()), lastLogged());
        assertEquals(bob.getUniqueId(), service.getLock(chest).orElseThrow().owner());
    }

    @Test
    @DisplayName("Uebertragen durch den Besitzer: LOCK_TRANSFER wie bisher")
    void uebertragen_besitzer() throws SQLException {
        assertTrue(service.transferLock(alice, lockId, bob.getUniqueId()));

        assertEquals(new LoggedAction(LogRepository.ACTION_LOCK_TRANSFER,
                "from=" + alice.getUniqueId() + ",to=" + bob.getUniqueId()), lastLogged());
    }

    @Test
    @DisplayName("Vertrauen setzen durch Admin: TRUST_SET_ADMIN mit Spieler, Stufe und Besitzer")
    void vertrauenSetzen_admin() throws SQLException {
        assertTrue(service.setTrustById(operator, lockId, bob.getUniqueId(), AccessLevel.STOCKER));

        assertEquals(new LoggedAction(LogRepository.ACTION_TRUST_SET_ADMIN,
                "target=" + bob.getUniqueId() + ",level=STOCKER,target_owner=" + alice.getUniqueId()), lastLogged());
        assertEquals(AccessLevel.STOCKER, service.getLock(chest).orElseThrow().trusted().get(bob.getUniqueId()));
    }

    @Test
    @DisplayName("Vertrauen setzen durch den Besitzer: TRUST_SET wie bisher")
    void vertrauenSetzen_besitzer() throws SQLException {
        assertTrue(service.setTrustById(alice, lockId, bob.getUniqueId(), AccessLevel.VIEW));

        assertEquals(new LoggedAction(LogRepository.ACTION_TRUST_SET,
                "target=" + bob.getUniqueId() + ",level=VIEW"), lastLogged());
    }

    @Test
    @DisplayName("Vertrauen entfernen durch Admin: TRUST_REMOVE_ADMIN mit Spieler, bisheriger Stufe und Besitzer")
    void vertrauenEntfernen_admin() throws SQLException {
        assertTrue(service.setTrustById(alice, lockId, bob.getUniqueId(), AccessLevel.VIEW));

        assertTrue(service.removeTrustById(operator, lockId, bob.getUniqueId()));

        assertEquals(new LoggedAction(LogRepository.ACTION_TRUST_REMOVE_ADMIN,
                "target=" + bob.getUniqueId() + ",level=VIEW,target_owner=" + alice.getUniqueId()), lastLogged());
        assertFalse(service.getLock(chest).orElseThrow().trusted().containsKey(bob.getUniqueId()));
    }

    @Test
    @DisplayName("Vertrauen entfernen durch den Besitzer: TRUST_REMOVE wie bisher")
    void vertrauenEntfernen_besitzer() throws SQLException {
        assertTrue(service.setTrustById(alice, lockId, bob.getUniqueId(), AccessLevel.VIEW));

        assertTrue(service.removeTrustById(alice, lockId, bob.getUniqueId()));

        assertEquals(new LoggedAction(LogRepository.ACTION_TRUST_REMOVE, "target=" + bob.getUniqueId()), lastLogged());
    }

    @Test
    @DisplayName("Fremder ohne Admin-Permission: Dienst lehnt ab, kein Log")
    void fremder_abgelehnt() throws SQLException {
        int before = loggedActions().size();

        assertFalse(service.unlockByIdWithUndo(bob, lockId));
        assertFalse(service.transferLock(bob, lockId, bob.getUniqueId()));
        assertFalse(service.setTrustById(bob, lockId, bob.getUniqueId(), AccessLevel.STOCKER));
        assertFalse(service.removeTrustById(bob, lockId, alice.getUniqueId()));

        assertEquals(before, loggedActions().size());
        assertTrue(service.isLocked(chest));
    }

    @Test
    @DisplayName("Sperre ausserhalb eines aktiven Wirkungsbereichs: Entfernen, Uebertragen und Vertrauen gehen durch")
    void ausserhalbWirkungsbereich_nichtBlockiert() {
        AreaService strictAreas = mock(AreaService.class);
        when(strictAreas.isEnabled()).thenReturn(true);
        when(strictAreas.isLockPlacementAllowed(any())).thenReturn(false);
        when(strictAreas.isInside(anyString(), anyInt(), anyInt())).thenReturn(false);
        when(fixture.plugin.areas()).thenReturn(strictAreas);

        assertTrue(service.setTrustById(operator, lockId, bob.getUniqueId(), AccessLevel.VIEW));
        assertTrue(service.removeTrustById(operator, lockId, bob.getUniqueId()));
        assertTrue(service.transferLock(operator, lockId, bob.getUniqueId()));
        assertTrue(service.unlockByIdWithUndo(operator, lockId));

        assertFalse(service.isLocked(chest));
    }

    @Test
    @DisplayName("Bestehendes Verhalten: Rueckgaengig einer Sperre ausserhalb des aktiven Wirkungsbereichs wird abgelehnt")
    void ausserhalbWirkungsbereich_undoAbgelehnt() {
        assertTrue(service.unlockByIdWithUndo(operator, lockId));
        AreaService strictAreas = mock(AreaService.class);
        when(strictAreas.isEnabled()).thenReturn(true);
        when(strictAreas.isLockPlacementAllowed(any())).thenReturn(false);
        when(fixture.plugin.areas()).thenReturn(strictAreas);

        assertFalse(service.restoreUndo(operator));

        assertFalse(service.isLocked(chest));
        assertTrue(fixture.plugin.undos().hasUndo(operator.getUniqueId()));
    }
}
