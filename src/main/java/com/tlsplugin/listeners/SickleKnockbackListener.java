package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Knockback inverso da Sickle: por probabilidade, puxa o alvo para o atacante.
 *
 * Configuravel no config.yml (lido a cada hit, por isso /tlsreload aplica logo):
 *   sickle:
 *     pull: 0.6      # forca do puxao (tem de ser > ~0.4 para vencer o knockback vanilla)
 *     chance: 0.15   # probabilidade de puxar por hit (0.0 a 1.0). 0.15 = 15%
 *     debug: false
 *
 * O atributo attack_knockback do Minecraft tem minimo 0 (nao aceita negativos), por isso
 * isto tem mesmo de ser feito por codigo.
 */
public class SickleKnockbackListener implements Listener {

    private final Tlsplugin plugin;

    public SickleKnockbackListener(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim.equals(attacker)) return;

        if (!isSickle(attacker.getInventory().getItemInMainHand())) return;

        final double  pull   = plugin.getConfig().getDouble("sickle.pull", 0.6);
        final double  chance = plugin.getConfig().getDouble("sickle.chance", 0.15);
        final boolean debug  = plugin.getConfig().getBoolean("sickle.debug", false);

        // Probabilidade
        if (ThreadLocalRandom.current().nextDouble() > chance) {
            if (debug) plugin.getLogger().info("[Sickle] " + attacker.getName()
                    + " — sem puxao (probabilidade " + (chance * 100) + "%).");
            return;
        }

        if (debug) plugin.getLogger().info("[Sickle] " + attacker.getName()
                + " puxou " + victim.getName() + " (pull=" + pull + ").");

        // 1 tick depois, para sobrepor o knockback vanilla (que empurra para fora).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (victim.isDead() || !victim.isValid()) return;

            Vector dir = attacker.getLocation().toVector()
                    .subtract(victim.getLocation().toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 1.0e-4) return; // praticamente em cima um do outro

            dir.normalize().multiply(pull);
            dir.setY(0.15); // pequeno salto para nao ser comido pelo atrito do chao
            victim.setVelocity(dir);
        }, 1L);
    }

    private boolean isSickle(ItemStack item) {
        if (item == null) return false;
        CustomStack cs = CustomStack.byItemStack(item);
        return cs != null && cs.getNamespacedID().endsWith("sickle");
    }
}