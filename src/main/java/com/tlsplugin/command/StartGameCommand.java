package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class StartGameCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    public StartGameCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("confirmar")) {
            sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.jogo_confirmar_start", "§eConfirme com /startgame confirmar"));
            return true;
        }

        // Iniciar ciclo e tracking
        plugin.getBorderManager().startCycle();
        plugin.getMVPStatsManager().resetAll();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getMVPStatsManager().registerPlayer(online.getName());
        }
        plugin.getMVPStatsManager().startTracking();

        sender.sendMessage(plugin.getConfig().getString("mensagens_comandos.jogo_iniciado", "§aO jogo começou."));

        // Coloca todos em Adventure + vida e comida cheias
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) continue;
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }

        // Freeze todos os jogadores imediatamente
        plugin.getFreezeManager().freezeForStart();

        // Countdown 5 → 1 → "COMEÇOU, BOA SORTE!" com sons e freeze
        plugin.getFreezeManager().startCountdown(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) continue;
                p.setGameMode(GameMode.SURVIVAL);
                giveCraftBook(p);
            }
        });

        return true;
    }

    private void giveCraftBook(Player p) {
        String itemId = plugin.getConfig().getString("game.craft_book_item", "tls_plugin:craft_book");
        CustomStack cs = CustomStack.getInstance(itemId);
        if (cs == null) {
            plugin.getLogger().warning("[TLS] Item '" + itemId + "' não encontrado no ItemsAdder — livro não entregue a " + p.getName());
            return;
        }
        ItemStack book = cs.getItemStack();
        // Só dá se o jogador não tiver já um
        if (!p.getInventory().containsAtLeast(book, 1)) {
            p.getInventory().addItem(book);
        }
    }
}