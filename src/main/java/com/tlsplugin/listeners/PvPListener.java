package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.GameFreezeManager;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
    //  PvP gate — AUTORIDADE FINAL sobre o PvP (ganha à flag do mundo e ao Multiverse)
    // ---------------------------------------------------------------

    /**
     * Decide, a cada golpe entre jogadores, se o dano conta ou não — independentemente
     * da flag de PvP do mundo (que fica sempre true) e do que o Multiverse fizer.
     * Corre a HIGHEST para ter a última palavra: o que ficar decidido aqui é o que vale.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPvPDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(e);
        if (attacker == null || attacker.equals(victim)) return; // PvE ou auto-dano: não mexe

        // No lobby nunca há PvP
        if (plugin.isLobbyWorld(victim.getWorld())) { e.setCancelled(true); return; }

        boolean pvpAllowed = plugin.getBorderManager().isPvPAllowed()
                && !freezeManager.isFrozen();

        e.setCancelled(!pvpAllowed);
    }

    /** Resolve o jogador atacante: golpe direto ou projétil (flecha/tridente) lançado por um jogador. */
    private Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    /** True se for dano entre jogadores mas o PvP ainda não está permitido (1ª borda / freeze). */
    private boolean isBlockedPvP(EntityDamageEvent e, Player victim) {
        if (!(e instanceof EntityDamageByEntityEvent ev)) return false;
        Player attacker = resolveAttacker(ev);
        if (attacker == null || attacker.equals(victim)) return false;
        return !plugin.getBorderManager().isPvPAllowed();
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

        // Dano entre jogadores antes do PvP estar permitido: ignora tudo (nem mensagem, nem stats).
        // O cancelamento do dano em si é tratado no onPvPDamage (HIGHEST).
        if (isBlockedPvP(e, p)) return;

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
        // Não notificar valores irrelevantes (0.0, 0.5, etc.) ou se já está full vida
        if (amount <= 0.5) return;
        double maxHealth = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (p.getHealth() >= maxHealth) return;
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