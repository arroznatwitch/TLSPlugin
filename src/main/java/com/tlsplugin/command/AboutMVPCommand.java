package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AboutMVPCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public AboutMVPCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.isOp() && !sender.hasPermission("tls.admin")) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUso: /sobremvp <nick>");
            return true;
        }

        String playerName = args[0];
        MVPStatsManager mvpManager = plugin.getMVPStatsManager();

        // Tentar encontrar o nome exato ignorando maiúsculas/minúsculas
        MVPStatsManager.PlayerStats stats = null;
        for (MVPStatsManager.PlayerStats s : mvpManager.getAllPlayers()) {
            if (s.playerName.equalsIgnoreCase(playerName)) {
                stats = s;
                playerName = s.playerName;
                break;
            }
        }

        // Se ainda não encontrou, verificar se o jogador está online
        if (stats == null) {
            org.bukkit.entity.Player onlineTarget = org.bukkit.Bukkit.getPlayer(playerName);
            if (onlineTarget != null) {
                playerName = onlineTarget.getName();
                mvpManager.registerPlayer(playerName);
                stats = mvpManager.getStats(playerName);
            }
        }

        if (stats == null) {
            String msg = plugin.getConfig().getString("mensagens_comandos.erro_jogador_nao_encontrado", "§cJogador \"{player}\" não encontrado.")
                    .replace("{player}", playerName);
            sender.sendMessage(msg);
            return true;
        }

        long pausedMs    = mvpManager.getEffectivePausedMs();
        int  totalPoints = stats.calculateTotalMVPPoints(pausedMs);
        long aliveMin    = stats.getAliveTimeMinutes(pausedMs);
        int  timePts     = stats.calculateTimePoints(pausedMs);
        double ddrd      = stats.getDDRD();
        String ddrdColor = ddrd >= 0 ? "§a" : "§c";
        String ddrdSign  = ddrd >= 0 ? "+" : "";
        String ptColor   = totalPoints >= 0 ? "§a" : "§c";

        sender.sendMessage(plugin.getConfig().getString("mvp_design.sub_titulo_detalhes", "§6§l★ MVP — {player} ★").replace("{player}", playerName));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.linha_divisor", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.pontuacao_total", "§e✦ Pontuação Total: {color}§l{pts} pts")
                .replace("{color}", ptColor).replace("{pts}", String.valueOf(totalPoints)));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.linha_divisor", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.secao_combate", "§f⚔ §lCombate"));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_kills", "  §7Kills:   §a{val} §8(+{pts} pts)")
                .replace("{val}", String.valueOf(stats.kills)).replace("{pts}", String.valueOf(stats.kills * 8)));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_assists", "  §7Assists: §a{val} §8(+{pts} pts)")
                .replace("{val}", String.valueOf(stats.assists)).replace("{pts}", String.valueOf(stats.assists * 3)));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_mortes", "  §7Mortes:  §c{val} §8({pts} pts)")
                .replace("{val}", String.valueOf(stats.deaths)).replace("{pts}", String.valueOf(stats.deaths * -6)));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.linha_divisor", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.secao_dano", "§f🗡 §lDano"));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_dado", "  §7Dado (DDJ):       §a{val}")
                .replace("{val}", String.format("%.2f", stats.damageGiven)));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_recebido", "  §7Recebido (DRJ):   §c{val}")
                .replace("{val}", String.format("%.2f", stats.damageReceived)));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_diferenca", "  §7Diferença (DDRD): {color}{val} §8({pts} pts)")
                .replace("{color}", ddrdColor).replace("{val}", ddrdSign + String.format("%.2f", ddrd))
                .replace("{pts}", String.valueOf(stats.calculateDDRDPoints())));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.linha_divisor", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.secao_tempo", "§f⏱ §lTempo & Revivals"));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_tempo", "  §7Tempo Vivo: §b{val} min §8(+{pts} pts)")
                .replace("{val}", String.valueOf(aliveMin)).replace("{pts}", String.valueOf(timePts)));
        sender.sendMessage(plugin.getConfig().getString("mvp_design.formato_revivals", "  §7Reviveu:    §a{val} pessoa(s) §8(+{pts} pts)")
                .replace("{val}", String.valueOf(stats.revivals)).replace("{pts}", String.valueOf(stats.revivals * 5)));

        sender.sendMessage(plugin.getConfig().getString("mvp_design.linha_divisor", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        return true;
    }
}