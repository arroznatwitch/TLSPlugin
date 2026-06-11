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

        // Descobrir equipa do jogador pela scoreboard principal
        Team team = Bukkit.getScoreboardManager()
                .getMainScoreboard()
                .getEntryTeam(player.getName());

        if (team == null) {
            player.sendMessage("§f[§bTLS§f] §cNão pertences a nenhuma equipa.");
            return true;
        }

        String teamName = team.getName().toLowerCase();

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
