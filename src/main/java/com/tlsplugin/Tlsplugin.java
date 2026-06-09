package com.tlsplugin;

import com.tlsplugin.command.*;
import com.tlsplugin.gui.CraftBookGui;
import com.tlsplugin.listeners.*;
import com.tlsplugin.manager.BorderManager;
import com.tlsplugin.manager.BorderScoreboardManager;
import com.tlsplugin.manager.GameFreezeManager;
import com.tlsplugin.manager.MVPStatsManager;
import com.tlsplugin.manager.PlayerPauseManager;
import com.tlsplugin.utils.GrapplerItem;
import com.tlsplugin.utils.KitMedicRecipe;
import com.tlsplugin.utils.RecipeUnlocker;
import com.tlsplugin.utils.SpecialAppleRecipe;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Tlsplugin extends JavaPlugin {

    private static Tlsplugin instance;

    private BorderManager            borderManager;
    private BorderScoreboardManager  borderScoreboardManager;
    private PvPListener              pvpListener;
    private KillListener             killListener;
    private DeathListener            deathListener;
    private SpectatorInspectListener spectatorListener;
    private TrackerCompassListener   trackerCompassListener;
    private GrapplerItemListener     grapplerItemListener;
    private GoldPotionListener       goldPotionListener;
    private GameFreezeManager        freezeManager;
    private MVPStatsManager          mvpStatsManager;
    private PlayerPauseManager       pauseManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Copiar gui.yml padrão se não existir
        if (!new java.io.File(getDataFolder(), "gui.yml").exists()) {
            saveResource("gui.yml", false);
        }
        CraftBookGui.loadConfig(getDataFolder());

        // ==== MANAGERS ====
        this.freezeManager          = new GameFreezeManager(this);
        this.borderManager          = new BorderManager(this);
        this.borderScoreboardManager = new BorderScoreboardManager(this, borderManager);
        this.mvpStatsManager        = new MVPStatsManager();
        this.mvpStatsManager.loadStats();
        this.pauseManager           = new PlayerPauseManager();

        // ==== REGISTER MANAGERS AS LISTENERS ====
        Bukkit.getPluginManager().registerEvents(borderManager, this);
        Bukkit.getPluginManager().registerEvents(freezeManager, this);

        // ==== LISTENERS ====
        this.grapplerItemListener = new GrapplerItemListener(this);
        this.pvpListener          = new PvPListener(this, freezeManager, mvpStatsManager, grapplerItemListener);
        this.killListener         = new KillListener(this, borderManager, mvpStatsManager, pvpListener);
        this.deathListener        = new DeathListener(this, mvpStatsManager);
        this.spectatorListener    = new SpectatorInspectListener();
        this.trackerCompassListener = new TrackerCompassListener(this);
        this.goldPotionListener   = new GoldPotionListener(this);

        // ==== REGISTER LISTENERS ====
        Bukkit.getPluginManager().registerEvents(pvpListener,           this);
        Bukkit.getPluginManager().registerEvents(killListener,          this);
        Bukkit.getPluginManager().registerEvents(deathListener,         this);
        Bukkit.getPluginManager().registerEvents(spectatorListener,     this);
        Bukkit.getPluginManager().registerEvents(trackerCompassListener, this);
        Bukkit.getPluginManager().registerEvents(grapplerItemListener,  this);
        Bukkit.getPluginManager().registerEvents(goldPotionListener,    this);
        Bukkit.getPluginManager().registerEvents(new CraftBookListener(), this);

        // ItemsAdder load
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onItemsAdderLoad(ItemsAdderLoadDataEvent e) {
                registerCustomRecipes();
                getLogger().info("ItemsAdder carregado! Receitas e lores customizadas aplicadas.");
            }
        }, this);

        // Scoreboard + MVP stats
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                borderScoreboardManager.create(e.getPlayer());
                mvpStatsManager.registerPlayer(e.getPlayer().getName());
                goldPotionListener.applyBaseLore(e.getPlayer());
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                borderScoreboardManager.remove(e.getPlayer());
                mvpStatsManager.unregisterPlayer(e.getPlayer().getName());
            }
        }, this);

        // Jogadores já online (reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            borderScoreboardManager.create(p);
            mvpStatsManager.registerPlayer(p.getName());
            goldPotionListener.applyBaseLore(p);
        }

        pvpListener.startUpdater();

        // Auto-save MVP a cada minuto
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (mvpStatsManager != null) mvpStatsManager.saveStats();
        }, 1200L, 1200L);

        // ==== COMMANDS ====
        getCommand("setborda").setExecutor(new BorderCommand(this));
        getCommand("startgame").setExecutor(new StartGameCommand(this));
        getCommand("teamjoin").setExecutor(new TeamJoinCommand());
        getCommand("tlsreload").setExecutor(new ReloadCommand(this));
        getCommand("endgame").setExecutor(new EndGameCommand(this, borderManager));
        getCommand("pause").setExecutor(new PauseCommand(borderManager, freezeManager, mvpStatsManager));
        getCommand("unpause").setExecutor(new UnPauseCommand(borderManager, freezeManager, mvpStatsManager));
        getCommand("pedirpausa").setExecutor(new PedirPausaCommand(this));
        getCommand("aceitarpausa").setExecutor(new AceitarPausaCommand(this));
        getCommand("allteamscreate").setExecutor(new AllTeamsCreateCommand());
        getCommand("mvp").setExecutor(new MvPCommand(this));
        getCommand("sobremvp").setExecutor(new AboutMVPCommand(this));
        getCommand("craftbook").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) CraftBookGui.openMain(p);
            return true;
        });

        // ==== RECIPES ====
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            registerCustomRecipes();
        }

        getLogger().info("TLSPlugin ativado.");
    }

    private void registerCustomRecipes() {
        SpecialAppleRecipe.register(this);
        KitMedicRecipe.register(this);
        GrapplerItem.register(this);
        com.tlsplugin.utils.GoldPotionItem.register(this);
        RecipeUnlocker.register(this);

        if (trackerCompassListener != null) trackerCompassListener.reloadRecipe();
        if (grapplerItemListener   != null) grapplerItemListener.reloadRecipe();
    }

    /** Chamado pelo /tlsreload — recarrega configs incluindo gui.yml */
    public void reloadAllConfigs() {
        reloadConfig();
        CraftBookGui.loadConfig(getDataFolder());
    }

    @Override
    public void onDisable() {
        if (borderManager != null) {
            borderManager.saveState();
            borderManager.markSafeExit();
        }
        if (mvpStatsManager != null) mvpStatsManager.saveStats();
        if (trackerCompassListener != null) trackerCompassListener.cleanup();
        if (grapplerItemListener   != null) grapplerItemListener.cleanup();
        getLogger().info("TLSPlugin desativado.");
    }

    // ==== GETTERS ====
    public static Tlsplugin getInstance()              { return instance; }
    public BorderManager getBorderManager()            { return borderManager; }
    public DeathListener getDeathListener()            { return deathListener; }
    public GameFreezeManager getFreezeManager()        { return freezeManager; }
    public MVPStatsManager getMVPStatsManager()        { return mvpStatsManager; }
    public PlayerPauseManager getPauseManager()        { return pauseManager; }
}