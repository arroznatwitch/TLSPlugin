package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.PlayerPauseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AceitarPausaCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public AceitarPausaCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Apenas OPs
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao",
                    "§cNão tens permissão para executar este comando."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUso: /aceitarpausa <nick>");
            return true;
        }

        String nick = args[0];
        PlayerPauseManager pauseManager = plugin.getPauseManager();

        if (!pauseManager.temPedidoPendente(nick)) {
            sender.sendMessage(plugin.getConfig().getString(
                            "pausa_jogador.mensagens.sem_pedido_pendente",
                            "§cNão existe nenhum pedido de pausa pendente de {player}.")
                    .replace("{player}", nick));
            return true;
        }

        if (plugin.getBorderManager().isPaused()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.jogo_ja_pausado",
                    "§cO jogo já está pausado!"));
            return true;
        }

        // Consumir o uso e limpar pedido
        pauseManager.aceitarPedido(nick);

        // Pausar o jogo
        plugin.getBorderManager().setPaused(true);
        plugin.getFreezeManager().freezeAll();
        plugin.getMVPStatsManager().onPause();

        // Mensagem global de pausa aceite
        String msgGlobal = plugin.getConfig().getString(
                        "pausa_jogador.mensagens.pausa_aceite_global",
                        "§f[§bTLS§f] O jogador \"§e{player}§f\" pausou o jogo e voltamos nuns instantes...")
                .replace("{player}", nick);
        Tlsplugin.broadcast(msgGlobal);

        // Iniciar o countdown de 2 minutos e depois despausar automaticamente
        int duracaoSegundos = plugin.getConfig().getInt("pausa_jogador.duracao_segundos", 120);
        plugin.getFreezeManager().freezePlayerPauseCountdown(nick, duracaoSegundos, () -> {
            plugin.getBorderManager().setPaused(false);
            plugin.getBorderManager().resumeAfterPause();
            plugin.getMVPStatsManager().onUnpause();
        });

        return true;
    }
}