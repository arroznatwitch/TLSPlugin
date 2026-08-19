package com.tlsplugin.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

/**
 * Resolve um {@link Sound} a partir do nome escrito no config.
 *
 * <p>Na 1.21+ o {@code Sound} deixou de ser um enum e passou a vir de um registry, pelo que
 * {@code Sound.valueOf(...)}, {@code Sound#name()} e {@code Sound#key()} estão todos marcados
 * para remoção. Aqui usamos apenas API de registry ({@link Registry#get} e
 * {@link Registry#getKey}), que não está depreciada.</p>
 *
 * <p>Aceita os dois formatos, para o config continuar a funcionar como está e já suportar o
 * formato novo:</p>
 * <ul>
 *   <li>chave do registry — {@code block.note_block.pling}</li>
 *   <li>estilo enum antigo — {@code BLOCK_NOTE_BLOCK_PLING}</li>
 * </ul>
 *
 * <p>Nota: não dá para converter um pelo outro só com {@code replace('_', '.')}, porque há
 * chaves que têm underscores dentro de um segmento ({@code block.note_block.pling},
 * {@code ui.toast.challenge_complete}). Por isso, para o formato antigo, comparamos contra as
 * chaves reais do registry já normalizadas.</p>
 */
public final class SoundUtils {

    private SoundUtils() {}

    /** @return o som correspondente, ou {@code null} se o nome não existir. */
    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String limpo = name.trim();

        // 1) Formato de chave do registry (ex: "block.lava.pop").
        NamespacedKey key = NamespacedKey.minecraft(limpo.toLowerCase());
        Sound direto = Registry.SOUNDS.get(key);
        if (direto != null) return direto;

        // 2) Formato estilo enum (ex: "BLOCK_LAVA_POP") — compara com as chaves reais
        //    normalizadas (pontos → underscore).
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey k = Registry.SOUNDS.getKey(s);
            if (k == null) continue;
            if (k.getKey().replace('.', '_').equalsIgnoreCase(limpo)) return s;
        }
        return null;
    }

    /** Como {@link #resolve(String)}, mas devolve {@code fallback} se o nome não existir. */
    public static Sound resolveOr(String name, Sound fallback) {
        Sound s = resolve(name);
        return s != null ? s : fallback;
    }
}
