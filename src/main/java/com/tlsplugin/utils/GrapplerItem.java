package com.tlsplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import dev.lone.itemsadder.api.CustomStack;

public class GrapplerItem implements Listener {

    private static JavaPlugin plugin;
    private static final String CUSTOM_ITEM_ID = "tls_plugin:grappler_item";

    private GrapplerItem(JavaPlugin plugin) {
        // privado — usar register()
    }

    public static void register(JavaPlugin pl) {
        plugin = pl;

        // Regista listener (necessário se no futuro tiver eventos aqui)
        Bukkit.getPluginManager().registerEvents(new GrapplerItem(plugin), plugin);

        // Remove receita anterior (evita duplicados em reload)
        NamespacedKey key = new NamespacedKey(plugin, "grappler_item");
        if (!plugin.getConfig().getBoolean("craft_book.special_items.grappler_item.habilitar_receita", true)) {
            Bukkit.removeRecipe(key);
            return;
        }
        Bukkit.removeRecipe(key);

        CustomStack grapplerItemStack = CustomStack.getInstance(CUSTOM_ITEM_ID);
        if (grapplerItemStack == null) {
            plugin.getLogger().severe("[GrapplerItem] Item '" + CUSTOM_ITEM_ID + "' não encontrado no ItemsAdder!");
            return;
        }
        ItemStack special = grapplerItemStack.getItemStack().clone();

        // Aplica lore do config
        java.util.List<String> lore = plugin.getConfig().getStringList("grappler_item.lore");
        if (lore != null && !lore.isEmpty()) {
            ItemUtils.applyLore(special, lore);
        }

        ShapedRecipe recipe = new ShapedRecipe(key, special);
        recipe.shape("  A",
                "BC ",
                "DB ");
        recipe.setIngredient('A', Material.FISHING_ROD);
        recipe.setIngredient('B', Material.IRON_INGOT);
        recipe.setIngredient('C', Material.STICK);
        recipe.setIngredient('D', Material.IRON_BLOCK);
        Bukkit.addRecipe(recipe);

        plugin.getLogger().info("[GrapplerItem] Receita registada com sucesso!");
    }
}