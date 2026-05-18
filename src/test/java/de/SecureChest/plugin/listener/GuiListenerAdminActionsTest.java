package de.SecureChest.plugin.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuiListener – ADMIN_ONLY_ACTIONS Whitelist (SEC-07)")
class GuiListenerAdminActionsTest {

    @Test
    @DisplayName("Alle Admin-Panel-Actions sind enthalten")
    void adminPanelActions_enthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_admin"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("back_admin"));
    }

    @Test
    @DisplayName("Item-Vergabe-Actions sind enthalten")
    void itemVergabeActions_enthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("give_admin_key"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("give_logs_book"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("give_admin_report"));
    }

    @Test
    @DisplayName("Destruktive Admin-Actions (Inspect, GlobalUnlock) sind enthalten")
    void destruktiveActions_enthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_inspect"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("global_unlock"));
    }

    @Test
    @DisplayName("Logs-Actions sind komplett enthalten")
    void logsActions_komplettEnthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_logs"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("logs_page"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("logs_cycle_action"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("logs_cycle_time"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("logs_set_actor"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("log_entry"));
    }

    @Test
    @DisplayName("Spielereigene Actions duerfen NICHT im Admin-Set sein")
    void spielerEigeneActions_nichtImSet() {
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_quicklock"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_quickremove"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("confirm_unlock_all_self"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("confirm_undo_restore"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("manage_lock"));
    }

    @Test
    @DisplayName("Navigation-Actions duerfen NICHT im Admin-Set sein")
    void navigationActions_nichtImSet() {
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("back_main"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_help"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_stats"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_locks"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_hoppers"));
    }

    @Test
    @DisplayName("Trust-Actions sind NICHT Admin-only (Owner kann selber trusten)")
    void trustActions_nichtImSet() {
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("add_trust_name"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("set_trust_role"));
        assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains("confirm_remove_trust"));
    }

    @Test
    @DisplayName("Set ist unmodifizierbar (Set.of-Garantie)")
    void set_istUnmodifizierbar() {
        assertThrows(UnsupportedOperationException.class,
                () -> GuiListener.ADMIN_ONLY_ACTIONS.add("hack"));
    }
}
