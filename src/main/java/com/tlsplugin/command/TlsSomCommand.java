package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.manager.SoundPreferenceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /tlssom — cada jogador liga/desliga os sons de ambiente do plugin só para si.
 * Não precisa de permissão: a escolha é pessoal e não afeta mais ninguém.
 */
public class TlsSomCommand implements CommandExecutor {

    private final Tlsplugin plugin;
    private final SoundPreferenceManager somManager;

    public TlsSomCommand(Tlsplugin plugin, SoundPreferenceManager somManager) {
        this.plugin     = plugin;
        this.somManager = somManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§f[§bTLS§f] §cApenas jogadores podem usar este comando.");
            return true;
        }

        boolean silenciado = somManager.alternar(player.getUniqueId());

        player.sendMessage("§8§m──────────────────────────────");
        if (silenciado) {
            player.sendMessage("§f[§bTLS§f] §c🔇 Sons do TLS §ldesligados§c.");
            player.sendMessage("§7Deixas de ouvir a música de pausa, a contagem,");
            player.sendMessage("§7os avisos de borda e a morte súbita.");
        } else {
            player.sendMessage("§f[§bTLS§f] §a🔊 Sons do TLS §lligados§a.");
            player.sendMessage("§7Voltas a ouvir os avisos do evento.");
        }
        player.sendMessage("§7Isto vale §bsó para ti§7 — usa §b/" + label + " §7para voltar a trocar.");
        player.sendMessage("§8§m──────────────────────────────");
        return true;
    }
}
