package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BorderCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public BorderCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        plugin.getBorderManager().setToInitial();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) continue;
            p.setGameMode(GameMode.ADVENTURE);
        }

        sender.sendMessage("");
        sender.sendMessage("§b§l  Borda configurada");
        sender.sendMessage("");
        sender.sendMessage(plugin.getConfig().getString(
                "mensagens_comandos.borda_setada",
                "  §7A borda inicial foi definida com sucesso."));
        sender.sendMessage("");
        return true;
    }
}