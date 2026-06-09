package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeathListener implements Listener {

    private final Tlsplugin plugin;
    private final MVPStatsManager mvpStatsManager;

    public static final String METADATA_BLOCO_KEY   = "TLS_BLOCO_REVIVE";
    public static final String METADATA_HOLOGRAM_KEY = "TLS_HOLOGRAM";

    private final Map<String, BukkitTask> activeTasks        = new HashMap<>();
    private final Map<String, Location>   deathBlockLocations = new HashMap<>();
    private final Map<String, ArmorStand> hologramStands      = new HashMap<>();

    public DeathListener(Tlsplugin plugin, MVPStatsManager mvpStatsManager) {
        this.plugin           = plugin;
        this.mvpStatsManager  = mvpStatsManager;
    }

    public void removeBody(String playerName) {
        Bukkit.getLogger().info("[TLS] Removendo corpo de: " + playerName);

        BukkitTask expireTask = activeTasks.remove(playerName);
        if (expireTask != null) expireTask.cancel();

        BukkitTask updateTask = activeTasks.remove(playerName + "_UPDATE");
        if (updateTask != null) updateTask.cancel();

        ArmorStand hologram = hologramStands.remove(playerName);
        if (hologram != null && !hologram.isDead()) hologram.remove();

        Location deathLoc = deathBlockLocations.remove(playerName);
        if (deathLoc != null) {
            Block block = deathLoc.getBlock();
            if (block.getType() == Material.SEA_LANTERN) {
                block.removeMetadata(METADATA_BLOCO_KEY, plugin);
                block.setType(Material.AIR);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p         = event.getEntity();
        Location deathLoc = p.getLocation().getBlock().getLocation();

        // 2. SEMPRE forçar espectador se configurado
        boolean forceSpectator = plugin.getConfig().getBoolean("revive.forcamorte_spectator", true);
        if (forceSpectator) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(deathLoc.clone().add(0.5, 1.1, 0.5));
                }
            }, 1L);
        }

        // 3. Sistema de Revive (Corpo/Holograma)
        if (!plugin.getConfig().getBoolean("revive.habilitar", true) ||
            !plugin.getConfig().getBoolean("revive.habilitar_corpo_revive", true)) {
            return;
        }

        int timeLimit = plugin.getConfig().getInt("revive.tempo_limite_revive_segundos", 300);

        event.setDroppedExp(0);
        removeBody(p.getName());

        Block deathBlock = deathLoc.getBlock();
        if (deathBlock.getType().isSolid()) {
            deathBlock = deathLoc.clone().add(0, 1, 0).getBlock();
        }

        deathBlock.setType(Material.SEA_LANTERN);
        deathBlock.setMetadata(METADATA_BLOCO_KEY, new FixedMetadataValue(plugin, p.getName()));
        deathBlockLocations.put(p.getName(), deathBlock.getLocation());

        ArmorStand hologramStand = (ArmorStand) deathBlock.getWorld().spawnEntity(
                deathBlock.getLocation().clone().add(0.5, 1.2, 0.5),
                EntityType.ARMOR_STAND
        );
        hologramStand.setGravity(false);
        hologramStand.setVisible(false);
        hologramStand.setCustomNameVisible(true);
        hologramStand.setMarker(true);
        hologramStand.setInvulnerable(true);
        hologramStands.put(p.getName(), hologramStand);

        long totalTicks = (long) timeLimit * 20L;

        BukkitTask updateTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            long remainingTicks = totalTicks;

            @Override
            public void run() {
                if (remainingTicks <= 0 || !hologramStand.isValid()) {
                    cancelSelf();
                    return;
                }
                remainingTicks -= 20;
                long sec = Math.max(0, remainingTicks / 20);
                long min = sec / 60;
                long s   = sec % 60;
                String linha1 = plugin.getConfig()
                        .getString("holograma.linha_1", "§6💀 {jogador} §c[Revivível]")
                        .replace("{jogador}", p.getName());
                String linha2 = plugin.getConfig()
                        .getString("holograma.linha_2", "§7Tempo restante: §e{minutos}m {segundos}s")
                        .replace("{minutos}", String.valueOf(min))
                        .replace("{segundos}", String.format("%02d", s));
                hologramStand.setCustomName(linha1 + " | " + linha2);
            }

            private void cancelSelf() {
                BukkitTask t = activeTasks.get(p.getName() + "_UPDATE");
                if (t != null) t.cancel();
            }
        }, 20L, 20L);
        activeTasks.put(p.getName() + "_UPDATE", updateTask);

        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeBody(p.getName());
            if (p.isOnline() && p.getGameMode() == GameMode.SPECTATOR) {
                p.sendMessage(plugin.getConfig().getString(
                        "mensagens_revive.revive_expirou_alvo",
                        "§cO tempo para te reanimar expirou!"));
            }
        }, totalTicks);
        activeTasks.put(p.getName(), expireTask);

        // 4. Mensagem de anúncio
        if (plugin.getConfig().getBoolean("mensagens_revive.anunciar_morte_chat", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                String msg = plugin.getConfig()
                        .getString("mensagens_revive.morte_anuncio_chat", "§c💀 {player} morreu!")
                        .replace("{player}", p.getName())
                        .replace("{tempo_limite}", String.valueOf(TimeUnit.SECONDS.toMinutes(timeLimit)))
                        .replace("{bloco_marcador}", "X/Z " + deathLoc.getBlockX() + "/" + deathLoc.getBlockZ());
                online.sendMessage(msg);
            }
        }
        String title    = plugin.getConfig().getString("mensagens_revive.morte_anuncio_title",    "MORTE");
        String subtitle = plugin.getConfig().getString("mensagens_revive.morte_anuncio_subtitle", "Aguarde reanimação!");
        p.sendTitle(title, subtitle, 10, 70, 20);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        // Não altera a localização de respawn — deixar o Minecraft gerir
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Block brokenBlock = e.getBlock();
        if (!brokenBlock.hasMetadata(METADATA_BLOCO_KEY)) return;

        // Bloco de revive é indestrutível — apenas cancela, NÃO contabiliza revival aqui
        // O revival é contabilizado no KitMedicRecipe.onPlayerReviveAttempt após sucesso
        e.setCancelled(true);
        String msg = plugin.getConfig().getString(
                "mensagens_revive.bloco_indestrutilvel", "§cNão podes destruir o corpo.");
        e.getPlayer().sendMessage(msg);
    }

    @EventHandler
    public void onSpectatorTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            if (p.getSpectatorTarget() instanceof Player target) {
                Team pt = p.getScoreboard().getEntryTeam(p.getName());
                Team tt = target.getScoreboard().getEntryTeam(target.getName());
                if (pt == null || tt == null || !pt.equals(tt)) {
                    e.setCancelled(true);
                    p.sendMessage("§cNão podes espiar jogadores de outras equipas.");
                }
            }
        }
    }
}
