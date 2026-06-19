package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public class TlsCommand implements CommandExecutor {

    private final Tlsplugin    plugin;
    private final SpawnManager spawnManager;

    public TlsCommand(Tlsplugin plugin, SpawnManager spawnManager) {
        this.plugin       = plugin;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(noPerms());
            return true;
        }

        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "setspawn"  -> handleSetSpawn(sender, args);
            case "tp"        -> handleTp(sender, args);
            case "tpspawn"   -> handleTpSpawn(sender, args);
            case "spawns"    -> handleListSpawns(sender);
            case "delspawn"  -> handleDelSpawn(sender, args);
            default          -> sendHelp(sender);
        }
        return true;
    }

    // ─── /tls setspawn <equipa> [x y z [yaw pitch]] ──────────────────────────

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tls setspawn <equipa> §7[x y z] [yaw pitch]");
            return;
        }

        String team = args[1].toLowerCase();
        Location loc;

        if (args.length >= 5) {
            double x, y, z;
            float yaw = 0, pitch = 0;
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
                if (args.length >= 6) yaw   = Float.parseFloat(args[5]);
                if (args.length >= 7) pitch = Float.parseFloat(args[6]);
            } catch (NumberFormatException e) {
                sender.sendMessage(prefix() + "§cCoordenadas inválidas. Usa números (ex: 100.5 64 -200).");
                return;
            }

            World world = resolveWorld(sender);
            if (world == null) {
                sender.sendMessage(prefix() + "§cNão foi possível determinar o mundo. Define o mundo com §b/tlsworld§c primeiro.");
                return;
            }
            loc = new Location(world, x, y, z, yaw, pitch);

        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix() + "§cA consola precisa de especificar coordenadas: §b/tls setspawn <equipa> <x> <y> <z>");
                return;
            }
            loc = player.getLocation();
        }

        spawnManager.setSpawn(team, loc);

        sender.sendMessage(" ");
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage(prefix() + "§a✔ Spawn definido!");
        sender.sendMessage("§7Equipa§8: §b" + team);
        sender.sendMessage("§7Mundo§8:  §b" + loc.getWorld().getName());
        sender.sendMessage("§7X§8: §e" + String.format("%.1f", loc.getX()) +
                " §7Y§8: §e" + String.format("%.1f", loc.getY()) +
                " §7Z§8: §e" + String.format("%.1f", loc.getZ()));
        sender.sendMessage("§7Yaw§8: §e" + String.format("%.1f", loc.getYaw()) +
                " §7Pitch§8: §e" + String.format("%.1f", loc.getPitch()));
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ─── /tls tp <equipa> ────────────────────────────────────────────────────

    private void handleTp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tls tp <equipa>");
            return;
        }

        String team = args[1].toLowerCase();

        if (!spawnManager.hasSpawn(team)) {
            sender.sendMessage(prefix() + "§cNão existe spawn definido para a equipa §b" + team +
                    "§c. Usa §b/tls setspawn§c primeiro.");
            return;
        }

        Location spawn = spawnManager.getSpawn(team);

        if (spawn.getWorld() == null) {
            sender.sendMessage(prefix() + "§cO mundo do spawn da equipa §b" + team + " §cnão está carregado.");
            return;
        }

        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team sbTeam   = sb.getTeam(team);

        if (sbTeam == null) {
            sender.sendMessage(prefix() + "§cEquipa §b" + team + " §cnão existe na scoreboard.");
            return;
        }

        List<Player> teleported = new ArrayList<>();
        for (String entry : sbTeam.getEntries()) {
            Player p = Bukkit.getPlayer(entry);
            if (p != null && p.isOnline()) {
                p.teleport(spawn);
                p.sendMessage(prefix() + "§aTeleportado para o spawn da equipa §b" + team + "§a.");
                teleported.add(p);
            }
        }

        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage(prefix() + "§a✔ Equipa §b" + team + " §ateleportada!");
        sender.sendMessage("§7Jogadores teleportados§8: §b" + teleported.size());
        if (!teleported.isEmpty()) {
            sender.sendMessage("§7Nomes§8: §f" + teleported.stream()
                    .map(Player::getName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ─── /tls tpspawn <equipa> ───────────────────────────────────────────────
    // Teleporta apenas o jogador que executa para o spawn da equipa indicada.
    // Útil para OPs sem equipa usarem as placas de spawn.

    private void handleTpSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cApenas jogadores podem usar este comando.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tls tpspawn <equipa>");
            return;
        }

        String team = args[1].toLowerCase();

        if (!spawnManager.hasSpawn(team)) {
            sender.sendMessage(prefix() + "§cNão existe spawn definido para a equipa §b" + team + "§c.");
            return;
        }

        Location spawn = spawnManager.getSpawn(team);

        if (spawn.getWorld() == null) {
            sender.sendMessage(prefix() + "§cO mundo do spawn da equipa §b" + team + " §cnão está carregado.");
            return;
        }

        player.teleport(spawn);
        player.sendMessage(prefix() + "§aTeleportado para o spawn da equipa §b" + team + "§a.");
    }

    // ─── /tls spawns ─────────────────────────────────────────────────────────

    private void handleListSpawns(CommandSender sender) {
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§b§lSpawns de Equipas");
        sender.sendMessage("§8§m──────────────────────────────");

        if (spawnManager.getAllTeams().isEmpty()) {
            sender.sendMessage("§7Nenhum spawn definido ainda.");
            sender.sendMessage("§7Usa §b/tls setspawn <equipa>§7.");
        } else {
            for (String team : spawnManager.getAllTeams()) {
                Location loc = spawnManager.getSpawn(team);
                String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "§c(mundo não carregado)";
                sender.sendMessage("§7▸ §b" + team + " §8→ §7" + worldName +
                        " §8[§e" + String.format("%.0f", loc.getX()) +
                        "§8, §e" + String.format("%.0f", loc.getY()) +
                        "§8, §e" + String.format("%.0f", loc.getZ()) + "§8]");
            }
        }
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // ─── /tls delspawn <equipa> ───────────────────────────────────────────────

    private void handleDelSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUso: §b/tls delspawn <equipa>");
            return;
        }
        String team = args[1].toLowerCase();
        if (!spawnManager.hasSpawn(team)) {
            sender.sendMessage(prefix() + "§cA equipa §b" + team + " §cnão tem spawn definido.");
            return;
        }
        spawnManager.removeSpawn(team);
        sender.sendMessage(prefix() + "§a✔ Spawn da equipa §b" + team + " §aremovido.");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§b§lTLS §8▸ §fComandos de Spawn");
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§b/tls setspawn §e<equipa>            §8▸ §7Spawn na tua posição");
        sender.sendMessage("§b/tls setspawn §e<equipa> <x> <y> <z>§8▸ §7Spawn em coords específicas");
        sender.sendMessage("§b/tls tp §e<equipa>                  §8▸ §7Teleporta equipa para o spawn");
        sender.sendMessage("§b/tls tpspawn §e<equipa>             §8▸ §7Teleporta-te para o spawn da equipa");
        sender.sendMessage("§b/tls spawns                        §8▸ §7Lista todos os spawns");
        sender.sendMessage("§b/tls delspawn §e<equipa>            §8▸ §7Remove spawn de equipa");
        sender.sendMessage("§8§m────────────────────────────────────────");
        sender.sendMessage("§7Equipas§8: §bred §8| §bblue §8| §bgreen §8| §byellow §8| §bpink §8| §bgrey §8| §bpurple §8| §borange");
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) return player.getWorld();
        return plugin.getBorderManager().getTargetWorld();
    }

    private String prefix() { return "§f[§bTLS§f] "; }
    private String noPerms() {
        return plugin.getConfig().getString(
                "mensagens_comandos.sem_permissao",
                "§f[§bTLS§f] §cNão tens permissão.");
    }
}
