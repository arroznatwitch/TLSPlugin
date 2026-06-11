package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.GameFreezeManager;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PvPListener implements Listener {

    private final Tlsplugin plugin;
    private BukkitTask updaterTask;
    private final GameFreezeManager freezeManager;
    private final MVPStatsManager mvpStatsManager;
    private final GrapplerItemListener grapplerListener;

    /**
     * Damage tracker para assists.
     * damageTracker[vítimaUUID][atacanteUUID] = timestamp do último hit (ms)
     */
    private final Map<UUID, Map<UUID, Long>> damageTracker = new HashMap<>();

    public PvPListener(Tlsplugin plugin, GameFreezeManager freezeManager, MVPStatsManager mvpStatsManager, GrapplerItemListener grapplerListener) {
        this.plugin = plugin;
        this.freezeManager = freezeManager;
        this.mvpStatsManager = mvpStatsManager;
        this.grapplerListener = grapplerListener;
    }

    // ---------------------------------------------------------------
    //  Damage tracker — público para o KillListener usar
    // ---------------------------------------------------------------

    /** Regista que [attackerUUID] deu dano a [victimUUID] agora. */
    public void trackDamage(UUID victimUUID, UUID attackerUUID) {
        damageTracker
                .computeIfAbsent(victimUUID, k -> new HashMap<>())
                .put(attackerUUID, System.currentTimeMillis());
    }

    /**
     * Devolve os UUIDs de jogadores que deram dano à vítima dentro da janela
     * de tempo configurada, excluindo o killer.
     * Limpa o tracker da vítima no final.
     */
    public java.util.Set<UUID> getAssistants(UUID victimUUID, UUID killerUUID) {
        Map<UUID, Long> hits = damageTracker.remove(victimUUID);
        java.util.Set<UUID> assistants = new java.util.HashSet<>();
        if (hits == null) return assistants;

        long windowMs = plugin.getConfig().getLong("assist_janela_segundos", 10L) * 1000L;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : hits.entrySet()) {
            UUID attacker = entry.getKey();
            long timestamp = entry.getValue();
            // Só conta se dentro da janela e não for o killer
            if (!attacker.equals(killerUUID) && (now - timestamp) <= windowMs) {
                assistants.add(attacker);
            }
        }
        return assistants;
    }

    /** Limpa o tracker de uma vítima (ex: quando morre sem killer). */
    public void clearTracker(UUID victimUUID) {
        damageTracker.remove(victimUUID);
    }

    // ---------------------------------------------------------------
    //  Eventos
    // ---------------------------------------------------------------

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        double amount = e.getFinalDamage();

        if (plugin.isLobbyWorld(p.getWorld())) return;
        if (freezeManager.isFrozen()) return;
        if (e.isCancelled()) return;

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && grapplerListener.isUsingGrappler(p.getUniqueId())) {
            return;
        }

        if (amount <= 0) return;

        int dec = plugin.getConfig().getInt("pvp_decimais", 1);
        String formatted = format(amount, dec);

        String tmpl = plugin.getConfig().getString("mensagens.pvp_dmg", "§c[PvP] {player} perdeu {value} de vida.");
        String msg = tmpl.replace("{player}", p.getName()).replace("{value}", formatted);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        if (e instanceof EntityDamageByEntityEvent dmgEvent) {
            if (dmgEvent.getDamager() instanceof Player damager) {
                mvpStatsManager.addDamageReceived(p.getName(), amount);
                mvpStatsManager.addDamageGiven(damager.getName(), amount);
                // Registar no tracker de assists
                trackDamage(p.getUniqueId(), damager.getUniqueId());
            }
        }

        updatePlayerListName(p);
    }

    @EventHandler
    public void onPlayerHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (plugin.isLobbyWorld(p.getWorld())) return;
        if (freezeManager.isFrozen()) return;
        double amount = e.getAmount();
        int dec = plugin.getConfig().getInt("pvp_decimais", 1);
        String formatted = format(amount, dec);

        String tmpl = plugin.getConfig().getString("mensagens.pvp_heal", "§a[PvP] {player} ganhou {value} de vida.");
        String msg = tmpl.replace("{player}", p.getName()).replace("{value}", formatted);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        updatePlayerListName(p);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerListName(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerListName(e.getPlayer()), 1L);
    }

    // ---------------------------------------------------------------
    //  Utilitários
    // ---------------------------------------------------------------

    private String format(double v, int dec) {
        double rounded = Math.round(v * 2) / 2.0;
        return String.format("%.1f", rounded);
    }

    public void updatePlayerListName(Player p) {
        double health = p.getHealth();
        double max = p.getMaxHealth();
        double perc = (max <= 0) ? 0 : (health / max);

        double greenThreshold  = plugin.getConfig().getDouble("tab.verde",   0.66);
        double yellowThreshold = plugin.getConfig().getDouble("tab.amarelo", 0.33);

        String color = "§a";
        if (perc <= yellowThreshold) color = "§c";
        else if (perc <= greenThreshold) color = "§e";

        String healthText = String.format("%.1f", health);
        String display = color + p.getName() + " §7[" + healthText + "❤]";
        p.setPlayerListName(display);
    }

    public void startUpdater() {
        if (updaterTask != null) return;
        updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updatePlayerListName(p);
            }
        }, 0L, 20L);
    }

    public void stopUpdater() {
        if (updaterTask != null) {
            updaterTask.cancel();
            updaterTask = null;
        }
    }
}