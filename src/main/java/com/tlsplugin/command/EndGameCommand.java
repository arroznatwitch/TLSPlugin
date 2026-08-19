package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.BorderManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EndGameCommand implements CommandExecutor {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;



    public EndGameCommand(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.sem_permissao", "§cSem permissão."));
            return true;
        }

        String confirmarPalavra = plugin.getConfig().getString(
                "mensagens_comandos.endgame_palavra_confirmar", "confirmar");

        if (args.length == 0 || !args[0].equalsIgnoreCase(confirmarPalavra)) {
            com.tlsplugin.utils.ConfirmationManager.pedir(sender, "endgame");
            sender.sendMessage("");
            sender.sendMessage("§c§l  ⚠ Terminar jogo");
            sender.sendMessage("");
            sender.sendMessage(plugin.getConfig().getString(
                    "mensagens_comandos.endgame_confirmar",
                    "  §cTens a certeza? Escreve §f/endgame confirmar §cpara terminar."));
            sender.sendMessage("  §7(válido por §f"
                    + com.tlsplugin.utils.ConfirmationManager.getValidadeSegundos() + "s§7)");
            sender.sendMessage("");
            return true;
        }

        // "confirmar" só vale se o aviso tiver sido mostrado a este sender há pouco tempo.
        if (!com.tlsplugin.utils.ConfirmationManager.confirmar(sender, "endgame")) {
            sender.sendMessage("");
            sender.sendMessage("  " + plugin.getConfig().getString(
                    "mensagens_comandos.confirmacao_invalida",
                    "§cNão tens nenhuma confirmação pendente. Corre §f/endgame §cprimeiro."));
            sender.sendMessage("");
            return true;
        }

        borderManager.stopAll();
        plugin.getBorderTimerAnnouncer().stop();

        if (plugin.getMVPStatsManager() != null) {
            plugin.getMVPStatsManager().stopTracking();
            plugin.getMVPStatsManager().backupStats();
            plugin.getMVPStatsManager().saveStats();
        }

        // Envia toda a gente de volta ao lobby. OPs mantêm o gamemode em que estavam
        // (staff costuma ficar em Criativo/Spectator a organizar); os jogadores voltam
        // a Adventure. O listener de mudança de mundo já repõe o Criativo dos OPs no
        // lobby, por isso guardamos e reaplicamos o modo atual deles depois do teleporte.
        String lobbyName = plugin.getConfig().getString("mundo_lobby", "world");
        org.bukkit.World lobby = Bukkit.getWorld(lobbyName);
        if (lobby == null) {
            sender.sendMessage("§f[§bTLS§f] §cMundo do lobby '" + lobbyName + "' não está carregado — jogadores não foram teleportados.");
        }

        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.GameMode modoAntes = p.getGameMode();
            if (lobby != null) p.teleport(lobby.getSpawnLocation());

            if (p.isOp()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) p.setGameMode(modoAntes);
                }, 3L);
            } else {
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }

        Tlsplugin.broadcast("");
        Tlsplugin.broadcast(plugin.getConfig().getString(
                "mensagens_comandos.jogo_terminado",
                "§f[§bTLS§f] §c§lO jogo foi terminado pelo administrador!"));
        Tlsplugin.broadcast("");
        return true;
    }
}