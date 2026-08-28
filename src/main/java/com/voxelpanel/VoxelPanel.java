package com.voxelpanel;

import com.voxelpanel.commands.TPUAdminCommand;
import com.voxelpanel.commands.TPUCommand;
import com.voxelpanel.commands.WaypointCommand;
import com.voxelpanel.commands.CompassCommand;
import com.voxelpanel.commands.LanguageCommand;
import com.voxelpanel.commands.TPACommand;
import com.voxelpanel.commands.BackCommand;
import com.voxelpanel.commands.ShareResponseCommand;
import com.voxelpanel.database.DatabaseManager;
import com.voxelpanel.database.WaypointRepository;
import com.voxelpanel.listeners.InventoryListener;
import com.voxelpanel.listeners.PlayerListener;
import com.voxelpanel.managers.CompassTrackerManager;
import com.voxelpanel.managers.DeathWaypointManager;
import com.voxelpanel.managers.LanguageManager;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.ShareRequestManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.managers.TeleportRequestManager;
import com.voxelpanel.managers.WaypointManager;
import com.voxelpanel.managers.RankManager;
import com.voxelpanel.managers.ModerationManager;
import com.voxelpanel.services.TeleportService;
import com.voxelpanel.firebase.FirebaseManager;
import com.voxelpanel.firebase.FirebaseSyncService;
import com.voxelpanel.firebase.FirebaseCommandListener;
import com.voxelpanel.firebase.ActivityLogger;
import com.voxelpanel.firebase.PlayerInspector;
import com.voxelpanel.firebase.PluginManagerBridge;
import com.voxelpanel.firebase.AuthMirrorService;
import com.voxelpanel.firebase.ChatBridge;
import com.voxelpanel.firebase.ServerIdentity;
import com.voxelpanel.firebase.FileManagerBridge;
import com.voxelpanel.firebase.PanelController;
import com.voxelpanel.firebase.ConsoleBridge;
import com.voxelpanel.firebase.HeartbeatService;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoxelPanel extends JavaPlugin {
    private boolean systemEnabled = true;
    private long startTime = System.currentTimeMillis();
    private DatabaseManager databaseManager;
    private WaypointRepository waypointRepository;
    private MessageManager messageManager;
    private SoundManager soundManager;
    private WaypointManager waypointManager;
    private TeleportService teleportService;
    private LanguageManager languageManager;
    private CompassTrackerManager compassTrackerManager;
    private DeathWaypointManager deathWaypointManager;
    private TeleportRequestManager teleportRequestManager;
    private ShareRequestManager shareRequestManager;
    private RankManager rankManager;
    private ModerationManager moderationManager;
    private FirebaseManager firebaseManager;
    private FirebaseSyncService firebaseSyncService;
    private FirebaseCommandListener firebaseCommandListener;
    private ActivityLogger activityLogger;
    private PlayerInspector playerInspector;
    private PluginManagerBridge pluginManagerBridge;
    private AuthMirrorService authMirrorService;
    private ChatBridge chatBridge;
    private ServerIdentity serverIdentity;
    private FileManagerBridge fileManagerBridge;
    private PanelController panelController;
    private ConsoleBridge consoleBridge;
    private HeartbeatService heartbeatService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("ar.yml", false);
        saveResource("en.yml", false);
        saveResource("players.yml", false);

        languageManager = new LanguageManager(this);
        messageManager = new MessageManager(this);
        soundManager = new SoundManager(this);

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Database initialization failed. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        waypointRepository = new WaypointRepository(databaseManager);
        waypointManager = new WaypointManager(this, waypointRepository, messageManager, soundManager);
        teleportService = new TeleportService(this, messageManager, soundManager);
        compassTrackerManager = new CompassTrackerManager(this);
        deathWaypointManager = new DeathWaypointManager(this, waypointRepository);
        deathWaypointManager.startCleanupTask();
        teleportRequestManager = new TeleportRequestManager(this);
        shareRequestManager = new ShareRequestManager(this);
        rankManager = new RankManager(this);
        moderationManager = new ModerationManager(this);

        getCommand("tpu").setExecutor(new TPUCommand(this, waypointManager, messageManager, soundManager, compassTrackerManager));
        getCommand("tpubook").setExecutor(new com.voxelpanel.commands.BookCommand(this, messageManager, soundManager));
        getCommand("waypoint").setExecutor(new WaypointCommand(this, waypointManager, messageManager, soundManager));
        getCommand("tpuadmin").setExecutor(new TPUAdminCommand(this, waypointManager, messageManager, soundManager));
        getCommand("compass").setExecutor(new CompassCommand(this, compassTrackerManager, messageManager, soundManager));
        getCommand("language").setExecutor(new LanguageCommand(this, languageManager, messageManager, soundManager));
        TPACommand tpaCommand = new TPACommand(this, teleportRequestManager, messageManager, soundManager);
        getCommand("tpe").setExecutor(tpaCommand);
        getCommand("tpeaccept").setExecutor(tpaCommand);
        getCommand("tpedeny").setExecutor(tpaCommand);
        getCommand("back").setExecutor(new BackCommand(this, messageManager, soundManager));
        ShareResponseCommand shareResponseCommand = new ShareResponseCommand(this, shareRequestManager, messageManager, soundManager);
        getCommand("shareaccept").setExecutor(shareResponseCommand);
        getCommand("sharedeny").setExecutor(shareResponseCommand);

        // Tab completion for waypoint-name based commands.
        com.voxelpanel.commands.WaypointTabCompleter waypointTab = new com.voxelpanel.commands.WaypointTabCompleter(this);
        getCommand("tpu").setTabCompleter(waypointTab);
        getCommand("waypoint").setTabCompleter(waypointTab);
        getCommand("compass").setTabCompleter(waypointTab);
        getCommand("language").setTabCompleter(waypointTab);

        getServer().getPluginManager().registerEvents(new InventoryListener(this, waypointManager, messageManager, soundManager, teleportService), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, waypointManager, messageManager, soundManager, deathWaypointManager), this);

        // Firebase dashboard integration (optional, controlled by config).
        firebaseManager = new FirebaseManager(this);
        if (firebaseManager.initialize()) {
            // Establish this server's unique identity + pairing code for the dashboard.
            serverIdentity = new ServerIdentity(this);
            serverIdentity.validateConfig();
            serverIdentity.printBanner();
            // Start the live authorization watch first: in dashboard-token mode
            // nothing syncs until the dashboard confirms the token in real time.
            serverIdentity.startAuthWatch();
            serverIdentity.publishMeta();
            // Re-publish shortly after startup in case the DB wasn't ready on the first write.
            getServer().getScheduler().runTaskLater(this, () -> serverIdentity.publishMeta(), 100L);
            getServer().getScheduler().runTaskLater(this, () -> serverIdentity.publishMeta(), 400L);
            activityLogger = new ActivityLogger(this, firebaseManager);
            playerInspector = new PlayerInspector(this, firebaseManager);
            pluginManagerBridge = new PluginManagerBridge(this, firebaseManager);
            firebaseSyncService = new FirebaseSyncService(this, firebaseManager);
            firebaseSyncService.start();
            firebaseCommandListener = new FirebaseCommandListener(this, firebaseManager);
            firebaseCommandListener.start();
            // Publish the installed-plugins list once on startup.
            pluginManagerBridge.publishPlugins();
            // Mirror Firebase Authentication users to the dashboard.
            authMirrorService = new AuthMirrorService(this, firebaseManager);
            authMirrorService.start();
            chatBridge = new ChatBridge(this, firebaseManager);
            chatBridge.start();
            fileManagerBridge = new FileManagerBridge(this, firebaseManager);
            panelController = new PanelController(this);
            consoleBridge = new ConsoleBridge(this, firebaseManager);
            consoleBridge.start();
            heartbeatService = new HeartbeatService(this, firebaseManager);
            heartbeatService.start();
        }

        getLogger().info("[VoxelPanel] Plugin enabled successfully.");
        getLogger().info("[VoxelPanel] Paper API: 26.2");
        getLogger().info("[VoxelPanel] Database: SQLite");
        getLogger().info("[VoxelPanel] Waypoint limit: " + getConfig().getInt("waypoints.max-per-player", 10));
    }

    @Override
    public void onDisable() {
        if (firebaseSyncService != null) {
            firebaseSyncService.stop();
        }
        if (firebaseCommandListener != null) {
            firebaseCommandListener.stop();
        }
        if (authMirrorService != null) {
            authMirrorService.stop();
        }
        if (chatBridge != null) {
            chatBridge.stop();
        }
        if (serverIdentity != null) {
            serverIdentity.markOffline();
        }
        if (consoleBridge != null) {
            consoleBridge.stop();
        }
        if (heartbeatService != null) {
            heartbeatService.stop();
        }
        if (firebaseManager != null) {
            firebaseManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (deathWaypointManager != null) {
            deathWaypointManager.stopCleanupTask();
        }
        getLogger().info("[VoxelPanel] Plugin disabled.");
    }

    public boolean isSystemEnabled() {
        return systemEnabled;
    }

    public void setSystemEnabled(boolean systemEnabled) {
        this.systemEnabled = systemEnabled;
    }

    public WaypointRepository getWaypointRepository() {
        return waypointRepository;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }

    public TeleportService getTeleportService() {
        return teleportService;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public CompassTrackerManager getCompassTrackerManager() {
        return compassTrackerManager;
    }

    public DeathWaypointManager getDeathWaypointManager() {
        return deathWaypointManager;
    }

    public TeleportRequestManager getTeleportRequestManager() {
        return teleportRequestManager;
    }

    public ShareRequestManager getShareRequestManager() {
        return shareRequestManager;
    }

    public FirebaseManager getFirebaseManager() {
        return firebaseManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public ActivityLogger getActivityLogger() {
        return activityLogger;
    }

    public long getStartTime() {
        return startTime;
    }

    public PlayerInspector getPlayerInspector() {
        return playerInspector;
    }

    public PluginManagerBridge getPluginManagerBridge() {
        return pluginManagerBridge;
    }

    public AuthMirrorService getAuthMirrorService() {
        return authMirrorService;
    }

    public ServerIdentity getServerIdentity() {
        return serverIdentity;
    }

    public FileManagerBridge getFileManagerBridge() {
        return fileManagerBridge;
    }

    public PanelController getPanelController() {
        return panelController;
    }
}
