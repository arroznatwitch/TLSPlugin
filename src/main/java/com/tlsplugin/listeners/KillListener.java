package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderManager;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Set;
import java.util.UUID;

public class KillListener implements Listener {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;
    private final MVPStatsManager mvpStatsManager;
    private final PvPListener pvpListener;

    public KillListener(Tlsplugin plugin, BorderManager borderManager, MVPStatsManager mvpStatsManager, PvPListener pvpListener) {
        this.plugin          = plugin;
        this.borderManager   = borderManager;
        this.mvpStatsManager = mvpStatsManager;
        this.pvpListener     = pvpListener;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // 1. Morte da vítima
        mvpStatsManager.addDeath(victim.getName());

        if (killer != null) {
            // 2. Kill
            mvpStatsManager.addKill(killer.getName());

            // 3. Assists — quem deu dano à vítima na janela de tempo, excluindo o killer
            Set<UUID> assistants = pvpListener.getAssistants(victim.getUniqueId(), killer.getUniqueId());
            for (UUID assistUUID : assistants) {
                Player assistPlayer = Bukkit.getPlayer(assistUUID);
                if (assistPlayer != null) {
                    mvpStatsManager.addAssist(assistPlayer.getName());

                    // Notificar o jogador que recebeu assist
                    String assistMsg = plugin.getConfig().getString(
                            "mensagens.assist_recebido",
                            "§e[ASSIST] Recebeste um assist na morte de {victim}!");
                    assistPlayer.sendMessage(assistMsg.replace("{victim}", victim.getName()));
                } else {
                    // Jogador offline — usar nome pelo UUID se possível
                    String name = Bukkit.getOfflinePlayer(assistUUID).getName();
                    if (name != null) mvpStatsManager.addAssist(name);
                }
            }

            // 4. Curar o killer
            double healAmount = plugin.getConfig().getDouble("heal_on_kill", 2.0);
            double max = killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                    ? killer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                    : killer.getMaxHealth();
            killer.setHealth(Math.min(max, killer.getHealth() + healAmount));

            // 5. Cabeça da vítima
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(victim);
            meta.setDisplayName("Cabeça de " + victim.getName());
            head.setItemMeta(meta);
            killer.getWorld().dropItemNaturally(killer.getLocation(), head);

            // 6. Mensagem global de kill
            String killMsg = plugin.getConfig().getString(
                    "mensagens.kill_ganhou",
                    "§6[KILL] {killer} matou {victim} e ganhou +{value} de vida e uma cabeça.");
            killMsg = killMsg.replace("{killer}", killer.getName())
                    .replace("{victim}", victim.getName())
                    .replace("{value}", String.valueOf(healAmount));

            // Adicionar assists à mensagem se houver
            if (!assistants.isEmpty()) {
                StringBuilder assistNames = new StringBuilder();
                for (UUID uuid : assistants) {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name != null) {
                        if (assistNames.length() > 0) assistNames.append(", ");
                        assistNames.append(name);
                    }
                }
                if (assistNames.length() > 0) {
                    String assistSuffix = plugin.getConfig().getString(
                            "mensagens.kill_assist_sufixo",
                            " §7(Assist: {assistants})");
                    killMsg += assistSuffix.replace("{assistants}", assistNames.toString());
                }
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(killMsg);
            }

        } else {
            // Morte sem killer — limpar tracker
            pvpListener.clearTracker(victim.getUniqueId());
        }
    }
}