package de.SecureChest.plugin;

import de.SecureChest.plugin.command.ChestSecureCommand;
import de.SecureChest.plugin.db.DatabaseManager;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.GuiListener;
import de.SecureChest.plugin.listener.PlayerQuitListener;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.listener.TrustChatListener;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SqlLockService;
import de.SecureChest.plugin.ping.PingManager;
import de.SecureChest.plugin.recipe.RecipeRegistry;
import de.SecureChest.plugin.sign.SignManager;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import de.SecureChest.plugin.util.Worlds;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestSecurePlugin extends JavaPlugin {

    private NamespacedKey keyItemType;
    private NamespacedKey keyGuiAction;
    private NamespacedKey keyGuiContext;
    private NamespacedKey keySecureHopperBlock;

    private DatabaseManager database;
    private LockService lockService;
    private RecipeRegistry recipes;
    private GuiManager guiManager;
    private Items items;
    private SignManager signManager;
    private ConfirmManager confirmManager;
    private UndoManager undoManager;
    private UiStateStore uiStateStore;
    private PingManager pingManager;
    private ProtectionListener protectionListener;

    private PluginConfig pluginConfig;
    private MessageService messageService;
    private Worlds worlds;

    private final Map<UUID, PendingChatAction> pendingChatActions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig   = new PluginConfig(this);
        this.messageService = new MessageService(this);
        this.worlds         = new Worlds(this);

        this.keyItemType          = new NamespacedKey(this, "item_type");
        this.keyGuiAction         = new NamespacedKey(this, "gui_action");
        this.keyGuiContext        = new NamespacedKey(this, "gui_context");
        this.keySecureHopperBlock = new NamespacedKey(this, "secure_hopper_block");

        this.items = new Items(this);

        int maxUndoHistory = cfg().maxUndoHistory();
        this.confirmManager = new ConfirmManager();
        this.undoManager    = new UndoManager(maxUndoHistory);
        this.uiStateStore   = new UiStateStore();
        this.pingManager    = new PingManager(this);

        this.database = new DatabaseManager(this);
        if (!database.init()) {
            getLogger().severe("Failed to initialize database. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.signManager = new SignManager(this);

        SqlLockService sqlLockService = new SqlLockService(this, database, () -> signManager.spawnAllSigns());
        this.lockService = sqlLockService;

        this.recipes = new RecipeRegistry(this);
        this.recipes.registerAll();

        this.guiManager = new GuiManager(this);

        PluginCommand chestsecureCommand = getCommand("chestsecure");
        if (chestsecureCommand != null) {
            chestsecureCommand.setExecutor(new ChestSecureCommand(this));
        } else {
            getLogger().warning("Command 'chestsecure' not found in plugin.yml.");
        }

        this.protectionListener = new ProtectionListener(this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new TrustChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(signManager, this);

        final long VALIDATION_INTERVAL_TICKS = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimer(this,
                () -> signManager.validateAndCleanOrphans(),
                VALIDATION_INTERVAL_TICKS,
                VALIDATION_INTERVAL_TICKS);

        getLogger().info("ChestSecure enabled.");
    }

    @Override
    public void onDisable() {
        if (database != null) database.close();
        pendingChatActions.clear();
        getLogger().info("ChestSecure disabled.");
    }

    public NamespacedKey keyItemType()          { return keyItemType; }
    public NamespacedKey keyGuiAction()         { return keyGuiAction; }
    public NamespacedKey keyGuiContext()        { return keyGuiContext; }
    public NamespacedKey keySecureHopperBlock() { return keySecureHopperBlock; }

    public DatabaseManager db()            { return database; }
    public LockService locks()             { return lockService; }
    public RecipeRegistry recipes()        { return recipes; }
    public GuiManager gui()                { return guiManager; }
    public ConfirmManager confirms()       { return confirmManager; }
    public UndoManager undos()             { return undoManager; }
    public UiStateStore ui()               { return uiStateStore; }
    public Items items()                   { return items; }
    public SignManager signs()             { return signManager; }
    public PingManager ping()              { return pingManager; }
    public ProtectionListener protectionListener() { return protectionListener; }
    public PluginConfig cfg()                  { return pluginConfig; }
    public MessageService msg()                { return messageService; }
    public Worlds worlds()                     { return worlds; }
    public Map<UUID, PendingChatAction> pendingChat() { return pendingChatActions; }
}
