package com.tlsplugin.manager;

import java.util.HashMap;
import java.util.Map;

/**
 * Gere os pedidos de pausa por jogador:
 *  - quantos usos cada jogador já consumiu
 *  - qual o pedido pendente (nick → motivo)
 *
 * Um pedido só é contabilizado como "uso" quando for ACEITE por um OP.
 * Se o pedido for ignorado, não conta.
 */
public class PlayerPauseManager {

    // nick (lowercase) → número de pausas já usadas
    private final Map<String, Integer> usos = new HashMap<>();

    // nick (case-insensitive) → motivo do pedido pendente
    private final Map<String, String> pedidosPendentes = new HashMap<>();

    // ----------------------------------------------------------------
    //  Pedidos
    // ----------------------------------------------------------------

    /** Regista um pedido pendente. Não incrementa usos. */
    public void registarPedido(String nick, String motivo) {
        pedidosPendentes.put(nick.toLowerCase(), motivo);
    }

    /** Verifica se existe pedido pendente para este jogador. */
    public boolean temPedidoPendente(String nick) {
        return pedidosPendentes.containsKey(nick.toLowerCase());
    }

    /**
     * Aceita o pedido pendente: incrementa o uso e remove o pedido.
     * Deve ser chamado pelo AceitarPausaCommand.
     */
    public void aceitarPedido(String nick) {
        String key = nick.toLowerCase();
        pedidosPendentes.remove(key);
        usos.merge(key, 1, Integer::sum);
    }

    /** Remove o pedido pendente sem contar como uso (ex: jogo terminou). */
    public void cancelarPedido(String nick) {
        pedidosPendentes.remove(nick.toLowerCase());
    }

    // ----------------------------------------------------------------
    //  Usos
    // ----------------------------------------------------------------

    public int getUsosJogador(String nick) {
        return usos.getOrDefault(nick.toLowerCase(), 0);
    }

    /** Reseta todos os usos e pedidos (ex: novo jogo). */
    public void resetar() {
        usos.clear();
        pedidosPendentes.clear();
    }
}