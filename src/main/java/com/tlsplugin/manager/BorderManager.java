package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BorderManager implements Listener {

    private final Tlsplugin plugin;
    private List<Double> stages;
    private String targetWorldName = null;
    private int currentStageIndex = -1;
    private BukkitTask pauseTask;
    private BukkitTask alertTask;
    private BossBar bossBar;
    private boolean running  = false;
    private boolean paused   = false;
    private boolean bordaSetada = false;

    private int remainingShrinkSeconds = 0;

    private final int ALERT_DIST            = 75;
    private final int ALERT_REPEATS         = 3;
    private final int ALERT_INTERVAL_TICKS  = 40;
    private final int ALERT_COOLDOWN_SECONDS = 15;
    private final Set<UUID> alertCooldown   = new HashSet<>();
    private boolean crashDetected = false;

    public BorderManager(Tlsplugin plugin) {
        this.plugin = plugin;
        List<Double> l = getModoConfig().getDoubleList("bordas");
        this.stages = (l == null || l.isEmpty())
                ? Arrays.asList(10000.0, 9500.0, 8000.0, 5000.0, 2500.0, 1000.0, 500.0)
                : l;
        this.bossBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
        this.bossBar.setVisible(false);

        loadState();
    }

    // ── Helpers de configuração por modo ──────────────────────────────────────

    private org.bukkit.configuration.ConfigurationSection getModoConfig() {
        String modo = plugin.getConfig().getString("modo_jogo", "final");
        org.bukkit.configuration.ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("modos." + modo);
        if (sec == null) {
            plugin.getLogger().warning("[TLS] Modo de jogo '" + modo + "' não encontrado no config! A usar 'final'.");
            sec = plugin.getConfig().getConfigurationSection("modos.final");
        }
        return sec;
    }

    private int getShrinkSeconds() {
        org.bukkit.configuration.ConfigurationSection sec = getModoConfig();
        return sec != null ? sec.getInt("tempo_shrink_segundos", 720) : 720;
    }

    private int getPauseSeconds() {
        org.bukkit.configuration.ConfigurationSection sec = getModoConfig();
        return sec != null ? sec.getInt("tempo_pausa_segundos", 30) : 30;
    }

    public String getModoAtivo() {
        return plugin.getConfig().getString("modo_jogo", "final");
    }

    public World getTargetWorld() {
        if (targetWorldName != null) {
            World w = Bukkit.getWorld(targetWorldName);
            if (w != null) return w;
        }
        return getTargetWorld();
    }

    public void setTargetWorld(World world) {
        this.targetWorldName = world.getName();
        plugin.getLogger().info("[TLS] Mundo alvo: " + targetWorldName);
    }

    public String getTargetWorldName() {
        return targetWorldName != null ? targetWorldName : getTargetWorld().getName();
    }

    /** Recarrega a lista de bordas do modo ativo — chamado após reloadConfig(). */
    public void reloadStages() {
        List<Double> l = getModoConfig().getDoubleList("bordas");
        this.stages = (l == null || l.isEmpty())
                ? Arrays.asList(10000.0, 9500.0, 8000.0, 5000.0, 2500.0, 1000.0, 500.0)
                : l;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public void saveState() {
        File file = new File(plugin.getDataFolder(), "estado.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("running",                running);
        yaml.set("paused",                 paused);
        yaml.set("currentStageIndex",      currentStageIndex);
        yaml.set("remainingShrinkSeconds", remainingShrinkSeconds);
        yaml.set("lastSafeExit",           false);
        try { yaml.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadState() {
        File file = new File(plugin.getDataFolder(), "estado.yml");
        if (!file.exists()) { applyGameRulesForStage(1); return; }

        YamlConfiguration yaml    = YamlConfiguration.loadConfiguration(file);
        boolean wasRunning        = yaml.getBoolean("running",       false);
        boolean wasPaused         = yaml.getBoolean("paused",        false);
        boolean lastSafeExit      = yaml.getBoolean("lastSafeExit",  true);

        if (wasRunning) {
            this.running              = true;
            this.currentStageIndex    = yaml.getInt("currentStageIndex",      0);
            this.remainingShrinkSeconds = yaml.getInt("remainingShrinkSeconds", 0);

            World w          = getTargetWorld();
            double targetSize = stages.get(Math.min(currentStageIndex + 1, stages.size() - 1));

            if (!lastSafeExit) {
                this.paused       = true;
                this.crashDetected = true;
                plugin.getLogger().warning(
                        "[TLS] Queda do servidor detectada! Jogo sera pausado quando jogadores entrarem.");
            } else {
                this.paused = wasPaused;
            }

            if (this.paused) {
                w.getWorldBorder().setSize(stages.get(currentStageIndex));
                updateBossBarPaused(0);
            } else {
                w.getWorldBorder().setSize(targetSize, remainingShrinkSeconds);
                scheduleNextShrink();
            }

            applyGameRulesForStage(currentStageIndex + 1);
            bossBar.setVisible(true);
            for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        } else {
            applyGameRulesForStage(1);
        }
    }

    public void markSafeExit() {
        File file = new File(plugin.getDataFolder(), "estado.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("lastSafeExit", true);
        try { yaml.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (running) bossBar.addPlayer(e.getPlayer());
        if (crashDetected) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getFreezeManager().freezeAll();
                String crashMsg = plugin.getConfig().getString(
                        "mensagens_comandos.crash_aviso",
                        "§c[TLS] ⚠ O servidor caiu inesperadamente. O jogo está §lPAUSADO§c. Use §f/unpause §cpara retomar.");
                Bukkit.broadcastMessage(crashMsg);
                crashDetected = false;
            }, 20L);
        }
    }

    public void setToInitial() {
        if (stages.isEmpty()) return;
        World w     = getTargetWorld();
        double size = stages.get(0);
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(size);
        currentStageIndex = 0;
        this.paused       = false;
        this.bordaSetada  = true;
        saveState();
        broadcastFormatted(plugin.getConfig().getString("mensagens.borda_terminou"),
                currentStageIndex + 1, stages.size(), size, 0);
        applyGameRulesForStage(1);
    }

    public void startCycle() {
        if (running) return;
        running = true;

        bossBar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        bossBar.setVisible(true);

        currentStageIndex = 0;
        this.paused = false;
        saveState();

        broadcastFormatted(plugin.getConfig().getString("mensagens.borda_inicio"),
                currentStageIndex + 1, stages.size(), stages.get(currentStageIndex + 1),
                getShrinkSeconds());

        scheduleNextShrink();
    }

    private void scheduleNextShrink() {
        if (currentStageIndex < 0 || currentStageIndex >= stages.size() - 1) {
            running = false;
            bossBar.removeAll();
            bossBar.setVisible(false);
            if (alertTask != null) { alertTask.cancel(); alertTask = null; }
            saveState();
            return;
        }

        this.paused = false;
        saveState();
        applyGameRulesForStage(currentStageIndex + 1);

        double to          = stages.get(currentStageIndex + 1);
        int shrinkSeconds  = getShrinkSeconds();
        int pauseSeconds   = getPauseSeconds();

        World w = getTargetWorld();
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(to, shrinkSeconds);
        this.remainingShrinkSeconds = shrinkSeconds;
        saveState();

        if (alertTask != null) { alertTask.cancel(); alertTask = null; }

        // ── CORREÇÃO CRÍTICA #3: array de referência para evitar NPE no 1º tick ──
        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (paused) return;

            if (remainingShrinkSeconds <= 0) {
                if (taskRef[0] != null) taskRef[0].cancel();
                alertTask = null;

                currentStageIndex++;
                paused = true;

                double nextTarget = (currentStageIndex < stages.size() - 1)
                        ? stages.get(currentStageIndex + 1)
                        : stages.get(currentStageIndex);

                broadcastFormatted(plugin.getConfig().getString("mensagens.borda_terminou"),
                        currentStageIndex + 1, stages.size(), nextTarget, 0);

                saveState();
                startPauseCountdown(pauseSeconds, shrinkSeconds);
                return;
            }

            updateBossBar(currentStageIndex + 1, stages.size(), to, remainingShrinkSeconds);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isNearBorder(p, w.getWorldBorder().getSize(), ALERT_DIST)
                        && !alertCooldown.contains(p.getUniqueId())) {
                    triggerWarning(p);
                }
            }

            remainingShrinkSeconds--;
            if (remainingShrinkSeconds % 30 == 0) {
                saveState();
                plugin.getMVPStatsManager().saveStats();
            }
        }, 20L, 20L);
        alertTask = taskRef[0];
    }

    public void applyGameRulesForStage(int stageNumber) {
        boolean isPvPActive = (stageNumber >= 2);

        plugin.getLogger().info("[TLS] Aplicando Regras - Estágio: " + stageNumber +
                " | Pausado: " + paused + " | PvP Final: " + isPvPActive);

        boolean announceAdvancements = plugin.getConfig().getBoolean("game.gamerules.announceAdvancements", false);
        boolean doImmediateRespawn   = plugin.getConfig().getBoolean("game.gamerules.doImmediateRespawn",   true);
        boolean showLocatorBar       = plugin.getConfig().getBoolean("game.gamerules.locator_bar",          false);

        for (World world : Bukkit.getWorlds()) {
            // PvP: false na 1ª borda, true a partir da 2ª
            world.setPVP(isPvPActive);

            // naturalRegen: true na 1ª borda, false a partir da 2ª
            world.setGameRule(GameRule.NATURAL_REGENERATION, !isPvPActive);

            // Configuráveis pelo config.yml
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announceAdvancements);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN,  doImmediateRespawn);

            // showDeathMessages: sempre false no Vanilla (o plugin faz a notificação)
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES,   false);
            world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        }

        // pvp via comando (compatibilidade extra)
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule pvp " + isPvPActive);

        // locatorBar: configurável
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule locator_bar " + showLocatorBar);
    }

    public void resetCurrentStage() {
        if (currentStageIndex < 0 || currentStageIndex >= stages.size()) return;
        World w              = getTargetWorld();
        double originalSize  = stages.get(currentStageIndex);
        int shrinkSeconds    = getShrinkSeconds();
        w.getWorldBorder().setSize(originalSize);
        this.remainingShrinkSeconds = shrinkSeconds;
        updateBossBar(currentStageIndex + 1, stages.size(), originalSize, shrinkSeconds);
    }

    private void startPauseCountdown(int pauseSeconds, int shrinkSeconds) {
        this.paused = true;
        saveState();
        if (pauseTask != null) pauseTask.cancel();
        pauseTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = pauseSeconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    pauseTask.cancel();
                    paused = false;
                    saveState();
                    if (currentStageIndex < stages.size() - 1) {
                        broadcastFormatted(plugin.getConfig().getString("mensagens.borda_retomada"),
                                currentStageIndex + 1, stages.size(),
                                stages.get(currentStageIndex + 1), shrinkSeconds);
                    }
                    scheduleNextShrink();
                    return;
                }
                updateBossBarPaused(remaining);
                remaining--;
            }
        }, 0L, 20L);
    }

    private void updateBossBar(int stage, int total, double toSize, double secondsLeft) {
        double half     = toSize / 2.0;
        String coord    = "±" + (int) half;
        String timeText = formatSeconds((int) Math.ceil(secondsLeft));

        String template = plugin.getConfig().getString("bossbar_template",
                "§bBorda {stage}/{total} - A fechar para X/Z {coord} em §f{time}");
        String text = template
                .replace("{stage}", String.valueOf(stage))
                .replace("{total}", String.valueOf(total))
                .replace("{coord}", coord)
                .replace("{to}",    coord)
                .replace("{time}",  timeText);

        bossBar.setTitle(ChatColor.translateAlternateColorCodes('§', text));
        bossBar.setColor(BarColor.RED);

        double progress = Math.max(0.0, Math.min(1.0, secondsLeft / getShrinkSeconds()));
        bossBar.setProgress(progress);
    }

    private void updateBossBarPaused(int seconds) {
        String pausedTemplate = plugin.getConfig().getString("mensagens.borda_pausada",
                "§e[Borda] PAUSADA — volta em {time}");
        String text = pausedTemplate
                .replace("{stage}", String.valueOf(currentStageIndex + 1))
                .replace("{total}", String.valueOf(stages.size()))
                .replace("{time}",  formatSeconds(seconds));
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('§', text));
        bossBar.setProgress(1.0);
        bossBar.setColor(BarColor.YELLOW);
    }

    private void broadcastFormatted(String template, int stage, int total, double to, int timeSeconds) {
        if (template == null) return;
        String coord = "±" + (int) (to / 2);
        String msg = template
                .replace("{stage}", String.valueOf(stage))
                .replace("{total}", String.valueOf(total))
                .replace("{coord}", coord)
                .replace("{to}",    coord)
                .replace("{time}",  formatSeconds(timeSeconds));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('§', msg));
    }

    private String formatSeconds(int seconds) {
        if (seconds <= 0) return "00:00";
        int m = seconds / 60, s = seconds % 60;
        return (m > 0) ? String.format("%02d:%02d", m, s) : String.format("00:%02d", s);
    }

    private void triggerWarning(Player p) {
        alertCooldown.add(p.getUniqueId());
        for (int i = 0; i < ALERT_REPEATS; i++) {
            int delay = i * ALERT_INTERVAL_TICKS;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.sendTitle("§c§lCUIDADO!!!", "§eA borda está perto!", 10, 40, 10);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            }, delay);
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> alertCooldown.remove(p.getUniqueId()),
                ALERT_COOLDOWN_SECONDS * 20L);
    }

    private boolean isNearBorder(Player p, double borderDiameter, int threshold) {
        double half      = borderDiameter / 2.0;
        double dx        = Math.abs(p.getLocation().getX());
        double dz        = Math.abs(p.getLocation().getZ());
        double distToEdge = half - Math.max(dx, dz);
        return distToEdge <= threshold;
    }

    public void stopAll() {
        if (alertTask != null) { alertTask.cancel(); alertTask = null; }
        if (pauseTask != null) { pauseTask.cancel(); pauseTask = null; }
        running = false;
        paused  = false;
        bossBar.removeAll();
        bossBar.setVisible(false);

        World w = getTargetWorld();
        w.getWorldBorder().setSize(30000000);
        w.getWorldBorder().setCenter(0, 0);

        this.currentStageIndex      = -1;
        this.remainingShrinkSeconds = 0;
        saveState();
        this.bordaSetada = false;
        applyGameRulesForStage(1);
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        saveState();
        if (paused) resetCurrentStage();
    }

    public void resumeAfterPause() {
        this.paused = false;
        saveState();
        scheduleNextShrink();
    }

    public boolean isBordaSetada()  { return bordaSetada; }
    public boolean isRunning()       { return running; }
    public boolean isPaused()        { return paused; }

    public double getNextTarget() {
        if (currentStageIndex + 1 < stages.size()) return stages.get(currentStageIndex + 1);
        return stages.get(currentStageIndex);
    }

    public int getCurrentStage()    { return currentStageIndex < 0 ? 0 : currentStageIndex + 1; }
    public int getTotalStages()     { return stages.size(); }

    public double getCurrentBorderSize() {
        return getTargetWorld().getWorldBorder().getSize();
    }

    public int getRemainingShrinkSeconds() { return remainingShrinkSeconds; }
}