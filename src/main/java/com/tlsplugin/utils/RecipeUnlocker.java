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

    // Receitas registadas pelo TLSPlugin (namespace do plugin)
    private static final List<String> PLUGIN_KEYS = Arrays.asList(
            "tlsplugin:tls_special_apple",
            "tlsplugin:grappler_item",
            "tlsplugin:tls_tracker_compass",
            "tlsplugin:tls_kit_parcial",
            "tlsplugin:tls_kit_completo",
            "tlsplugin:goldpotion_item"
    );

    // Receitas das armas registadas pelo ItemsAdder (namespace tls_plugin)
    private static final List<String> WEAPON_KEYS = Arrays.asList(
            "tls_plugin:wooden_longsword",   "tls_plugin:wooden_broadsword",
            "tls_plugin:wooden_hammer",      "tls_plugin:wooden_hatchet",
            "tls_plugin:wooden_sickle",      "tls_plugin:wooden_scythe",
            "tls_plugin:wooden_battleaxe",
            "tls_plugin:stone_longsword",    "tls_plugin:stone_broadsword",
            "tls_plugin:stone_hammer",       "tls_plugin:stone_hatchet",
            "tls_plugin:stone_sickle",       "tls_plugin:stone_scythe",
            "tls_plugin:stone_battleaxe",
            "tls_plugin:iron_longsword",     "tls_plugin:iron_broadsword",
            "tls_plugin:iron_hammer",        "tls_plugin:iron_hatchet",
            "tls_plugin:iron_sickle",        "tls_plugin:iron_scythe",
            "tls_plugin:iron_battleaxe",
            "tls_plugin:golden_longsword",   "tls_plugin:golden_broadsword",
            "tls_plugin:golden_hammer",      "tls_plugin:golden_hatchet",
            "tls_plugin:golden_sickle",      "tls_plugin:golden_scythe",
            "tls_plugin:golden_battleaxe",
            "tls_plugin:copper_longsword",   "tls_plugin:copper_broadsword",
            "tls_plugin:copper_hammer",      "tls_plugin:copper_hatchet",
            "tls_plugin:copper_sickle",      "tls_plugin:copper_scythe",
            "tls_plugin:copper_battleaxe",
            "tls_plugin:diamond_longsword",  "tls_plugin:diamond_broadsword",
            "tls_plugin:diamond_hammer",     "tls_plugin:diamond_hatchet",
            "tls_plugin:diamond_sickle",     "tls_plugin:diamond_scythe",
            "tls_plugin:diamond_battleaxe"
    );

    public static void register(JavaPlugin plugin) {
        // desbloqueia para quem já está online (reloads, etc.)
        for (Player p : Bukkit.getOnlinePlayers()) {
            giveAllTo(plugin, p);
        }

        // desbloqueia no join — com delay para garantir que o jogador está pronto
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> giveAllTo(plugin, e.getPlayer()), 20L);
            }
        }, plugin);
    }

    /** Chamado externamente, ex: no /startgame */
    public static void unlockAll(JavaPlugin plugin, Player player) {
        giveAllTo(plugin, player);
    }

    private static void giveAllTo(JavaPlugin plugin, Player player) {
        for (String raw : PLUGIN_KEYS) {
            discover(plugin, player, raw);
        }
        for (String raw : WEAPON_KEYS) {
            discover(plugin, player, raw);
        }
    }

    private static void discover(JavaPlugin plugin, Player player, String raw) {
        NamespacedKey key = parseKey(plugin, raw);
        Recipe r = Bukkit.getRecipe(key);
        if (r != null) {
            player.discoverRecipe(key);
        } else {
            plugin.getLogger().fine("[RecipeUnlocker] Recipe não encontrada: " + key);
        }
    }

    private static NamespacedKey parseKey(JavaPlugin plugin, String raw) {
        NamespacedKey k = NamespacedKey.fromString(raw);
        return (k != null) ? k : new NamespacedKey(plugin, raw);
    }
}