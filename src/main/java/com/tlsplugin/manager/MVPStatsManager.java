package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MVPStatsManager {

    private boolean gameStarted = false;

    // Tempo acumulado de pausa (em ms) — subtrai-se do playtime total
    private long totalPausedMs = 0;
    private long pauseStartMs  = 0;
    private boolean currentlyPaused = false;

    // Momento em que o jogo foi iniciado (startTracking)
    private long gameStartMs = 0;

    public static class PlayerStats {
        public String playerName;
        public double damageGiven = 0;
        public double damageReceived = 0;
        public int kills = 0;
        public int assists = 0;
        public int deaths = 0;
        public int revivals = 0;
        public long joinTime = System.currentTimeMillis();
        public long lastActivityTime = System.currentTimeMillis();
        public long deathTime = 0; // 0 = vivo; >0 = timestamp da morte

        public PlayerStats(String playerName) {
            this.playerName = playerName;
        }

        public double getDDRD() {
            return damageGiven - damageReceived;
        }

        public int calculateDDRDPoints() {
            double ddrd = getDDRD();
            FileConfiguration config = Tlsplugin.getInstance().getConfig();

            if (ddrd >= config.getDouble("mvp_pontos.ddrd.excelente_limiar", 21))
                return config.getInt("mvp_pontos.ddrd.excelente_pontos", 6);
            else if (ddrd >= config.getDouble("mvp_pontos.ddrd.bom_limiar", 11))
                return config.getInt("mvp_pontos.ddrd.bom_pontos", 4);
            else if (ddrd >= config.getDouble("mvp_pontos.ddrd.positivo_limiar", 1))
                return config.getInt("mvp_pontos.ddrd.positivo_pontos", 2);
            else if (ddrd <= config.getDouble("mvp_pontos.ddrd.negativo_limiar", -10))
                return config.getInt("mvp_pontos.ddrd.negativo_pontos", -3);

            return 0;
        }

        public long getAliveTimeMinutes(long totalPausedMs) {
            long endTime = deathTime > 0 ? deathTime : System.currentTimeMillis();
            long aliveMs = endTime - joinTime - totalPausedMs;
            if (aliveMs < 0) aliveMs = 0;
            return TimeUnit.MILLISECONDS.toMinutes(aliveMs);
        }

        @Deprecated
        public long getAliveTimeMinutes() {
            return getAliveTimeMinutes(0);
        }

        public int calculateTimePoints(long totalPausedMs) {
            long endTime = deathTime > 0 ? deathTime : System.currentTimeMillis();
            long aliveMs = endTime - joinTime - totalPausedMs;
            if (aliveMs < 0) aliveMs = 0;
            long aliveMinutes = TimeUnit.MILLISECONDS.toMinutes(aliveMs);

            FileConfiguration config = Tlsplugin.getInstance().getConfig();
            boolean devMode = config.getBoolean("game.modo-desenvolvedor", false);
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(playerName);
            if (op.isOp() && !devMode) return 0;

            int cada   = config.getInt("mvp_pontos.tempo_vivo.cada_minutos", 5);
            int pontos = config.getInt("mvp_pontos.tempo_vivo.pontos", 2);
            return (int) (aliveMinutes / cada) * pontos;
        }

        @Deprecated
        public int calculateTimePoints() {
            return calculateTimePoints(0);
        }

        public int calculateTotalMVPPoints(long totalPausedMs) {
            FileConfiguration config = Tlsplugin.getInstance().getConfig();
            int points = 0;
            points += calculateDDRDPoints();
            points += calculateTimePoints(totalPausedMs);
            points += kills   * config.getInt("mvp_pontos.kill",   8);
            points += assists * config.getInt("mvp_pontos.assist",  3);
            points += deaths  * config.getInt("mvp_pontos.morte",  -6);
            points += revivals* config.getInt("mvp_pontos.revive",  5);
            return points;
        }

        @Deprecated
        public int calculateTotalMVPPoints() {
            return calculateTotalMVPPoints(0);
        }
    }

    private final Map<String, PlayerStats> playerStats = new HashMap<>();

    // ------------------------------------------------------------------
    //  Pausa — para o playtime durante a pausa
    // ------------------------------------------------------------------
    public void onPause() {
        if (currentlyPaused) return;
        currentlyPaused = true;
        pauseStartMs = System.currentTimeMillis();
    }

    public void onUnpause() {
        if (!currentlyPaused) return;
        currentlyPaused = false;
        totalPausedMs += System.currentTimeMillis() - pauseStartMs;
        pauseStartMs = 0;
    }

    /** Tempo total pausado até agora (inclui pausa ativa, se houver). */
    public long getEffectivePausedMs() {
        if (currentlyPaused) {
            return totalPausedMs + (System.currentTimeMillis() - pauseStartMs);
        }
        return totalPausedMs;
    }

    // ------------------------------------------------------------------

    public void registerPlayer(String playerName) {
        playerStats.putIfAbsent(playerName, new PlayerStats(playerName));
    }

    public void unregisterPlayer(String playerName) {
        // Mantém estatísticas de quem saiu
    }

    public boolean isEligible(String playerName) {
        FileConfiguration config = Tlsplugin.getInstance().getConfig();
        boolean devMode = config.getBoolean("game.modo-desenvolvedor", false);
        if (devMode) return true;
        return true;
    }

    private boolean isEligibleInternal(String playerName) {
        return true;
    }

    public void addDamageGiven(String playerName, double damage) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.damageGiven += damage; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    public void addDamageReceived(String playerName, double damage) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.damageReceived += damage; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    public void addKill(String playerName) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.kills++; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    public void addAssist(String playerName) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.assists++; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    public void addDeath(String playerName) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) {
            stats.deaths++;
            stats.lastActivityTime = System.currentTimeMillis();
            stats.deathTime = System.currentTimeMillis(); // congela o tempo vivo
        }
    }

    public void addRevival(String playerName) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.revivals++; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    public PlayerStats getStats(String playerName) {
        return playerStats.get(playerName);
    }

    public List<PlayerStats> getRanking() {
        long pausedMs = getEffectivePausedMs();
        List<PlayerStats> ranking = new ArrayList<>();
        for (PlayerStats stats : playerStats.values()) {
            if (isEligible(stats.playerName)) ranking.add(stats);
        }
        ranking.sort((a, b) -> Integer.compare(
                b.calculateTotalMVPPoints(pausedMs),
                a.calculateTotalMVPPoints(pausedMs)));
        return ranking;
    }

    public PlayerStats getMVP() {
        List<PlayerStats> ranking = getRanking();
        return ranking.isEmpty() ? null : ranking.get(0);
    }

    public Collection<PlayerStats> getAllPlayers() {
        return playerStats.values();
    }

    public void resetAll() {
        backupStats();
        playerStats.clear();
        gameStarted = false;
        totalPausedMs = 0;
        pauseStartMs  = 0;
        currentlyPaused = false;
        gameStartMs = 0;
        saveStats();
    }

    public void backupStats() {
        if (playerStats.isEmpty()) return;
        try {
            File dataFolder = Tlsplugin.getInstance().getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                    .format(new java.util.Date());
            File backupFile = new File(dataFolder, "mvp_backup_" + timestamp + ".yml");

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("gameStarted", gameStarted);
            yaml.set("backupTime", timestamp);
            yaml.set("totalPausedMs", totalPausedMs);
            for (Map.Entry<String, PlayerStats> entry : playerStats.entrySet()) {
                String path = "players." + entry.getKey() + ".";
                PlayerStats stats = entry.getValue();
                yaml.set(path + "damageGiven",      stats.damageGiven);
                yaml.set(path + "damageReceived",   stats.damageReceived);
                yaml.set(path + "kills",            stats.kills);
                yaml.set(path + "assists",          stats.assists);
                yaml.set(path + "deaths",           stats.deaths);
                yaml.set(path + "revivals",         stats.revivals);
                yaml.set(path + "joinTime",         stats.joinTime);
                yaml.set(path + "lastActivityTime", stats.lastActivityTime);
            }
            yaml.save(backupFile);
            File lastGame = new File(dataFolder, "mvp_ultimo_jogo.yml");
            yaml.save(lastGame);
            Tlsplugin.getInstance().getLogger().info("[TLS] Backup MVP guardado: " + backupFile.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveStats() {
        File file = new File(Tlsplugin.getInstance().getDataFolder(), "mvp_stats.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("gameStarted",   gameStarted);
        yaml.set("totalPausedMs", totalPausedMs);

        if (!playerStats.isEmpty()) {
            for (Map.Entry<String, PlayerStats> entry : playerStats.entrySet()) {
                String pName = entry.getKey();
                PlayerStats stats = entry.getValue();
                String path = "players." + pName + ".";
                yaml.set(path + "damageGiven",      stats.damageGiven);
                yaml.set(path + "damageReceived",   stats.damageReceived);
                yaml.set(path + "kills",            stats.kills);
                yaml.set(path + "assists",          stats.assists);
                yaml.set(path + "deaths",           stats.deaths);
                yaml.set(path + "revivals",         stats.revivals);
                yaml.set(path + "joinTime",         stats.joinTime);
                yaml.set(path + "lastActivityTime", stats.lastActivityTime);
                yaml.set(path + "deathTime",        stats.deathTime);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            Tlsplugin.getInstance().getLogger().severe("Não foi possível salvar mvp_stats.yml: " + e.getMessage());
        }
    }

    public void loadStats() {
        File file = new File(Tlsplugin.getInstance().getDataFolder(), "mvp_stats.yml");
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.gameStarted   = yaml.getBoolean("gameStarted", false);
        this.totalPausedMs = yaml.getLong("totalPausedMs", 0);

        if (yaml.contains("players")) {
            org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("players");
            if (section != null) {
                for (String pName : section.getKeys(false)) {
                    PlayerStats stats = new PlayerStats(pName);
                    String path = "players." + pName + ".";
                    stats.damageGiven      = yaml.getDouble(path + "damageGiven");
                    stats.damageReceived   = yaml.getDouble(path + "damageReceived");
                    stats.kills            = yaml.getInt(path + "kills");
                    stats.assists          = yaml.getInt(path + "assists");
                    stats.deaths           = yaml.getInt(path + "deaths");
                    stats.revivals         = yaml.getInt(path + "revivals");
                    stats.joinTime         = yaml.getLong(path + "joinTime");
                    stats.lastActivityTime = yaml.getLong(path + "lastActivityTime");
                    stats.deathTime        = yaml.getLong(path + "deathTime", 0);
                    playerStats.put(pName, stats);
                }
            }
        }
    }

    public void startTracking() {
        this.gameStarted    = true;
        this.totalPausedMs  = 0;
        this.pauseStartMs   = 0;
        this.currentlyPaused = false;
        this.gameStartMs    = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        for (PlayerStats stats : playerStats.values()) {
            stats.joinTime = now;
            stats.lastActivityTime = now;
        }
        saveStats();
    }

    public void stopTracking() {
        this.gameStarted = false;
        saveStats();
    }

    public boolean isTrackingActive() {
        return gameStarted;
    }
}