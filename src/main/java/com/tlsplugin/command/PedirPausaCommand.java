package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PedirPausaCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public PedirPausaCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        // Verificar se o jogo está a decorrer (opcional mas boa prática)
        if (!plugin.getBorderManager().isRunning()) {
            p.sendMessage(plugin.getConfig().getString(
                    "pausa_jogador.mensagens.jogo_nao_iniciado",
                    "§cO jogo ainda não começou."));
            return true;
        }

        // Verificar usos restantes
        int maxUsos = plugin.getConfig().getInt("pausa_jogador.max_pausas_por_jogador", 2);
        int usosFeitos = plugin.getPauseManager().getUsosJogador(p.getName());
        if (usosFeitos >= maxUsos) {
            String msg = plugin.getConfig().getString(
                    "pausa_jogador.mensagens.sem_pausas_restantes",
                    "§cJá não tens pausas disponíveis! (Máx: {max})");
            p.sendMessage(msg.replace("{max}", String.valueOf(maxUsos)));
            return true;
        }

        // Verificar se já há um pedido pendente deste jogador
        if (plugin.getPauseManager().temPedidoPendente(p.getName())) {
            p.sendMessage(plugin.getConfig().getString(
                    "pausa_jogador.mensagens.pedido_ja_pendente",
                    "§cJá tens um pedido de pausa pendente."));
            return true;
        }

        // Verificar se o jogo está pausado
        if (plugin.getBorderManager().isPaused()) {
            p.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.jogo_ja_pausado",
                    "§cO jogo já está pausado!"));
            return true;
        }

        // Obter motivo
        String motivo;
        if (args.length == 0) {
            motivo = plugin.getConfig().getString("pausa_jogador.motivo_padrao", "Sem motivo indicado.");
        } else {
            motivo = String.join(" ", args);
        }

        // Registar pedido pendente
        plugin.getPauseManager().registarPedido(p.getName(), motivo);

        // Confirmar ao jogador
        String msgConfirm = plugin.getConfig().getString(
                "pausa_jogador.mensagens.pedido_enviado",
                "§aPedido de pausa enviado aos operadores.");
        p.sendMessage(msgConfirm);

        // Notificar OPs
        String sep       = plugin.getConfig().getString(
                "pausa_jogador.mensagens.notificacao_op_separador",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String linhaJogador = plugin.getConfig().getString(
                "pausa_jogador.mensagens.notificacao_op_linha1",
                "§fO jogador §e\"{player}\"§f está a pedir pausa!");
        String linhaMotivo  = plugin.getConfig().getString(
                "pausa_jogador.mensagens.notificacao_op_linha2",
                "§eMotivo: §f{motivo}");
        String linhaAceitar = plugin.getConfig().getString(
                "pausa_jogador.mensagens.notificacao_op_linha3",
                "§7» Usa §f/aceitarpausa {player} §7para aceitar.");

        String l1 = linhaJogador.replace("{player}", p.getName());
        String l2 = linhaMotivo.replace("{motivo}", motivo).replace("{player}", p.getName());
        String l3 = linhaAceitar.replace("{player}", p.getName());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) {
                online.sendMessage("");
                online.sendMessage(sep);
                online.sendMessage(l1);
                online.sendMessage(l2);
                online.sendMessage(l3);
                online.sendMessage(sep);
                online.sendMessage("");
            }
        }

        return true;
    }
}