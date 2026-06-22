package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.SignManager;
import com.tlsplugin.manager.SignManager.SignData;
import com.tlsplugin.manager.SpawnManager;
import com.tlsplugin.utils.TeamWoolItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TlsSignCommand implements CommandExecutor {

    private final Tlsplugin    plugin;
    private final SignManager  signManager;
    private final SpawnManager spawnManager;

    public TlsSignCommand(Tlsplugin plugin, SignManager signManager, SpawnManager spawnManager) {
        this.plugin       = plugin;
        this.signManager  = signManager;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(prefix() + "§cSem permissão.");
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        // Atalho: /tlssign <cor> → liga a placa que estás a olhar ao spawn dessa equipa.
        if (args.length == 1 && isTeamColor(args[0])) {
            handleLink(sender, args[0]);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set"    -> handleSet(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list"   -> handleList(sender);
            case "info"   -> handleInfo(sender, args);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // ── /tlssign <cor> ───────────────────────────────────────────────────────
    // Atalho que liga a placa olhada ao spawn da equipa (que o /tlscapsulas ou
    // o /tls setspawn já definiram no centro da cápsula). Equivale a:
    //   /tlssign set spawn_<cor> tls.team.<cor> tlspawn

    private void handleLink(CommandSender sender, String color) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cApenas jogadores podem registar placas (tem de estar a olhar para a placa).");
            return;
        }
        color = color.toLowerCase();

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !targetBlock.getType().name().contains("SIGN")) {
            sender.sendMessage(prefix() + "§cOlha para uma placa (distância máx. 5 blocos).");
            return;
        }

        String id   = "spawn_" + color;
        String perm = "tls.team." + color;
        String cmd  = "tlspawn";

        if (!spawnManager.hasSpawn(color)) {
            sender.sendMessage(prefix() + "§e⚠ A equipa §b" + color + " §eainda não tem spawn definido.");
            sender.sendMessage(prefix() + "§7Corre §b/tlscapsulas <world> §7ou §b/tls setspawn " + color + " §7para o definir.");
            sender.sendMessage(prefix() + "§7A placa fica registada na mesma e funcionará assim que o spawn existir.");
        }

        Location loc = targetBlock.getLocation();
        signManager.addSign(id, loc, perm,
                Collections.singletonList(cmd), Collections.emptyList(),
                "§cNão tens permissão para usar esta placa.");

        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage(prefix() + "§a✔ Placa ligada à equipa §b" + color + "§a!");
        sender.sendMessage("§7ID§8:          §b" + id);
        sender.sendMessage("§7Permissão§8:   §b" + perm);
        sender.sendMessage("§7Comando§8:     §btlspawn §7(leva ao spawn da própria equipa)");
        sender.sendMessage("§7Localização§8: §e" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " §7(" + loc.getWorld().getName() + ")");
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ── /tlssign set <id> <permissão> <comando...> ───────────────────────────
    // Exemplo: /tlssign set spawn_red tls.team.red tlspawn
    // Para comando de consola: /tlssign set spawn_red tls.team.red !tls tp red

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cApenas jogadores podem registar placas (tem de estar junto à placa).");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(prefix() + "§cUso: §b/tlssign set <id> <permissão> <comando...>");
            sender.sendMessage(prefix() + "§7Prefixo §b!§7 no comando = corre como consola. Ex: §b!tls tp red");
            sender.sendMessage(prefix() + "§7Usa §b.§7 como permissão para nenhuma restrição.");
            return;
        }

        String id         = args[1].toLowerCase();
        String permission = args[2].equals(".") ? "" : args[2];
        String rawCmd     = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !targetBlock.getType().name().contains("SIGN")) {
            sender.sendMessage(prefix() + "§cOlha para uma placa (distância máx. 5 blocos).");
            return;
        }

        List<String> playerCmds  = Collections.emptyList();
        List<String> consoleCmds = Collections.emptyList();

        if (rawCmd.startsWith("!")) {
            consoleCmds = Collections.singletonList(rawCmd.substring(1).trim());
        } else {
            playerCmds = Collections.singletonList(rawCmd);
        }

        Location loc = targetBlock.getLocation();
        signManager.addSign(id, loc, permission, playerCmds, consoleCmds,
                "§cNão tens permissão para usar esta placa.");

        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage(prefix() + "§a✔ Placa registada!");
        sender.sendMessage("§7ID§8:          §b" + id);
        sender.sendMessage("§7Permissão§8:   §b" + (permission.isEmpty() ? "(sem restrição)" : permission));
        sender.sendMessage("§7Comando§8:     §b" + rawCmd);
        sender.sendMessage("§7Localização§8: §e" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                + " §7(" + loc.getWorld().getName() + ")");
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ── /tlssign remove <id> ─────────────────────────────────────────────────

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tlssign remove <id>");
            return;
        }
        String id = args[1].toLowerCase();
        if (signManager.removeSign(id)) {
            sender.sendMessage(prefix() + "§a✔ Placa §b" + id + " §aremovida.");
        } else {
            sender.sendMessage(prefix() + "§cPlaca §b" + id + " §cnão encontrada.");
        }
    }

    // ── /tlssign list ─────────────────────────────────────────────────────────

    private void handleList(CommandSender sender) {
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§b§lPlacas TLS registadas");
        sender.sendMessage("§8§m──────────────────────────────");
        if (signManager.getAllSigns().isEmpty()) {
            sender.sendMessage("§7Nenhuma placa registada ainda.");
            sender.sendMessage("§7Usa §b/tlssign set §7para registar.");
        } else {
            for (SignData d : signManager.getAllSigns()) {
                String perm = d.permission.isEmpty() ? "§7(sem restrição)" : "§b" + d.permission;
                sender.sendMessage("§7▸ §b" + d.id + " §8│ " + perm
                        + " §8│ §7" + d.world + " §8[§e" + d.x + "§8, §e" + d.y + "§8, §e" + d.z + "§8]");
            }
        }
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ── /tlssign info <id> ───────────────────────────────────────────────────

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tlssign info <id>");
            return;
        }
        SignData d = signManager.getSign(args[1]);
        if (d == null) {
            sender.sendMessage(prefix() + "§cPlaca §b" + args[1] + " §cnão encontrada.");
            return;
        }
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§b§lPlaca: §f" + d.id);
        sender.sendMessage("§7Mundo§8:           §b" + d.world);
        sender.sendMessage("§7Posição§8:         §e" + d.x + " " + d.y + " " + d.z);
        sender.sendMessage("§7Permissão§8:       §b" + (d.permission.isEmpty() ? "(sem restrição)" : d.permission));
        if (!d.commands.isEmpty())
            sender.sendMessage("§7Comandos§8:        §f" + String.join("§8, §f", d.commands));
        if (!d.consoleCommands.isEmpty())
            sender.sendMessage("§7Cmds consola§8:    §f" + String.join("§8, §f", d.consoleCommands));
        sender.sendMessage("§7Msg sem perm§8:    §c" + d.noPermMessage);
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§b§lTLS §8▸ §fGestão de Placas");
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§b/tlssign §e<cor>                 §8▸ §7Liga a placa ao spawn da equipa");
        sender.sendMessage("§b/tlssign set §e<id> <perm> <cmd> §8▸ §7Olha para a placa e regista");
        sender.sendMessage("§b/tlssign remove §e<id>           §8▸ §7Remove uma placa");
        sender.sendMessage("§b/tlssign list                    §8▸ §7Lista todas as placas");
        sender.sendMessage("§b/tlssign info §e<id>             §8▸ §7Detalhes de uma placa");
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§7Cores§8: §bred §8| §bblue §8| §bgreen §8| §byellow §8| §bpink §8| §bgrey §8| §bpurple §8| §borange");
        sender.sendMessage("§7Prefixo §b!§7 no comando = consola. Ex: §b!tls tp red");
        sender.sendMessage("§7Usa §b.§7 como permissão = sem restrição.");
    }

    private boolean isTeamColor(String s) {
        for (String t : TeamWoolItem.allTeams()) {
            if (t.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private String prefix() { return "§f[§bTLS§f] "; }
}
