package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProximityAlertListener {

    private final Tlsplugin plugin;

    /**
     * Regista pares de jogadores que já foram alertados recentemente.
     * Chave: UUID menor + UUID maior (para ser simétrico)
     * Valor: timestamp do último alerta
     */
    private final Map<String, Long> alertCooldowns = new HashMap<>();

    public ProximityAlertListener(Tlsplugin plugin) {
        this.plugin = plugin;
        startScanner();
    }

    private void startScanner() {
        // Verificar a cada 2 segundos (40 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 40L, 40L);
    }

    private void scan() {
        // Só alertar se o jogo estiver a decorrer
        if (!plugin.getBorderManager().isRunning()) return;
        if (plugin.getBorderManager().isPaused()) return;

        double raio = plugin.getConfig().getDouble("proximidade_fight.raio_blocos", 30.0);
        long cooldownMs = plugin.getConfig().getLong("proximidade_fight.cooldown_segundos", 30) * 1000L;
        long now = System.currentTimeMillis();

        Player[] jogadores = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.isOp())
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .toArray(Player[]::new);

        for (int i = 0; i < jogadores.length; i++) {
            for (int j = i + 1; j < jogadores.length; j++) {
                Player a = jogadores[i];
                Player b = jogadores[j];

                // Ignorar se forem da mesma equipa
                if (mesmEquipa(a, b)) continue;

                // Ignorar se não estiverem no mesmo mundo
                if (!a.getWorld().equals(b.getWorld())) continue;

                double dist = a.getLocation().distance(b.getLocation());
                if (dist > raio) continue;

                // Verificar cooldown do par
                String pairKey = pairKey(a.getUniqueId(), b.getUniqueId());
                Long lastAlert = alertCooldowns.get(pairKey);
                if (lastAlert != null && (now - lastAlert) < cooldownMs) continue;

                alertCooldowns.put(pairKey, now);
                alertarOps(a, b, (int) dist);
            }
        }

        // Limpar cooldowns antigos para não acumular memória
        alertCooldowns.entrySet().removeIf(e -> (now - e.getValue()) > cooldownMs * 2);
    }

    private void alertarOps(Player a, Player b, int dist) {
        String template = plugin.getConfig().getString(
                "proximidade_fight.mensagem",
                "§f[§bTLS§f] §e⚔ §f{a} §ee §f{b} §eestão a §f{dist} blocos§e um do outro!");

        String msg = template
                .replace("{a}", a.getName())
                .replace("{b}", b.getName())
                .replace("{dist}", String.valueOf(dist));

        String labelTp = plugin.getConfig().getString(
                "proximidade_fight.label_teleportar", "§b§l[TELEPORTAR]");
        String hoverTp = plugin.getConfig().getString(
                "proximidade_fight.hover_teleportar", "§7Clica para te teleportares para a fight");

        for (Player op : Bukkit.getOnlinePlayers()) {
            if (!op.isOp()) continue;

            // Comando de TP directo para o OP que clica
            String tpCmd = "/tp " + op.getName() + " " + a.getName();

            TextComponent mensagem = new TextComponent(msg + " ");
            TextComponent botao = new TextComponent(labelTp);
            botao.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd));
            botao.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(hoverTp).create()));

            mensagem.addExtra(botao);
            op.spigot().sendMessage(mensagem);
        }
    }

    private boolean mesmEquipa(Player a, Player b) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team teamA = sb.getEntryTeam(a.getName());
        Team teamB = sb.getEntryTeam(b.getName());
        if (teamA == null || teamB == null) return false;
        return teamA.getName().equals(teamB.getName());
    }

    /** Chave simétrica para o par de jogadores. */
    private String pairKey(UUID u1, UUID u2) {
        if (u1.compareTo(u2) < 0) return u1 + ":" + u2;
        return u2 + ":" + u1;
    }
}