package com.tlsplugin.command;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProntoCommand implements CommandExecutor {

    private final Tlsplugin plugin;
    private final Set<UUID> prontos = new HashSet<>();

    public ProntoCommand(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    /** Marca/desmarca um jogador como pronto e actualiza os OPs. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        String prefixo = plugin.getConfig().getString("pronto.prefixo",
                "§f[§bTLS§f] ");

        // Toggle
        if (prontos.contains(p.getUniqueId())) {
            prontos.remove(p.getUniqueId());
            String msg = plugin.getConfig().getString("pronto.mensagem_nao_pronto",
                    "§f[§bTLS§f] §cJá não estás pronto.");
            p.sendMessage(msg);
        } else {
            prontos.add(p.getUniqueId());
            String msg = plugin.getConfig().getString("pronto.mensagem_pronto",
                    "§f[§bTLS§f] §aEstás agora marcado como pronto!");
            p.sendMessage(msg);
        }

        enviarListaOps();
        return true;
    }

    /** Remove um jogador da lista (ex: ao sair do servidor). */
    public void remover(UUID uuid) {
        if (prontos.remove(uuid)) {
            enviarListaOps();
        }
    }

    /** Limpa toda a lista (ex: no /startgame ou /endgame). */
    public void limpar() {
        prontos.clear();
    }

    // ── Envia a lista actualizada a todos os OPs online ───────────────────────

    private void enviarListaOps() {
        String cabecalho  = plugin.getConfig().getString("pronto.cabecalho",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String rodape     = plugin.getConfig().getString("pronto.rodape",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String titulo     = plugin.getConfig().getString("pronto.titulo",
                "§b§lLista de Jogadores");
        String linhaPronto    = plugin.getConfig().getString("pronto.linha_pronto",
                "§a[ PRONTO ] §f{nome}");
        String linhaNaoPronto = plugin.getConfig().getString("pronto.linha_nao_pronto",
                "§c[ NÃO ESTÁ PRONTO ] §f{nome}");
        String msgTodosProntos = plugin.getConfig().getString("pronto.mensagem_todos_prontos",
                "§f[§bTLS§f] §a§lTodos os jogadores estão prontos! O jogo pode começar.");

        // Construir lista apenas com jogadores em modo Survival/Adventure
        StringBuilder sb = new StringBuilder();
        sb.append(cabecalho).append("\n");
        sb.append(titulo).append("\n");

        int totalJogadores = 0;
        int totalProntos   = 0;

        for (Player online : Bukkit.getOnlinePlayers()) {
            // Ignorar OPs na lista (são os operadores, não jogadores)
            if (online.isOp()) continue;

            totalJogadores++;
            boolean pronto = prontos.contains(online.getUniqueId());
            if (pronto) totalProntos++;

            String linha = (pronto ? linhaPronto : linhaNaoPronto)
                    .replace("{nome}", online.getName());
            sb.append(linha).append("\n");
        }

        sb.append(rodape);

        String lista = sb.toString();

        // Enviar apenas a OPs
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                op.sendMessage(lista);
            }
        }

        // Se todos os jogadores (não-OP) estiverem prontos e houver pelo menos 1
        if (totalJogadores > 0 && totalProntos == totalJogadores) {
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.isOp()) {
                    op.sendMessage(msgTodosProntos);
                }
            }
        }
    }

    public Set<UUID> getProntos() {
        return prontos;
    }
}