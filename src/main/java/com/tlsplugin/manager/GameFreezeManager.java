package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameFreezeManager implements Listener {

    private final Plugin plugin;
    private boolean frozen = false;

    private final Set<LivingEntity> frozenMobs  = new HashSet<>();
    private final Set<Item>         frozenItems = new HashSet<>();

    // Congelamento do dia e das fornalhas durante a pausa (/pause e /aceitarpausa).
    private Boolean preFreezeDaylightCycle = null;
    private final Map<Location, Furnace> frozenFurnaces         = new HashMap<>();
    private final Map<Location, int[]>   frozenFurnaceSnapshot  = new HashMap<>(); // [burnTime, cookTime, cookTimeTotal]
    private BukkitTask furnaceFreezeTask;

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

    /** Avisa todos os OPs no chat que o jogo foi pausado (quem pausou + motivo). */
    private void avisarOpsPausa(String quemPausou, String motivo) {
        var config = Tlsplugin.getInstance().getConfig();
        if (!config.getBoolean("pausa.avisar_ops_no_chat", true)) return;

        String semMotivo = "§7(sem motivo)";
        String msg = config.getString("pausa.mensagem_aviso_ops",
                        "§f[§bTLS§f] §e⏸ §f{player} §epausou o jogo. §7Motivo§8: §f{motivo}")
                .replace("{player}", quemPausou)
                .replace("{motivo}", (motivo == null || motivo.isEmpty()) ? semMotivo : motivo);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    // ----------------------------------------------------------------
    //  Música
    // ----------------------------------------------------------------

    private BukkitTask musicLoopTask = null;

    private void startPauseMusic() {
        if (!Tlsplugin.getInstance().getConfig().getBoolean("pausa.musica_habilitar", true)) return;
        var som = Tlsplugin.getInstance().getSoundPreferenceManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) som.play(p, pauseMusic, 0.8f, 1.0f);
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
    private World getEventWorld() {
        return Tlsplugin.getInstance().getBorderManager().getTargetWorld();
    }

    /** Pausa sem indicar quem pausou (ex: recuperação de crash). */
    public void freezeAll() {
        freezeAll("Servidor", "");
    }

    /**
     * @param quemPausou nome de quem pausou (jogador ou "Servidor"), mostrado aos OPs
     * @param motivo     motivo opcional; vazio se não houver
     */
    public void freezeAll(String quemPausou, String motivo) {
        frozen = true;
        pauseWorldTicking();
        avisarOpsPausa(quemPausou, motivo);

        for (Entity e : getEventWorld().getEntities()) {
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
                    resumeWorldTicking();
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
        for (Entity e : getEventWorld().getEntities()) {
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
                    resumeWorldTicking();
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
    //     CONGELAR TEMPO/FORNALHAS (dia parado + fornalhas paradas)
    // ==========================================================

    /** Para o ciclo dia/noite e trava o progresso de todas as fornalhas do mundo do evento. */
    private void pauseWorldTicking() {
        World world = getEventWorld();

        preFreezeDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        freezeFurnaces(world);
    }

    /** Restaura o ciclo dia/noite anterior e liberta as fornalhas para continuarem normalmente. */
    private void resumeWorldTicking() {
        World world = getEventWorld();

        if (preFreezeDaylightCycle != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, preFreezeDaylightCycle);
            preFreezeDaylightCycle = null;
        }

        unfreezeFurnaces();
    }

    private static boolean isFurnaceMaterial(Material m) {
        return m == Material.FURNACE || m == Material.BLAST_FURNACE || m == Material.SMOKER;
    }

    /**
     * Não existe gamerule para "pausar" fornalhas — o burn/cook time delas avança sempre,
     * gamerule nenhum trava isso. A solução é capturar o estado (burnTime/cookTime) de todas
     * as fornalhas do mundo do evento ao pausar, e todos os ticks repor esses valores exatos
     * enquanto a pausa durar, para elas ficarem visivelmente congeladas. Ao despausar, paramos
     * de repor e elas continuam a arder a partir de onde ficaram.
     */
    private void freezeFurnaces(World world) {
        frozenFurnaces.clear();
        frozenFurnaceSnapshot.clear();

        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!isFurnaceMaterial(state.getType())) continue;
                if (!(state instanceof Furnace furnace)) continue;
                Location loc = furnace.getLocation();
                frozenFurnaces.put(loc, furnace);
                frozenFurnaceSnapshot.put(loc,
                        new int[]{furnace.getBurnTime(), furnace.getCookTime(), furnace.getCookTimeTotal()});
            }
        }

        if (furnaceFreezeTask != null) furnaceFreezeTask.cancel();
        furnaceFreezeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<Location, Furnace> entry : frozenFurnaces.entrySet()) {
                int[] snap = frozenFurnaceSnapshot.get(entry.getKey());
                if (snap == null) continue;
                Furnace furnace = entry.getValue();
                furnace.setBurnTime((short) snap[0]);
                furnace.setCookTime((short) snap[1]);
                furnace.setCookTimeTotal(snap[2]);
                furnace.update(true, false);
            }
        }, 0L, 1L);
    }

    private void unfreezeFurnaces() {
        if (furnaceFreezeTask != null) { furnaceFreezeTask.cancel(); furnaceFreezeTask = null; }
        frozenFurnaces.clear();
        frozenFurnaceSnapshot.clear();
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
        var som = Tlsplugin.getInstance().getSoundPreferenceManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            som.play(p, sound, 1.0f, pitch);
        }
    }

    private void playEnd(Sound sound) {
        var som = Tlsplugin.getInstance().getSoundPreferenceManager();
        for (Player p : Bukkit.getOnlinePlayers()) {
            som.play(p, sound, 1.0f, 1.0f);
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
    @EventHandler public void onHit(EntityDamageEvent e) {
        if (!frozen) return;
        // OPs podem bater livremente mesmo durante freeze
        if (e instanceof EntityDamageByEntityEvent ede && ede.getDamager() instanceof Player p && p.isOp()) return;
        e.setCancelled(true);
    }
    @EventHandler public void onTarget(EntityTargetEvent e)     { if (frozen) e.setCancelled(true); }
    @EventHandler public void onProj(ProjectileLaunchEvent e)   { if (frozen) e.setCancelled(true); }
}