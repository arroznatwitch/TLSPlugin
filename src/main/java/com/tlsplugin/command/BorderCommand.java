package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class BorderCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public BorderCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        // define a borda para o primeiro valor do config (inicial)
        plugin.getBorderManager().setToInitial();
        sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.borda_setada", "§eBorda setada."));
        return true;
    }
}
