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

        // ── Tempo vivo: acumulador em vez de "agora - joinTime" ──────────────────
        // Calcular o tempo como (agora - joinTime) é frágil: o relógio continua a correr
        // quando o jogador se desliga, quando morre, e durante o downtime do servidor —
        // e uma queda perdia/inflacionava tudo. Aqui só acumulamos tempo REALMENTE vivo,
        // validado a cada segundo (ver MVPStatsManager.tick()), e é o acumulado que fica
        // gravado. Assim uma queda perde no máximo 1 segundo, nunca o progresso todo.

        /** Tempo vivo já confirmado e gravado (ms). Fonte de verdade para os pontos. */
        public long accumulatedAliveMs = 0;
        /** 0 = contador parado. Caso contrário, instante em que o contador arrancou. */
        public long aliveSinceMs = 0;

        public PlayerStats(String playerName) {
            this.playerName = playerName;
        }

        /** Tempo vivo total (ms): o já acumulado + o troço a decorrer, se estiver a contar. */
        public long getAliveMs() {
            long total = accumulatedAliveMs;
            if (aliveSinceMs > 0) {
                long delta = System.currentTimeMillis() - aliveSinceMs;
                if (delta > 0) total += delta;
            }
            return total;
        }

        /** Arranca o contador (idempotente — não reinicia se já estiver a contar). */
        void startCounting(long now) {
            if (aliveSinceMs == 0) aliveSinceMs = now;
        }

        /**
         * Move o troço decorrido para o acumulador e continua a contar. Chamado a cada
         * segundo: é isto que garante que uma queda do servidor perde ≤1s.
         */
        void flush(long now) {
            if (aliveSinceMs > 0) {
                long delta = now - aliveSinceMs;
                if (delta > 0) accumulatedAliveMs += delta;
                aliveSinceMs = now;
            }
        }

        /** Guarda o troço decorrido e para o contador. */
        void stopCounting(long now) {
            flush(now);
            aliveSinceMs = 0;
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

        /**
         * @param totalPausedMs ignorado — as pausas já são tratadas parando o contador,
         *                      logo subtrair aqui contava a mesma pausa duas vezes.
         *                      Mantido só para não partir as chamadas existentes.
         */
        public long getAliveTimeMinutes(long totalPausedMs) {
            return TimeUnit.MILLISECONDS.toMinutes(getAliveMs());
        }

        @Deprecated
        public long getAliveTimeMinutes() {
            return getAliveTimeMinutes(0);
        }

        /** @param totalPausedMs ignorado — ver {@link #getAliveTimeMinutes(long)}. */
        public int calculateTimePoints(long totalPausedMs) {
            long aliveMinutes = TimeUnit.MILLISECONDS.toMinutes(getAliveMs());

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
            // OPs e jogadores com group.admin têm sempre 0 pontos (staff/árbitros)
            boolean devMode = config.getBoolean("game.modo-desenvolvedor", false);
            if (!devMode) {
                org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(playerName);
                if (op.isOp()) return 0;
                // Verificar group.admin via LuckPerms (só se estiver online — sem chamadas async aqui)
                org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(playerName);
                if (online != null) {
                    var provider = org.bukkit.Bukkit.getServicesManager()
                            .getRegistration(net.luckperms.api.LuckPerms.class);
                    if (provider != null) {
                        net.luckperms.api.model.user.User user =
                                provider.getProvider().getUserManager().getUser(online.getUniqueId());
                        if (user != null) {
                            boolean isAdmin = user.getNodes(net.luckperms.api.node.NodeType.INHERITANCE)
                                    .stream()
                                    .anyMatch(n -> n.getGroupName().equalsIgnoreCase("admin"));
                            if (isAdmin) return 0;
                        }
                    }
                }
            }
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
        tick(); // para já os contadores de toda a gente
    }

    public void onUnpause() {
        if (!currentlyPaused) return;
        currentlyPaused = false;
        totalPausedMs += System.currentTimeMillis() - pauseStartMs;
        pauseStartMs = 0;
        tick(); // retoma a contagem de quem está vivo e em survival
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

    /** Para o relógio de tempo vivo de quem sai ou cai do servidor. */
    public void onPlayerQuit(String playerName) {
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) stats.stopCounting(System.currentTimeMillis());
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
            long now = System.currentTimeMillis();
            stats.deaths++;
            stats.lastActivityTime = now;
            stats.deathTime = now;
            stats.stopCounting(now); // congela o tempo vivo já no instante da morte
        }
    }

    public void addRevival(String playerName) {
        if (!gameStarted || !isEligibleInternal(playerName)) return;
        PlayerStats stats = playerStats.get(playerName);
        if (stats != null) { stats.revivals++; stats.lastActivityTime = System.currentTimeMillis(); }
    }

    /**
     * Chamado quando um jogador é revivido com sucesso.
     * Limpa o deathTime e reinicia o joinTime para que o tempo vivo
     * recomece a contar a partir do momento do revive.
     */
    public void onPlayerRevived(String revivedPlayerName) {
        if (!gameStarted) return;
        PlayerStats stats = playerStats.get(revivedPlayerName);
        if (stats != null) {
            // Só limpamos a marca de morte: o tempo vivo anterior fica no acumulador e a
            // contagem retoma daí (antes, reiniciar o joinTime apagava tudo o que ele já
            // tinha acumulado antes de morrer).
            stats.deathTime = 0;
            stats.lastActivityTime = System.currentTimeMillis();
            syncPlayer(Bukkit.getPlayerExact(revivedPlayerName));
        }
    }

    // ------------------------------------------------------------------
    //  Motor do tempo vivo (2 fatores: estado verificado + flush periódico)
    // ------------------------------------------------------------------

    /**
     * Fator 1 — o estado tem de estar TODO correto para o relógio andar. Verificado de
     * raiz a cada segundo, em vez de depender de apanhar cada evento individualmente:
     * mesmo que um evento se perca, no segundo seguinte a contagem é corrigida.
     */
    private boolean deveContar(Player p, PlayerStats stats) {
        if (!gameStarted || currentlyPaused) return false;   // jogo parado/pausado
        if (p == null || !p.isOnline())      return false;   // caiu/saiu do servidor
        if (p.getGameMode() != GameMode.SURVIVAL) return false; // GM3/criativo/adventure
        return stats.deathTime == 0;                          // morto não conta
    }

    /** Recalcula, para um jogador, se o relógio deve estar a andar. Seguro com null. */
    public void syncPlayer(Player p) {
        if (p == null) return;
        PlayerStats stats = playerStats.get(p.getName());
        if (stats == null) return;
        long now = System.currentTimeMillis();
        if (deveContar(p, stats)) stats.startCounting(now);
        else                      stats.stopCounting(now);
    }

    /**
     * Fator 2 — chamado a cada segundo. Revalida o estado de toda a gente e move o tempo
     * decorrido para o acumulador (que é o que fica gravado). É isto que torna o sistema
     * à prova de quedas: o pior caso é perder 1 segundo, nunca o tempo todo. Também
     * apanha jogadores que se desligaram sem passar por nenhum evento.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, PlayerStats> entry : playerStats.entrySet()) {
            PlayerStats stats = entry.getValue();
            Player p = Bukkit.getPlayerExact(entry.getKey());
            if (deveContar(p, stats)) {
                stats.startCounting(now);
                stats.flush(now);
            } else {
                stats.stopCounting(now);
            }
        }
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

    /**
     * Grava um snapshot das stats de um jogador no momento em que morre, em
     * {@code plugins/tlsplugin/mortes/}. Serve de registo permanente por morte (útil para
     * conferir disputas e reconstruir o que aconteceu), independente do mvp_stats.yml, que
     * é sobrescrito ao longo do jogo.
     *
     * @return o ficheiro criado, ou null se não houver stats/ocorreu erro.
     */
    public File saveDeathSnapshot(String playerName, String killerName) {
        PlayerStats stats = playerStats.get(playerName);
        if (stats == null) return null;

        try {
            File pasta = new File(Tlsplugin.getInstance().getDataFolder(), "mortes");
            if (!pasta.exists()) pasta.mkdirs();

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                    .format(new java.util.Date());
            File ficheiro = new File(pasta, playerName + "_" + timestamp + ".yml");

            long pausedMs = getEffectivePausedMs();
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("jogador",          playerName);
            yaml.set("morreuEm",         timestamp);
            yaml.set("mortoPor",         killerName == null ? "—" : killerName);
            yaml.set("pontosTotais",     stats.calculateTotalMVPPoints(pausedMs));
            yaml.set("tempoVivoMinutos", stats.getAliveTimeMinutes(pausedMs));
            yaml.set("kills",            stats.kills);
            yaml.set("assists",          stats.assists);
            yaml.set("deaths",           stats.deaths);
            yaml.set("revivals",         stats.revivals);
            yaml.set("damageGiven",      stats.damageGiven);
            yaml.set("damageReceived",   stats.damageReceived);
            yaml.set("ddrd",             stats.getDDRD());
            yaml.set("pontosDDRD",       stats.calculateDDRDPoints());
            yaml.set("pontosTempo",      stats.calculateTimePoints(pausedMs));
            yaml.save(ficheiro);

            Tlsplugin.getInstance().getLogger().info(
                    "[TLS] Snapshot de morte guardado: mortes/" + ficheiro.getName());
            return ficheiro;
        } catch (Exception e) {
            Tlsplugin.getInstance().getLogger().severe(
                    "[TLS] Falha ao guardar snapshot de morte de " + playerName + ": " + e.getMessage());
            return null;
        }
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
                yaml.set(path + "deathTime",        stats.deathTime);
                yaml.set(path + "accumulatedAliveMs", stats.getAliveMs());
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
                // Grava o tempo já confirmado + o troço a decorrer, para uma queda entre
                // dois flushes não perder o segundo em curso.
                yaml.set(path + "accumulatedAliveMs", stats.getAliveMs());
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

                    // O contador NUNCA é restaurado a correr: o servidor esteve em baixo,
                    // esse tempo não é tempo vivo. Só volta a andar quando o jogador entrar
                    // e o tick() confirmar que está vivo e em survival.
                    stats.aliveSinceMs = 0;
                    if (yaml.contains(path + "accumulatedAliveMs")) {
                        stats.accumulatedAliveMs = yaml.getLong(path + "accumulatedAliveMs", 0);
                    } else {
                        // Ficheiro do formato antigo (só tinha joinTime/deathTime): aproveita
                        // o que dá para reconstruir, em vez de zerar o jogador.
                        long fim = stats.deathTime > 0 ? stats.deathTime : System.currentTimeMillis();
                        long estimado = fim - stats.joinTime - this.totalPausedMs;
                        stats.accumulatedAliveMs = Math.max(0, estimado);
                    }
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
        // Novo jogo: o tempo vivo arranca sempre do zero, mesmo que tenha ficado valor de
        // um jogo anterior (ex: /startgame depois de uma queda sem /endgame).
        for (PlayerStats stats : playerStats.values()) {
            stats.joinTime = now;
            stats.lastActivityTime = now;
            stats.deathTime = 0;
            stats.accumulatedAliveMs = 0;
            stats.aliveSinceMs = 0;
        }
        tick(); // arranca a contagem só a quem está vivo e em survival
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