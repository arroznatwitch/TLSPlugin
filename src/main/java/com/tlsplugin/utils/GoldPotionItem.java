package com.tlsplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import dev.lone.itemsadder.api.CustomStack;


public class GoldPotionItem implements Listener {

    private static JavaPlugin plugin;
    private static final String CUSTOM_ITEM_ID = "tls_plugin:goldpotion_item";

    private GoldPotionItem(JavaPlugin plugin) {
        // privado — usar register()
    }

    public static void register(JavaPlugin pl) {
        plugin = pl;

        // Regista listener (necessário se no futuro tiver eventos aqui)
        Bukkit.getPluginManager().registerEvents(new GoldPotionItem(plugin), plugin);


        // Remove receita anterior (evita duplicados em reload)
        NamespacedKey key = new NamespacedKey(plugin, "goldpotion_item");
        if (!plugin.getConfig().getBoolean("craft_book.special_items.goldpotion_item.habilitar_receita", true)) {
            Bukkit.removeRecipe(key);
            return;
        }
        Bukkit.removeRecipe(key);

        CustomStack GoldPotionStack = CustomStack.getInstance(CUSTOM_ITEM_ID);
        if (GoldPotionStack == null) {
            plugin.getLogger().severe("[GoldPotionItem] Item '" + CUSTOM_ITEM_ID + "' não encontrado no ItemsAdder!");
            return;
        }
        ItemStack special = GoldPotionStack.getItemStack().clone();

        // Aplica lore do config
        java.util.List<String> lore = plugin.getConfig().getStringList("gold_potion.lore");
        if (lore != null && !lore.isEmpty()) {
            ItemUtils.applyLore(special, lore);
        }

        ShapedRecipe recipe = new ShapedRecipe(key, special);
        recipe.shape("BBB",
                "BAB",
                "BBB");
        recipe.setIngredient('A', Material.POTION);
        recipe.setIngredient('B', Material.GOLD_INGOT);
        Bukkit.addRecipe(recipe);

        plugin.getLogger().info("[GoldPotionItem] Receita registada com sucesso!");
    }

}


