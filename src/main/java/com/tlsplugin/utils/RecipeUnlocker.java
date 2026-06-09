package com.tlsplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public final class RecipeUnlocker {

    private static final List<String> RAW_KEYS = Arrays.asList(
            "tls_tracker_compass",
            "tls_kit_parcial",
            "tls_kit_completo",
            "tls_plugin:tls_special_apple",
            "tls_plugin:grappler_item"
    );

    public static void register(JavaPlugin plugin) {
        // desbloqueia para quem já está online (reloads, etc.)
        for (Player p : Bukkit.getOnlinePlayers()) {
            giveAllTo(plugin, p);
        }

        // desbloqueia no join
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                giveAllTo(plugin, e.getPlayer());
            }
        }, plugin);
    }

    private static void giveAllTo(JavaPlugin plugin, Player player) {
        for (String raw : RAW_KEYS) {
            NamespacedKey key = parseKey(plugin, raw);

            // só tenta descobrir se a recipe realmente existe
            Recipe r = Bukkit.getRecipe(key);
            if (r != null) {
                player.discoverRecipe(key);
            } else {
                // opcional: loga para te ajudar a detectar chaves erradas
                plugin.getLogger().fine("[RecipeUnlocker] Recipe não encontrada: " + key);
            }
        }
    }

    private static NamespacedKey parseKey(JavaPlugin plugin, String raw) {
        NamespacedKey k = NamespacedKey.fromString(raw);
        return (k != null) ? k : new NamespacedKey(plugin, raw);
    }
}
