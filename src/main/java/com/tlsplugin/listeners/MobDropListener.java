package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class MobDropListener implements Listener {

    private final Tlsplugin plugin;

    public MobDropListener(Tlsplugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (!plugin.getConfig().getBoolean("mob_gold_drop.habilitar", true)) return;

        World.Environment env = entity.getWorld().getEnvironment();
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) return;

        if (!(entity instanceof Monster)) return;

        double chance;
        String path = "mob_gold_drop.mobs.";

        if (entity instanceof Witch)         chance = plugin.getConfig().getDouble(path + "witch");
        else if (entity instanceof Vindicator) chance = plugin.getConfig().getDouble(path + "vindicator");
        else if (entity instanceof Enderman)   chance = plugin.getConfig().getDouble(path + "enderman");
        else if (entity instanceof Pillager)   chance = plugin.getConfig().getDouble(path + "pillager");
        else if (entity instanceof Skeleton)   chance = plugin.getConfig().getDouble(path + "skeleton");
        else if (entity instanceof CaveSpider) chance = plugin.getConfig().getDouble(path + "cave_spider");
        else if (entity instanceof Creeper)    chance = plugin.getConfig().getDouble(path + "creeper");
        else if (entity instanceof Zombie)     chance = plugin.getConfig().getDouble(path + "zombie");
        else if (entity instanceof Spider)     chance = plugin.getConfig().getDouble(path + "spider");
        else                                   chance = plugin.getConfig().getDouble(path + "default");

        if (Math.random() < chance) {
            entity.getWorld().dropItemNaturally(
                    entity.getLocation(),
                    new ItemStack(Material.GOLD_INGOT, 1)
            );
        }
    }
}