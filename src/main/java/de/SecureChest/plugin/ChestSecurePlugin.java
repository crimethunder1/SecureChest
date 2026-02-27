package de.SecureChest.plugin;

import de.SecureChest.plugin.command.ChestSecureCommand;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.GuiListener;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.listener.TrustChatListener;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SqlLockService;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestSecurePlugin extends JavaPlugin {

    private NamespacedKey KEY_ITEM_TYPE;
    private NamespacedKey KEY_GUI_ACTION;
    private NamespacedKey KEY_GUI_CONTEXT;
    private NamespacedKey KEY_SECURE_HOPPER_BLOCK;

    private DatabaseManager database;
    private LockService lockService;
    private RecipeRegistry recipes;
    private GuiManager gui;
    private Items items;

    private ConfirmManager confirmManager;
    private UndoManager undoManager;
    private UiStateStore uiState;

    
    private final Map<UUID, PendingChatAction> pendingChat = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.KEY_ITEM_TYPE = new NamespacedKey(this, "item_type");
        this.KEY_GUI_ACTION = new NamespacedKey(this, "gui_action");
        this.KEY_GUI_CONTEXT = new NamespacedKey(this, "gui_context");
        this.KEY_SECURE_HOPPER_BLOCK = new NamespacedKey(this, "secure_hopper_block");

        this.items = new Items(this);

        this.confirmManager = new ConfirmManager();
        this.undoManager = new UndoManager();
        this.uiState = new UiStateStore();

        
        this.database = new DatabaseManager(this);
        if (!database.init()) {
            getLogger().severe("Failed to initialize database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.lockService = new SqlLockService(this, database);

        
        this.recipes = new RecipeRegistry(this);
        this.recipes.registerAll();

        
        this.gui = new GuiManager(this);

        
        PluginCommand cmd = getCommand("chestsecure");
        if (cmd != null) {
            cmd.setExecutor(new ChestSecureCommand(this));
        } else {
            getLogger().warning("Command 'chestsecure' not found in plugin.yml - /cs will not work.");
        }

        
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TrustChatListener(this), this);
        getServer().getPluginManager().registerEvents(new de.SecureChest.plugin.listener.PlayerQuitListener(this), this);

        getLogger().info("ChestSecure enabled.");
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        pendingChat.clear();
        getLogger().info("ChestSecure disabled.");
    }

    public NamespacedKey keyItemType() {
        return KEY_ITEM_TYPE;
    }

    public NamespacedKey keyGuiAction() {
        return KEY_GUI_ACTION;
    }

    public NamespacedKey keyGuiContext() {
        return KEY_GUI_CONTEXT;
    }

    public NamespacedKey keySecureHopperBlock() {
        return KEY_SECURE_HOPPER_BLOCK;
    }

    public DatabaseManager db() {
        return database;
    }

    public LockService locks() {
        return lockService;
    }

    public RecipeRegistry recipes() {
        return recipes;
    }

    public GuiManager gui() {
        return gui;
    }

    public ConfirmManager confirms() {
        return confirmManager;
    }

    public UndoManager undos() {
        return undoManager;
    }

    public UiStateStore ui() {
        return uiState;
    }

    public Items items() {
        return items;
    }

    public Map<UUID, PendingChatAction> pendingChat() {
        return pendingChat;
    }
}
