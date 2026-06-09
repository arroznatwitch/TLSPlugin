package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final Tlsplugin plugin;



    public ReloadCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        plugin.reloadAllConfigs();
        plugin.getBorderManager().applyGameRulesForStage(
                plugin.getBorderManager().getCurrentStage());

        sender.sendMessage("");
        sender.sendMessage("§b§l  ↺ Configuração recarregada");
        sender.sendMessage("");
        sender.sendMessage("  " + plugin.getConfig().getString(
                "mensagens_comandos.reloadead", "§aConfiguração recarregada com sucesso!"));
        sender.sendMessage("  §7Recarregado por: §f" + sender.getName());
        sender.sendMessage("");

        plugin.getLogger().info("Configuração recarregada por " + sender.getName());
        return true;
    }
}