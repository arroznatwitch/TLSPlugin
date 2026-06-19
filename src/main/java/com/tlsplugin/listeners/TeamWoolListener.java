package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.TeamWoolItem;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public class TeamWoolListener implements Listener {

    private final Tlsplugin plugin;

    public TeamWoolListener(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        String teamId = TeamWoolItem.getTeamId(item);
        if (teamId == null) return;

        e.setCancelled(true);
        Player player = e.getPlayer();

        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            player.sendMessage(plugin.getConfig().getString(
                    "teams.mensagem_sem_luckperms", "§cLuckPerms não encontrado."));
            return;
        }

        LuckPerms lp = provider.getProvider();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("teams.items." + teamId);
        String groupName = sec != null ? sec.getString("group", teamId) : teamId;

        lp.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            // Verificar se já está na equipa
            boolean jaEsta = user.getNodes(NodeType.INHERITANCE)
                    .stream()
                    .anyMatch(n -> n.getGroupName().equalsIgnoreCase(groupName));

            if (jaEsta) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(plugin.getConfig()
                            .getString("teams.mensagem_ja_na_equipa", "§eJá fazes parte desta equipa.")
                            .replace("{equipa}", groupName)));
                return;
            }

            // Remover das outras equipas se configurado
            if (plugin.getConfig().getBoolean("teams.remover_outras_equipas", true)) {
                for (String other : TeamWoolItem.allTeams()) {
                    ConfigurationSection otherSec = plugin.getConfig()
                            .getConfigurationSection("teams.items." + other);
                    if (otherSec == null) continue;
                    String otherGroup = otherSec.getString("group", other);
                    user.data().remove(InheritanceNode.builder(otherGroup).build());
                }
            }

            // Adicionar à nova equipa
            user.data().add(InheritanceNode.builder(groupName).build());
            lp.getUserManager().saveUser(user);

            String display = sec != null ? sec.getString("display_name", groupName) : groupName;
            String displayClean = display.replaceAll("§.", "").trim();
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Adicionar à scoreboard team
                org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

                // Remover das outras teams
                for (String other : TeamWoolItem.allTeams()) {
                    org.bukkit.scoreboard.Team otherTeam = sb.getTeam(other);
                    if (otherTeam != null) otherTeam.removeEntry(player.getName());
                }

                // Adicionar à nova team
                org.bukkit.scoreboard.Team sbTeam = sb.getTeam(groupName);
                if (sbTeam != null) {
                    sbTeam.addEntry(player.getName());
                }

                player.sendMessage(plugin.getConfig()
                        .getString("teams.mensagem_equipa", "§aJuntaste-te à equipa §b{equipa}§a!")
                        .replace("{equipa}", displayClean));
            });
        });
    }
}
