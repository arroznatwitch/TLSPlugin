package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BorderCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public BorderCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    private static final String SEP = "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        plugin.getBorderManager().setToInitial();

        sender.sendMessage(SEP);
        sender.sendMessage("§b§l  Borda configurada");
        sender.sendMessage(SEP);
        sender.sendMessage(plugin.getConfig().getString(
                "mensagens_comandos.borda_setada",
                "  §7A borda inicial foi definida com sucesso."));
        sender.sendMessage(SEP);
        return true;
    }
}