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
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("confirmar")) {
            sender.sendMessage("");
            sender.sendMessage("§e§l  ⚠ Iniciar jogo");
            sender.sendMessage("");
            sender.sendMessage("  " + plugin.getConfig().getString(
                    "mensagens_comandos.jogo_confirmar_start",
                    "§eTens a certeza? Digita §f/startgame confirmar §epara prosseguir."));
            sender.sendMessage("");
            return true;
        }

        plugin.getBorderManager().startCycle();
        plugin.getBorderTimerAnnouncer().start();
        plugin.getMVPStatsManager().resetAll();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getMVPStatsManager().registerPlayer(online.getName());
        }
        plugin.getMVPStatsManager().startTracking();

        // Limpar lista de prontos ao iniciar
        if (plugin.getProntoCommand() != null) {
            plugin.getProntoCommand().limpar();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) continue;
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }

        plugin.getFreezeManager().freezeForStart();
        plugin.getFreezeManager().startCountdown(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) continue;
                p.setGameMode(GameMode.SURVIVAL);
                giveCraftBook(p);
            }
        });

        sender.sendMessage("");
        sender.sendMessage("§a§l  ▶ Jogo iniciado");
        sender.sendMessage("");
        sender.sendMessage("  " + plugin.getConfig().getString(
                "mensagens_comandos.jogo_iniciado", "§aO jogo começou e as regras foram aplicadas."));
        sender.sendMessage("  §7Modo: §b" + plugin.getBorderManager().getModoAtivo());
        sender.sendMessage("  §7Total de fases: §f" + plugin.getBorderManager().getTotalStages());
        sender.sendMessage("");
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
        if (!p.getInventory().containsAtLeast(book, 1)) {
            p.getInventory().addItem(book);
        }
    }
}