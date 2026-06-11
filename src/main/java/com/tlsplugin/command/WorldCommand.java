package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WorldCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public WorldCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString(
                "mensagens_comandos.sem_permissao",
                "§f[§bTLS§f] §cNão tens permissão."));
            return true;
        }

        if (args.length == 0) {
            // Mostra o mundo atual
            String atual = plugin.getBorderManager().getTargetWorldName();
            sender.sendMessage("§8§m──────────────────────────────");
            sender.sendMessage("§b§lTLS §8▸ §fMundo Ativo");
            sender.sendMessage("§8§m──────────────────────────────");
            sender.sendMessage("§7Mundo§8: §b" + atual);
            sender.sendMessage(" ");
            sender.sendMessage("§7Mundos carregados§8:");
            for (World w : Bukkit.getWorlds()) {
                boolean isAtivo = w.getName().equals(atual);
                sender.sendMessage("§7  " + (isAtivo ? "§a▶ " : "§8- ") + w.getName());
            }
            sender.sendMessage("§8§m──────────────────────────────");
            sender.sendMessage("§7Uso§8: §b/tlsworld <nomeMundo>");
            return true;
        }

        String nomeMundo = args[0];
        World world = Bukkit.getWorld(nomeMundo);

        if (world == null) {
            sender.sendMessage("§f[§bTLS§f] §cMundo §b" + nomeMundo + " §cnão encontrado ou não está carregado.");
            sender.sendMessage("§f[§bTLS§f] §7Garante que o mundo está criado e carregado no Multiverse primeiro.");
            return true;
        }

        plugin.getBorderManager().setTargetWorld(world);
        sender.sendMessage("§f[§bTLS§f] §a✔ Mundo do TLS definido para §b" + world.getName() + "§a.");
        sender.sendMessage("§f[§bTLS§f] §7A borda, o scoreboard e os comandos de jogo irão agora operar neste mundo.");
        return true;
    }
}
