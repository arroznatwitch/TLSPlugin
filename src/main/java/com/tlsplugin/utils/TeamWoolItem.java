package com.tlsplugin.utils;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public class TeamWoolItem {

    private static NamespacedKey teamKey;

    private static NamespacedKey key() {
        if (teamKey == null) teamKey = new NamespacedKey(Tlsplugin.getInstance(), "team_wool");
        return teamKey;
    }

    private static final Map<String, Material> MATERIALS = Map.of(
        "blue",   Material.BLUE_WOOL,
        "grey",   Material.GRAY_WOOL,
        "green",  Material.GREEN_WOOL,
        "orange", Material.ORANGE_WOOL,
        "pink",   Material.PINK_WOOL,
        "purple", Material.PURPLE_WOOL,
        "red",    Material.RED_WOOL,
        "yellow", Material.YELLOW_WOOL
    );

    public static ItemStack create(String teamId) {
        Tlsplugin plugin = Tlsplugin.getInstance();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("teams.items." + teamId);
        if (sec == null) return null;

        Material mat = MATERIALS.getOrDefault(teamId, Material.WHITE_WOOL);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = sec.getString("display_name", "§f" + teamId);
        List<String> lore  = sec.getStringList("lore");

        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, teamId);
        item.setItemMeta(meta);
        return item;
    }

    public static String getTeamId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }

    public static String[] allTeams() {
        return new String[]{"blue", "grey", "green", "orange", "pink", "purple", "red", "yellow"};
    }
}
