package com.tlsplugin.gui;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CraftBookGui {

    public static String PREFIX_MAIN;
    public static String PREFIX_CATEGORY;
    public static String PREFIX_RECIPE;
    public static String PREFIX_SPECIAL;

    private static FileConfiguration guiConfig;
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private enum Tier {
        WOODEN  ("Madeira",  "§6", Material.OAK_PLANKS,   "Oak Planks"),
        STONE   ("Pedra",    "§7", Material.COBBLESTONE,  "Cobblestone"),
        IRON    ("Ferro",    "§f", Material.IRON_INGOT,   "Iron Ingot"),
        GOLDEN  ("Ouro",     "§e", Material.GOLD_INGOT,   "Gold Ingot"),
        COPPER  ("Cobre",    "§c", Material.COPPER_INGOT, "Copper Ingot"),
        DIAMOND ("Diamante", "§b", Material.DIAMOND,      "Diamond");

        String ptName, color, ingName;
        final Material material;

        Tier(String ptName, String color, Material material, String ingName) {
            this.ptName   = ptName;
            this.color    = color;
            this.material = material;
            this.ingName  = ingName;
        }
    }

    private enum Weapon {
        LONGSWORD  ("Longsword",  "⚔",  "_W__W_WSW"),
        BROADSWORD ("Broadsword", "⚔",  "__W_W_S__"),
        HAMMER     ("Hammer",     "🔨", "WSWWSW_S_"),
        HATCHET    ("Hatchet",    "🪓", "_W_WS__S_"),
        SICKLE     ("Sickle",     "🌾", "_W___WSW_"),
        SCYTHE     ("Scythe",     "🌙", "WWW__S__S"),
        BATTLEAXE  ("Battleaxe",  "🪓", "WW_WSW_S_");

        final String name, emoji, pattern;

        Weapon(String name, String emoji, String pattern) {
            this.name    = name;
            this.emoji   = emoji;
            this.pattern = pattern;
        }
    }

    // ── Carregar gui.yml ──────────────────────────────────────────────────
    public static void loadConfig(File dataFolder) {
        File guiFile = new File(dataFolder, "gui.yml");
        if (!guiFile.exists()) {
            com.tlsplugin.Tlsplugin plugin = com.tlsplugin.Tlsplugin.getInstance();
            if (plugin != null) plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        PREFIX_MAIN     = guiConfig.getString("craft_book.titulos.principal",  "§0 §lLivro de Receitas");
        PREFIX_CATEGORY = guiConfig.getString("craft_book.titulos.categorias", "§0§lArmas de ");
        PREFIX_RECIPE   = guiConfig.getString("craft_book.titulos.receita",    "§0 Receita: ");
        PREFIX_SPECIAL  = guiConfig.getString("craft_book.titulos.especiais",  "§0§lItens Especiais");

        for (Tier t : Tier.values()) {
            String base = "craft_book.tiers." + t.name() + ".";
            t.ptName = guiConfig.getString(base + "nome", t.ptName);
            t.color  = guiConfig.getString(base + "cor",  t.color);
        }
    }

    private static FileConfiguration cfg() {
        if (guiConfig == null) {
            com.tlsplugin.Tlsplugin p = com.tlsplugin.Tlsplugin.getInstance();
            if (p != null) loadConfig(p.getDataFolder());
        }
        return guiConfig;
    }

    private static String cfgStr(String path, String def) {
        FileConfiguration c = cfg();
        return c != null ? c.getString(path, def) : def;
    }

    private static boolean cfgBool(String path, boolean def) {
        FileConfiguration c = cfg();
        return c != null ? c.getBoolean(path, def) : def;
    }

    // ── Menu Principal ────────────────────────────────────────────────────
    public static void openMain(Player player) {
        ensurePrefixes();
        Inventory inv = Bukkit.createInventory(null, 54, PREFIX_MAIN);
        fillGlass(inv);

        int[] catSlots = {10, 12, 14, 16, 28, 30, 32};
        Tier[] tiers = Tier.values();
        String loreCategoria = cfgStr("craft_book.lores.clique_categoria", "§7Clique para ver as receitas");

        for (int i = 0; i < tiers.length; i++) {
            Tier t = tiers[i];
            ItemStack icon = makeItem(t.material,
                    t.color + "§lArmas de " + t.ptName,
                    Arrays.asList(loreCategoria));
            inv.setItem(catSlots[i], icon);
        }

        String nomeEspeciais = cfgStr("craft_book.titulos.especiais", "§0§lItens Especiais")
                .replace("§0§l", "§d§l");
        ItemStack special = makeItem(Material.GOLDEN_APPLE, nomeEspeciais,
                Arrays.asList(loreCategoria));
        inv.setItem(32, special);

        player.openInventory(inv);
    }

    // ── Menu Categoria ────────────────────────────────────────────────────
    public static void openCategory(Player player, String tierName) {
        Tier tier = tierByPtName(tierName);
        if (tier == null) return;

        ensurePrefixes();
        Inventory inv = Bukkit.createInventory(null, 54, PREFIX_CATEGORY + tier.ptName);
        fillGlass(inv);

        int[] slots = {10, 12, 14, 16, 28, 30, 32};
        Weapon[] weapons = Weapon.values();
        String loreArma = cfgStr("craft_book.lores.clique_arma", "§7Clique para ver a receita");

        for (int i = 0; i < weapons.length; i++) {
            Weapon w = weapons[i];
            String iaId = "tls_plugin:" + tier.name().toLowerCase() + "_" + w.name.toLowerCase();
            ItemStack icon = getIAItem(iaId);
            if (icon == null) icon = makeItem(Material.IRON_SWORD,
                    tier.color + "§l" + w.name + " de " + tier.ptName, null);
            setItemMeta(icon, tier.color + "§l" + w.name + " de " + tier.ptName,
                    Arrays.asList(loreArma));
            inv.setItem(slots[i], icon);
        }

        inv.setItem(49, backButton("Catálogo"));
        player.openInventory(inv);
    }

    // ── Menu Receita de Arma ──────────────────────────────────────────────
    public static void openWeaponRecipe(Player player, String tierName, String weaponName) {
        Tier tier     = tierByPtName(tierName);
        Weapon weapon = weaponByName(weaponName);
        if (tier == null || weapon == null) return;

        ensurePrefixes();
        String title = PREFIX_RECIPE + weapon.emoji + " " + weapon.name + " de " + tier.ptName;
        Inventory inv = Bukkit.createInventory(null, 54, title);
        fillGlass(inv);

        String loreIng = cfgStr("craft_book.lores.ingrediente", "§7Ingrediente");
        ItemStack emptySlot = makeItem(glassPaneGrelha(), " ", null);

        for (int i = 0; i < 9; i++) {
            char ch   = weapon.pattern.charAt(i);
            int  slot = GRID[i];
            if (ch == 'W') {
                inv.setItem(slot, makeItem(tier.material, "§f" + tier.ingName, Arrays.asList(loreIng)));
            } else if (ch == 'S') {
                inv.setItem(slot, makeItem(Material.STICK, "§fStick", Arrays.asList(loreIng)));
            } else {
                inv.setItem(slot, emptySlot);
            }
        }

        inv.setItem(23, makeItem(Material.ARROW, "§e➜", null));

        // Resultado — só altera o nome, atributos nativos (dano/velocidade) mantidos
        String iaId  = "tls_plugin:" + tier.name().toLowerCase() + "_" + weapon.name.toLowerCase();
        ItemStack result = getIAItem(iaId);
        if (result == null) result = makeItem(Material.IRON_SWORD,
                tier.color + "§l" + weapon.name + " de " + tier.ptName, null);

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(tier.color + "§l" + weapon.name + " de " + tier.ptName);
            result.setItemMeta(meta);
        }
        inv.setItem(25, result);

        inv.setItem(49, backButton("Armas de " + tier.ptName));
        player.openInventory(inv);
    }

    // ── Menu Itens Especiais ──────────────────────────────────────────────
    public static void openSpecial(Player player) {
        ensurePrefixes();
        Inventory inv = Bukkit.createInventory(null, 54, PREFIX_SPECIAL);
        fillGlass(inv);

        int[] slots = {10, 12, 14, 28, 30};
        String[] ids = {
                "tls_plugin:tls_special_apple",
                "tls_plugin:goldpotion_item",
                "tls_plugin:grappler_item",
                "tls_plugin:kit_completo",
                "tls_plugin:kit_parcial"
        };
        String[] configKeys = {
                "tls_special_apple", "goldpotion_item", "grappler_item",
                "kit_completo", "kit_parcial"
        };
        String[] fallbackNames = {
                "§a§lMaçã Dourada Especial", "§e§lPoção Dourada", "§5§lGrappler",
                "§e§lKit Médico (Completo)", "§c§lKit Médico (Parcial)"
        };
        Material[] fallbacks = {
                Material.GOLDEN_APPLE, Material.POTION, Material.FISHING_ROD,
                Material.PAPER, Material.PAPER
        };

        String loreEsp = cfgStr("craft_book.lores.clique_especial", "§7Clique para ver a receita");

        int slotIndex = 0;
        for (int i = 0; i < ids.length; i++) {
            boolean enabled = cfgBool("craft_book.special_items." + configKeys[i] + ".habilitar", true);
            if (!enabled) continue;

            String displayName = cfgStr("craft_book.special_items." + configKeys[i] + ".nome_display",
                    fallbackNames[i]);

            ItemStack icon = getIAItem(ids[i]);
            if (icon == null) icon = makeItem(fallbacks[i], displayName, null);
            setItemMeta(icon, displayName, Arrays.asList(loreEsp));
            inv.setItem(slots[slotIndex], icon);
            slotIndex++;
        }

        inv.setItem(49, backButton("Catálogo"));
        player.openInventory(inv);
    }

    // ── Menu Receita de Item Especial ─────────────────────────────────────
    public static void openSpecialRecipe(Player player, String itemId) {
        ensurePrefixes();
        String title = PREFIX_RECIPE + specialDisplayName(itemId);
        Inventory inv = Bukkit.createInventory(null, 54, title);
        fillGlass(inv);

        String loreIng = cfgStr("craft_book.lores.ingrediente", "§7Ingrediente");
        ItemStack emptySlot = makeItem(glassPaneGrelha(), " ", null);

        ItemStack[] grid = buildSpecialGrid(itemId, loreIng);
        for (int i = 0; i < 9; i++) {
            inv.setItem(GRID[i], grid[i] != null ? grid[i] : emptySlot);
        }

        inv.setItem(23, makeItem(Material.ARROW, "§e➜", null));

        // Resultado — aplica lore do config para itens especiais, mantém atributos nativos
        ItemStack result = getIAItem(itemId);
        String displayName = specialDisplayName(itemId);
        if (result == null) result = makeItem(Material.PAPER, displayName, null);

        // Aplicar lore do config.yml (goldpotion, grappler, special apple têm lore própria)
        applyConfigLore(result, itemId);

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            result.setItemMeta(meta);
        }
        inv.setItem(25, result);

        inv.setItem(49, backButton("Itens Especiais"));
        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Aplica a lore definida no config.yml ao item resultado na GUI.
     * Necessário porque os itens na GUI são clones temporários — o updater
     * periódico dos listeners não os alcança.
     */
    private static void applyConfigLore(ItemStack item, String itemId) {
        com.tlsplugin.Tlsplugin plugin = com.tlsplugin.Tlsplugin.getInstance();
        if (plugin == null) return;

        String loreKey;
        switch (itemId) {
            case "tls_plugin:goldpotion_item":   loreKey = "gold_potion.lore"; break;
            case "tls_plugin:grappler_item":     loreKey = "grappler_item.lore"; break;
            case "tls_plugin:tls_special_apple": loreKey = "med_items.special_apple.lore"; break;
            case "tls_plugin:kit_completo":      loreKey = "med_items.kit_completo.lore"; break;
            case "tls_plugin:kit_parcial":       loreKey = "med_items.kit_parcial.lore"; break;
            default: return;
        }

        List<String> lore = plugin.getConfig().getStringList(loreKey);
        if (!lore.isEmpty()) {
            com.tlsplugin.utils.ItemUtils.applyLore(item, lore);
        }
    }

    private static void ensurePrefixes() {
        if (PREFIX_MAIN == null) {
            com.tlsplugin.Tlsplugin p = com.tlsplugin.Tlsplugin.getInstance();
            if (p != null) loadConfig(p.getDataFolder());
            else {
                PREFIX_MAIN     = "§0 §lLivro de Receitas";
                PREFIX_CATEGORY = "§0§lArmas de ";
                PREFIX_RECIPE   = "§0 Receita: ";
                PREFIX_SPECIAL  = "§0§lItens Especiais";
            }
        }
    }

    private static Material glassPaneGeral() {
        String name = cfgStr("craft_book.fundo.cor_geral", "GRAY_STAINED_GLASS_PANE");
        try { return Material.valueOf(name); } catch (Exception e) { return Material.GRAY_STAINED_GLASS_PANE; }
    }

    private static Material glassPaneGrelha() {
        String name = cfgStr("craft_book.fundo.cor_grelha", "LIGHT_GRAY_STAINED_GLASS_PANE");
        try { return Material.valueOf(name); } catch (Exception e) { return Material.LIGHT_GRAY_STAINED_GLASS_PANE; }
    }

    private static void fillGlass(Inventory inv) {
        ItemStack glass = makeItem(glassPaneGeral(), " ", null);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore != null) m.setLore(lore);
            item.setItemMeta(m);
        }
        return item;
    }

    private static void setItemMeta(ItemStack item, String name, List<String> lore) {
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore != null) m.setLore(lore);
            item.setItemMeta(m);
        }
    }

    private static ItemStack backButton(String destination) {
        String label = cfgStr("craft_book.botao_voltar", "§c§l← Voltar");
        return makeItem(Material.ARROW,
                label + (destination.isEmpty() ? "" : " — " + destination),
                Arrays.asList(cfgStr("craft_book.lores.voltar", "§7Clique para voltar")));
    }

    private static ItemStack getIAItem(String namespacedId) {
        try {
            CustomStack cs = CustomStack.getInstance(namespacedId);
            if (cs != null) return cs.getItemStack().clone();
        } catch (Exception ignored) {}
        return null;
    }

    private static Tier tierByPtName(String ptName) {
        for (Tier t : Tier.values()) {
            if (t.ptName.equalsIgnoreCase(ptName)) return t;
        }
        return null;
    }

    private static Weapon weaponByName(String name) {
        for (Weapon w : Weapon.values()) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    private static String specialDisplayName(String id) {
        String key = id.replace("tls_plugin:", "");
        return cfgStr("craft_book.special_items." + key + ".nome_display", id);
    }

    private static ItemStack[] buildSpecialGrid(String id, String loreIng) {
        ItemStack[] g = new ItemStack[9];
        ItemStack gold   = makeItem(Material.GOLD_INGOT,   "§fGold Ingot",   Arrays.asList(loreIng));
        ItemStack iron   = makeItem(Material.IRON_INGOT,   "§fIron Ingot",   Arrays.asList(loreIng));
        ItemStack apple  = makeItem(Material.GOLDEN_APPLE, "§fGolden Apple", Arrays.asList(loreIng));
        ItemStack potion = makeItem(Material.POTION,        "§fPotion",       Arrays.asList(loreIng));
        ItemStack rod    = makeItem(Material.FISHING_ROD,   "§fFishing Rod",  Arrays.asList(loreIng));
        ItemStack stick  = makeItem(Material.STICK,         "§fStick",        Arrays.asList(loreIng));
        ItemStack block  = makeItem(Material.IRON_BLOCK,    "§fIron Block",   Arrays.asList(loreIng));
        ItemStack yWool  = makeItem(Material.YELLOW_WOOL,   "§fYellow Wool",  Arrays.asList(loreIng));
        ItemStack rWool  = makeItem(Material.RED_WOOL,      "§fRed Wool",     Arrays.asList(loreIng));
        ItemStack pHead  = makeItem(Material.PLAYER_HEAD,   "§fPlayer Head",  Arrays.asList(loreIng));

        switch (id) {
            case "tls_plugin:tls_special_apple":
                g[0]=gold; g[1]=gold;   g[2]=gold;
                g[3]=gold; g[4]=pHead;  g[5]=gold;
                g[6]=gold; g[7]=gold;   g[8]=gold;
                break;
            case "tls_plugin:goldpotion_item":
                g[0]=gold;   g[1]=gold;   g[2]=gold;
                g[3]=gold;   g[4]=potion; g[5]=gold;
                g[6]=gold;   g[7]=gold;   g[8]=gold;
                break;
            case "tls_plugin:grappler_item":
                g[2]=rod;
                g[3]=iron;  g[4]=stick;
                g[6]=block; g[7]=iron;
                break;
            case "tls_plugin:kit_completo":
                g[0]=gold;  g[1]=yWool; g[2]=gold;
                g[3]=yWool; g[4]=apple; g[5]=yWool;
                g[6]=gold;  g[7]=yWool; g[8]=gold;
                break;
            case "tls_plugin:kit_parcial":
                g[0]=iron;  g[1]=rWool; g[2]=iron;
                g[3]=rWool; g[4]=apple; g[5]=rWool;
                g[6]=iron;  g[7]=rWool; g[8]=iron;
                break;
        }
        return g;
    }
}