package de.SecureChest.plugin;

import de.SecureChest.plugin.sign.SignManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ChestSecurePlugin.onDisable: Namenstexte werden entfernt und die Kontrolle gestoppt")
class ChestSecurePluginDisableTest {

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = ChestSecurePlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("onDisable stoppt die langsame Kontrolle und entfernt danach alle Texte")
    void disableRemovesSignsAndStopsCoverCheck() throws ReflectiveOperationException {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        doCallRealMethod().when(plugin).onDisable();
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        SignManager signs = mock(SignManager.class);
        Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();
        pendingChat.put(UUID.randomUUID(), new PendingChatAction.GlobalUnlock());
        setField(plugin, "signManager", signs);
        setField(plugin, "pendingChatActions", pendingChat);

        plugin.onDisable();

        InOrder order = inOrder(signs);
        order.verify(signs).stopCoverCheck();
        order.verify(signs).removeAllSigns();
        assertTrue(pendingChat.isEmpty());
    }

    @Test
    @DisplayName("onDisable ohne SignManager (Start abgebrochen) wirft keine Ausnahme")
    void disableWithoutSignManager() throws ReflectiveOperationException {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        doCallRealMethod().when(plugin).onDisable();
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        setField(plugin, "pendingChatActions", new ConcurrentHashMap<UUID, PendingChatAction>());

        assertDoesNotThrow(plugin::onDisable);
    }
}
