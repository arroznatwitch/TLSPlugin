package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Modo opcional (desligado por predefinição, {@code sudden_death.habilitar}) que dispara
 * depois da ÚLTIMA borda terminar naturalmente: espera "delay_minutos" e depois força o fim
 * do jogo — a borda encolhe para 1 bloco no centro (0,0) e a lava sobe camada a camada até
 * "lava_camada_alvo", matando quem estiver acampado.
 *
 * <p>Não existe gamerule nem API para "subir lava instantaneamente" num mundo inteiro sem
 * matar o TPS — por isso a subida é feita aos poucos (um número limitado de blocos por tick,
 * configurável), avançando camada a camada em X/Z dentro da área que a borda tinha no
 * momento em que a morte súbita começou (pior caso, já que a borda só encolhe a partir daí).</p>
 */
public class SuddenDeathManager implements Listener {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;

    private BukkitTask delayTask;
    private BukkitTask lavaTask;
    private BukkitTask countdownTask;
    private boolean scheduled = false;
    private boolean active    = false;

    // Progresso do enchimento de lava, camada a camada.
    private int currentY;
    private int targetY;
    private int halfSize;
    private int cursorX;
    private int cursorZ;
    private boolean layerFilled;
    private int ticksPerLayer;
    private int layerWaitTicksRemaining;
    private int lastAnnouncedY;

    // Boss bar com a contagem decrescente (delay antes de começar, e depois o fecho da borda).
    private final BossBar bossBar;
    private int remainingDelaySeconds;
    private int totalDelaySeconds;
    private int remainingBorderSeconds;
    private int totalBorderSeconds;

    public SuddenDeathManager(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin        = plugin;
        this.borderManager = borderManager;
        this.bossBar       = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
        this.bossBar.setVisible(false);
    }

    /** Chamado pelo BorderManager assim que a última borda termina naturalmente. */
    public void onFinalBorderReached() {
        if (scheduled || active) return;
        if (!plugin.getConfig().getBoolean("sudden_death.habilitar", false)) return;

        scheduled = true;
        int delayMinutos = plugin.getConfig().getInt("sudden_death.delay_minutos", 5);

        String aviso = plugin.getConfig().getString("sudden_death.mensagens.aviso_delay",
                        "§f[§bTLS§f] §c§lA última borda terminou! A MORTE SÚBITA começa em §f{minutos} minutos§c.")
                .replace("{minutos}", String.valueOf(delayMinutos));
        Tlsplugin.broadcast(aviso);

        // Boss bar já aparece nesta espera de "delay_minutos" — não faz sentido ficar sem
        // nada só porque a morte súbita ainda não começou oficialmente.
        this.totalDelaySeconds     = Math.max(1, delayMinutos * 60);
        this.remainingDelaySeconds = totalDelaySeconds;

        bossBar.setColor(BarColor.YELLOW);
        bossBar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        bossBar.setVisible(true);
        updateDelayCountdownTitle();

        if (delayTask != null) delayTask.cancel();
        delayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (remainingDelaySeconds <= 0) {
                if (delayTask != null) delayTask.cancel();
                delayTask = null;
                start();
                return;
            }
            updateDelayCountdownTitle();
            remainingDelaySeconds--;
        }, 0L, 20L);
    }

    private void updateDelayCountdownTitle() {
        String tempo = formatSeconds(remainingDelaySeconds);
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('§',
                "§4§l☠ Morte Súbita começa em §f" + tempo));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, remainingDelaySeconds / (double) totalDelaySeconds)));
    }

    private void start() {
        scheduled = false;

        World world = borderManager.getTargetWorld();
        if (world == null) return;
        active = true;

        String inicio = plugin.getConfig().getString("sudden_death.mensagens.inicio",
                "§f[§bTLS§f] §4§l☠ MORTE SÚBITA INICIADA! §cA lava está a subir e a borda vai fechar completamente!");
        Tlsplugin.broadcast(inicio);
        playSoundToAll("sudden_death.som.inicio", "ENTITY_WITHER_SPAWN");

        // Borda: fecha para 1 bloco (mínimo absoluto do WorldBorder), centrada em (0,0).
        int bordaTempoSegundos = plugin.getConfig().getInt("sudden_death.borda_tempo_segundos", 300);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(1, bordaTempoSegundos);

        startCountdownBossBar(bordaTempoSegundos);
        // A lava sobe ao MESMO ritmo que a borda demora a fechar — as duas coisas terminam
        // juntas (borda em (0,0) + lava no nível alvo), em vez da lava encher tudo em segundos.
        startLavaRise(world, bordaTempoSegundos);
    }

    private void playSoundToAll(String configKey, String defaultSound) {
        String soundName = plugin.getConfig().getString(configKey, defaultSound);
        Sound sound = SoundUtils.resolve(soundName);
        if (sound == null) {
            plugin.getLogger().warning("[TLS] Som inválido em '" + configKey + "': '" + soundName + "'.");
            return;
        }
        float volume = (float) plugin.getConfig().getDouble("sudden_death.som.volume", 1.0);
        float pitch  = (float) plugin.getConfig().getDouble("sudden_death.som.pitch",  1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    /** Boss bar com o tempo que falta até a borda fechar por completo (1 bloco). */
    private void startCountdownBossBar(int bordaTempoSegundos) {
        this.totalBorderSeconds     = Math.max(1, bordaTempoSegundos);
        this.remainingBorderSeconds = bordaTempoSegundos;

        bossBar.setColor(BarColor.RED);
        bossBar.setProgress(1.0);
        bossBar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);
        bossBar.setVisible(true);
        updateCountdownTitle();

        if (countdownTask != null) countdownTask.cancel();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (remainingBorderSeconds <= 0) {
                bossBar.setTitle(ChatColor.translateAlternateColorCodes('§',
                        "§4§l☠ Morte Súbita §7» §cBorda fechada!"));
                bossBar.setProgress(0.0);
                if (countdownTask != null) countdownTask.cancel();
                countdownTask = null;
                return;
            }
            updateCountdownTitle();
            remainingBorderSeconds--;
        }, 0L, 20L);
    }

    private void updateCountdownTitle() {
        String tempo = formatSeconds(remainingBorderSeconds);
        bossBar.setTitle(ChatColor.translateAlternateColorCodes('§',
                "§4§l☠ Morte Súbita §7» §cBorda fecha em §f" + tempo));
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, remainingBorderSeconds / (double) totalBorderSeconds)));
    }

    private String formatSeconds(int seconds) {
        if (seconds <= 0) return "00:00";
        int m = seconds / 60, s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void startLavaRise(World world, int bordaTempoSegundos) {
        this.targetY  = plugin.getConfig().getInt("sudden_death.lava_camada_alvo", 64);
        this.currentY = world.getMinHeight();
        // Usa o tamanho da borda no momento em que a morte súbita começa como área a
        // inundar — é o pior caso, já que a borda só vai encolher a partir daqui.
        this.halfSize = (int) Math.ceil(world.getWorldBorder().getSize() / 2.0);
        this.cursorX  = -halfSize;
        this.cursorZ  = -halfSize;
        this.layerFilled    = false;
        this.lastAnnouncedY = Integer.MIN_VALUE;

        // Distribui a subida ao longo de "bordaTempoSegundos", para acabar ao mesmo tempo
        // que a borda fecha em (0,0) — cada camada (nível de Y) tem o seu próprio "slot" de
        // tempo antes de subir para a seguinte, em vez de encher tudo o mais rápido possível.
        int camadas = Math.max(1, targetY - currentY + 1);
        this.ticksPerLayer = Math.max(1, (bordaTempoSegundos * 20) / camadas);
        this.layerWaitTicksRemaining = ticksPerLayer;

        int blocosPorTick = plugin.getConfig().getInt("sudden_death.lava_blocos_por_tick", 4000);
        int avisoIntervaloY = Math.max(1, plugin.getConfig().getInt("sudden_death.lava_aviso_intervalo_y", 5));

        anunciarNivelLava(); // aviso inicial

        if (lavaTask != null) lavaTask.cancel();
        lavaTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentY > targetY) {
                if (lavaTask != null) lavaTask.cancel();
                lavaTask = null;
                active   = false;
                bossBar.setVisible(false);
                bossBar.removeAll();
                Tlsplugin.broadcast(plugin.getConfig().getString(
                        "sudden_death.mensagens.fim", "§f[§bTLS§f] §4§l☠ A morte súbita terminou."));
                return;
            }

            if (layerFilled) {
                // Camada concluída — espera o tempo alocado antes de subir para a próxima,
                // para o ritmo da lava acompanhar o encolhimento da borda.
                if (layerWaitTicksRemaining > 0) { layerWaitTicksRemaining--; return; }

                currentY++;
                cursorX = -halfSize;
                cursorZ = -halfSize;
                layerFilled = false;
                layerWaitTicksRemaining = ticksPerLayer;
                if (currentY > targetY) return;

                if (currentY - lastAnnouncedY >= avisoIntervaloY) {
                    anunciarNivelLava();
                }
                return;
            }

            int processados = 0;
            while (processados < blocosPorTick) {
                if (cursorZ > halfSize) { layerFilled = true; break; }

                Block block = world.getBlockAt(cursorX, currentY, cursorZ);
                if (block.getType() != Material.LAVA) {
                    block.setType(Material.LAVA, false);
                }
                processados++;

                cursorX++;
                if (cursorX > halfSize) {
                    cursorX = -halfSize;
                    cursorZ++;
                    if (cursorZ > halfSize) { layerFilled = true; break; }
                }
            }
        }, 0L, 1L);
    }

    /** Avisa (chat + som) todos os jogadores do nível Y atual da lava a subir. */
    private void anunciarNivelLava() {
        lastAnnouncedY = currentY;
        String msg = plugin.getConfig().getString("sudden_death.mensagens.lava_subindo",
                        "§f[§bTLS§f] §c⚠ A lava está a subir! Nível atual§8: §fY = {y}")
                .replace("{y}", String.valueOf(currentY));
        Tlsplugin.broadcast(msg);
        playSoundToAll("sudden_death.som.lava_subindo", "BLOCK_LAVA_POP");
    }

    /** Cancela qualquer morte súbita agendada ou em curso (ex: /endgame manual). */
    public void cancelAll() {
        scheduled = false;
        active    = false;
        if (delayTask     != null) { delayTask.cancel();     delayTask     = null; }
        if (lavaTask      != null) { lavaTask.cancel();      lavaTask      = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        bossBar.setVisible(false);
        bossBar.removeAll();
    }

    /** Jogadores que entram depois da morte súbita já ter começado também vêem a boss bar. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (active || scheduled) bossBar.addPlayer(e.getPlayer());
    }

    public boolean isScheduled() { return scheduled; }
    public boolean isActive()    { return active; }
}
