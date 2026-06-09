package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BorderScoreboardManager {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public BorderScoreboardManager(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        startUpdater();
    }

    private void startUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateBoard(p);
            }
        }, 0L, 20L);
    }

    public void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();

        // Sincronizar teams da scoreboard principal
        for (Team t : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            Team nt = sb.registerNewTeam(t.getName());

            nt.setPrefix(t.getPrefix());
            nt.setSuffix(t.getSuffix());
            nt.setColor(t.getColor());

            for (Team.Option option : Team.Option.values()) {
                try {
                    nt.setOption(option, t.getOption(option));
                } catch (Exception ignored) {}
            }

            for (String entry : t.getEntries()) {
                nt.addEntry(entry);
            }
        }

        FileConfiguration cfg = plugin.getConfig();

        String titulo = processUnicode(
                cfg.getString("scoreboard.titulo", "§b§lTLS - III")
        );

        Objective obj = sb.registerNewObjective(
                "border",
                "dummy",
                titulo
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateBoard(Player p) {

        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), id -> {
            create(p);
            return p.getScoreboard();
        });

        Objective obj = sb.getObjective("border");

        if (obj == null) {
            return;
        }

        sb.getEntries().forEach(sb::resetScores);

        FileConfiguration cfg = plugin.getConfig();

        String titulo = processUnicode(
                cfg.getString("scoreboard.titulo", "§b§lTLS - III")
        );

        String lFase      = color(cfg.getString("scoreboard.label_fase", "§8Fase: "));
        String lTempo     = color(cfg.getString("scoreboard.label_tempo", "§8Tempo: "));
        String lLoc       = color(cfg.getString("scoreboard.label_localizacao", "§bLocalização:"));
        String lX         = color(cfg.getString("scoreboard.label_x", "§8 X: "));
        String lZ         = color(cfg.getString("scoreboard.label_z", "§8 Z: "));
        String lBorda     = color(cfg.getString("scoreboard.label_borda", "§bBorda:"));
        String lBordaXZ   = color(cfg.getString("scoreboard.label_borda_xz", "§8 X/Z: "));
        String lBordaDist = color(cfg.getString("scoreboard.label_borda_dist", "§8 Dist: "));
        String lEquipa    = color(cfg.getString("scoreboard.label_equipa", "§bEquipa:"));

        String corValor  = color(cfg.getString("scoreboard.cor_valor", "§b"));
        String corPerigo = color(cfg.getString("scoreboard.cor_perigo", "§c"));
        String corAviso  = color(cfg.getString("scoreboard.cor_aviso", "§e"));
        String corSeguro = color(cfg.getString("scoreboard.cor_seguro", "§a"));

        int limiarPerigo = cfg.getInt("scoreboard.limiar_perigo", 100);
        int limiarAviso  = cfg.getInt("scoreboard.limiar_aviso", 250);

        String tmplVivo = color(
                cfg.getString(
                        "scoreboard.linha_jogador_vivo",
                        "§b⬢ §7{nome}§8 - §c❤ {vida}"
                )
        );

        String tmplMorto = color(
                cfg.getString(
                        "scoreboard.linha_jogador_morto",
                        "§c§l⬢ §7{nome} - MORTO"
                )
        );

        int line = 20;

        // Título atualizado com Unicode processado
        obj.setDisplayName(titulo);

        // Fase
        obj.getScore(
                lFase
                        + corValor
                        + borderManager.getCurrentStage()
                        + "/"
                        + borderManager.getTotalStages()
        ).setScore(line--);

        // Tempo
        obj.getScore(
                lTempo
                        + corValor
                        + format(borderManager.getRemainingShrinkSeconds())
        ).setScore(line--);

        obj.getScore(" ").setScore(line--);

        // Localização
        Location l = p.getLocation();

        obj.getScore(lLoc).setScore(line--);

        obj.getScore(
                lX + corValor + l.getBlockX()
        ).setScore(line--);

        obj.getScore(
                lZ + corValor + l.getBlockZ()
        ).setScore(line--);

        obj.getScore("  ").setScore(line--);

        // Borda
        WorldBorder wb = p.getWorld().getWorldBorder();

        double half = wb.getSize() / 2;

        double dist = half - Math.max(
                Math.abs(l.getX()),
                Math.abs(l.getZ())
        );

        String corDist;

        if (dist <= limiarPerigo) {
            corDist = corPerigo;
        } else if (dist <= limiarAviso) {
            corDist = corAviso;
        } else {
            corDist = corSeguro;
        }

        obj.getScore(lBorda).setScore(line--);

        obj.getScore(
                lBordaXZ
                        + corValor
                        + "±"
                        + (int) half
        ).setScore(line--);

        obj.getScore(
                lBordaDist
                        + corDist
                        + (int) dist
                        + "m"
        ).setScore(line--);

        obj.getScore("   ").setScore(line--);

        // Equipa
        Team t = Bukkit.getScoreboardManager()
                .getMainScoreboard()
                .getEntryTeam(p.getName());

        obj.getScore(lEquipa).setScore(line--);

        if (t != null) {

            int count = 0;

            for (String e : t.getEntries()) {

                Player m = Bukkit.getPlayer(e);

                if (m == null) continue;

                boolean vivo =
                        m.getGameMode() == GameMode.SURVIVAL
                                || m.getGameMode() == GameMode.ADVENTURE;

                String lineText;

                if (!vivo || m.isDead() || m.getHealth() <= 0) {

                    lineText = tmplMorto
                            .replace("{nome}", m.getName());

                } else {

                    int vida = (int) Math.ceil(m.getHealth());

                    lineText = tmplVivo
                            .replace("{nome}", m.getName())
                            .replace("{vida}", String.valueOf(vida));
                }

                obj.getScore(lineText).setScore(line--);

                count++;

                if (count >= 4) break;
            }

        } else {

            boolean vivo =
                    p.getGameMode() == GameMode.SURVIVAL
                            || p.getGameMode() == GameMode.ADVENTURE;

            String lineText;

            if (!vivo || p.isDead() || p.getHealth() <= 0) {

                lineText = tmplMorto
                        .replace("{nome}", p.getName());

            } else {

                int vida = (int) Math.ceil(p.getHealth());

                lineText = tmplVivo
                        .replace("{nome}", p.getName())
                        .replace("{vida}", String.valueOf(vida));
            }

            obj.getScore(lineText).setScore(line--);
        }
    }

    private String processUnicode(String text) {

        if (text == null) return "";

        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

        Matcher matcher = pattern.matcher(text);

        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {

            char unicode = (char)
                    Integer.parseInt(
                            matcher.group(1),
                            16
                    );

            matcher.appendReplacement(
                    buffer,
                    Matcher.quoteReplacement(
                            String.valueOf(unicode)
                    )
            );
        }

        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes(
                '&',
                buffer.toString()
        );
    }

    private String color(String s) {
        if (s == null) return "";
        return processUnicode(s);
    }

    private String format(int sec) {
        int m = sec / 60;
        int s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }
}