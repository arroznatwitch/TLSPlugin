package com.tlsplugin;

import com.tlsplugin.manager.*;
import com.tlsplugin.command.*;
import com.tlsplugin.gui.ConfigGui;
import com.tlsplugin.gui.ConfigGuiListener;
import com.tlsplugin.gui.CraftBookGui;
import com.tlsplugin.listeners.*;
import com.tlsplugin.utils.GrapplerItem;
import com.tlsplugin.utils.KitMedicRecipe;
import com.tlsplugin.utils.RecipeUnlocker;
import com.tlsplugin.utils.SpecialAppleRecipe;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Tlsplugin extends JavaPlugin {

    private static Tlsplugin instance;

    /** ID do livro de receitas (item do ItemsAdder já existente). */
    private static final String CRAFT_BOOK_ID = "tls_plugin:craft_book";

    private BorderManager              borderManager;
    private SpawnManager               spawnManager;
    private BorderScoreboardManager    borderScoreboardManager;
    private BorderScoreboardListener   borderScoreboardListener;
    private BorderTimerAnnouncer       borderTimerAnnouncer;
    private PvPListener                pvpListener;
    private KillListener               killListener;
    private DeathListener              deathListener;
    private SpectatorInspectListener   spectatorListener;
    private TrackerCompassListener     trackerCompassListener;
    private GrapplerItemListener       grapplerItemListener;
    private GoldPotionListener         goldPotionListener;
    private GameFreezeManager          freezeManager;
    private MVPStatsManager            mvpStatsManager;
    private PlayerPauseManager         pauseManager;
    private ProntoCommand              prontoCommand;
    private ProximityAlertListener     proximityAlertListener;
    private TeamManager                teamManager;
    private CapsuleManager             capsuleManager;
    private CenterCompassTask          centerCompassTask;
    private WeaponRangeListener        weaponRangeListener;
    private SickleKnockbackListener    sickleKnockbackListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!new java.io.File(getDataFolder(), "gui.yml").exists()) {
            saveResource("gui.yml", false);
        }
        CraftBookGui.loadConfig(getDataFolder());

        // ==== MANAGERS ====
        this.spawnManager            = new SpawnManager(this);
        this.freezeManager           = new GameFreezeManager(this);
        this.borderManager           = new BorderManager(this);
        this.borderTimerAnnouncer    = new BorderTimerAnnouncer(this, borderManager);
        this.borderScoreboardManager = new BorderScoreboardManager(this, borderManager);
        this.borderScoreboardListener = new BorderScoreboardListener(this, borderScoreboardManager);
        this.mvpStatsManager         = new MVPStatsManager();
        this.mvpStatsManager.loadStats();
        this.pauseManager            = new PlayerPauseManager();
        this.prontoCommand           = new ProntoCommand(this);
        this.proximityAlertListener  = new ProximityAlertListener(this);
        this.teamManager             = new TeamManager(this);

        // ==== REGISTER MANAGERS AS LISTENERS ====
        Bukkit.getPluginManager().registerEvents(borderManager, this);
        Bukkit.getPluginManager().registerEvents(freezeManager, this);
        Bukkit.getPluginManager().registerEvents(borderScoreboardListener, this);

        // ==== LISTENERS ====
        this.grapplerItemListener   = new GrapplerItemListener(this);
        this.pvpListener            = new PvPListener(this, freezeManager, mvpStatsManager, grapplerItemListener);
        this.killListener           = new KillListener(this, borderManager, mvpStatsManager, pvpListener);
        this.deathListener          = new DeathListener(this, mvpStatsManager);
        this.spectatorListener      = new SpectatorInspectListener();
        this.trackerCompassListener = new TrackerCompassListener(this);
        this.goldPotionListener     = new GoldPotionListener(this);
        this.weaponRangeListener    = new WeaponRangeListener(this);
        this.sickleKnockbackListener = new SickleKnockbackListener(this);

        // ==== REGISTER LISTENERS ====
        Bukkit.getPluginManager().registerEvents(pvpListener,            this);
        Bukkit.getPluginManager().registerEvents(killListener,           this);
        Bukkit.getPluginManager().registerEvents(deathListener,          this);
        Bukkit.getPluginManager().registerEvents(spectatorListener,      this);
        Bukkit.getPluginManager().registerEvents(trackerCompassListener, this);
        Bukkit.getPluginManager().registerEvents(grapplerItemListener,   this);
        Bukkit.getPluginManager().registerEvents(goldPotionListener,     this);
        Bukkit.getPluginManager().registerEvents(weaponRangeListener,    this);
        Bukkit.getPluginManager().registerEvents(sickleKnockbackListener, this);
        Bukkit.getPluginManager().registerEvents(new CraftBookListener(), this);
        Bukkit.getPluginManager().registerEvents(new MobDropListener(this), this);

        // ItemsAdder load
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onItemsAdderLoad(ItemsAdderLoadDataEvent e) {
                registerCustomRecipes();
                getLogger().info("ItemsAdder carregado! Receitas e lores customizadas aplicadas.");
            }
        }, this);

        // Equipas: garante que as 9 Teams existem com a cor/ícone certos (auto-cura, mesmo se o
        // scoreboard.dat tiver perdido informação numa queda anterior do servidor) e sincroniza
        // quem já estiver online a partir do grupo do LuckPerms.
        teamManager.ensureTeamsExist();
        for (Player p : Bukkit.getOnlinePlayers()) {
            teamManager.syncPlayer(p);
        }

        // MVP stats + gold potion lore (join/quit — scoreboard tratada pelo BorderScoreboardListener)
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                mvpStatsManager.registerPlayer(e.getPlayer().getName());
                goldPotionListener.applyBaseLore(e.getPlayer());
                teamManager.syncPlayer(e.getPlayer());

                // Ao entrar no servidor, garante que o jogador tem o livro CraftBook.
                darCraftBook(e.getPlayer());

                // Rejoin com jogo a decorrer: o mapa tls_evento1 força Adventure, mas quem
                // estava a jogar deve voltar a Survival. Só converte Adventure→Survival;
                // Criativo e Spectator ficam intactos. Delay de 3 ticks para correr DEPOIS
                // do teleporte/gamemode que o mundo aplica no spawn.
                Player jogador = e.getPlayer();
                Bukkit.getScheduler().runTaskLater(Tlsplugin.this, () -> {
                    if (!jogador.isOnline()) return;
                    if (!borderManager.isRunning()) return;
                    if (isLobbyWorld(jogador.getWorld())) return;
                    if (jogador.getGameMode() == GameMode.ADVENTURE) {
                        jogador.setGameMode(GameMode.SURVIVAL);
                    }
                }, 3L);
            }
            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                mvpStatsManager.unregisterPlayer(e.getPlayer().getName());
                prontoCommand.remover(e.getPlayer().getUniqueId());
            }
        }, this);

        // Aplica gamerules a todos os mundos carregados (inclui mundos do Multiverse)
        for (World w : Bukkit.getWorlds()) applyGamerules(w);
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onWorldLoad(WorldLoadEvent e) {
                applyGamerules(e.getWorld());
            }

            // OPs entram em Criativo no mundo lobby (configurável)
            @EventHandler
            public void onWorldChange(PlayerChangedWorldEvent e) {
                Player p = e.getPlayer();
                String lobby  = getConfig().getString("mundo_lobby", "world");
                String evento = getConfig().getString("mundo_evento", "tls_evento1");

                if (p.isOp() && p.getWorld().getName().equals(lobby)) {
                    Bukkit.getScheduler().runTaskLater(Tlsplugin.this, () -> {
                        if (p.isOnline() && p.getWorld().getName().equals(lobby)) {
                            p.setGameMode(GameMode.CREATIVE);
                        }
                    }, 2L);
                }

                // Ao ENTRAR no mundo de evento: limpa o inventário e dá o CraftBook de novo.
                // Só limpa jogadores não-OP (protege staff/admins que estejam a montar o mapa).
                // ATENÇÃO: isto limpa sempre que um jogador atravessa para o tls_evento1 — se
                // alguém sair e voltar a entrar no mundo a meio do jogo, perde o inventário.
                if (p.getWorld().getName().equalsIgnoreCase(evento)) {
                    if (!p.isOp()) {
                        p.getInventory().clear();
                    }
                    darCraftBook(p);

                    // Ao entrar no mundo do evento, força Adventure (o Multiverse nem sempre
                    // tira do Spectator quem morreu antes, ficando preso em Spectator).
                    // Delay de 2 ticks para correr DEPOIS do gamemode que o Multiverse aplica.
                    // Única exceção: se o jogo ESTÁ a decorrer, não tiramos de Survival quem
                    // está vivo a jogar — mas quem está em Spectator volta na mesma a Adventure.
                    if (!p.isOp()) {
                        boolean jogoADecorrer = borderManager != null && borderManager.isRunning();
                        Bukkit.getScheduler().runTaskLater(Tlsplugin.this, () -> {
                            if (!p.isOnline()) return;
                            if (!p.getWorld().getName().equalsIgnoreCase(evento)) return;
                            GameMode gm = p.getGameMode();
                            if (gm == GameMode.ADVENTURE) return;                 // já está bem
                            if (jogoADecorrer && gm == GameMode.SURVIVAL) return; // vivo a jogar
                            p.setGameMode(GameMode.ADVENTURE);
                        }, 2L);
                    }
                }
            }
        }, this);

        // Jogadores já online (reload). A sidebar é (re)criada pelo updater do
        // BorderScoreboardManager no próximo tick (auto-cura), quando os canais
        // de rede já estão prontos — por isso não chamamos create() aqui.
        for (Player p : Bukkit.getOnlinePlayers()) {
            mvpStatsManager.registerPlayer(p.getName());
            goldPotionListener.applyBaseLore(p);
            darCraftBook(p);
        }

        pvpListener.startUpdater();

        // Compass na action bar a apontar pro centro (0,0) — cede a vez ao grappler
        this.centerCompassTask = new CenterCompassTask(this, grapplerItemListener);
        this.centerCompassTask.start();

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
        getCommand("allteamscreate").setExecutor(new AllTeamsCreateCommand(this));
        getCommand("mvp").setExecutor(new MvPCommand(this));
        getCommand("sobremvp").setExecutor(new AboutMVPCommand(this));
        getCommand("scoreboard").setExecutor(borderScoreboardListener);
        getCommand("craftbook").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) CraftBookGui.openMain(p);
            return true;
        });
        getCommand("pronto").setExecutor(prontoCommand);
        getCommand("anunciar").setExecutor(new AnunciarCommand(this));
        getCommand("tls").setExecutor(new TlsCommand(this, spawnManager));
        getCommand("tlspawn").setExecutor(new TpSpawnCommand(this, spawnManager));
        getCommand("tlsworld").setExecutor(new WorldCommand(this));
        SignManager signManager = new SignManager(this);
        getCommand("tlssign").setExecutor(new TlsSignCommand(this, signManager, spawnManager));
        Bukkit.getPluginManager().registerEvents(new com.tlsplugin.listeners.SignListener(this, signManager), this);
        // TLSCapsulas: gera cápsulas por equipa e auto-configura os spawns (SpawnManager).
        this.capsuleManager = new CapsuleManager(this, spawnManager);
        getCommand("tlscapsulas").setExecutor(new TlsCapsulasCommand(this, capsuleManager));
        getCommand("tlsteams").setExecutor(new TeamsCommand(this));
        Bukkit.getPluginManager().registerEvents(new com.tlsplugin.listeners.TeamWoolListener(this), this);
        ConfigGui configGui = new ConfigGui(this);
        ConfigGuiListener configGuiListener = new ConfigGuiListener(this, configGui);
        Bukkit.getPluginManager().registerEvents(configGuiListener, this);
        getCommand("tlsconfig").setExecutor(new ConfigCommand(this, configGui, configGuiListener));

        // ==== RECIPES ====
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            registerCustomRecipes();
        }

        getLogger().info("TLSPlugin ativado.");
    }

    /**
     * Dá o livro de receitas (tls_plugin:craft_book) ao jogador, mas só se ainda não o tiver,
     * para não duplicar em reconexões. Usa o item do ItemsAdder já existente.
     */
    private void darCraftBook(Player p) {
        // Já tem o livro? não dá outro.
        for (ItemStack it : p.getInventory().getContents()) {
            CustomStack cs = CustomStack.byItemStack(it);
            if (cs != null && CRAFT_BOOK_ID.equals(cs.getNamespacedID())) return;
        }
        CustomStack book = CustomStack.getInstance(CRAFT_BOOK_ID);
        if (book != null) {
            p.getInventory().addItem(book.getItemStack());
        } else {
            getLogger().warning("Não consegui dar o craft_book: item '" + CRAFT_BOOK_ID
                    + "' não encontrado (ItemsAdder ainda não carregou?).");
        }
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

    public void reloadAllConfigs() {
        reloadConfig();
        CraftBookGui.loadConfig(getDataFolder());
        if (borderManager != null) borderManager.reloadStages();
        for (World w : Bukkit.getWorlds()) applyGamerules(w);
        if (teamManager != null) {
            teamManager.ensureTeamsExist();
            for (Player p : Bukkit.getOnlinePlayers()) teamManager.syncPlayer(p);
        }

        // Se quiseres que /tlsreload force regenerar as cápsulas do zero, descomenta a linha
        // abaixo. ATENÇÃO: só apaga o capsulas.yml — tens de voltar a correr /tlscapsulas a seguir.
        // Durante um evento é mais seguro deixar comentado para o reload NÃO mexer nas cápsulas.
        // if (capsuleManager != null) capsuleManager.clearState();
    }

    public boolean isSoloMode() {
        return getConfig().getString("tipo_jogo", "equipas").equalsIgnoreCase("solo");
    }

    public boolean isLobbyWorld(org.bukkit.World world) {
        if (world == null) return false;
        return world.getName().equalsIgnoreCase(getConfig().getString("mundo_lobby", "world"));
    }

    /**
     * Envia uma mensagem a todos os jogadores online + consola, diretamente, em vez de usar
     * {@code Bukkit.broadcastMessage(...)}. O broadcast nativo do Bukkit só entrega a quem tem a
     * permissão "bukkit.broadcast.user" — em servidores com LuckPerms a negar essa permissão por
     * predefinição (comum para evitar spam de "conquista alcançada"), as mensagens do plugin
     * (/anunciar, avisos de borda, etc.) simplesmente não chegavam a ninguém, mesmo a OPs sem essa
     * permissão explícita. Esta forma garante a entrega a toda a gente, independentemente de
     * permissões de broadcast.
     */
    public static void broadcast(String message) {
        if (message == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public void applyGamerules(World world) {
        ConfigurationSection rules = getConfig().getConfigurationSection("game.gamerules");
        if (rules == null) return;
        for (String key : rules.getKeys(false)) {
            String value = String.valueOf(rules.get(key));
            world.setGameRuleValue(key, value);
        }
    }

    @Override
    public void onDisable() {
        if (borderScoreboardManager != null) borderScoreboardManager.shutdown();
        if (borderManager != null) {
            borderManager.saveState();
            borderManager.markSafeExit();
        }
        if (borderTimerAnnouncer != null) borderTimerAnnouncer.stop();
        if (mvpStatsManager != null) mvpStatsManager.saveStats();
        if (trackerCompassListener != null) trackerCompassListener.cleanup();
        if (grapplerItemListener   != null) grapplerItemListener.cleanup();
        if (centerCompassTask      != null) centerCompassTask.stop();
        getLogger().info("TLSPlugin desativado.");
    }

    // ==== GETTERS ====
    public static Tlsplugin getInstance()                          { return instance; }
    public BorderManager getBorderManager()                        { return borderManager; }
    public SpawnManager getSpawnManager()                          { return spawnManager; }
    public BorderTimerAnnouncer getBorderTimerAnnouncer()          { return borderTimerAnnouncer; }
    public DeathListener getDeathListener()                        { return deathListener; }
    public GameFreezeManager getFreezeManager()                    { return freezeManager; }
    public MVPStatsManager getMVPStatsManager()                    { return mvpStatsManager; }
    public PlayerPauseManager getPauseManager()                    { return pauseManager; }
    public ProntoCommand getProntoCommand()                        { return prontoCommand; }
    public TeamManager getTeamManager()                            { return teamManager; }
    public CapsuleManager getCapsuleManager()                      { return capsuleManager; }
}