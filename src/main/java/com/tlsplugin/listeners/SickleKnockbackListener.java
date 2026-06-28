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

/**
 * Knockback inverso da Sickle: em vez de empurrar o alvo, puxa-o para o atacante.
 *
 * O atributo attack_knockback do Minecraft tem minimo 0 (nao aceita valores negativos),
 * por isso o puxao tem de ser feito por codigo. Sobrepoe o knockback vanilla 1 tick depois.
 */
public class SickleKnockbackListener implements Listener {

    private final Tlsplugin plugin;

    /** Forca do puxao (aprox. blocos/tick). Sobe para puxar mais forte. */
    private static final double PULL = 0.25;

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

        // 1 tick depois, para sobrepor o knockback vanilla (que empurra para fora).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (victim.isDead() || !victim.isValid()) return;

            Vector dir = attacker.getLocation().toVector()
                    .subtract(victim.getLocation().toVector());
            dir.setY(0);
            if (dir.lengthSquared() < 1.0e-4) return; // praticamente em cima um do outro

            dir.normalize().multiply(PULL);
            dir.setY(0.1); // pequeno salto para nao ficar preso no chao
            victim.setVelocity(dir);
        }, 1L);
    }

    private boolean isSickle(ItemStack item) {
        if (item == null) return false;
        CustomStack cs = CustomStack.byItemStack(item);
        return cs != null && cs.getNamespacedID().endsWith("sickle");
    }
}