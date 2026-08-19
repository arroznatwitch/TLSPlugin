package com.tlsplugin.utils;

import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Confirmação em dois passos para comandos destrutivos.
 *
 * <p>Sem isto, alguém podia escrever {@code /startgame confirmar} logo à primeira e saltar
 * por completo o aviso — o "confirmar" deixava de ser uma confirmação e passava a ser só
 * um argumento a decorar. Aqui, o {@code confirmar} só é aceite se o mesmo sender tiver
 * corrido o comando sem argumentos primeiro (o passo que mostra o aviso), e dentro de uma
 * janela curta de tempo.</p>
 *
 * <p>O pedido é consumido assim que é confirmado, por isso cada confirmação serve uma vez só.</p>
 */
public final class ConfirmationManager {

    private ConfirmationManager() {}

    /** Quanto tempo um pedido de confirmação continua válido. */
    private static final long VALIDADE_MS = 30_000L;

    /** chave = nome do sender + ":" + id do comando → instante em que o aviso foi mostrado. */
    private static final Map<String, Long> pendentes = new HashMap<>();

    private static String chave(CommandSender sender, String comandoId) {
        return sender.getName().toLowerCase() + ":" + comandoId.toLowerCase();
    }

    /** Regista que o sender viu o aviso e pode confirmar nos próximos segundos. */
    public static void pedir(CommandSender sender, String comandoId) {
        pendentes.put(chave(sender, comandoId), System.currentTimeMillis());
    }

    /**
     * Consome o pedido pendente do sender para este comando.
     *
     * @return true se havia um pedido válido (dentro da janela de tempo); false se não
     *         existia ou já tinha expirado — nesse caso o comando NÃO deve prosseguir.
     */
    public static boolean confirmar(CommandSender sender, String comandoId) {
        Long pedidoEm = pendentes.remove(chave(sender, comandoId));
        if (pedidoEm == null) return false;
        return (System.currentTimeMillis() - pedidoEm) <= VALIDADE_MS;
    }

    /** Segundos que um pedido de confirmação continua válido (para mensagens ao utilizador). */
    public static int getValidadeSegundos() {
        return (int) (VALIDADE_MS / 1000L);
    }
}
