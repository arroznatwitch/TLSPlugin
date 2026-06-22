package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.CapsuleManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /tlscapsulas [world] [modo]
 *
 * <p>Sem argumentos: usa o mundo do evento (BorderManager) e o modo ativo no config.</p>
 * <p>Com argumentos: {@code /tlscapsulas <world>} ou {@code /tlscapsulas <world> <teste|final>}</p>
 */
public class TlsCapsulasCommand implements CommandExecutor {

    private final Tlsplugin      plugin;
    private final CapsuleManager capsuleManager;

    public TlsCapsulasCommand(Tlsplugin plugin, CapsuleManager capsuleManager) {
        this.plugin         = plugin;
        this.capsuleManager = capsuleManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§f[§bTLS§f] §cNão tens permissão."));
            return true;
        }

        // Resolver mundo
        World world;
        if (args.length >= 1) {
            world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage(prefix() + "§cMundo §b" + args[0] + " §cnão encontrado ou não carregado.");
                sender.sendMessage(prefix() + "§7Garante que o mundo está criado e carregado (Multiverse) primeiro.");
                return true;
            }
        } else {
            world = plugin.getBorderManager().getTargetWorld();
            if (world == null) {
                sender.sendMessage(prefix() + "§cNenhum mundo de evento ativo. Define com §b/tlsworld <world>§c ou indica o mundo: §b/tlscapsulas <world>");
                return true;
            }
        }

        // Resolver modo
        String modo;
        if (args.length >= 2) {
            modo = args[1].toLowerCase();
            if (!modo.equals("teste") && !modo.equals("final")) {
                sender.sendMessage(prefix() + "§cModo inválido. Usa §bteste §cou §bfinal§c.");
                return true;
            }
        } else {
            modo = plugin.getConfig().getString("modo_jogo", "final");
        }

        CapsuleManager.Result r = capsuleManager.generate(world, modo);

        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage(prefix() + "§a✔ Cápsulas geradas!");
        sender.sendMessage("§7Mundo§8:    §b" + world.getName());
        sender.sendMessage("§7Modo§8:     §b" + r.modo);
        sender.sendMessage("§7Frente§8:   §b" + r.frente);
        sender.sendMessage("§7Cápsulas§8: §a" + r.capsulas + " §7│ §7Spawns§8: §a" + r.capsulas);
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§7Spawns configurados. Liga as placas no Spawn com §b/tlssign <cor>§7.");
        return true;
    }

    private String prefix() { return "§f[§bTLS§f] "; }
}
