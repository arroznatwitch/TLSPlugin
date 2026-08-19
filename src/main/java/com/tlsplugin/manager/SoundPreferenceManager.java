package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Preferência de som por jogador (comando /tlssom).
 *
 * <p>Silenciar é sempre <b>local ao jogador</b>: quem desliga deixa de ouvir os sons de
 * ambiente do plugin (música de pausa, contagem decrescente, aviso de bordas, morte
 * súbita), mas isso não afeta mais ninguém no servidor.</p>
 *
 * <p>Só cobre sons de ambiente/anúncio — os sons de feedback da própria ação (comer a maçã
 * especial, beber a poção, o grappler a partir) continuam a tocar, porque são a resposta
 * direta a algo que o jogador acabou de fazer e sem eles a ação parece não ter acontecido.</p>
 *
 * <p>A escolha fica gravada em {@code sons.yml}, por UUID, para sobreviver a reconexões e
 * reinícios do servidor.</p>
 */
public class SoundPreferenceManager {

    private final Tlsplugin plugin;
    private final File file;
    /** UUIDs de quem escolheu NÃO ouvir os sons de ambiente do plugin. */
    private final Set<UUID> silenciados = new HashSet<>();

    public SoundPreferenceManager(Tlsplugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "sons.yml");
        load();
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    public boolean isSilenciado(UUID uuid) {
        return uuid != null && silenciados.contains(uuid);
    }

    /** Alterna a preferência do jogador. @return true se ficou silenciado. */
    public boolean alternar(UUID uuid) {
        boolean agoraSilenciado;
        if (silenciados.contains(uuid)) {
            silenciados.remove(uuid);
            agoraSilenciado = false;
        } else {
            silenciados.add(uuid);
            agoraSilenciado = true;
        }
        save();
        return agoraSilenciado;
    }

    // ── Reprodução ────────────────────────────────────────────────────────────

    /** Toca um som de ambiente a um jogador, respeitando a preferência dele. */
    public void play(Player p, Sound sound, float volume, float pitch) {
        if (p == null || sound == null) return;
        if (isSilenciado(p.getUniqueId())) return;
        p.playSound(p.getLocation(), sound, volume, pitch);
    }

    // ── Persistência ──────────────────────────────────────────────────────────

    private void load() {
        silenciados.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yaml.getStringList("silenciados")) {
            try {
                silenciados.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[TLS] UUID inválido em sons.yml: " + raw);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lista = new ArrayList<>();
        for (UUID uuid : silenciados) lista.add(uuid.toString());
        yaml.set("silenciados", lista);
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[TLS] Não foi possível gravar sons.yml: " + e.getMessage());
        }
    }
}
