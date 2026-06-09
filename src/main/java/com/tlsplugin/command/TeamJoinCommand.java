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
            sender.sendMessage("Somente jogadores podem usar este comando.");
            return true;
        }

        if (args.length < 2) {
            p.sendMessage("§cUso: /teamjoin <jogador> <equipa>");
            return true;
        }

        String targetName = args[0];
        String team = args[1];

        // Se for outro jogador, só OP pode
        if (!targetName.equalsIgnoreCase(p.getName()) && !p.isOp()) {
            p.sendMessage("§cApenas OP pode adicionar outros jogadores a equipes.");
            return true;
        }

        // Executa o comando de equipa nativo do Minecraft
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "team join " + team + " " + targetName);

        // CORREÇÃO: Removido o "group." para o LuckPerms encontrar o grupo corretamente
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + targetName + " parent set " + team);

        if (targetName.equalsIgnoreCase(p.getName())) {
            p.sendMessage("§aVocê entrou na equipe " + team + " e recebeu o grupo.");
        } else {
            p.sendMessage("§aJogador " + targetName + " entrou na equipe " + team + " e recebeu o grupo.");
        }

        return true;
    }
}