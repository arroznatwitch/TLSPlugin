package com.tlsplugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public class GameFreezeManager implements Listener {

    private final Plugin plugin;
    private boolean frozen = false;

    private final Set<LivingEntity> frozenMobs  = new HashSet<>();
    private final Set<Item>         frozenItems = new HashSet<>();

    private Sound pauseMusic = Sound.MUSIC_DISC_CAT;

    public GameFreezeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen() { return frozen; }

    public void setPauseMusic(Sound sound) {
        this.pauseMusic = sound;
    }

    // ----------------------------------------------------------------
    //  Helpers de display
    //
    //  Layout adotado para todas as pausas:
    //    • Título    = "§cO JOGO FOI PAUSADO" (sem bold → ligeiramente menor, sem corte)
    //    • Subtítulo = linha de informação dinâmica (nick + timer, ou mensagem estática)
    //
    //  stay=999999 e fadeIn/Out=0 → nunca pisca.
    // ----------------------------------------------------------------

    /**
     * Envia título+subtítulo de pausa (stay=999999, sem fade → nunca pisca).
     * Título sem bold → visivelmente menor que a versão bold mas maior que o subtítulo.
     *
     * @param sub  Linha do subtítulo (timer, mensagem estática, etc.)
     */
    private void sendPauseTitle(String sub) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) {
                p.sendTitle("§cO JOGO FOI PAUSADO", sub, 0, 999999, 0);
            }
        }
    }

    // ----------------------------------------------------------------
    //  Música
    // ----------------------------------------------------------------

    private BukkitTask musicLoopTask = null;

    private void startPauseMusic() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) p.playSound(p.getLocation(), pauseMusic, 0.8f, 1.0f);
        }
    }

    private void stopPauseMusic() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.stopSound(pauseMusic);
        }
    }

    /** Loop de música para a pausa normal (sem tempo fixo). */
    private void startMusicLoop() {
        stopMusicLoop();
        // Music Disc Cat dura ~185s → reagendar a cada 180s (3600 ticks)
        musicLoopTask = new BukkitRunnable() {
            @Override public void run() {
                if (!frozen) { cancel(); return; }
                startPauseMusic();
            }
        }.runTaskTimer(plugin, 3600L, 3600L);
    }

    private void stopMusicLoop() {
        if (musicLoopTask != null) { musicLoopTask.cancel(); musicLoopTask = null; }
    }

    // ==========================================================
    //                       PAUSAR  (/pause)
    // ==========================================================
    public void freezeAll() {
        frozen = true;

        for (Entity e : Bukkit.getWorlds().get(0).getEntities()) {
            if (e instanceof LivingEntity mob && !(e instanceof Player)) {
                mob.setAI(false); frozenMobs.add(mob);
            }
            if (e instanceof Item item) {
                item.setGravity(false); frozenItems.add(item);
            }
        }

        // Título+subtítulo estático (stay=999999 → não pisca)
        sendPauseTitle("§eVamos retornar o jogo em instantes.");

        startPauseMusic();
        startMusicLoop();
    }

    // ==========================================================
    //                      DESPAUSAR  (/unpause)
    // ==========================================================
    public void unfreezeAfterCountdown(Runnable after) {
        stopMusicLoop();
        stopPauseMusic();

        for (Player p : Bukkit.getOnlinePlayers()) p.resetTitle();

        new BukkitRunnable() {
            int count = 5;

            @Override public void run() {
                switch (count) {
                    case 5 -> { sendCountdownTitle("§a§l5", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 4 -> { sendCountdownTitle("§e§l4", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 3 -> { sendCountdownTitle("§e§l3", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 2 -> { sendCountdownTitle("§6§l2", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f); }
                    case 1 -> { sendCountdownTitle("§c§l1", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f); }
                }

                if (count == 0) {
                    sendCountdownTitle("§a§lA PAUSA ACABOU!", "");
                    playEnd(Sound.UI_TOAST_CHALLENGE_COMPLETE);
                    frozen = false;
                    frozenMobs.forEach(m -> m.setAI(true));
                    frozenItems.forEach(i -> i.setGravity(true));
                    frozenMobs.clear(); frozenItems.clear();
                    Bukkit.getScheduler().runTaskLater(plugin, after, 60L);
                    cancel(); return;
                }
                count--;
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ==========================================================
    //                 FREEZE SEM COUNTDOWN  (/startgame)
    // ==========================================================
    public void freezeForStart() {
        frozen = true;
        for (Entity e : Bukkit.getWorlds().get(0).getEntities()) {
            if (e instanceof LivingEntity mob && !(e instanceof Player)) {
                mob.setAI(false); frozenMobs.add(mob);
            }
            if (e instanceof Item item) {
                item.setGravity(false); frozenItems.add(item);
            }
        }
    }

    public void startCountdown(Runnable afterUnfreeze) {
        new BukkitRunnable() {
            int count = 5;

            @Override public void run() {
                switch (count) {
                    case 5 -> { sendCountdownTitle("§a§l5", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 4 -> { sendCountdownTitle("§e§l4", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 3 -> { sendCountdownTitle("§e§l3", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 2 -> { sendCountdownTitle("§6§l2", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f); }
                    case 1 -> { sendCountdownTitle("§c§l1", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f); }
                }

                if (count == 0) {
                    sendCountdownTitle("§a§lCOMEÇOU, BOA SORTE!", "");
                    playEnd(Sound.UI_TOAST_CHALLENGE_COMPLETE);
                    frozen = false;
                    frozenMobs.forEach(m -> m.setAI(true));
                    frozenItems.forEach(i -> i.setGravity(true));
                    frozenMobs.clear(); frozenItems.clear();
                    afterUnfreeze.run();
                    cancel(); return;
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==========================================================
    //          PAUSA DE JOGADOR  (/aceitarpausa)
    // ==========================================================

    /**
     * Pausa de jogador com:
     *  • Subtítulo estático "O JOGO FOI PAUSADO" (não pisca, tamanho médio)
     *  • Subtítulo com timer atualizado a cada segundo  "qArroz — Volta em 1:59"
     *  • Música (uma vez, ~2 min)
     *  • Quando restam 5s → para a música e lança o countdown integrado
     *    (frozen permanece true durante os 5s — jogadores bloqueados até ao fim)
     */
    public void freezePlayerPauseCountdown(String nomeJogador, int duracaoSegundos, Runnable after) {

        // Música (sem loop — pausa tem duração fixa)
        startPauseMusic();

        new BukkitRunnable() {
            int remaining = duracaoSegundos;
            boolean transitioning = false;

            @Override public void run() {
                if (!frozen) { cancel(); return; }

                // Últimos 5 segundos → transitar para countdown final
                if (remaining <= 5 && !transitioning) {
                    transitioning = true;
                    cancel();
                    stopPauseMusic();
                    for (Player p : Bukkit.getOnlinePlayers()) p.resetTitle();
                    runFinalCountdown(after);
                    return;
                }

                if (transitioning) return;

                // Formatar timer e atualizar título+subtítulo
                int mins = remaining / 60;
                int secs = remaining % 60;
                String timer = String.format("%d:%02d", mins, secs);
                sendPauseTitle("§e" + nomeJogador + " §f— Volta em §a" + timer);

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Countdown 5→1 final — frozen permanece true até count==0. */
    private void runFinalCountdown(Runnable after) {
        new BukkitRunnable() {
            int count = 5;

            @Override public void run() {
                switch (count) {
                    case 5 -> { sendCountdownTitle("§a§l5", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 4 -> { sendCountdownTitle("§e§l4", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 3 -> { sendCountdownTitle("§e§l3", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f); }
                    case 2 -> { sendCountdownTitle("§6§l2", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f); }
                    case 1 -> { sendCountdownTitle("§c§l1", ""); playTick(Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f); }
                }

                if (count == 0) {
                    sendCountdownTitle("§a§lA PAUSA ACABOU!", "");
                    playEnd(Sound.UI_TOAST_CHALLENGE_COMPLETE);
                    frozen = false;
                    frozenMobs.forEach(m -> m.setAI(true));
                    frozenItems.forEach(i -> i.setGravity(true));
                    frozenMobs.clear(); frozenItems.clear();
                    Bukkit.getScheduler().runTaskLater(plugin, after, 60L);
                    cancel(); return;
                }
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ==========================================================
    //                  HELPERS PRIVADOS
    // ==========================================================

    /** Título grande (usado apenas no countdown 5→1 e "COMEÇOU"). */
    private void sendCountdownTitle(String title, String sub) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, sub, 5, 25, 5);
        }
    }

    private void playTick(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 1.0f, pitch);
        }
    }

    private void playEnd(Sound sound) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    // ==========================================================
    //                   EVENTOS QUE CONGELAM
    // ==========================================================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if (!frozen) return;
        if (e.getPlayer().isOp()) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) e.setTo(from);
    }

    @EventHandler public void onBreak(BlockBreakEvent e)        { if (frozen && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e)        { if (frozen && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent e) { if (frozen && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e)     { if (frozen && !e.getPlayer().isOp()) e.setCancelled(true); }
    @EventHandler public void onInv(InventoryClickEvent e)      { if (frozen && e.getWhoClicked() instanceof Player p && !p.isOp()) e.setCancelled(true); }
    @EventHandler public void onHit(EntityDamageEvent e)        { if (frozen) e.setCancelled(true); }
    @EventHandler public void onTarget(EntityTargetEvent e)     { if (frozen) e.setCancelled(true); }
    @EventHandler public void onProj(ProjectileLaunchEvent e)   { if (frozen) e.setCancelled(true); }
}