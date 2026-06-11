package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpawnManager {

    private final Tlsplugin plugin;
    private final File      file;
    private YamlConfiguration yaml;

    // Cache em memória: teamName → Location
    private final Map<String, Location> spawns = new HashMap<>();

    public SpawnManager(Tlsplugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "spawns.yml");
        load();
    }

    // ─── Leitura/escrita ─────────────────────────────────────────────────────

    public void load() {
        spawns.clear();
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("spawns")) return;

        for (String team : yaml.getConfigurationSection("spawns").getKeys(false)) {
            String path = "spawns." + team;
            String worldName = yaml.getString(path + ".world");
            if (worldName == null) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                // Mundo não carregado agora — guarda o nome para tentar depois
                plugin.getLogger().warning("[TLS] Spawn da equipa '" + team +
                        "' aponta para o mundo '" + worldName + "' que não está carregado.");
                continue;
            }

            double x   = yaml.getDouble(path + ".x");
            double y   = yaml.getDouble(path + ".y");
            double z   = yaml.getDouble(path + ".z");
            float  yaw = (float) yaml.getDouble(path + ".yaw",   0);
            float  pitch = (float) yaml.getDouble(path + ".pitch", 0);

            spawns.put(team.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
        }

        plugin.getLogger().info("[TLS] " + spawns.size() + " spawn(s) de equipa carregados.");
    }

    public void save() {
        if (yaml == null) yaml = new YamlConfiguration();

        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            String   team = entry.getKey();
            Location loc  = entry.getValue();
            String   path = "spawns." + team;

            yaml.set(path + ".world", loc.getWorld().getName());
            yaml.set(path + ".x",     loc.getX());
            yaml.set(path + ".y",     loc.getY());
            yaml.set(path + ".z",     loc.getZ());
            yaml.set(path + ".yaw",   (double) loc.getYaw());
            yaml.set(path + ".pitch", (double) loc.getPitch());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[TLS] Erro ao guardar spawns.yml: " + e.getMessage());
        }
    }

    // ─── API pública ─────────────────────────────────────────────────────────

    public void setSpawn(String team, Location location) {
        spawns.put(team.toLowerCase(), location.clone());
        save();
    }

    public Location getSpawn(String team) {
        return spawns.get(team.toLowerCase());
    }

    public boolean hasSpawn(String team) {
        return spawns.containsKey(team.toLowerCase());
    }

    public void removeSpawn(String team) {
        spawns.remove(team.toLowerCase());
        if (yaml != null) yaml.set("spawns." + team.toLowerCase(), null);
        save();
    }

    public Set<String> getAllTeams() {
        return spawns.keySet();
    }

    /** Recarrega spawns do disco — útil se o mundo foi carregado depois. */
    public void reload() {
        load();
    }
}
