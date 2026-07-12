package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class BorderTimerAnnouncer {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;
    private BukkitTask task;

    public BorderTimerAnnouncer(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    public void start() {
        if (task != null) task.cancel();
        int intervalTicks = (int) (plugin.getConfig().getDouble("border_announcer.intervalo_minutos", 2.5) * 60 * 20);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcast, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void broadcast() {
        if (!borderManager.isRunning()) return;

        String modo = plugin.getConfig().getString("modo_jogo", "final");
        List<Double> bordas = plugin.getConfig().getDoubleList("modos." + modo + ".bordas");
        int currentIndex = borderManager.getCurrentStage() - 1;
        int total = bordas.size();

        String cabecalho   = plugin.getConfig().getString("border_announcer.cabecalho", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String titulo      = plugin.getConfig().getString("border_announcer.titulo",    "§b§l⚔ BORDAS DO EVENTO ⚔");
        String rodape      = plugin.getConfig().getString("border_announcer.rodape",    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String corPassada  = plugin.getConfig().getString("border_announcer.cor_passada",  "§8§m");
        String corAtual    = plugin.getConfig().getString("border_announcer.cor_atual",    "§a");
        String corFutura   = plugin.getConfig().getString("border_announcer.cor_futura",   "§f");
        String setaAtual   = plugin.getConfig().getString("border_announcer.seta_atual",   "▶ ");
        String prefixoFutura = plugin.getConfig().getString("border_announcer.prefixo_futuro", "  ");

        Tlsplugin.broadcast(t(cabecalho));
        Tlsplugin.broadcast(t(titulo));
        Tlsplugin.broadcast("");

        for (int i = 0; i < bordas.size(); i++) {
            double borda = bordas.get(i);
            String coord = "±" + (int) (borda / 2);
            String numero = (i + 1) + "/" + total;
            String linha;

            if (i < currentIndex) {
                // Passada — strikethrough cinzento
                linha = corPassada + "  Borda " + numero + " — X/Z " + coord + "§r";
            } else if (i == currentIndex) {
                // Atual — seta + cor configurável
                linha = corAtual + setaAtual + "Borda " + numero + " — X/Z " + coord;
            } else {
                // Futura
                linha = corFutura + prefixoFutura + "Borda " + numero + " — X/Z " + coord;
            }

            Tlsplugin.broadcast(t(linha));
        }

        Tlsplugin.broadcast("");
        Tlsplugin.broadcast(t(rodape));

        // Som de noteblock para todos os jogadores que estão a jogar (não OPs em criativo)
        boolean somHabilitado = plugin.getConfig().getBoolean("border_announcer.som.habilitar", true);
        if (somHabilitado) {
            String soundName = plugin.getConfig().getString("border_announcer.som.tipo", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) plugin.getConfig().getDouble("border_announcer.som.volume", 1.0);
            float pitch  = (float) plugin.getConfig().getDouble("border_announcer.som.pitch",  1.0);
            // Usa NamespacedKey + Registry para evitar o Sound.valueOf() deprecated no Paper 1.21+
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(soundName.toLowerCase());
            Sound sound = org.bukkit.Registry.SOUNDS.get(key);
            if (sound == null) {
                plugin.getLogger().warning("[TLS] Som inválido no border_announcer.som.tipo: '" + soundName + "'. A usar BLOCK_NOTE_BLOCK_PLING.");
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
            }
            Sound finalSound = sound;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), finalSound, volume, pitch);
            }
        }
    }

    private String t(String s) {
        return ChatColor.translateAlternateColorCodes('§', s);
    }
}