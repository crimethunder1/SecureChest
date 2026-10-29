package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Meldung bei einem Sperr-Klick im Cooldown")
class ProtectionListenerCooldownNoticeTest {

    private static final String NOTICE_KEY = "aktion-zu-schnell";
    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");

    private LockServiceTestFixture fixture;
    private SqlLockService service;
    private MessageService messages;
    private Items items;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private ProtectionListener listener;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        service = fixture.service;
        messages = fixture.plugin.msg();
        when(messages.get(NOTICE_KEY)).thenReturn(NOTICE_KEY);
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.plugin.gui()).thenReturn(mock(GuiManager.class));
        when(fixture.plugin.confirms()).thenReturn(new ConfirmManager());
        when(fixture.plugin.keySecureHopperBlock()).thenReturn(SECURE_HOPPER_MARK);
        when(fixture.config.lockActionCooldownMs()).thenReturn(5000L);
        listener = new ProtectionListener(fixture.plugin, clock::get);
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private PlayerInteractEvent rightClick(Player player, Block block) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        return event;
    }

    private void holdChestKey(Player player, boolean sneaking) {
        when(items.isHoldingChestKey(player)).thenReturn(true);
        when(player.isSneaking()).thenReturn(sneaking);
    }

    private Block barrel(int x) {
        return fixture.world.setBlock(x, 64, 0, Material.BARREL);
    }

    private int noticeCount(Player player) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        int count = 0;
        for (Component component : captor.getAllValues()) {
            if (NOTICE_KEY.equals(((TextComponent) component).content())) count++;
        }
        return count;
    }

    private void verifyNoNotice(Player player) {
        verify(player, never()).sendMessage(org.mockito.ArgumentMatchers.<Component>argThat(component ->
                component instanceof TextComponent text && NOTICE_KEY.equals(text.content())));
    }

    @Test
    @DisplayName("Schleichen: der zweite Klick im Cooldown bekommt eine gelbe Meldung, die Aktion entfaellt")
    void sneakSecondClickNotice() {
        holdChestKey(alice, true);
        Block barrel = barrel(0);

        listener.onPlayerInteract(rightClick(alice, barrel));
        clock.addAndGet(200L);
        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(alice, times(2)).sendMessage(captor.capture());
        TextComponent notice = (TextComponent) captor.getAllValues().get(1);
        assertEquals(NOTICE_KEY, notice.content());
        assertEquals(NamedTextColor.YELLOW, notice.color());
    }

    @Test
    @DisplayName("Gehaltene Maustaste: hoechstens eine Meldung je 2 Sekunden")
    void heldMouseButtonThrottled() {
        holdChestKey(alice, true);
        Block barrel = barrel(0);
        listener.onPlayerInteract(rightClick(alice, barrel));

        for (int click = 0; click < 10; click++) {
            clock.addAndGet(150L);
            listener.onPlayerInteract(rightClick(alice, barrel));
        }
        assertEquals(1, noticeCount(alice));

        clock.addAndGet(700L);
        listener.onPlayerInteract(rightClick(alice, barrel));
        assertEquals(2, noticeCount(alice));
        assertTrue(service.isLocked(barrel));
    }

    @Test
    @DisplayName("Cooldown 0: kein Cooldown und keine Meldung")
    void zeroCooldownNoNotice() {
        when(fixture.config.lockActionCooldownMs()).thenReturn(0L);
        holdChestKey(alice, true);
        Block barrel = barrel(0);

        listener.onPlayerInteract(rightClick(alice, barrel));
        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        verify(messages, never()).get(NOTICE_KEY);
        verifyNoNotice(alice);
    }

    @Test
    @DisplayName("Getrennt je Spieler: beide bekommen ihre eigene Meldung")
    void separatePerPlayer() {
        holdChestKey(alice, true);
        holdChestKey(bob, true);

        listener.onPlayerInteract(rightClick(alice, barrel(0)));
        listener.onPlayerInteract(rightClick(bob, barrel(5)));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, barrel(10)));
        listener.onPlayerInteract(rightClick(bob, barrel(15)));

        assertEquals(1, noticeCount(alice));
        assertEquals(1, noticeCount(bob));
    }

    @Test
    @DisplayName("Ereignis der Zweithand zum selben Klick bekommt keine Meldung")
    void offHandEventNoNotice() {
        holdChestKey(alice, true);
        Block barrel = barrel(0);
        listener.onPlayerInteract(rightClick(alice, barrel));
        clearInvocations(alice);

        PlayerInteractEvent offHandEvent = rightClick(alice, barrel);
        when(offHandEvent.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        listener.onPlayerInteract(offHandEvent);

        verifyNoNotice(alice);
        assertTrue(service.isLocked(barrel));
    }

    @Test
    @DisplayName("Quick Lock: zweiter Klick im Cooldown bekommt die Meldung")
    void quickLockNotice() {
        service.setQuickLock(alice.getUniqueId(), true);
        when(items.hasChestKeyInInventory(alice)).thenReturn(true);

        listener.onPlayerInteract(rightClick(alice, barrel(0)));
        clock.addAndGet(100L);
        Block second = barrel(5);
        listener.onPlayerInteract(rightClick(alice, second));

        assertFalse(service.isLocked(second));
        assertEquals(1, noticeCount(alice));
    }

    @Test
    @DisplayName("Quick Remove: Entsperren im Cooldown bekommt die Meldung")
    void quickRemoveNotice() {
        service.setQuickRemove(alice.getUniqueId(), true);
        holdChestKey(alice, false);
        Block first = barrel(0);
        Block second = barrel(5);
        assertTrue(service.lock(alice, first));
        assertTrue(service.lock(alice, second));

        listener.onPlayerInteract(rightClick(alice, first));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, second));

        assertFalse(service.isLocked(first));
        assertTrue(service.isLocked(second));
        assertEquals(1, noticeCount(alice));
    }

    @Test
    @DisplayName("Dialog: ein zweiter Klick ohne Schleichen im Cooldown bekommt die Meldung")
    void dialogClickNotice() {
        holdChestKey(alice, false);

        listener.onPlayerInteract(rightClick(alice, barrel(0)));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, barrel(5)));

        assertEquals(1, noticeCount(alice));
    }

    @Test
    @DisplayName("Secure Hopper entbinden im Cooldown bekommt die Meldung")
    void unbindNotice() {
        Block container = barrel(0);
        assertTrue(service.lock(alice, container));
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(BlockFace.DOWN);
        Block hopperBlock = fixture.world.setBlock(0, 65, 0, Material.HOPPER, hopperData);
        assertTrue(service.bindSecureHopper(alice, hopperBlock, container));
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, barrel(5)));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        assertTrue(service.isSecureHopper(hopperBlock));
        assertEquals(1, noticeCount(alice));
    }

    @Test
    @DisplayName("Admin Key (fremder Weg) verwirft weiter still ohne Meldung")
    void adminKeyStaysSilent() {
        Player admin = fixture.player("Admin", true);
        when(items.isHoldingAdminKey(admin)).thenReturn(true);
        when(admin.isSneaking()).thenReturn(true);
        Block barrel = barrel(0);
        assertTrue(service.lock(alice, barrel));

        listener.onPlayerInteract(rightClick(admin, barrel));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(admin, barrel));

        verify(fixture.scheduler, times(1)).runTask(any(), any(Runnable.class));
        verifyNoNotice(admin);
    }

    @Test
    @DisplayName("Verlassen des Servers setzt Cooldown und Meldung zurueck")
    void evictResets() {
        holdChestKey(alice, true);
        listener.onPlayerInteract(rightClick(alice, barrel(0)));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, barrel(5)));
        assertEquals(1, noticeCount(alice));

        listener.evictPlayer(alice.getUniqueId());
        clock.addAndGet(100L);
        Block third = barrel(10);
        listener.onPlayerInteract(rightClick(alice, third));

        assertTrue(service.isLocked(third));
    }
}
