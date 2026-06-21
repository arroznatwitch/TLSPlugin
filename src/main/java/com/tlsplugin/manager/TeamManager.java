package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fonte única da verdade para as 9 equipas (Bukkit Scoreboard Teams): ícone + cor da nametag.
 *
 * <p><b>Porque é que as equipas "deixavam de existir" depois de um crash:</b> as Teams do Bukkit
 * vivem no {@code scoreboard.dat} do mundo, um ficheiro separado do LuckPerms. Numa queda abrupta
 * do servidor, esse ficheiro pode perder as últimas alterações por não ter sido gravado a tempo
 * (ao contrário do LuckPerms, que guarda o grupo de cada jogador de forma independente e mais
 * fiável). O resultado era as equipas/cores desaparecerem do scoreboard mesmo com o LuckPerms
 * correto, obrigando a repetir {@code /teamjoin} manualmente para "reconstruir" a Team.</p>
 *
 * <p>Esta classe corrige isso em dois pontos: (1) garante sempre que as 9 Teams existem e têm a
 * cor certa (auto-criação/auto-cura, chamado ao ligar o plugin), e (2) sempre que um jogador entra,
 * sincroniza a Team dele a partir do grupo que o LuckPerms diz que ele tem — o LuckPerms passa a
 * ser sempre a fonte da verdade, sem precisares de repetir o comando à mão.</p>
 *
 * <p><b>Sobre a cor da nametag não corresponder ao LuckPerms quando se usa "#" (hex):</b> a
 * nametag/Team do Minecraft vanilla só suporta as 16 cores clássicas (§0–§f) — não suporta cores
 * hex (#RRGGBB). Se o grupo no LuckPerms tiver uma cor definida em hex, essa cor nunca vai
 * conseguir aparecer exatamente igual na nametag, seja qual for o plugin; é uma limitação do
 * cliente do Minecraft. Por isso aqui aplicamos sempre uma das 16 cores clássicas (a mesma já usada
 * nos blocos de lã/itens de equipa no config.yml), para que pelo menos fique sempre consistente e
 * previsível, em vez de cair para a cor por omissão quando o hex não é suportado.</p>
 */
public class TeamManager {

    private final Tlsplugin plugin;

    /** id da equipa → ícone (glyph de fonte customizada usado no prefixo da Team). */
    private static final Map<String, String> ICONS = new LinkedHashMap<>();
    /** id da equipa → cor da nametag (uma das 16 cores legacy — única opção suportada pelo vanilla). */
    private static final Map<String, ChatColor> COLORS = new LinkedHashMap<>();
    static {
        ICONS.put("red",    "󰀁");
        ICONS.put("blue",   "󰀂");
        ICONS.put("green",  "󰀃");
        ICONS.put("yellow", "󰀄");
        ICONS.put("admin",  "󰀅");
        ICONS.put("pink",   "󰀆");
        ICONS.put("grey",   "󰀇");
        ICONS.put("purple", "󰀈");
        ICONS.put("orange", "󰀉");

        COLORS.put("red",    ChatColor.RED);
        COLORS.put("blue",   ChatColor.BLUE);
        COLORS.put("green",  ChatColor.GREEN);
        COLORS.put("yellow", ChatColor.YELLOW);
        COLORS.put("admin",  ChatColor.WHITE);
        COLORS.put("pink",   ChatColor.LIGHT_PURPLE);
        COLORS.put("grey",   ChatColor.GRAY);
        COLORS.put("purple", ChatColor.DARK_PURPLE);
        COLORS.put("orange", ChatColor.GOLD);
    }

    /** Equipas que os jogadores podem escolher (exclui "admin", que é só para staff). */
    private static final String[] PLAYER_TEAMS = {"red", "blue", "green", "yellow", "pink", "grey", "purple", "orange"};

    public TeamManager(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Garante que as 9 Teams existem e reaplica sempre o ícone + cor certos, mesmo a Teams já
     * existentes (auto-cura). Seguro chamar várias vezes (no enable, no reload, etc.).
     *
     * @return {@code int[]}{criadas, atualizadas}
     */
    public int[] ensureTeamsExist() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        int created = 0, updated = 0;

        for (Map.Entry<String, ChatColor> entry : COLORS.entrySet()) {
            String id    = entry.getKey();
            ChatColor cor = entry.getValue();
            String icone = ICONS.get(id);

            Team team = scoreboard.getTeam(id);
            if (team == null) {
                team = scoreboard.registerNewTeam(id);
                created++;
            } else {
                updated++;
            }
            team.setColor(cor);
            if (icone != null) team.prefix(Component.text(icone + " "));
        }
        return new int[]{created, updated};
    }

    /**
     * Sincroniza a Team do jogador a partir do grupo que o LuckPerms diz que ele tem.
     * Chamado ao entrar no servidor (e ao ligar o plugin para quem já está online), para que a
     * cor/Team se "auto-cure" sozinha mesmo que o scoreboard.dat tenha perdido a informação
     * num crash — sem precisares de repetir /teamjoin à mão.
     */
    public void syncPlayer(Player player) {
        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) return;

        LuckPerms lp = provider.getProvider();
        lp.getUserManager().loadUser(player.getUniqueId()).thenAcceptAsync(user -> {
            String equipa = null;
            for (String id : PLAYER_TEAMS) {
                boolean tem = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(n -> n.getGroupName().equalsIgnoreCase(id));
                if (tem) { equipa = id; break; }
            }
            if (equipa == null) return; // jogador ainda não escolheu equipa — nada a sincronizar

            String equipaFinal = equipa;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

                for (String id : PLAYER_TEAMS) {
                    if (id.equals(equipaFinal)) continue;
                    Team outra = scoreboard.getTeam(id);
                    if (outra != null) outra.removeEntry(player.getName());
                }

                Team team = scoreboard.getTeam(equipaFinal);
                if (team == null) {
                    // Auto-cura: a Team nem existia (scoreboard.dat perdido) — recria-a já.
                    ensureTeamsExist();
                    team = scoreboard.getTeam(equipaFinal);
                }
                if (team != null && !team.hasEntry(player.getName())) {
                    team.addEntry(player.getName());
                }
            });
        });
    }
}
