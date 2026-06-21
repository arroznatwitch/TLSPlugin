package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EndGameCommand implements CommandExecutor {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;



    public EndGameCommand(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        String confirmarPalavra = plugin.getConfig().getString(
                "mensagens_comandos.endgame_palavra_confirmar", "confirmar");

        if (args.length == 0 || !args[0].equalsIgnoreCase(confirmarPalavra)) {
            sender.sendMessage("");
            sender.sendMessage("§c§l  ⚠ Terminar jogo");
            sender.sendMessage("");
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.endgame_confirmar",
                    "  §cTens a certeza? Escreve §f/endgame confirmar §cpara terminar."));
            sender.sendMessage("");
            return true;
        }

        borderManager.stopAll();
        plugin.getBorderTimerAnnouncer().stop();

        if (plugin.getMVPStatsManager() != null) {
            plugin.getMVPStatsManager().stopTracking();
            plugin.getMVPStatsManager().backupStats();
            plugin.getMVPStatsManager().saveStats();
        }

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);
        }

        Tlsplugin.broadcast("");
        Tlsplugin.broadcast(plugin.getConfig().getString(
                "mensagens_comandos.jogo_terminado",
                "§f[§bTLS§f] §c§lO jogo foi terminado pelo administrador!"));
        Tlsplugin.broadcast("");
        return true;
    }
}