package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BorderScoreboardListener implements Listener, CommandExecutor {

    private final Tlsplugin plugin;
    private final BorderScoreboardManager manager;

    public BorderScoreboardListener(Tlsplugin plugin, BorderScoreboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.create(e.getPlayer()), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.remove(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        boolean visible = manager.toggle(p);
        if (visible) {
            p.sendMessage("§aScoreboard §lativada§a.");
        } else {
            p.sendMessage("§7Scoreboard §ldesativada§7.");
        }
        return true;
    }
}