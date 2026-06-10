package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

        Bukkit.broadcastMessage(t(cabecalho));
        Bukkit.broadcastMessage(t(titulo));
        Bukkit.broadcastMessage("");

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

            Bukkit.broadcastMessage(t(linha));
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(t(rodape));
    }

    private String t(String s) {
        return ChatColor.translateAlternateColorCodes('§', s);
    }
}