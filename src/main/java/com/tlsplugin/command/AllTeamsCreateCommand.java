package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.TeamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AllTeamsCreateCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public AllTeamsCreateCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Tlsplugin.getInstance().getConfig()
                    .getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        TeamManager teamManager = plugin.getTeamManager();
        int[] resultado = teamManager.ensureTeamsExist();

        sender.sendMessage("");
        sender.sendMessage("§b§l  Equipas criadas");
        sender.sendMessage("");
        sender.sendMessage("  §7Criadas:          §a" + resultado[0]);
        sender.sendMessage("  §7Já existiam (cor/ícone reaplicados): §e" + resultado[1]);
        sender.sendMessage("");
        return true;
    }
}
