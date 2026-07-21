package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.listeners.GrapplerItemListener;
import com.tlsplugin.listeners.TrackerCompassListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mostra uma action bar por jogador com uma seta a apontar para o (0,0) + a distância.
 * É "client-side" no sentido prático: cada jogador só vê a SUA seta, calculada a partir
 * de onde ele está virado (yaw). Nada é enviado aos outros.
 *
 * A action bar aparece encima da vida. Resenviada a cada 5 ticks para não desaparecer
 * e para a seta rodar suavemente quando o jogador vira a câmara.
 *
 * A action bar só pode ter UM dono de cada vez — vários sistemas a escrever ao mesmo
 * tempo faz o texto ficar a piscar/trocar. Este compass cede sempre a quem tiver
 * prioridade: Grappler (cooldown) e Tracker (a rastrear) escrevem primeiro; o compass
 * do centro só aparece quando nenhum dos outros dois está ativo.
 */
public class CenterCompassTask {

    private final Tlsplugin plugin;
    private final GrapplerItemListener grappler;
    private final TrackerCompassListener tracker;
    private BukkitTask task;

    // ↑ ↗ → ↘ ↓ ↙ ← ↖  — 8 direções, índice 0 = frente
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public CenterCompassTask(Tlsplugin plugin, GrapplerItemListener grappler, TrackerCompassListener tracker) {
        this.plugin   = plugin;
        this.grappler = grappler;
        this.tracker  = tracker;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Só na arena (não no lobby). Se quiseres, troca por: plugin.getBorderManager().isRunning()
                if (plugin.isLobbyWorld(p.getWorld())) continue;
                // Cede a action bar ao grappler quando este está a mostrar o cooldown.
                if (grappler != null && grappler.isShowingActionBar(p)) continue;
                // Cede a action bar ao tracker enquanto estiver ativamente a rastrear.
                if (tracker != null && tracker.isTracking(p)) continue;
                sendCompass(p);
            }
        }, 0L, 5L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void sendCompass(Player p) {
        double px = p.getLocation().getX();
        double pz = p.getLocation().getZ();

        double dist = Math.sqrt(px * px + pz * pz);

        // Yaw que apontaria para o (0,0). Convenção de yaw do Minecraft:
        // 0 = sul (+Z), 90 = oeste (-X), 180 = norte (-Z), -90 = este (+X).
        double targetYaw = Math.toDegrees(Math.atan2(px, -pz));

        // Diferença relativa ao para onde o jogador está virado, normalizada a [-180, 180].
        double rel = targetYaw - p.getLocation().getYaw();
        while (rel > 180)  rel -= 360;
        while (rel < -180) rel += 360;

        // Mapeia para uma das 8 setas (cada sector = 45°, com offset de 22.5°).
        int idx = (int) Math.round(rel / 45.0);
        idx = ((idx % 8) + 8) % 8;
        String arrow = ARROWS[idx];

        // Verde quando já está praticamente em cima do centro
        String arrowColor = dist <= 50 ? "§a" : "§e";

        String template = plugin.getConfig().getString(
                "compass_template",
                "{arrow} §fCentro (0,0) §7• §f{dist}m");
        String text = template
                .replace("{arrow}", arrowColor + arrow)
                .replace("{dist}", String.valueOf((int) dist));

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(
                        org.bukkit.ChatColor.translateAlternateColorCodes('§', text)));
    }
}