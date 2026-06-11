package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.TeamWoolItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class TeamsCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public TeamsCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        int dados = 0;
        for (String teamId : TeamWoolItem.allTeams()) {
            ItemStack wool = TeamWoolItem.create(teamId);
            if (wool != null) {
                player.getInventory().addItem(wool);
                dados++;
            }
        }

        player.sendMessage("§8§m──────────────────────────────");
        player.sendMessage("§f[§bTLS§f] §a✔ Recebeste §b" + dados + " §ablocos de equipa!");
        player.sendMessage("§7Dá aos jogadores para escolherem a sua equipa.");
        player.sendMessage("§8§m──────────────────────────────");
        return true;
    }
}
