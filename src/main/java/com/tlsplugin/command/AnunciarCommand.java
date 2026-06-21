package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AnunciarCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public AnunciarCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            String semPerm = plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao",
                    "§f[§bTLS§f] §cNão tens permissão para executar este comando.");
            sender.sendMessage(semPerm);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUso: /anunciar <mensagem>");
            return true;
        }

        String mensagem = String.join(" ", args);

        String separador = plugin.getConfig().getString("anunciar.separador",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String prefixoAnuncio = plugin.getConfig().getString("anunciar.prefixo",
                "§b§l[TLS ANÚNCIO] §f§l");
        String linhaVazia = "";

        String anuncio = separador + "\n"
                + linhaVazia + "\n"
                + linhaVazia + "\n"
                + prefixoAnuncio + mensagem + "\n"
                + linhaVazia + "\n"
                + linhaVazia + "\n"
                + separador;

        Tlsplugin.broadcast(org.bukkit.ChatColor.translateAlternateColorCodes('&', anuncio));
        return true;
    }
}