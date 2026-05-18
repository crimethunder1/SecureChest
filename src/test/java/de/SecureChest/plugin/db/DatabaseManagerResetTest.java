package de.SecureChest.plugin.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@DisplayName("DatabaseManager.resetConnectionForPool – Pool-Reset Sicherheit (SEC-04)")
class DatabaseManagerResetTest {

    @Test
    @DisplayName("Wenn autoCommit=false: rollback + setAutoCommit(true) + clearWarnings")
    void offeneTransaktion_wirdRollbacked() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(false);

        DatabaseManager.resetConnectionForPool(connection);

        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).clearWarnings();
    }

    @Test
    @DisplayName("Wenn autoCommit=true: KEIN rollback, kein setAutoCommit – nur clearWarnings")
    void autoCommitTrue_keinRollback() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);

        DatabaseManager.resetConnectionForPool(connection);

        verify(connection, never()).rollback();
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection).clearWarnings();
    }

    @Test
    @DisplayName("rollback() wirft Exception → reset wirft nicht weiter und ruft clearWarnings trotzdem")
    void rollbackException_resetSchluckt_clearWarningsLaeuft() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("rollback failed")).when(connection).rollback();

        assertDoesNotThrow(() -> DatabaseManager.resetConnectionForPool(connection));
        verify(connection).clearWarnings();
    }

    @Test
    @DisplayName("setAutoCommit() wirft Exception → reset wirft nicht weiter")
    void setAutoCommitException_resetSchluckt() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("setAutoCommit failed")).when(connection).setAutoCommit(true);

        assertDoesNotThrow(() -> DatabaseManager.resetConnectionForPool(connection));
        verify(connection).clearWarnings();
    }

    @Test
    @DisplayName("clearWarnings() wirft Exception → reset wirft nicht weiter")
    void clearWarningsException_resetSchluckt() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("clearWarnings failed")).when(connection).clearWarnings();

        assertDoesNotThrow(() -> DatabaseManager.resetConnectionForPool(connection));
    }

    @Test
    @DisplayName("getAutoCommit() wirft Exception → reset bricht ab, ohne weiter zu manipulieren")
    void getAutoCommitException_resetBrichtAb() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenThrow(new SQLException("connection broken"));

        assertDoesNotThrow(() -> DatabaseManager.resetConnectionForPool(connection));
        verify(connection, never()).rollback();
        verify(connection, never()).clearWarnings();
    }
}
