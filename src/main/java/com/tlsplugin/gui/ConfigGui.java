package com.tlsplugin.gui;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ConfigGui {

    private final Tlsplugin plugin;
    public final NamespacedKey CONFIG_PATH_KEY;
    public final NamespacedKey NAV_TYPE_KEY;

    private static final Map<String, Material> ICONS = new HashMap<>();
    private static final Map<String, String>   DISPLAY_NAMES = new HashMap<>();

    static {
        ICONS.put("modo_jogo",             Material.COMPASS);
        ICONS.put("modos",                 Material.BOOK);
        ICONS.put("mensagens",             Material.PAPER);
        ICONS.put("bossbar_template",      Material.MAGENTA_BANNER);
        ICONS.put("pvp_decimais",          Material.IRON_SWORD);
        ICONS.put("heal_on_kill",          Material.RED_DYE);
        ICONS.put("grappler_item",         Material.FISHING_ROD);
        ICONS.put("habilitar_receita_maça",Material.CRAFTING_TABLE);
        ICONS.put("tab",                   Material.LEATHER_CHESTPLATE);
        ICONS.put("revive",                Material.TOTEM_OF_UNDYING);
        ICONS.put("game",                  Material.COMMAND_BLOCK);
        ICONS.put("gamerules",             Material.COMMAND_BLOCK);
        ICONS.put("assist_janela_segundos",Material.EXPERIENCE_BOTTLE);
        ICONS.put("mensagens_comandos",    Material.WRITABLE_BOOK);
        ICONS.put("pausa_jogador",         Material.CLOCK);
        ICONS.put("mvp_design",            Material.PAINTING);
        ICONS.put("mvp_pontos",            Material.NETHER_STAR);
        ICONS.put("holograma",             Material.END_CRYSTAL);
        ICONS.put("mensagens_revive",      Material.BELL);
        ICONS.put("compass_tracker",       Material.FILLED_MAP);
        ICONS.put("med_items",             Material.APPLE);
        ICONS.put("gold_potion",           Material.POTION);
        ICONS.put("scoreboard",            Material.OAK_SIGN);
        ICONS.put("craft_book",            Material.BOOKSHELF);
        ICONS.put("pronto",                Material.LIME_DYE);
        ICONS.put("anunciar",              Material.GOAT_HORN);
        ICONS.put("proximidade_fight",     Material.ENDER_EYE);
        ICONS.put("mob_gold_drop",         Material.GOLD_NUGGET);
        ICONS.put("border_announcer",      Material.BELL);
        ICONS.put("spectator",             Material.GLASS);
        ICONS.put("special_apple",         Material.GOLDEN_APPLE);
        ICONS.put("kit_parcial",           Material.YELLOW_DYE);
        ICONS.put("kit_completo",          Material.GREEN_DYE);
        ICONS.put("final",                 Material.DIAMOND);
        ICONS.put("teste",                 Material.EMERALD);
        ICONS.put("efeitos",               Material.SPLASH_POTION);
        ICONS.put("efeito_resistencia",    Material.SHIELD);
        ICONS.put("vida_revive",           Material.HEART_OF_THE_SEA);
        ICONS.put("tempo_vivo",            Material.CLOCK);
        ICONS.put("ddrd",                  Material.GOLD_INGOT);
    }

    static {
        DISPLAY_NAMES.put("modo_jogo",              "Modo de Jogo");
        DISPLAY_NAMES.put("modos",                  "Configuração dos Modos");
        DISPLAY_NAMES.put("mensagens",              "Mensagens");
        DISPLAY_NAMES.put("bossbar_template",       "Template da BossBar");
        DISPLAY_NAMES.put("pvp_decimais",           "Casas Decimais PvP");
        DISPLAY_NAMES.put("heal_on_kill",           "Vida ao Matar");
        DISPLAY_NAMES.put("grappler_item",          "Grappler");
        DISPLAY_NAMES.put("habilitar_receita_maça", "Receita Maçã Especial");
        DISPLAY_NAMES.put("tab",                    "Cores do TAB");
        DISPLAY_NAMES.put("revive",                 "Sistema de Revive");
        DISPLAY_NAMES.put("game",                   "Configurações do Jogo");
        DISPLAY_NAMES.put("gamerules",              "Regras do Jogo");
        DISPLAY_NAMES.put("assist_janela_segundos", "Janela de Assist (s)");
        DISPLAY_NAMES.put("mensagens_comandos",     "Mensagens de Comandos");
        DISPLAY_NAMES.put("pausa_jogador",          "Pausa por Jogador");
        DISPLAY_NAMES.put("mvp_design",             "Design do MVP");
        DISPLAY_NAMES.put("mvp_pontos",             "Pontuação MVP");
        DISPLAY_NAMES.put("holograma",              "Holograma de Morte");
        DISPLAY_NAMES.put("mensagens_revive",       "Mensagens de Revive");
        DISPLAY_NAMES.put("compass_tracker",        "Tracker Compass");
        DISPLAY_NAMES.put("med_items",              "Itens Médicos");
        DISPLAY_NAMES.put("gold_potion",            "Poção de Ouro");
        DISPLAY_NAMES.put("scoreboard",             "Scoreboard");
        DISPLAY_NAMES.put("craft_book",             "CraftBook");
        DISPLAY_NAMES.put("pronto",                 "Comando /pronto");
        DISPLAY_NAMES.put("anunciar",               "Comando /anunciar");
        DISPLAY_NAMES.put("proximidade_fight",      "Alerta de Proximidade");
        DISPLAY_NAMES.put("mob_gold_drop",          "Drops de Ouro (Mobs)");
        DISPLAY_NAMES.put("border_announcer",       "Anunciador de Bordas");
        DISPLAY_NAMES.put("spectator",              "Modo Espectador");
        DISPLAY_NAMES.put("special_apple",          "Maçã Especial");
        DISPLAY_NAMES.put("kit_parcial",            "Kit Parcial");
        DISPLAY_NAMES.put("kit_completo",           "Kit Completo");
        DISPLAY_NAMES.put("final",                  "Modo Final");
        DISPLAY_NAMES.put("teste",                  "Modo Teste");
        DISPLAY_NAMES.put("efeitos",                "Efeitos");
        DISPLAY_NAMES.put("efeito_resistencia",     "Efeito de Resistência");
        DISPLAY_NAMES.put("vida_revive",            "Vida ao Reviver");
        DISPLAY_NAMES.put("tempo_vivo",             "Tempo Vivo");
        DISPLAY_NAMES.put("ddrd",                   "DDRD");
        DISPLAY_NAMES.put("habilitar",              "Habilitar");
        DISPLAY_NAMES.put("bordas",                 "Bordas");
        DISPLAY_NAMES.put("lore",                   "Lore do Item");
        DISPLAY_NAMES.put("chances_nivel",          "Chances por Nível");
        DISPLAY_NAMES.put("duracao",                "Duração");
        DISPLAY_NAMES.put("mobs",                   "Mobs");
    }

    public ConfigGui(Tlsplugin plugin) {
        this.plugin = plugin;
        this.CONFIG_PATH_KEY = new NamespacedKey(plugin, "config_path");
        this.NAV_TYPE_KEY    = new NamespacedKey(plugin, "nav_type");
    }

    // ─── Main menu ────────────────────────────────────────────────────────────

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8[§bTLS§8] §f§lConfiguração");

        // Top and bottom glass border
        ItemStack glass = makeGlass();
        for (int i = 0;  i < 9;  i++) inv.setItem(i,      glass);
        for (int i = 45; i < 54; i++) inv.setItem(i,      glass);

        // Side borders for rows 1-4
        for (int r = 1; r <= 4; r++) {
            inv.setItem(r * 9,     glass);
            inv.setItem(r * 9 + 8, glass);
        }

        // Title item (top-center)
        inv.setItem(4, decor(Material.NETHER_STAR,
            "§b§lTLS Plugin §8▸ §f§lConfiguração",
            "§8§m──────────────────────────",
            "§7Seleciona uma secção para editar.",
            "§7Apenas §badmins §7podem alterar valores.",
            "§8§m──────────────────────────"
        ));

        // Close button (bottom-center)
        inv.setItem(49, navItem(Material.BARRIER, "§c§lFechar",
            "§7Fecha este menu.", "close", null));

        // Place config keys in content area (slots 10-16, 19-25, 28-34, 37-43)
        List<String> keys = new ArrayList<>(plugin.getConfig().getKeys(false));
        int slot = 10;
        for (String key : keys) {
            if (slot > 43) break;
            // Advance past border columns
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot > 43) break;

            Object value = plugin.getConfig().get(key);
            String display = DISPLAY_NAMES.getOrDefault(key, formatKey(key));
            Material icon  = ICONS.getOrDefault(key, iconFor(value));
            inv.setItem(slot, configItem(key, display, icon, value));
            slot++;
        }

        player.openInventory(inv);
    }

    // ─── Section view ─────────────────────────────────────────────────────────

    public void openSection(Player player, String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return;

        List<String> keys  = new ArrayList<>(section.getKeys(false));
        int contentNeeded  = keys.size();

        // Calculate rows: header row + content rows (7 per row) + footer row
        int contentRows = Math.max(1, (int) Math.ceil(contentNeeded / 7.0));
        int rows        = Math.min(6, contentRows + 2);
        int size        = rows * 9;

        String lastKey = path.contains(".")
            ? path.substring(path.lastIndexOf('.') + 1)
            : path;
        String sTitle = DISPLAY_NAMES.getOrDefault(lastKey, formatKey(lastKey));

        Inventory inv = Bukkit.createInventory(null, size, "§8[§bTLS§8] §f" + sTitle);

        // Top and bottom borders
        ItemStack glass = makeGlass();
        for (int i = 0;       i < 9;    i++) inv.setItem(i,      glass);
        for (int i = size - 9; i < size; i++) inv.setItem(i,      glass);

        // Side borders for content rows
        for (int r = 1; r < rows - 1; r++) {
            inv.setItem(r * 9,     glass);
            inv.setItem(r * 9 + 8, glass);
        }

        // Breadcrumb header
        inv.setItem(4, decor(Material.MAP,
            "§b§l" + sTitle,
            "§8§m──────────────────────────",
            "§8Caminho: §7" + path,
            "§8§m──────────────────────────"
        ));

        // Back + Close buttons (bottom row)
        inv.setItem(size - 5, navItem(Material.ARROW,   "§f§lVoltar",   "§7Volta ao menu anterior", "back",  path));
        inv.setItem(size - 1, navItem(Material.BARRIER, "§c§lFechar",   "§7Fecha este menu",        "close", null));

        // Place section entries in content area
        int slot = 10;
        for (String key : keys) {
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= size - 9) break;

            String fullPath = path + "." + key;
            Object value    = section.get(key);
            String display  = DISPLAY_NAMES.getOrDefault(key, formatKey(key));
            Material icon   = ICONS.getOrDefault(key, iconFor(value));
            inv.setItem(slot, configItem(fullPath, display, icon, value));
            slot++;
        }

        player.openInventory(inv);
    }

    // ─── Item builders ────────────────────────────────────────────────────────

    ItemStack configItem(String path, String display, Material icon, Object value) {
        List<String> lore = new ArrayList<>();
        lore.add("§8§m──────────────────────────");
        String navType;

        if (value instanceof ConfigurationSection cs) {
            Set<String> subKeys = cs.getKeys(false);
            lore.add("§7Sub-opções: §b" + subKeys.size());
            // Preview up to 4 direct (non-section) values
            int preview = 0;
            for (String subKey : subKeys) {
                Object subVal = cs.get(subKey);
                if (!(subVal instanceof ConfigurationSection) && !(subVal instanceof List) && preview < 4) {
                    lore.add("§8  §7" + formatKey(subKey) + "§8: " + formatValue(subVal));
                    preview++;
                }
            }
            if (subKeys.size() - preview > 0)
                lore.add("§8  §7... e mais " + (subKeys.size() - preview) + " opções");
            lore.add("§8§m──────────────────────────");
            lore.add("§e▶ Clica para explorar");
            navType = "section";

        } else if (value instanceof List<?> list) {
            lore.add("§7Lista com §b" + list.size() + " §7itens");
            int shown = 0;
            for (Object item : list) {
                if (shown++ >= 4) { lore.add("§8  §7..."); break; }
                String s = String.valueOf(item);
                if (s.length() > 40) s = s.substring(0, 37) + "...";
                lore.add("§8  §7" + s);
            }
            lore.add("§8§m──────────────────────────");
            lore.add("§8Listas: edita directamente no config.yml");
            navType = "list";

        } else {
            lore.add("§7Valor atual§8: " + formatValue(value));
            lore.add("§8§m──────────────────────────");
            if (value instanceof Boolean) {
                lore.add((Boolean) value ? "§a⇄ Clica para §cdesativar" : "§c⇄ Clica para §aetivar");
                navType = "toggle";
            } else {
                lore.add("§e✎ Clica para editar");
                navType = "edit";
            }
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + display);
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(CONFIG_PATH_KEY, PersistentDataType.STRING, path);
            meta.getPersistentDataContainer().set(NAV_TYPE_KEY,    PersistentDataType.STRING, navType);
            item.setItemMeta(meta);
        }
        return item;
    }

    ItemStack navItem(Material mat, String name, String loreLine, String navType, String path) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(loreLine));
            meta.getPersistentDataContainer().set(NAV_TYPE_KEY, PersistentDataType.STRING, navType);
            if (path != null)
                meta.getPersistentDataContainer().set(CONFIG_PATH_KEY, PersistentDataType.STRING, path);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack decor(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) meta.setLore(Arrays.asList(loreLines));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeGlass() {
        return decor(Material.BLACK_STAINED_GLASS_PANE, "§8");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public String formatValue(Object value) {
        if (value == null)                  return "§cnulo";
        if (value instanceof Boolean b)     return b ? "§a✔ ativado" : "§c✘ desativado";
        if (value instanceof Number)        return "§e" + value;
        if (value instanceof String s) {
            String display = s.replace("§", "&");
            if (display.length() > 40) display = display.substring(0, 37) + "...";
            return "§f" + display;
        }
        if (value instanceof List<?> l)     return "§7Lista[" + l.size() + "]";
        return "§7" + value;
    }

    public String formatKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("[_\\-]")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private Material iconFor(Object value) {
        if (value instanceof Boolean b) return b ? Material.LIME_DYE : Material.GRAY_DYE;
        if (value instanceof Number)    return Material.GOLD_NUGGET;
        if (value instanceof List)      return Material.PAPER;
        if (value instanceof ConfigurationSection) return Material.CHEST;
        return Material.NAME_TAG;
    }
}
