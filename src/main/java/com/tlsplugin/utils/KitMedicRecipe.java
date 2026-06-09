package com.tlsplugin.utils;

import com.tlsplugin.listeners.DeathListener;
import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;

public class KitMedicRecipe {

    private static JavaPlugin plugin;

    private static final String ID_KIT_COMPLETO = "tls_plugin:kit_completo";
    private static final String ID_KIT_PARCIAL  = "tls_plugin:kit_parcial";

    public KitMedicRecipe(JavaPlugin plugin) {
        KitMedicRecipe.plugin = plugin;
    }

    public static void register(JavaPlugin plugin) {
        KitMedicRecipe.plugin = plugin;

        Bukkit.getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onIAReady(ItemsAdderLoadDataEvent e) {
                registerRecipes();
                for (Player p : Bukkit.getOnlinePlayers()) applyLoreToInventory(p);
            }

            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> applyLoreToInventory(e.getPlayer()), 5L);
            }

            @EventHandler
            public void onPrepareCraft(PrepareItemCraftEvent e) {
                Recipe recipe = e.getRecipe();
                if (!(recipe instanceof ShapedRecipe sr)) return;

                String recipeKey = sr.getKey().getKey().toLowerCase();
                if (!recipeKey.equals("tls_kit_completo") && !recipeKey.equals("tls_kit_parcial")) return;

                ItemStack center = e.getInventory().getMatrix()[4];
                if (center == null || center.getType() == Material.AIR) {
                    e.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }

                if (center.getType() == Material.GOLDEN_APPLE ||
                    center.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                    return;
                }

                if (isPotionMaterial(center.getType()) &&
                    center.getItemMeta() instanceof PotionMeta pMeta) {
                    PotionType type = pMeta.getBasePotionType();
                    if (type == PotionType.HEALING || type == PotionType.STRONG_HEALING) return;
                }

                e.getInventory().setResult(new ItemStack(Material.AIR));
            }

            private boolean isPotionMaterial(Material material) {
                return material == Material.POTION
                        || material == Material.SPLASH_POTION
                        || material == Material.LINGERING_POTION;
            }

            // ── Revive com Kit Médico ──────────────────────────────────────────
            @EventHandler(priority = EventPriority.HIGHEST)
            public void onPlayerReviveAttempt(PlayerInteractEvent e) {
                Player healer = e.getPlayer();

                if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !healer.isSneaking()) return;
                Block clickedBlock = e.getClickedBlock();
                if (clickedBlock == null) return;
                if (!clickedBlock.hasMetadata(DeathListener.METADATA_BLOCO_KEY)) return;

                e.setCancelled(true);

                String targetName = clickedBlock.getMetadata(DeathListener.METADATA_BLOCO_KEY)
                        .get(0).asString();
                Player target    = Bukkit.getPlayer(targetName);
                Tlsplugin tlsPlugin = (Tlsplugin) KitMedicRecipe.plugin;

                if (target == null || !target.isOnline() ||
                    target.getGameMode() != GameMode.SPECTATOR) {
                    healer.sendMessage(tlsPlugin.getConfig().getString(
                            "mensagens_revive.revive_erro_alvo_invalido",
                            "§cEste jogador não precisa de ser reanimado."));
                    return;
                }

                ItemStack hand = healer.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    healer.sendMessage(tlsPlugin.getConfig().getString(
                            "mensagens_revive.revive_erro_sem_kit", "§cKit Médico Inválido."));
                    return;
                }

                CustomStack customStack = CustomStack.byItemStack(hand);
                if (customStack == null) {
                    healer.sendMessage(tlsPlugin.getConfig().getString(
                            "mensagens_revive.revive_erro_sem_kit", "§cKit Médico Inválido."));
                    return;
                }

                String namespacedID = customStack.getNamespacedID();
                boolean completo    = namespacedID.equals(ID_KIT_COMPLETO);
                boolean parcial     = namespacedID.equals(ID_KIT_PARCIAL);

                if (!completo && !parcial) {
                    healer.sendMessage(tlsPlugin.getConfig().getString(
                            "mensagens_revive.revive_erro_sem_kit", "§cKit Médico Inválido."));
                    return;
                }

                // ── CORREÇÃO CRÍTICA #1: verificar null no getAttribute ────────
                var attr = target.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = attr != null ? attr.getValue() : 20.0;

                double percent = completo
                        ? tlsPlugin.getConfig().getDouble("revive.vida_revive.kit_completo_percentagem", 1.0)
                        : tlsPlugin.getConfig().getDouble("revive.vida_revive.kit_parcial_percentagem", 0.5);

                target.setHealth(Math.max(1.0, Math.min(maxHealth, maxHealth * percent)));

                if (tlsPlugin.getConfig().getBoolean("revive.efeito_resistencia.habilitar", true)) {
                    int duration = tlsPlugin.getConfig().getInt(
                            "revive.efeito_resistencia.duracao_segundos", 15) * 20;
                    int level = tlsPlugin.getConfig().getInt(
                            "revive.efeito_resistencia.nivel", 1);
                    target.addPotionEffect(new PotionEffect(
                            PotionEffectType.RESISTANCE, duration, level, false, true));
                }

                clickedBlock.setType(Material.AIR);
                target.setGameMode(GameMode.SURVIVAL);
                target.teleport(healer.getLocation());
                healer.getWorld().playSound(healer.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

                String msgHealer = tlsPlugin.getConfig()
                        .getString("mensagens_revive.revive_sucesso_healer", "§aReviveste {alvo}!")
                        .replace("{alvo}", target.getName());
                String msgTarget = tlsPlugin.getConfig()
                        .getString("mensagens_revive.revive_sucesso_alvo", "§aFoste revivido por {healer}!")
                        .replace("{healer}", healer.getName());

                healer.sendMessage(msgHealer);
                target.sendMessage(msgTarget);

                // ── CORREÇÃO CRÍTICA #2: addRevival AQUI após revive com sucesso ──
                tlsPlugin.getMVPStatsManager().addRevival(healer.getName());

                // Consumir o kit
                ItemStack handCopy = hand.clone();
                if (handCopy.getAmount() > 1) {
                    handCopy.setAmount(handCopy.getAmount() - 1);
                    healer.getInventory().setItemInMainHand(handCopy);
                } else {
                    healer.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }

                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        Tlsplugin.getPlugin(Tlsplugin.class).getDeathListener()
                                .removeBody(target.getName()), 1L);
            }

        }, plugin);

        registerRecipes();
    }

    // ── Regista as receitas dos kits ──────────────────────────────────────
    private static void registerRecipes() {
        // Kit Completo
        if (plugin.getConfig().getBoolean(
                "craft_book.special_items.kit_completo.habilitar_receita", true)) {
            CustomStack kitCompletoStack = CustomStack.getInstance(ID_KIT_COMPLETO);
            if (kitCompletoStack != null) {
                ItemStack kitCompleto = kitCompletoStack.getItemStack().clone();
                java.util.List<String> loreCompleto = plugin.getConfig()
                        .getStringList("med_items.kit_completo.lore");
                if (!loreCompleto.isEmpty()) ItemUtils.applyLore(kitCompleto, loreCompleto);

                NamespacedKey key1 = new NamespacedKey(plugin, "tls_kit_completo");
                Bukkit.removeRecipe(key1);
                ShapedRecipe recipe1 = new ShapedRecipe(key1, kitCompleto);
                recipe1.shape("IRI", "RPR", "IRI");
                recipe1.setIngredient('I', Material.GOLD_INGOT);
                recipe1.setIngredient('R', Material.YELLOW_WOOL);
                recipe1.setIngredient('P', Material.GOLDEN_APPLE);
                recipe1.setGroup("tls_kits");
                Bukkit.addRecipe(recipe1);
            }
        } else {
            Bukkit.removeRecipe(new NamespacedKey(plugin, "tls_kit_completo"));
        }

        // Kit Parcial
        if (plugin.getConfig().getBoolean(
                "craft_book.special_items.kit_parcial.habilitar_receita", true)) {
            CustomStack kitParcialStack = CustomStack.getInstance(ID_KIT_PARCIAL);
            if (kitParcialStack != null) {
                ItemStack kitParcial = kitParcialStack.getItemStack().clone();
                java.util.List<String> loreParcial = plugin.getConfig()
                        .getStringList("med_items.kit_parcial.lore");
                if (!loreParcial.isEmpty()) ItemUtils.applyLore(kitParcial, loreParcial);

                NamespacedKey key2 = new NamespacedKey(plugin, "tls_kit_parcial");
                Bukkit.removeRecipe(key2);
                ShapedRecipe recipe2 = new ShapedRecipe(key2, kitParcial);
                recipe2.shape("IRI", "RGR", "IRI");
                recipe2.setIngredient('I', Material.IRON_INGOT);
                recipe2.setIngredient('R', Material.RED_WOOL);
                recipe2.setIngredient('G', Material.GOLDEN_APPLE);
                recipe2.setGroup("tls_kits");
                Bukkit.addRecipe(recipe2);
            }
        } else {
            Bukkit.removeRecipe(new NamespacedKey(plugin, "tls_kit_parcial"));
        }
    }

    private static void applyLoreToInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            CustomStack cs = CustomStack.byItemStack(item);
            if (cs == null) continue;

            String id = cs.getNamespacedID();
            if (id.equals(ID_KIT_COMPLETO)) {
                java.util.List<String> lore = plugin.getConfig()
                        .getStringList("med_items.kit_completo.lore");
                if (!lore.isEmpty()) ItemUtils.applyLore(item, lore);
            } else if (id.equals(ID_KIT_PARCIAL)) {
                java.util.List<String> lore = plugin.getConfig()
                        .getStringList("med_items.kit_parcial.lore");
                if (!lore.isEmpty()) ItemUtils.applyLore(item, lore);
            }
        }
    }
}
