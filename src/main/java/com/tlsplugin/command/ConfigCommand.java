package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.gui.ConfigGui;
import com.tlsplugin.gui.ConfigGuiListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ConfigCommand implements CommandExecutor {

    private final Tlsplugin        plugin;
    private final ConfigGui        configGui;
    private final ConfigGuiListener listener;

    public ConfigCommand(Tlsplugin plugin, ConfigGui configGui, ConfigGuiListener listener) {
        this.plugin    = plugin;
        this.configGui = configGui;
        this.listener  = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfig().getString(
                "mensagens_comandos.sem_permissao",
                "§f[§bTLS§f] §cApenas jogadores podem usar este comando."));
            return true;
        }

        if (!player.isOp() && !player.hasPermission("tls.admin")) {
            player.sendMessage(plugin.getConfig().getString(
                "mensagens_comandos.sem_permissao",
                "§f[§bTLS§f] §cNão tens permissão para executar este comando."));
            return true;
        }

        // Clear navigation history and open fresh main menu
        listener.clearStack(player.getUniqueId());
        configGui.openMain(player);
        return true;
    }
}
