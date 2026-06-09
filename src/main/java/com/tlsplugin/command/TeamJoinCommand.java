package com.tlsplugin.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamJoinCommand implements CommandExecutor {



    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("");
            sender.sendMessage("§c§l  Uso incorrecto");
            sender.sendMessage("");
            sender.sendMessage("  §7Uso: §f/teamjoin <jogador> <equipa>");
            sender.sendMessage("");
            return true;
        }

        String targetName = args[0];
        String team       = args[1];

        if (!targetName.equalsIgnoreCase(p.getName()) && !p.isOp()) {
            sender.sendMessage(com.tlsplugin.Tlsplugin.getInstance().getConfig()
                    .getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "team join " + team + " " + targetName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetName + " parent set " + team);

        sender.sendMessage("");
        sender.sendMessage("§b§l  Equipa actualizada");
        sender.sendMessage("");
        if (targetName.equalsIgnoreCase(p.getName())) {
            sender.sendMessage("  §7Entraste na equipa: §f" + team);
        } else {
            sender.sendMessage("  §7Jogador: §f" + targetName);
            sender.sendMessage("  §7Equipa:  §f" + team);
        }
        sender.sendMessage("");
        return true;
    }
}