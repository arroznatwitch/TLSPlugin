package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.CustomStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class GrapplerItemListener implements Listener {

    private final Tlsplugin plugin;

    // Cooldown e usos são guardados na PDC do PRÓPRIO ITEM (não por jogador), para
    // que cada Grappler craftado tenha o seu timer independente — um jogador com 2
    // grapplers pode usar os 2 sem partilharem cooldown/usos (mesmo bug do Tracker).
    private final NamespacedKey cooldownKey;
    private final NamespacedKey usosKey;
    private final Map<UUID, Boolean> noFallDamage = new HashMap<>();
    // Debounce: o PlayerInteractEvent por vezes dispara 2x para o mesmo clique físico.
    private final Map<UUID, Long> lastInteractNano = new HashMap<>();
    private static final long DEBOUNCE_NANOS = 250_000_000L; // 250ms

    private final int     cooldownSegundos;
    private final int     maxUsos;
    private final boolean opInfinito;
    private final int     rangeBlocks;
    private final String  msgUsar, msgCooldown, msgLimite, msgForaRange, msgQuebrou;

    private static final String GRAPPLER_ID = "tls_plugin:grappler_item";

    public GrapplerItemListener(Tlsplugin plugin) {
        this.plugin = plugin;
        this.cooldownKey = new NamespacedKey(plugin, "grappler_cooldown_end");
        this.usosKey = new NamespacedKey(plugin, "grappler_usos");

        this.cooldownSegundos = plugin.getConfig().getInt("grappler_item.cooldown_segundos", 90);
        this.maxUsos          = plugin.getConfig().getInt("grappler_item.max_usos", 3);
        this.opInfinito       = plugin.getConfig().getBoolean("grappler_item.op_infinito", true);
        this.rangeBlocks      = plugin.getConfig().getInt("grappler_item.range_blocos", 15);

        this.msgUsar      = plugin.getConfig().getString("grappler_item.mensagem_usar",      "§aGrappler lançado!");
        this.msgCooldown  = plugin.getConfig().getString("grappler_item.mensagem_cooldown",  "§cO Grappler está em cooldown! Espera {tempo}s.");
        this.msgLimite    = plugin.getConfig().getString("grappler_item.mensagem_limite",    "§cJá usaste o Grappler o número máximo de vezes ({max}).");
        this.msgForaRange = plugin.getConfig().getString("grappler_item.mensagem_fora_range","§cNenhum bloco dentro do alcance ({range} blocos)!");
        this.msgQuebrou   = plugin.getConfig().getString("grappler_item.mensagem_quebrou",   "§c§l[!] §cO teu Grappler quebrou!");

        reloadRecipe();
        startActionBarUpdater();
    }

    public void reloadRecipe() {
        // lore estática — não há mais lore dinâmica
    }

    public boolean isUsingGrappler(UUID id) {
        return noFallDamage.containsKey(id);
    }

    public boolean isShowingActionBar(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        CustomStack handCustom = CustomStack.byItemStack(hand);
        if (handCustom == null || !GRAPPLER_ID.equals(handCustom.getNamespacedID())) return false;
        return System.currentTimeMillis() < getCooldownEnd(hand);
    }

    private long getCooldownEnd(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0L;
        return meta.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
    }

    private int getUsos(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(usosKey, PersistentDataType.INTEGER, 0);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null) return;

        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        CustomStack custom = CustomStack.byItemStack(item);
        if (custom == null || !GRAPPLER_ID.equals(custom.getNamespacedID())) return;

        e.setCancelled(true);

        UUID id  = p.getUniqueId();
        long nowNano = System.nanoTime();
        Long lastNano = lastInteractNano.get(id);
        if (lastNano != null && (nowNano - lastNano) < DEBOUNCE_NANOS) return;
        lastInteractNano.put(id, nowNano);

        long now = System.currentTimeMillis();
        boolean semLimites = opInfinito && p.isOp();

        // Cooldown
        if (!semLimites) {
            long expira = getCooldownEnd(item);
            if (now < expira) {
                long restante = (expira - now) / 1000L;
                p.sendMessage(msgCooldown.replace("{tempo}", String.valueOf(restante)));
                return;
            }
        }

        // Usos
        if (!semLimites) {
            int usados = getUsos(item);
            if (maxUsos > 0 && usados >= maxUsos) {
                p.sendMessage(msgLimite.replace("{max}", String.valueOf(maxUsos)));
                return;
            }
        }

        // Lançar
        if (!launch(p)) {
            p.sendMessage(msgForaRange.replace("{range}", String.valueOf(rangeBlocks)));
            return;
        }

        // Consumir uso
        if (!semLimites) {
            int novosUsos = getUsos(item) + 1;

            if (maxUsos > 0 && novosUsos >= maxUsos) {
                item.setAmount(0);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                p.sendMessage(msgQuebrou);
            } else {
                long expiraNovo = now + (cooldownSegundos * 1000L);
                item.editMeta(m -> {
                    m.getPersistentDataContainer().set(usosKey, PersistentDataType.INTEGER, novosUsos);
                    m.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, expiraNovo);
                });
                p.getInventory().setItemInMainHand(item);
            }
        }

        p.sendMessage(msgUsar);
    }

    @EventHandler
    public void onItemChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack oldItem = p.getInventory().getItem(e.getPreviousSlot());
        if (oldItem != null) {
            CustomStack custom = CustomStack.byItemStack(oldItem);
            if (custom != null && GRAPPLER_ID.equals(custom.getNamespacedID())) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }
    }

    private boolean launch(Player p) {
        var rayResult = p.rayTraceBlocks(rangeBlocks);
        if (rayResult == null || rayResult.getHitBlock() == null) return false;

        Location hit  = rayResult.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        Location eye  = p.getEyeLocation();
        Vector   dir  = hit.toVector().subtract(eye.toVector()).normalize();
        double   dist = eye.distance(hit);
        double   speed = Math.min(2.5, 0.15 * dist + 0.5);

        p.setVelocity(dir.multiply(speed));

        UUID id = p.getUniqueId();
        noFallDamage.put(id, true);
        new BukkitRunnable() {
            @Override public void run() { noFallDamage.remove(id); }
        }.runTaskLater(plugin, 60L);

        return true;
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (noFallDamage.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            noFallDamage.remove(p.getUniqueId());
        }
    }

    // Só action bar — sem tocar em lore/itens
    private void startActionBarUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    long now = System.currentTimeMillis();

                    ItemStack hand = p.getInventory().getItemInMainHand();
                    CustomStack handCustom = CustomStack.byItemStack(hand);
                    if (handCustom == null || !GRAPPLER_ID.equals(handCustom.getNamespacedID())) continue;

                    long expira = getCooldownEnd(hand);
                    if (now < expira) {
                        long restante = (expira - now) / 1000L;
                        int cheios = (int) Math.round(20.0 * (expira - now) / (cooldownSegundos * 1000.0));
                        int vazios  = 20 - cheios;
                        String barra = "§e" + "█".repeat(Math.max(0, cheios))
                                + "§8" + "░".repeat(Math.max(0, vazios));
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent("§6⚓ Grappler §r" + barra + " §7" + restante + "s"));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void cleanup() {
        noFallDamage.clear();
        lastInteractNano.clear();
    }
}