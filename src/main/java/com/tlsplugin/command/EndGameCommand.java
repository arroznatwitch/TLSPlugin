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
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        String confirmarPalavra = plugin.getConfig().getString("mensagens_comandos.endgame_palavra_confirmar", "confirmar");
        if (args.length == 0 || !args[0].equalsIgnoreCase(confirmarPalavra)) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.endgame_confirmar",
                    "§c⚠ Tens a certeza? Escreve §f/endgame confirmar §cpara terminar o jogo."));
            return true;
        }

        // Parar borda
        borderManager.stopAll();

        // Parar tracking mas NÃO apagar os dados — fazer backup e manter para consulta
        if (plugin.getMVPStatsManager() != null) {
            plugin.getMVPStatsManager().stopTracking();
            plugin.getMVPStatsManager().backupStats(); // Guardar backup com timestamp
            plugin.getMVPStatsManager().saveStats();   // Manter dados actuais acessíveis
            // NÃO chamar resetAll() — os dados do /mvp e /sobremvp ficam disponíveis
        }

        String msg = plugin.getConfig().getString("mensagens_comandos.jogo_terminado", "§c[TLS] O jogo foi terminado!");
        Bukkit.broadcastMessage(msg);
        return true;
    }
}