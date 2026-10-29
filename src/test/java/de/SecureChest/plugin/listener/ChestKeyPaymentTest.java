package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.util.Items;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ChestKeyPayment: Entnahme eines Chest Keys und Rueckgabe bei gescheiterter Sperre")
class ChestKeyPaymentTest {

    private ChestSecurePlugin plugin;
    private ChestKeyConsumptionSetting consumption;
    private Items items;
    private Player player;
    private FakePlayerInventory inventory;

    @BeforeEach
    void setUp() {
        plugin = mock(ChestSecurePlugin.class);
        consumption = mock(ChestKeyConsumptionSetting.class);
        when(consumption.isEnabled()).thenReturn(true);
        when(plugin.chestKeyConsumption()).thenReturn(consumption);
        items = mock(Items.class);
        when(plugin.items()).thenReturn(items);
        player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        inventory = new FakePlayerInventory(player);
        inventory.answerChestKeyChecks(items, player);
    }

    @Test
    @DisplayName("Verbrauch aus: kein Zugriff auf das Inventar, die Aktion laeuft wie bisher")
    void consumptionOffIsFree() {
        when(consumption.isEnabled()).thenReturn(false);
        inventory.putChestKeys(0, 3);

        ChestKeyPayment payment = ChestKeyPayment.take(plugin, player);

        assertNotNull(payment);
        assertTrue(payment.spendOn(() -> true));
        assertEquals(3, inventory.amount(0));
        verify(player, never()).getInventory();
        verifyNoInteractions(items);
    }

    @Test
    @DisplayName("Kreativmodus: nichts wird verbraucht, auch ohne Key im Inventar")
    void creativeIsFree() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);

        ChestKeyPayment payment = ChestKeyPayment.take(plugin, player);

        assertNotNull(payment);
        assertTrue(payment.spendOn(() -> true));
        verify(player, never()).getInventory();
    }

    @Test
    @DisplayName("Abenteuer- und Zuschauermodus verbrauchen wie der Ueberlebensmodus")
    void otherModesConsume() {
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        inventory.putChestKeys(0, 2);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));

        assertEquals(1, inventory.amount(0));
    }

    @Test
    @DisplayName("Hand zuerst: der Stapel in der Haupthand verliert genau einen Key")
    void mainHandFirst() {
        inventory.putChestKeys(0, 5);
        inventory.putChestKeys(4, 3);
        inventory.holdSlot(4);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));

        assertEquals(2, inventory.amount(4));
        assertEquals(5, inventory.amount(0));
    }

    @Test
    @DisplayName("Letzter Key in der Hand: das Feld wird leer")
    void lastKeyEmptiesSlot() {
        inventory.putChestKeys(0, 1);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));

        assertNull(inventory.item(0));
        assertEquals(0, inventory.chestKeyCount());
    }

    @Test
    @DisplayName("Zweithand vor dem uebrigen Inventar, wenn die Haupthand keinen Key haelt")
    void offHandBeforeInventory() {
        inventory.putChestKeys(10, 4);
        inventory.putChestKeys(FakePlayerInventory.OFF_HAND_SLOT, 2);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));

        assertEquals(1, inventory.amount(FakePlayerInventory.OFF_HAND_SLOT));
        assertEquals(4, inventory.amount(10));
    }

    @Test
    @DisplayName("Ohne Key in der Hand: der erste Stapel im Inventar, mehrere Stapel nacheinander")
    void firstInventoryStackThenNext() {
        inventory.putChestKeys(12, 1);
        inventory.putChestKeys(20, 2);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));
        assertNull(inventory.item(12));
        assertEquals(2, inventory.amount(20));

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));
        assertEquals(1, inventory.amount(20));
    }

    @Test
    @DisplayName("Nur echte Chest Keys: ein normaler Haken in der Hand und davor wird uebergangen")
    void onlyRealChestKeys() {
        inventory.putPlainTripwireHooks(0, 16);
        inventory.putPlainTripwireHooks(3, 16);
        inventory.putChestKeys(7, 1);

        assertTrue(ChestKeyPayment.take(plugin, player).spendOn(() -> true));

        assertEquals(16, inventory.amount(0));
        assertEquals(16, inventory.amount(3));
        assertNull(inventory.item(7));
    }

    @Test
    @DisplayName("Kein Key im Inventar: null, nichts veraendert")
    void missingKey() {
        inventory.putPlainTripwireHooks(0, 16);

        assertNull(ChestKeyPayment.take(plugin, player));
        assertEquals(16, inventory.amount(0));
    }

    @Test
    @DisplayName("Gescheiterte Sperre: der Key kommt in dasselbe Feld zurueck")
    void failedLockRefundsToSameStack() {
        inventory.putChestKeys(0, 3);

        assertFalse(ChestKeyPayment.take(plugin, player).spendOn(() -> false));

        assertEquals(3, inventory.amount(0));
        assertEquals(3, inventory.chestKeyCount());
    }

    @Test
    @DisplayName("Gescheiterte Sperre mit dem letzten Key: der Key liegt wieder im geleerten Feld")
    void failedLockRefundsIntoEmptiedSlot() {
        inventory.putChestKeys(FakePlayerInventory.OFF_HAND_SLOT, 1);

        assertFalse(ChestKeyPayment.take(plugin, player).spendOn(() -> false));

        assertEquals(1, inventory.amount(FakePlayerInventory.OFF_HAND_SLOT));
        assertTrue(inventory.isChestKey(inventory.item(FakePlayerInventory.OFF_HAND_SLOT)));
    }

    @Test
    @DisplayName("Ausnahme in der Sperre: der Key kommt zurueck, die Ausnahme bleibt sichtbar")
    void exceptionRefunds() {
        inventory.putChestKeys(0, 2);
        ChestKeyPayment payment = ChestKeyPayment.take(plugin, player);

        assertThrows(IllegalStateException.class, () -> payment.spendOn(() -> {
            throw new IllegalStateException("database");
        }));

        assertEquals(2, inventory.amount(0));
    }

    @Test
    @DisplayName("Kein Duplizieren: eine zweite gescheiterte Verwendung derselben Entnahme gibt nichts mehr zurueck")
    void refundOnlyOnce() {
        inventory.putChestKeys(0, 2);
        ChestKeyPayment payment = ChestKeyPayment.take(plugin, player);

        assertFalse(payment.spendOn(() -> false));
        assertFalse(payment.spendOn(() -> false));

        assertEquals(2, inventory.chestKeyCount());
    }

    @Test
    @DisplayName("Die Sperre laeuft genau einmal, erst nach der Entnahme")
    void actionRunsOnceAfterTaking() {
        inventory.putChestKeys(0, 2);
        AtomicInteger amountDuringAction = new AtomicInteger(-1);
        AtomicInteger calls = new AtomicInteger();

        ChestKeyPayment.take(plugin, player).spendOn(() -> {
            calls.incrementAndGet();
            amountDuringAction.set(inventory.amount(0));
            return true;
        });

        assertEquals(1, calls.get());
        assertEquals(1, amountDuringAction.get());
        assertEquals(1, inventory.amount(0));
    }
}
