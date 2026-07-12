package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class StartGameCommand implements CommandExecutor {

    private final Tlsplugin plugin;

    // Tipos de mob hostil que devem ser eliminados no início do jogo.
    private static final Set<EntityType> HOSTILE = Set.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME,
            EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON,
            EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.STRAY, EntityType.DROWNED,
            EntityType.PHANTOM, EntityType.PILLAGER, EntityType.RAVAGER, EntityType.VINDICATOR,
            EntityType.EVOKER, EntityType.VEX, EntityType.WARDEN, EntityType.BOGGED,
            EntityType.BREEZE, EntityType.ELDER_GUARDIAN, EntityType.GUARDIAN,
            EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN,
            EntityType.ENDERMITE, EntityType.SILVERFISH, EntityType.SHULKER
    );

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

        plugin.getMVPStatsManager().resetAll();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getMVPStatsManager().registerPlayer(online.getName());
        }
        // Nota: startTracking() é chamado dentro do callback do countdown,
        // para que o timer de tempo vivo só comece quando o jogo REALMENTE começa.

        // Limpar lista de prontos ao iniciar
        if (plugin.getProntoCommand() != null) {
            plugin.getProntoCommand().limpar();
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) {
                p.setGameMode(GameMode.CREATIVE);
                continue;
            }
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
        }

        // Mata mobs hostis e define o dia — ANTES do countdown, enquanto os jogadores
        // ainda estão frozen nas cápsulas. A borda SÓ começa depois do countdown acabar.
        killHostileMobs();
        setDayOnEventWorld();

        plugin.getFreezeManager().freezeForStart();
        plugin.getFreezeManager().startCountdown(() -> {
            // O timer de MVP começa AQUI — quando "COMEÇOU, BOA SORTE!" aparece.
            plugin.getMVPStatsManager().startTracking();

            // A borda começa AQUI — só quando o "COMEÇOU, BOA SORTE!" aparece.
            plugin.getBorderManager().startCycle();
            plugin.getBorderTimerAnnouncer().start();

            // Parte as cápsulas quando o jogo começa
            if (plugin.getCapsuleManager() != null) {
                plugin.getCapsuleManager().breakAll();
            }

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

    /** Mata todos os mobs hostis no mundo do evento. */
    private void killHostileMobs() {
        World eventWorld = plugin.getBorderManager().getTargetWorld();
        if (eventWorld == null) return;
        for (Entity e : eventWorld.getEntities()) {
            if (e instanceof Player) continue;
            if (HOSTILE.contains(e.getType())) e.remove();
        }
    }

    /** Define o tempo como dia no mundo do evento. */
    private void setDayOnEventWorld() {
        World eventWorld = plugin.getBorderManager().getTargetWorld();
        if (eventWorld == null) return;
        eventWorld.setTime(1000L); // 1000 = meio da manhã
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