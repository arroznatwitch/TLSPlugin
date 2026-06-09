package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.ItemUtils;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GoldPotionListener implements Listener {

    private final Tlsplugin plugin;
    private final String itemId;
    private final String msgUsar;
    private final List<String> effects;
    private final int chanceNivel1;
    private final int duracao1;
    private final int duracao2;
    private final List<String> baseLore;
    private final Random random = new Random();

    public GoldPotionListener(Tlsplugin plugin) {
        this.plugin        = plugin;
        this.itemId        = plugin.getConfig().getString("gold_potion.item_id", "tls_plugin:goldpotion_item");
        this.msgUsar       = plugin.getConfig().getString("gold_potion.mensagem_usar", "§aBebeste a poção dourada!");
        this.effects       = plugin.getConfig().getStringList("gold_potion.efeitos");
        this.chanceNivel1  = plugin.getConfig().getInt("gold_potion.chances_nivel.nivel_1", 70);
        this.duracao1      = plugin.getConfig().getInt("gold_potion.duracao.nivel_1", 90);
        this.duracao2      = plugin.getConfig().getInt("gold_potion.duracao.nivel_2", 60);
        this.baseLore      = plugin.getConfig().getStringList("gold_potion.lore");

        startLoreUpdater();
    }

    private void startLoreUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    List<ItemStack> items = new ArrayList<>(Arrays.asList(p.getInventory().getContents()));
                    items.add(p.getInventory().getItemInOffHand());

                    for (ItemStack item : items) {
                        if (item == null || item.getType() == Material.AIR) continue;
                        CustomStack custom = CustomStack.byItemStack(item);
                        if (custom != null && itemId.equals(custom.getNamespacedID())) {
                            ItemUtils.applyLore(item, baseLore);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    /**
     * O material base no ItemsAdder é HONEY_BOTTLE:
     *  - Tem animação de beber vanilla (igual à poção)
     *  - Tem som de beber
     *  - Não tem efeitos vanilla a interferir
     *  - O PlayerItemConsumeEvent dispara limpo, com o item ainda intacto
     *
     * Ao terminar de beber:
     *  1. Cancelamos o resultado vanilla do HONEY_BOTTLE (daria uma GLASS_BOTTLE).
     *  2. Removemos o item manualmente da mão.
     *  3. Aplicamos o efeito aleatório.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onConsume(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        // Verificar se é a nossa GoldPotion pelo CustomStack do ItemsAdder
        CustomStack custom = CustomStack.byItemStack(item);
        if (custom == null || !itemId.equals(custom.getNamespacedID())) return;

        // Cancelar o resultado vanilla (HONEY_BOTTLE daria uma garrafa de vidro vazia,
        // mas queremos controlo total sobre o que fica na mão)
        e.setCancelled(true);

        // Remover 1 unidade do item da mão manualmente
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.AIR && inHand.getAmount() > 0) {
            if (inHand.getAmount() > 1) {
                inHand.setAmount(inHand.getAmount() - 1);
            } else {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }

        // Tocar o som de beber (o cancel do evento suprime o som vanilla)
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);

        // Aplicar efeito e mensagem
        applyRandomEffect(p);
        p.sendMessage(msgUsar);
    }

    private void applyRandomEffect(Player p) {
        if (effects.isEmpty()) return;

        String effectName = effects.get(random.nextInt(effects.size()));
        PotionEffectType type = PotionEffectType.getByName(effectName);

        if (type == null) {
            plugin.getLogger().warning("Efeito de poção inválido no config: " + effectName);
            return;
        }

        int level         = (random.nextInt(100) < chanceNivel1) ? 0 : 1;
        int duration      = (level == 0) ? duracao1 : duracao2;
        int finalDuration = type.isInstant() ? 1 : duration * 20;

        p.addPotionEffect(new PotionEffect(type, finalDuration, level));
    }

    public void applyBaseLore(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            CustomStack custom = CustomStack.byItemStack(item);
            if (custom != null && itemId.equals(custom.getNamespacedID())) {
                ItemUtils.applyLore(item, baseLore);
            }
        }
    }
}