package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.SignManager;
import com.tlsplugin.manager.SignManager.SignData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {

    private final Tlsplugin   plugin;
    private final SignManager signManager;

    public SignListener(Tlsplugin plugin, SignManager signManager) {
        this.plugin      = plugin;
        this.signManager = signManager;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        if (block == null) return;

        // Aceita qualquer tipo de placa (sign, wall_sign, hanging_sign, etc.)
        String typeName = block.getType().name();
        if (!typeName.contains("SIGN")) return;

        Location loc = block.getLocation();
        SignData data = signManager.findByLocation(loc);
        if (data == null) return;

        e.setCancelled(true);
        Player player = e.getPlayer();

        // Verificar permissão
        if (!data.permission.isEmpty() && !player.hasPermission(data.permission)) {
            String msg = data.noPermMessage.isEmpty()
                ? "§cNão tens permissão para usar esta placa."
                : data.noPermMessage;
            player.sendMessage(msg);
            return;
        }

        // Correr comandos como o jogador
        for (String cmd : data.commands) {
            String resolved = cmd.replace("%player%", player.getName());
            player.performCommand(resolved);
        }

        // Correr comandos como consola
        if (!data.consoleCommands.isEmpty()) {
            for (String cmd : data.consoleCommands) {
                String resolved = cmd.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            }
        }
    }
}
