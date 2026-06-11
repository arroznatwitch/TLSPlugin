package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SignManager {

    private final Tlsplugin plugin;
    private final File file;
    private YamlConfiguration config;

    // id → data
    private final Map<String, SignData> signs = new LinkedHashMap<>();

    public SignManager(Tlsplugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "signs.yml");
        reload();
    }

    // ── Data class ────────────────────────────────────────────────────────────

    public static class SignData {
        public final String   id;
        public final String   world;
        public final int      x, y, z;
        public final String   permission;
        public final List<String> commands;
        public final List<String> consoleCommands;
        public final String   noPermMessage;

        public SignData(String id, String world, int x, int y, int z,
                        String permission,
                        List<String> commands, List<String> consoleCommands,
                        String noPermMessage) {
            this.id              = id;
            this.world           = world;
            this.x               = x;
            this.y               = y;
            this.z               = z;
            this.permission      = permission;
            this.commands        = commands;
            this.consoleCommands = consoleCommands;
            this.noPermMessage   = noPermMessage;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }

        public boolean matchesBlock(Location loc) {
            if (loc == null) return false;
            World w = Bukkit.getWorld(world);
            if (w == null || !w.equals(loc.getWorld())) return false;
            return loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void addSign(String id, Location loc, String permission,
                        List<String> commands, List<String> consoleCommands,
                        String noPermMessage) {
        SignData data = new SignData(
            id,
            loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            permission, commands, consoleCommands, noPermMessage
        );
        signs.put(id.toLowerCase(), data);
        save();
    }

    public boolean removeSign(String id) {
        if (signs.remove(id.toLowerCase()) != null) { save(); return true; }
        return false;
    }

    public SignData getSign(String id) {
        return signs.get(id.toLowerCase());
    }

    public SignData findByLocation(Location loc) {
        for (SignData d : signs.values()) {
            if (d.matchesBlock(loc)) return d;
        }
        return null;
    }

    public Collection<SignData> getAllSigns() {
        return Collections.unmodifiableCollection(signs.values());
    }

    public boolean hasSign(String id) {
        return signs.containsKey(id.toLowerCase());
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void reload() {
        signs.clear();
        if (!file.exists()) { save(); return; }
        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("signs");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            signs.put(id.toLowerCase(), new SignData(
                id,
                s.getString("world", "world"),
                s.getInt("x"), s.getInt("y"), s.getInt("z"),
                s.getString("permission", ""),
                s.getStringList("commands"),
                s.getStringList("console_commands"),
                s.getString("no_permission_message", "§cNão tens permissão para usar esta placa.")
            ));
        }
        plugin.getLogger().info("[TLS] " + signs.size() + " placa(s) carregada(s).");
    }

    private void save() {
        if (config == null) config = new YamlConfiguration();
        config.set("signs", null);
        for (SignData d : signs.values()) {
            String path = "signs." + d.id;
            config.set(path + ".world",                d.world);
            config.set(path + ".x",                    d.x);
            config.set(path + ".y",                    d.y);
            config.set(path + ".z",                    d.z);
            config.set(path + ".permission",            d.permission);
            config.set(path + ".commands",              d.commands);
            config.set(path + ".console_commands",      d.consoleCommands);
            config.set(path + ".no_permission_message", d.noPermMessage);
        }
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().severe("[TLS] Erro ao guardar signs.yml: " + e.getMessage()); }
    }
}
