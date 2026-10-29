package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.gui.ScreenActionRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("give_chest_key"));
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
    @DisplayName("Wirkungsbereich-Actions sind komplett enthalten")
    void wirkungsbereichActions_komplettEnthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_areas"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("areas_page"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("area_toggle"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("area_create"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("area_entry"));
    }

    @Test
    @DisplayName("Jede Action, die der Wirkungsbereich-Screen erzeugt, ist Admin-only")
    void wirkungsbereichScreen_alleErzeugtenActionsAdminOnly() {
        Set<String> actions = ScreenActionRecorder.areaScreenActions();

        assertTrue(actions.containsAll(Set.of("area_toggle", "area_create", "area_entry", "areas_page", "back_admin")),
                "Der Screen sollte alle Buttons inklusive Blaettern erzeugen: " + actions);
        for (String action : actions) {
            assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains(action),
                    "Action des Wirkungsbereich-Screens fehlt in ADMIN_ONLY_ACTIONS: " + action);
        }
    }

    @Test
    @DisplayName("Jede Action des Admin-Panels ausser Zurueck zum Hauptmenue ist Admin-only")
    void adminPanel_alleErzeugtenActionsAdminOnly() {
        Set<String> actions = ScreenActionRecorder.adminScreenActions();

        assertTrue(actions.contains("open_areas"), "Admin-Panel sollte den Wirkungsbereich-Button haben: " + actions);
        assertTrue(actions.contains("open_recipes"), "Admin-Panel sollte den Rezepte-Button haben: " + actions);
        assertTrue(actions.contains("toggle_owner_name_display"),
                "Admin-Panel sollte den Schalter fuer die Besitzernamen haben: " + actions);
        assertTrue(actions.contains("give_chest_key"), "Admin-Panel sollte den Chest-Key-Button haben: " + actions);
        for (String action : actions) {
            if (action.equals("back_main")) continue;
            assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains(action),
                    "Action des Admin-Panels fehlt in ADMIN_ONLY_ACTIONS: " + action);
        }
    }

    @Test
    @DisplayName("Rezept-Actions sind komplett enthalten")
    void rezeptActions_komplettEnthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("open_recipes"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("back_recipes"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_entry"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_cell"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_toggle"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_save"));
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("recipe_reset"));
    }

    @Test
    @DisplayName("Jede Action, die Rezeptuebersicht und Rezept-Editor erzeugen, ist Admin-only")
    void rezeptScreens_alleErzeugtenActionsAdminOnly() {
        Set<String> actions = ScreenActionRecorder.recipeScreenActions();

        assertTrue(actions.containsAll(Set.of("recipe_entry", "back_admin", "recipe_cell", "recipe_toggle",
                        "recipe_save", "recipe_reset", "back_recipes")),
                "Uebersicht und Editor sollten alle Buttons erzeugen: " + actions);
        for (String action : actions) {
            assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains(action),
                    "Action der Rezept-Screens fehlt in ADMIN_ONLY_ACTIONS: " + action);
        }
    }

    @Test
    @DisplayName("Aktionen des Admin-Verwaltungsmenues fuer einzelne Sperren sind enthalten")
    void adminVerwaltungActions_enthalten() {
        for (String action : Set.of("admin_lock_remove", "admin_lock_transfer", "admin_trust_add",
                "admin_trust_entry", "admin_lock_close")) {
            assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains(action), "Fehlt in ADMIN_ONLY_ACTIONS: " + action);
        }
    }

    @Test
    @DisplayName("Jede Action, die das Admin-Verwaltungsmenue erzeugt, ist Admin-only")
    void adminVerwaltungScreen_alleErzeugtenActionsAdminOnly() {
        Set<String> actions = ScreenActionRecorder.adminLockScreenActions();

        assertEquals(Set.of("admin_lock_remove", "admin_lock_transfer", "admin_trust_add",
                        "admin_trust_entry", "admin_lock_close"), actions,
                "Das Menue sollte genau diese Buttons inklusive Koepfen erzeugen: " + actions);
        for (String action : actions) {
            assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains(action),
                    "Action des Admin-Verwaltungsmenues fehlt in ADMIN_ONLY_ACTIONS: " + action);
        }
    }

    @Test
    @DisplayName("Die Besitzer-Aktionen des Verwaltungsmenues bleiben ausserhalb des Admin-Sets")
    void besitzerVerwaltungActions_nichtImSet() {
        for (String action : Set.of("manage_lock", "back_lock_manage", "confirm_unlock_one", "transfer_lock_name",
                "open_trust_nearby", "open_trust_role", "back_trust_nearby", "ping_lock")) {
            assertFalse(GuiListener.ADMIN_ONLY_ACTIONS.contains(action), "Besitzer-Aktion im Admin-Set: " + action);
        }
    }

    @Test
    @DisplayName("Schalter fuer die Besitzernamen ist Admin-only")
    void besitzernamenSchalter_enthalten() {
        assertTrue(GuiListener.ADMIN_ONLY_ACTIONS.contains("toggle_owner_name_display"));
    }

    @Test
    @DisplayName("Set ist unmodifizierbar (Set.of-Garantie)")
    void set_istUnmodifizierbar() {
        assertThrows(UnsupportedOperationException.class,
                () -> GuiListener.ADMIN_ONLY_ACTIONS.add("hack"));
    }
}
