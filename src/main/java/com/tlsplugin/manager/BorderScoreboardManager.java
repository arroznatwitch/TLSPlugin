package com.tlsplugin.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.score.ScoreFormat;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisplayScoreboard;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective.ObjectiveMode;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective.RenderType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore.Action;
import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scoreboard (sidebar) 100% client-side, enviada via pacotes do PacketEvents.
 *
 * <p>Ao contrário da abordagem antiga ({@code player.setScoreboard(...)}), esta
 * implementação NUNCA substitui a scoreboard do servidor do jogador. Isso significa
 * que as teams da scoreboard principal (cores de nametag, prefixos de equipa, etc.,
 * geridas por outras partes do plugin) continuam intactas — só enviamos a sidebar
 * por cima, diretamente ao cliente.</p>
 *
 * <p>Anti-flicker: a sidebar é criada uma única vez e cada atualização envia apenas
 * as linhas que mudaram (diff). Nunca removemos + readicionamos a mesma linha, que é
 * a causa clássica do flicker. Os números vermelhos à direita são escondidos com
 * {@link ScoreFormat#blankScore()} (disponível em 1.20.3+).</p>
 */
public class BorderScoreboardManager {

    private static final String OBJECTIVE = "tls_border";
    private static final int SIDEBAR_SLOT = 1; // 0 = tab list, 1 = sidebar, 2 = below name
    private static final int MAX_LINES = 20;

    /** Chaves (score holders) únicas e invisíveis por linha. Não são renderizadas
     *  porque enviamos sempre um display name por linha (1.20.3+). */
    private static final String[] ENTRIES;
    static {
        ENTRIES = new String[MAX_LINES];
        String alpha = "0123456789abcdefghij"; // 20 caracteres únicos
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + alpha.charAt(i);
        }
    }

    private final Tlsplugin plugin;
    private final BorderManager borderManager;

    // Estado por jogador
    private final Set<UUID> created = new HashSet<>();              // sidebar já criada no cliente
    private final Set<UUID> hidden = new HashSet<>();               // toggle off
    private final Map<UUID, List<String>> lastLines = new HashMap<>();
    private final Map<UUID, String> lastTitle = new HashMap<>();

    private BukkitTask updaterTask;

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
            hideBoard(p);
            return false; // agora escondida
        }
    }

    public boolean isHidden(Player p) {
        return hidden.contains(p.getUniqueId());
    }

    // ── Updater ───────────────────────────────────────────────────────────────

    private void startUpdater() {
        updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                if (hidden.contains(id)) continue;
                if (created.contains(id)) {
                    updateBoard(p);
                } else {
                    // Auto-cura: tenta (re)criar a sidebar até conseguir.
                    // Cobre o reload com jogadores online e qualquer CREATE perdido.
                    create(p);
                }
            }
        }, 0L, 20L);
    }

    // ── Ciclo de vida da sidebar ──────────────────────────────────────────────

    /** Cria (ou recria) a sidebar no cliente e repinta todas as linhas. */
    public void create(Player p) {
        if (!p.isOnline()) return;          // evita estado órfão se o jogador já saiu
        UUID id = p.getUniqueId();
        if (hidden.contains(id)) return;
        if (created.contains(id)) {         // já existe — não reenvia CREATE, só atualiza
            updateBoard(p);
            return;
        }

        String title = title();
        // Só marca como criada se o CREATE foi mesmo enviado; caso contrário o
        // updater volta a tentar no próximo tick (auto-cura, sem desync silencioso).
        if (!send(p, new WrapperPlayServerScoreboardObjective(
                OBJECTIVE, ObjectiveMode.CREATE, toComponent(title), RenderType.INTEGER, ScoreFormat.blankScore()))) {
            return;
        }
        send(p, new WrapperPlayServerDisplayScoreboard(SIDEBAR_SLOT, OBJECTIVE));

        created.add(id);
        lastTitle.put(id, title);
        lastLines.remove(id); // força repintura completa
        updateBoard(p);
    }

    /** Remove a sidebar do cliente (toggle off) sem mexer no estado de {@code hidden}. */
    private void hideBoard(Player p) {
        send(p, new WrapperPlayServerScoreboardObjective(
                OBJECTIVE, ObjectiveMode.REMOVE, Component.empty(), null));
        clearState(p.getUniqueId());
    }

    /** Limpeza ao sair do servidor. */
    public void remove(Player p) {
        clearState(p.getUniqueId());
        hidden.remove(p.getUniqueId());
    }

    private void clearState(UUID id) {
        created.remove(id);
        lastLines.remove(id);
        lastTitle.remove(id);
    }

    /** Cancela o updater e remove a sidebar de todos (chamado no onDisable). */
    public void shutdown() {
        if (updaterTask != null) updaterTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (created.contains(p.getUniqueId())) {
                send(p, new WrapperPlayServerScoreboardObjective(
                        OBJECTIVE, ObjectiveMode.REMOVE, Component.empty(), null));
            }
        }
        created.clear();
        lastLines.clear();
        lastTitle.clear();
    }

    // ── Update (diff — sem flicker) ───────────────────────────────────────────

    private void updateBoard(Player p) {
        UUID id = p.getUniqueId();
        if (!created.contains(id)) return; // criada via join/enable/toggle

        // Título (só reenvia se mudou)
        String title = title();
        if (!title.equals(lastTitle.get(id))) {
            send(p, new WrapperPlayServerScoreboardObjective(
                    OBJECTIVE, ObjectiveMode.UPDATE, toComponent(title), RenderType.INTEGER, ScoreFormat.blankScore()));
            lastTitle.put(id, title);
        }

        // Linhas
        List<String> lines = buildLines(p);
        int newCount = Math.min(lines.size(), MAX_LINES);
        List<String> prev = lastLines.getOrDefault(id, Collections.emptyList());

        // Score fixo por índice (independente do nº de linhas) → ordem estável,
        // mudar a contagem nunca desloca as linhas existentes.
        for (int i = 0; i < newCount; i++) {
            String line = lines.get(i);
            if (i >= prev.size() || !line.equals(prev.get(i))) {
                send(p, new WrapperPlayServerUpdateScore(
                        ENTRIES[i], Action.CREATE_OR_UPDATE_ITEM, OBJECTIVE,
                        MAX_LINES - i, toComponent(line), ScoreFormat.blankScore()));
            }
        }
        // Remove linhas que existiam antes mas já não existem
        for (int i = newCount; i < prev.size(); i++) {
            send(p, new WrapperPlayServerUpdateScore(
                    ENTRIES[i], Action.REMOVE_ITEM, OBJECTIVE, 0, null, null));
        }

        lastLines.put(id, new ArrayList<>(lines.subList(0, newCount)));
    }

    // ── Construção das linhas (texto legacy "§") ──────────────────────────────

    private List<String> buildLines(Player p) {
        FileConfiguration cfg = plugin.getConfig();

        String lFase      = color(cfg.getString("scoreboard.label_fase",         "§8Fase: "));
        String lTempo     = color(cfg.getString("scoreboard.label_tempo",        "§8Tempo: "));
        String lLoc       = color(cfg.getString("scoreboard.label_localizacao",  "§bLocalização:"));
        String lX         = color(cfg.getString("scoreboard.label_x",            "§8 X: "));
        String lZ         = color(cfg.getString("scoreboard.label_z",            "§8 Z: "));
        String lBorda     = color(cfg.getString("scoreboard.label_borda",        "§bBorda:"));
        String lBordaXZ   = color(cfg.getString("scoreboard.label_borda_xz",     "§8 X/Z: "));
        String lBordaDist = color(cfg.getString("scoreboard.label_borda_dist",   "§8 Dist: "));
        String lEquipa    = color(cfg.getString("scoreboard.label_equipa",       "§bEquipa:"));

        String corValor  = color(cfg.getString("scoreboard.cor_valor",  "§b"));
        String corPerigo = color(cfg.getString("scoreboard.cor_perigo", "§c"));
        String corAviso  = color(cfg.getString("scoreboard.cor_aviso",  "§e"));
        String corSeguro = color(cfg.getString("scoreboard.cor_seguro", "§a"));

        int limiarPerigo = cfg.getInt("scoreboard.limiar_perigo", 100);
        int limiarAviso  = cfg.getInt("scoreboard.limiar_aviso",  250);

        String tmplVivo  = color(cfg.getString("scoreboard.linha_jogador_vivo",  "§b⬢ §7{nome}§8 - §c❤ {vida}"));
        String tmplMorto = color(cfg.getString("scoreboard.linha_jogador_morto", "§c§l⬢ §7{nome} - MORTO"));

        List<String> lines = new ArrayList<>();

        lines.add(lFase + corValor + borderManager.getCurrentStage() + "/" + borderManager.getTotalStages());
        lines.add(lTempo + corValor + format(borderManager.getRemainingShrinkSeconds()));
        lines.add("");

        Location loc = p.getLocation();
        lines.add(lLoc);
        lines.add(lX + corValor + loc.getBlockX());
        lines.add(lZ + corValor + loc.getBlockZ());
        lines.add(" ");

        WorldBorder wb = p.getWorld().getWorldBorder();
        double half    = wb.getSize() / 2;
        double dist    = half - Math.max(Math.abs(loc.getX()), Math.abs(loc.getZ()));
        String corDist = dist <= limiarPerigo ? corPerigo : dist <= limiarAviso ? corAviso : corSeguro;

        lines.add(lBorda);
        lines.add(lBordaXZ + corValor + "±" + (int) half);
        lines.add(lBordaDist + corDist + (int) dist + "m");

        // A linha de jogador vivo/morto só faz sentido em modo Equipas. Usamos a config
        // "tipo_jogo" (a fonte da verdade do modo de jogo) — e não se o jogador tem uma Team
        // atribuída, porque isso pode continuar verdadeiro em Solo (ex.: resíduo de testes).
        boolean modoEquipas = cfg.getString("tipo_jogo", "solo").equalsIgnoreCase("equipas");
        if (modoEquipas) {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(p.getName());
            if (t != null) {
                lines.add("  ");
                lines.add(lEquipa);
                int count = 0;
                for (String e : t.getEntries()) {
                    Player m = Bukkit.getPlayer(e);
                    if (m == null) continue;
                    lines.add(playerLine(m, tmplVivo, tmplMorto));
                    if (++count >= 4) break;
                }
            }
        }

        // Rodapé (última linha) — centrado em relação à linha mais larga já existente, para
        // ficar equilibrado visualmente quer em Solo quer em Equipas.
        int targetWidth = 0;
        for (String l : lines) targetWidth = Math.max(targetWidth, textWidth(l));

        String rodape   = cfg.getString("scoreboard.rodape", "craftandhelps.com");
        String corRodape = color(cfg.getString("scoreboard.cor_rodape", "§7§o"));
        lines.add("  ");
        lines.add(centerText(corRodape + rodape, targetWidth));

        return lines;
    }

    private String playerLine(Player m, String tmplVivo, String tmplMorto) {
        boolean vivo = m.getGameMode() == GameMode.SURVIVAL || m.getGameMode() == GameMode.ADVENTURE;
        if (!vivo || m.isDead() || m.getHealth() <= 0) {
            return tmplMorto.replace("{nome}", m.getName());
        }
        return tmplVivo
                .replace("{nome}", m.getName())
                .replace("{vida}", String.valueOf((int) Math.ceil(m.getHealth())));
    }

    private String title() {
        FileConfiguration cfg = plugin.getConfig();
        return getLogoChar() + processUnicode(cfg.getString("scoreboard.titulo", "§b§lTLS - III"));
    }

    // ── Pacotes ───────────────────────────────────────────────────────────────

    private boolean send(Player p, PacketWrapper<?> wrapper) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(p, wrapper);
            return true;
        } catch (Throwable t) {
            // PacketEvents ainda não pronto, ou canal a fechar — sinaliza falha
            // para que create() não marque a sidebar como criada.
            return false;
        }
    }

    private Component toComponent(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
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

    // ── Centralização de texto (largura real de pixels da fonte default) ───────

    private static final Map<Character, Integer> CHAR_WIDTHS = new HashMap<>();
    private static final int DEFAULT_CHAR_WIDTH = 5;
    private static final int SPACE_WIDTH        = 3;
    static {
        String[] narrow1 = {"i", "l", ".", ",", ":", ";", "'", "!", "|", "I"};
        for (String s : narrow1) CHAR_WIDTHS.put(s.charAt(0), 1);
        CHAR_WIDTHS.put('I', 3);
        String[] w3 = {"[", "]", "t", "I"};
        for (String s : w3) CHAR_WIDTHS.put(s.charAt(0), 3);
        String[] w4 = {"f", "k", "\"", "<", ">", "(", ")", "{", "}", "*"};
        for (String s : w4) CHAR_WIDTHS.put(s.charAt(0), 4);
        CHAR_WIDTHS.put('@', 6);
    }

    /** Largura aproximada (em pixels) de um texto, ignorando os códigos de cor "§x". */
    private int textWidth(String text) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                bold = (code == 'l');
                if (code == 'r') bold = false;
                i++;
                continue;
            }
            int w = c == ' ' ? SPACE_WIDTH : CHAR_WIDTHS.getOrDefault(c, DEFAULT_CHAR_WIDTH);
            width += w + 1 + (bold ? 1 : 0); // +1 = espaço entre carateres da fonte default
        }
        return width;
    }

    /** Centra {@code text} adicionando espaços à esquerda em relação a {@code targetWidth} (px). */
    private String centerText(String text, int targetWidth) {
        int diff = targetWidth - textWidth(text);
        if (diff <= 0) return text;
        int spaces = diff / (2 * (SPACE_WIDTH + 1));
        return " ".repeat(Math.max(0, spaces)) + text;
    }
}