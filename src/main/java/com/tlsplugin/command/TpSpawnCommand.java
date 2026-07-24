package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public class TpSpawnCommand implements CommandExecutor {

    private final Tlsplugin    plugin;
    private final SpawnManager spawnManager;

    public TpSpawnCommand(Tlsplugin plugin, SpawnManager spawnManager) {
        this.plugin       = plugin;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§f[§bTLS§f] §cApenas jogadores podem usar este comando.");
            return true;
        }

        String teamName;

        if (args.length >= 1) {
            // Cor explícita (ex: placa ligada a "tlspawn red"): permite ir para o spawn
            // de uma equipa que não é a própria — só a OPs ou a quem tenha a permissão
            // dessa equipa específica (o mesmo node que a placa já exige, tls.team.<cor>).
            // Sem isto, um OP sem equipa clicava na placa e a permissão passava (bypass
            // de OP no SignListener), mas o comando falhava por não ter equipa própria.
            String cor = args[0].toLowerCase();
            if (!player.isOp() && !player.hasPermission("tls.team." + cor)) {
                player.sendMessage("§f[§bTLS§f] §cNão tens permissão para ir para o spawn da equipa §b" + cor + "§c.");
                return true;
            }
            teamName = cor;
        } else {
            // Sem argumento: usa a equipa do próprio jogador (comportamento original).
            Team team = Bukkit.getScoreboardManager()
                    .getMainScoreboard()
                    .getEntryTeam(player.getName());

            if (team == null) {
                player.sendMessage("§f[§bTLS§f] §cNão pertences a nenhuma equipa.");
                return true;
            }
            teamName = team.getName().toLowerCase();
        }

        if (!spawnManager.hasSpawn(teamName)) {
            player.sendMessage("§f[§bTLS§f] §cNão existe spawn definido para a tua equipa §b(" + teamName + ")§c.");
            player.sendMessage("§f[§bTLS§f] §7Pede a um admin para usar §b/tls setspawn " + teamName + "§7.");
            return true;
        }

        Location spawn = spawnManager.getSpawn(teamName);

        if (spawn.getWorld() == null) {
            player.sendMessage("§f[§bTLS§f] §cO mundo do spawn da tua equipa não está carregado.");
            return true;
        }

        player.teleport(spawn);
        player.sendMessage("§f[§bTLS§f] §aTeleportado para o spawn da equipa §b" + teamName + "§a.");
        return true;
    }
}
