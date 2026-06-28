package com.tlsplugin.listeners;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Aplica o "attack range" (alcance) das armas TLS.
 *
 * O ItemsAdder NAO suporta o atributo entity_interaction_range no YAML
 * (so suporta attack_damage, attack_speed, attack_knockback, etc.), por isso
 * o alcance e aplicado aqui ao jogador conforme a arma que tem na mao.
 *
 * Base do alcance de ataque = 3.0. Os deltas abaixo somam/subtraem a isso.
 *
 * Registar no onEnable():
 *   getServer().getPluginManager().registerEvents(new WeaponRangeListener(this), this);
 */
public class WeaponRangeListener implements Listener {

    private final NamespacedKey modifierKey;
    private final Attribute reach;
    private final Map<String, Double> rangeBySuffix = new HashMap<>();

    public WeaponRangeListener(Plugin plugin) {
        this.modifierKey = new NamespacedKey(plugin, "weapon_reach");
        // Resolve por registry (robusto entre versoes — evita problemas de nome do enum)
        this.reach = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("entity_interaction_range"));

        // Delta de alcance por tipo de arma (igual em todos os tiers)
        rangeBySuffix.put("longsword", 1.0);
        rangeBySuffix.put("scythe", 1.25);
        rangeBySuffix.put("broadsword", -0.5);
        rangeBySuffix.put("sickle", -0.25);

        // Re-sync de seguranca 1x/seg: cobre casos que o evento de held nao apanha
        // (arma partiu, item caiu/foi apanhado no mesmo slot, etc.)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    apply(p, p.getInventory().getItemInMainHand());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack next = e.getPlayer().getInventory().getItem(e.getNewSlot());
        apply(e.getPlayer(), next);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand());
    }

    private void apply(Player player, ItemStack item) {
        if (reach == null) return;
        AttributeInstance inst = player.getAttribute(reach);
        if (inst == null) return;

        // Limpa sempre o nosso modificador anterior primeiro (idempotente)
        inst.getModifiers().stream()
                .filter(m -> modifierKey.equals(m.getKey()))
                .forEach(inst::removeModifier);

        double delta = reachFor(item);
        if (delta != 0.0) {
            inst.addModifier(new AttributeModifier(
                    modifierKey,
                    delta,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.HAND));
        }
    }

    private double reachFor(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0.0;
        CustomStack cs = CustomStack.byItemStack(item);
        if (cs == null) return 0.0;
        String id = cs.getNamespacedID(); // ex: tls_plugin:diamond_longsword
        for (Map.Entry<String, Double> e : rangeBySuffix.entrySet()) {
            if (id.endsWith(e.getKey())) return e.getValue();
        }
        return 0.0;
    }
}