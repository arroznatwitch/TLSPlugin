package com.tlsplugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;

public class SpecialAppleRecipe implements Listener {

    private static JavaPlugin plugin;
    private static boolean listenerRegistered = false;
    private static final Material CUSTOM_ITEM_BASE_MATERIAL = Material.GOLDEN_APPLE;
    private static final String CUSTOM_ITEM_ID = "tls_plugin:tls_special_apple";

    private SpecialAppleRecipe(JavaPlugin plugin) {
        // construtor privado, só usamos via register()
    }

    public static void register(JavaPlugin pl) {
        plugin = pl;

        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(new SpecialAppleRecipe(plugin), plugin);
            listenerRegistered = true;
        }

        registerRecipe();
    }

    // ── Quando o ItemsAdder (re)carrega, volta a registar a receita e aplica lore ─
    @EventHandler
    public void onIAReady(ItemsAdderLoadDataEvent e) {
        registerRecipe();
        for (Player p : Bukkit.getOnlinePlayers()) applyLoreToInventory(p);
    }

    // ── Aplica lore quando o jogador entra ────────────────────────────────────────
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyLoreToInventory(e.getPlayer()), 5L);
    }

    // ── Consome a maçã e aplica efeitos ──────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        Player p = e.getPlayer();

        if (item.getType() != CUSTOM_ITEM_BASE_MATERIAL) return;

        CustomStack customStack = CustomStack.byItemStack(item);
        if (customStack == null || !customStack.getNamespacedID().equals(CUSTOM_ITEM_ID)) return;

        e.setCancelled(true);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 4));
            p.setSaturation(Math.min(20f, p.getSaturation() + 9.6f));

            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
            String msgConsumo = plugin.getConfig().getString(
                    "med_items.special_apple.mensagem_consumo",
                    "§6✨ Consumiste a Maçã Dourada Especial!");
            p.sendMessage(msgConsumo.replace("&", "§"));

            int regenSec  = plugin.getConfig().getInt("med_items.special_apple.efeitos.regeneracao.duracao_segundos", 10);
            int regenLvl  = plugin.getConfig().getInt("med_items.special_apple.efeitos.regeneracao.nivel", 1);
            int absSec    = plugin.getConfig().getInt("med_items.special_apple.efeitos.absorcao.duracao_segundos", 90);
            int absLvl    = plugin.getConfig().getInt("med_items.special_apple.efeitos.absorcao.nivel", 1);
            int fireSec   = plugin.getConfig().getInt("med_items.special_apple.efeitos.resistencia_fogo.duracao_segundos", 15);
            int fireLvl   = plugin.getConfig().getInt("med_items.special_apple.efeitos.resistencia_fogo.nivel", 0);
            int forceSec  = plugin.getConfig().getInt("med_items.special_apple.efeitos.forca.duracao_segundos", 5);
            int forceLvl  = plugin.getConfig().getInt("med_items.special_apple.efeitos.forca.nivel", 0);

            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,    regenSec * 20, Math.max(0, regenLvl - 1), false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,      absSec   * 20, Math.max(0, absLvl  - 1), false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireSec  * 20, fireLvl,                   false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,        forceSec * 20, forceLvl,                  false, true));

            // Remove 1 item da mão correta
            if (p.getInventory().getItemInMainHand().isSimilar(item)) {
                p.getInventory().getItemInMainHand().setAmount(item.getAmount() - 1);
            } else if (p.getInventory().getItemInOffHand().isSimilar(item)) {
                p.getInventory().getItemInOffHand().setAmount(item.getAmount() - 1);
            }

            plugin.getLogger().info("Maçã Dourada Especial consumida por: " + p.getName());
        }, 1L);
    }

    // ── Regista a receita ─────────────────────────────────────────────────────────
    private static void registerRecipe() {
        if (!plugin.getConfig().getBoolean("craft_book.special_items.tls_special_apple.habilitar_receita", true)) return;

        CustomStack specialAppleStack = CustomStack.getInstance(CUSTOM_ITEM_ID);
        if (specialAppleStack == null) {
            plugin.getLogger().severe("Item '" + CUSTOM_ITEM_ID + "' não encontrado no ItemsAdder!");
            return;
        }

        ItemStack special = specialAppleStack.getItemStack().clone();
        java.util.List<String> lore = plugin.getConfig().getStringList("med_items.special_apple.lore");
        if (!lore.isEmpty()) ItemUtils.applyLore(special, lore);

        NamespacedKey key = new NamespacedKey(plugin, "tls_special_apple");
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, special);
        recipe.shape("AAA", "ABA", "AAA");
        recipe.setIngredient('A', Material.GOLD_INGOT);
        recipe.setIngredient('B', Material.PLAYER_HEAD);
        Bukkit.addRecipe(recipe);

        plugin.getLogger().info("Receita da Maçã Dourada Especial registada com sucesso!");
    }

    // ── Percorre o inventário e aplica lore onde necessário ──────────────────────
    private static void applyLoreToInventory(Player p) {
        java.util.List<String> lore = plugin.getConfig().getStringList("med_items.special_apple.lore");
        if (lore.isEmpty()) return;

        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            CustomStack cs = CustomStack.byItemStack(item);
            if (cs != null && cs.getNamespacedID().equals(CUSTOM_ITEM_ID)) {
                ItemUtils.applyLore(item, lore);
            }
        }
    }
}