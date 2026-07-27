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

        // Última borda: não há mais nada para anunciar (já não vai encolher mais), então
        // paramos de mostrar este aviso periódico.
        if (currentIndex >= total - 1) return;

        String cabecalho   = plugin.getConfig().getString("border_announcer.cabecalho", "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String titulo      = plugin.getConfig().getString("border_announcer.titulo",    "§b§l⚔ BORDAS DO EVENTO ⚔");
        String rodape      = plugin.getConfig().getString("border_announcer.rodape",    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String corPassada  = plugin.getConfig().getString("border_announcer.cor_passada",  "§8§m");
        String corAtual    = plugin.getConfig().getString("border_announcer.cor_atual",    "§a");
        String corFutura   = plugin.getConfig().getString("border_announcer.cor_futura",   "§f");
        String setaAtual   = plugin.getConfig().getString("border_announcer.seta_atual",   "▶ ");
        String prefixoFutura = plugin.getConfig().getString("border_announcer.prefixo_futuro", "  ");

        List<String> linhas = new java.util.ArrayList<>();
        linhas.add(t(cabecalho));
        linhas.add(t(titulo));
        linhas.add("");

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

            linhas.add(t(linha));
        }

        linhas.add("");
        linhas.add(t(rodape));

        // Som de noteblock
        boolean somHabilitado = plugin.getConfig().getBoolean("border_announcer.som.habilitar", true);
        Sound sound = null;
        float volume = 1.0f, pitch = 1.0f;
        if (somHabilitado) {
            String soundName = plugin.getConfig().getString("border_announcer.som.tipo", "BLOCK_NOTE_BLOCK_PLING");
            volume = (float) plugin.getConfig().getDouble("border_announcer.som.volume", 1.0);
            pitch  = (float) plugin.getConfig().getDouble("border_announcer.som.pitch",  1.0);
            // As chaves reais do registry usam pontos (ex: "block.note_block.pling"), não
            // sublinhados — por isso NamespacedKey.minecraft(soundName.toLowerCase()) falhava
            // sempre. Convertemos a key de cada som (pontos → underscore) e comparamos com o
            // nome configurado (ex: BLOCK_NOTE_BLOCK_PLING).
            for (Sound s : org.bukkit.Registry.SOUNDS) {
                String keyAsEnumStyle = s.key().value().replace('.', '_');
                if (keyAsEnumStyle.equalsIgnoreCase(soundName)) { sound = s; break; }
            }
            if (sound == null) {
                plugin.getLogger().warning("[TLS] Som inválido no border_announcer.som.tipo: '" + soundName + "'. A usar BLOCK_NOTE_BLOCK_PLING.");
                sound = Sound.BLOCK_NOTE_BLOCK_PLING;
            }
        }

        // Só para quem NÃO é OP — este aviso é dirigido a jogadores, não à staff.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) continue;
            for (String linha : linhas) p.sendMessage(linha);
            if (sound != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
        Bukkit.getConsoleSender().sendMessage(String.join("\n", linhas));
    }

    private String t(String s) {
        return ChatColor.translateAlternateColorCodes('§', s);
    }
}