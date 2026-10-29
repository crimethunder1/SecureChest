package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Chest Key und Admin Key lassen sich nicht als Block platzieren")
class ProtectionListenerKeyPlaceTest {

    private final ItemStack chestKey = mock(ItemStack.class);
    private final ItemStack adminKey = mock(ItemStack.class);
    private final ItemStack normalHook = mock(ItemStack.class);
    private LockService locks;
    private Player player;
    private ProtectionListener listener;

    @BeforeEach
    void setUp() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        Items items = mock(Items.class);
        when(items.is(chestKey, Items.Type.CHEST_KEY)).thenReturn(true);
        when(items.is(adminKey, Items.Type.ADMIN_KEY)).thenReturn(true);
        when(plugin.items()).thenReturn(items);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        player = mock(Player.class);
        listener = new ProtectionListener(plugin);
    }

    private BlockPlaceEvent placing(ItemStack heldItem) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getItemInHand()).thenReturn(heldItem);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    @Test
    @DisplayName("Chest Key: Platzieren wird ohne Meldung abgebrochen")
    void chestKeyIsCancelled() {
        BlockPlaceEvent event = placing(chestKey);

        listener.onKeyPlace(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(player, locks);
    }

    @Test
    @DisplayName("Admin Key: Platzieren wird ohne Meldung abgebrochen")
    void adminKeyIsCancelled() {
        BlockPlaceEvent event = placing(adminKey);

        listener.onKeyPlace(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(player, locks);
    }

    @Test
    @DisplayName("Normaler Haken und leere Hand werden nicht angefasst")
    void normalHookIsNotTouched() {
        BlockPlaceEvent hookEvent = placing(normalHook);
        BlockPlaceEvent emptyHandEvent = placing(null);

        listener.onKeyPlace(hookEvent);
        listener.onKeyPlace(emptyHandEvent);

        verify(hookEvent, never()).setCancelled(anyBoolean());
        verify(emptyHandEvent, never()).setCancelled(anyBoolean());
        verifyNoInteractions(player, locks);
    }

    @Test
    @DisplayName("Der Handler laeuft mit Prioritaet HIGHEST und ueberspringt bereits abgebrochene Ereignisse")
    void handlerPriority() throws NoSuchMethodException {
        Method handler = ProtectionListener.class.getMethod("onKeyPlace", BlockPlaceEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
        assertTrue(annotation.ignoreCancelled());
    }
}
