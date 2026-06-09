package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderManager;
import com.tlsplugin.manager.GameFreezeManager;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class UnPauseCommand implements CommandExecutor {

    private final BorderManager borderManager;
    private final GameFreezeManager freezeManager;
    private final MVPStatsManager mvpStatsManager;



    public UnPauseCommand(BorderManager borderManager, GameFreezeManager freezeManager, MVPStatsManager mvpStatsManager) {
        this.borderManager   = borderManager;
        this.freezeManager   = freezeManager;
        this.mvpStatsManager = mvpStatsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!borderManager.isPaused()) {
            sender.sendMessage("");
            sender.sendMessage("§a§l  ▶ Retomar jogo");
            sender.sendMessage("");
            sender.sendMessage("  " + Tlsplugin.getInstance().getConfig().getString(
                    "mensagens_comandos.jogo_nao_pausado", "§cO jogo não está pausado!"));
            sender.sendMessage("");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage("§a§l  ▶ Retomar jogo");
        sender.sendMessage("");
        sender.sendMessage("  " + Tlsplugin.getInstance().getConfig().getString(
                "mensagens_comandos.jogo_retomando", "§aRetomando o jogo em instantes..."));
        sender.sendMessage("");

        mvpStatsManager.onUnpause();
        freezeManager.unfreezeAfterCountdown(() -> {
            borderManager.setPaused(false);
            borderManager.resumeAfterPause();
        });

        return true;
    }
}