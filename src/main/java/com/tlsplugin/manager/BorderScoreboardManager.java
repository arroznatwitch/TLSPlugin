package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BorderScoreboardManager {

    private final Tlsplugin plugin;
    private final BorderManager borderManager;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    // Jogadores que têm a scoreboard ESCONDIDA (toggle off)
    private final Set<UUID> hidden = new HashSet<>();

    // Número de linhas máximo da scoreboard
    private static final int MAX_LINES = 20;

    // Entradas dummy únicas por linha (invisíveis ao jogador)
    private static final String[] ENTRIES;
    static {
        ENTRIES = new String[MAX_LINES];
        for (int i = 0; i < MAX_LINES; i++) {
            // §0, §1, §2... §9, §a, §b... combinações únicas
            ENTRIES[i] = "§" + Integer.toHexString(i);
        }
    }

    public BorderScoreboardManager(Tlsplugin plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        startUpdater();
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (hidden.contains(id)) {
            hidden.remove(id);
            create(p);
            return true; // agora visível
        } else {
            hidden.add(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            boards.remove(id);
            return false; // agora escondida
        }
    }

    public boolean isHidden(Player p) {
        return hidden.contains(p.getUniqueId());
    }

    // ── Updater ───────────────────────────────────────────────────────────────

    private void startUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!hidden.contains(p.getUniqueId())) {
                    updateBoard(p);
                }
            }
        }, 0L, 20L);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public void create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();

        // Sincronizar teams da scoreboard principal (nomes de equipa, cores, etc.)
        for (Team t : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            Team nt = sb.registerNewTeam(t.getName());
            nt.setPrefix(t.getPrefix());
            nt.setSuffix(t.getSuffix());
            nt.setColor(t.getColor());
            for (Team.Option option : Team.Option.values()) {
                try { nt.setOption(option, t.getOption(option)); } catch (Exception ignored) {}
            }
            for (String entry : t.getEntries()) {
                nt.addEntry(entry);
            }
        }

        // Criar teams de linha (sb_line_0, sb_line_1, ...) para editar texto sem recriar entries
        for (int i = 0; i < MAX_LINES; i++) {
            Team lineTeam = sb.registerNewTeam("sb_line_" + i);
            lineTeam.addEntry(ENTRIES[i]);
        }

        FileConfiguration cfg = plugin.getConfig();
        String titulo = getLogoChar() + processUnicode(cfg.getString("scoreboard.titulo", "§b§lTLS - III"));

        Objective obj = sb.registerNewObjective("border", "dummy", titulo);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        hidden.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ── Update (sem recriar entries — sem flicker) ────────────────────────────

    private void updateBoard(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) {
            create(p);
            sb = boards.get(p.getUniqueId());
        }
        if (sb == null) return;

        Objective obj = sb.getObjective("border");
        if (obj == null) return;

        FileConfiguration cfg = plugin.getConfig();

        String titulo = getLogoChar() + processUnicode(cfg.getString("scoreboard.titulo", "§b§lTLS - III"));
        obj.setDisplayName(titulo);

        String lFase      = color(cfg.getString("scoreboard.label_fase",         "§8Fase: "));
        String lTempo     = color(cfg.getString("scoreboard.label_tempo",         "§8Tempo: "));
        String lLoc       = color(cfg.getString("scoreboard.label_localizacao",   "§bLocalização:"));
        String lX         = color(cfg.getString("scoreboard.label_x",             "§8 X: "));
        String lZ         = color(cfg.getString("scoreboard.label_z",             "§8 Z: "));
        String lBorda     = color(cfg.getString("scoreboard.label_borda",         "§bBorda:"));
        String lBordaXZ   = color(cfg.getString("scoreboard.label_borda_xz",     "§8 X/Z: "));
        String lBordaDist = color(cfg.getString("scoreboard.label_borda_dist",   "§8 Dist: "));
        String lEquipa    = color(cfg.getString("scoreboard.label_equipa",        "§bEquipa:"));

        String corValor  = color(cfg.getString("scoreboard.cor_valor",  "§b"));
        String corPerigo = color(cfg.getString("scoreboard.cor_perigo", "§c"));
        String corAviso  = color(cfg.getString("scoreboard.cor_aviso",  "§e"));
        String corSeguro = color(cfg.getString("scoreboard.cor_seguro", "§a"));

        int limiarPerigo = cfg.getInt("scoreboard.limiar_perigo", 100);
        int limiarAviso  = cfg.getInt("scoreboard.limiar_aviso",  250);

        String tmplVivo  = color(cfg.getString("scoreboard.linha_jogador_vivo",  "§b⬢ §7{nome}§8 - §c❤ {vida}"));
        String tmplMorto = color(cfg.getString("scoreboard.linha_jogador_morto", "§c§l⬢ §7{nome} - MORTO"));

        // Construir lista de linhas (de cima para baixo)
        java.util.List<String> lines = new java.util.ArrayList<>();

        lines.add(lFase + corValor + borderManager.getCurrentStage() + "/" + borderManager.getTotalStages());
        lines.add(lTempo + corValor + format(borderManager.getRemainingShrinkSeconds()));
        lines.add("");

        Location loc = p.getLocation();
        lines.add(lLoc);
        lines.add(lX + corValor + loc.getBlockX());
        lines.add(lZ + corValor + loc.getBlockZ());
        lines.add(" ");

        WorldBorder wb   = p.getWorld().getWorldBorder();
        double half      = wb.getSize() / 2;
        double dist      = half - Math.max(Math.abs(loc.getX()), Math.abs(loc.getZ()));
        String corDist   = dist <= limiarPerigo ? corPerigo : dist <= limiarAviso ? corAviso : corSeguro;

        lines.add(lBorda);
        lines.add(lBordaXZ + corValor + "±" + (int) half);
        lines.add(lBordaDist + corDist + (int) dist + "m");
        lines.add("  ");

        lines.add(lEquipa);

        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(p.getName());
        if (t != null) {
            int count = 0;
            for (String e : t.getEntries()) {
                Player m = Bukkit.getPlayer(e);
                if (m == null) continue;
                boolean vivo = m.getGameMode() == GameMode.SURVIVAL || m.getGameMode() == GameMode.ADVENTURE;
                if (!vivo || m.isDead() || m.getHealth() <= 0) {
                    lines.add(tmplMorto.replace("{nome}", m.getName()));
                } else {
                    lines.add(tmplVivo.replace("{nome}", m.getName()).replace("{vida}", String.valueOf((int) Math.ceil(m.getHealth()))));
                }
                if (++count >= 4) break;
            }
        } else {
            boolean vivo = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
            if (!vivo || p.isDead() || p.getHealth() <= 0) {
                lines.add(tmplMorto.replace("{nome}", p.getName()));
            } else {
                lines.add(tmplVivo.replace("{nome}", p.getName()).replace("{vida}", String.valueOf((int) Math.ceil(p.getHealth()))));
            }
        }

        // Aplicar linhas via Teams (sem recriar entries = sem flicker)
        // Score decresce de (lines.size()) até 1, de cima para baixo
        int totalLines = lines.size();
        for (int i = 0; i < MAX_LINES; i++) {
            Team lineTeam = sb.getTeam("sb_line_" + i);
            if (lineTeam == null) continue;

            if (i < totalLines) {
                String content = lines.get(i);
                // Dividir em prefix (max 64) + suffix se necessário
                if (content.length() > 64) {
                    lineTeam.setPrefix(content.substring(0, 64));
                    lineTeam.setSuffix(content.substring(64, Math.min(content.length(), 128)));
                } else {
                    lineTeam.setPrefix(content);
                    lineTeam.setSuffix("");
                }
                // Adicionar entry ao objetivo com score fixo (não muda = sem flicker)
                int score = totalLines - i;
                if (sb.getScores(ENTRIES[i]).isEmpty()) {
                    obj.getScore(ENTRIES[i]).setScore(score);
                }
            } else {
                // Esconder linhas não usadas
                lineTeam.setPrefix("");
                lineTeam.setSuffix("");
                sb.resetScores(ENTRIES[i]);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getLogoChar() {
        try {
            FontImageWrapper wrapper = FontImageWrapper.instance("tls_plugin:logo");
            if (wrapper != null) return wrapper.getString();
        } catch (Exception ignored) {}
        return "";
    }

    private String processUnicode(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            char unicode = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(unicode)));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private String color(String s) {
        if (s == null) return "";
        return processUnicode(s);
    }

    private String format(int sec) {
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }
}