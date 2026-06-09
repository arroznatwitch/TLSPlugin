package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderManager;
import com.tlsplugin.manager.GameFreezeManager;
import com.tlsplugin.manager.MVPStatsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PauseCommand implements CommandExecutor {

    private final BorderManager borderManager;
    private final GameFreezeManager freezeManager;
    private final MVPStatsManager mvpStatsManager;



    public PauseCommand(BorderManager borderManager, GameFreezeManager freezeManager, MVPStatsManager mvpStatsManager) {
        this.borderManager   = borderManager;
        this.freezeManager   = freezeManager;
        this.mvpStatsManager = mvpStatsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (borderManager.isPaused()) {
            sender.sendMessage("");
            sender.sendMessage("§e§l  ⏸ Jogo pausado");
            sender.sendMessage("");
            sender.sendMessage("  " + Tlsplugin.getInstance().getConfig().getString(
                    "mensagens_comandos.jogo_ja_pausado", "§cO jogo já está pausado!"));
            sender.sendMessage("");
            return true;
        }

        borderManager.setPaused(true);
        freezeManager.freezeAll();
        mvpStatsManager.onPause();

        sender.sendMessage("");
        sender.sendMessage("§e§l  ⏸ Jogo pausado");
        sender.sendMessage("");
        sender.sendMessage("  " + Tlsplugin.getInstance().getConfig().getString(
                "mensagens_comandos.jogo_pausado", "§c⏸ Jogo pausado."));
        sender.sendMessage("");
        return true;
    }
}