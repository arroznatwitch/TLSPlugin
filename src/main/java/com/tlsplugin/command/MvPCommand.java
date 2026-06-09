package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class MvPCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public MvPCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        MVPStatsManager mvpManager = plugin.getMVPStatsManager();
        List<MVPStatsManager.PlayerStats> ranking = mvpManager.getRanking();

        sender.sendMessage(plugin.getConfig().getString("mvp_design.titulo_ranking", "§6§l        ★ RANKING MVP ★"));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.rodape_ranking", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        if (ranking.isEmpty()) {
            sender.sendMessage(" §eNenhum dado registrado ainda.");
        } else {
            String[] medals = {"§6§l🥇", "§7§l🥈", "§b§l🥉"};
            long pausedMs = mvpManager.getEffectivePausedMs();

            int pos = 1;
            for (MVPStatsManager.PlayerStats stats : ranking) {
                if (pos > 10) break;
                String prefix  = pos <= 3 ? medals[pos - 1] : "§7 #" + pos;
                int    pts     = stats.calculateTotalMVPPoints(pausedMs);
                String ptColor = pts >= 0 ? "§a" : "§c";
                sender.sendMessage(String.format(" %s §f%-16s %s%d pts",
                        prefix, stats.playerName, ptColor, pts));
                pos++;
            }
        }

        sender.sendMessage(plugin.getConfig().getString("mvp_design.rodape_ranking", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.comando_sobre", "§7» Use §f/sobremvp <nick>§7 para detalhes."));

        return true;
    }
}