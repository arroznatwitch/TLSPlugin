package com.tlsplugin.gui;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ConfigGuiListener implements Listener {

    private static final String GUI_TITLE_PREFIX = "§8[§bTLS§8]";

    private final Tlsplugin plugin;
    private final ConfigGui  configGui;

    /** UUID → navigation stack. Top = current open section path. Empty = main menu. */
    private final Map<UUID, Deque<String>> navStack     = new HashMap<>();
    /** UUID → config path currently awaiting chat input. */
    private final Map<UUID, String>        awaitingChat = new HashMap<>();

    public ConfigGuiListener(Tlsplugin plugin, ConfigGui configGui) {
        this.plugin    = plugin;
        this.configGui = configGui;
    }

    // ─── Inventory click ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String navType = meta.getPersistentDataContainer().get(configGui.NAV_TYPE_KEY, PersistentDataType.STRING);
        String path    = meta.getPersistentDataContainer().get(configGui.CONFIG_PATH_KEY, PersistentDataType.STRING);

        if (navType == null) return;

        switch (navType) {

            case "close" -> {
                player.closeInventory();
                navStack.remove(player.getUniqueId());
            }

            case "back" -> {
                Deque<String> stack = navStack.get(player.getUniqueId());
                if (stack != null && !stack.isEmpty()) {
                    stack.pop(); // remove current
                    if (stack.isEmpty()) configGui.openMain(player);
                    else                 configGui.openSection(player, stack.peek());
                } else {
                    configGui.openMain(player);
                }
            }

            case "section" -> {
                if (path == null) return;
                navStack.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>()).push(path);
                configGui.openSection(player, path);
            }

            case "toggle" -> {
                if (path == null) return;
                boolean current = plugin.getConfig().getBoolean(path);
                plugin.getConfig().set(path, !current);
                plugin.saveConfig();
                plugin.reloadAllConfigs();
                String newValStr = configGui.formatValue(!current);
                player.sendMessage("§f[§bTLS§f] §a✔ §b" + path + " §falterado para " + newValStr);
                reopenCurrent(player);
            }

            case "edit" -> {
                if (path == null) return;
                Object currentVal = plugin.getConfig().get(path);
                player.closeInventory();
                player.sendMessage(" ");
                player.sendMessage("§8§m──────────────────────────────");
                player.sendMessage("§f[§bTLS§f] §e§lEditar Configuração");
                player.sendMessage("§8§m──────────────────────────────");
                player.sendMessage("§7Chave§8: §b" + path);
                player.sendMessage("§7Valor atual§8: " + configGui.formatValue(currentVal));
                player.sendMessage("§7Tipo§8: §e" + typeName(currentVal));
                player.sendMessage("§8§m──────────────────────────────");
                player.sendMessage("§eEscreve o novo valor no chat.");
                player.sendMessage("§7(§ccancelar §7para cancelar)");
                player.sendMessage(" ");
                awaitingChat.put(player.getUniqueId(), path);
            }

            case "list" ->
                player.sendMessage("§f[§bTLS§f] §cListas têm de ser editadas diretamente no §bconfig.yml§c.");
        }
    }

    // ─── Chat input capture ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String path   = awaitingChat.get(player.getUniqueId());
        if (path == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancelar")) {
            awaitingChat.remove(player.getUniqueId());
            player.sendMessage("§f[§bTLS§f] §cEditação cancelada.");
            Bukkit.getScheduler().runTask(plugin, () -> reopenCurrent(player));
            return;
        }

        Object current = plugin.getConfig().get(path);
        Object newVal;
        try {
            newVal = parseInput(input, current);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§f[§bTLS§f] §c" + e.getMessage());
            player.sendMessage("§f[§bTLS§f] §7Tenta novamente ou escreve §ccancelar§7.");
            return;
        }

        awaitingChat.remove(player.getUniqueId());
        final Object finalVal = newVal;
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getConfig().set(path, finalVal);
            plugin.saveConfig();
            plugin.reloadAllConfigs();
            player.sendMessage(" ");
            player.sendMessage("§8§m──────────────────────────────");
            player.sendMessage("§f[§bTLS§f] §a✔ §lConfiguração atualizada!");
            player.sendMessage("§7Chave§8:      §b" + path);
            player.sendMessage("§7Novo valor§8: " + configGui.formatValue(finalVal));
            player.sendMessage("§8§m──────────────────────────────");
            reopenCurrent(player);
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Opens the inventory the player was on (based on their nav stack). */
    private void reopenCurrent(Player player) {
        Deque<String> stack = navStack.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) configGui.openMain(player);
        else                                   configGui.openSection(player, stack.peek());
    }

    /** Clears the navigation stack — called before opening the main menu fresh. */
    public void clearStack(UUID uuid) {
        navStack.remove(uuid);
    }

    /** Parses user input to match the type of the current config value. */
    private Object parseInput(String input, Object current) {
        if (current instanceof Integer) {
            try { return Integer.parseInt(input); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor inválido! Esperava um número inteiro (ex: 10).");
            }
        }
        if (current instanceof Long) {
            try { return Long.parseLong(input); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor inválido! Esperava um número longo (ex: 1200).");
            }
        }
        if (current instanceof Double) {
            try { return Double.parseDouble(input.replace(',', '.')); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor inválido! Esperava um número decimal (ex: 0.5).");
            }
        }
        if (current instanceof Float) {
            try { return Float.parseFloat(input.replace(',', '.')); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor inválido! Esperava um número decimal (ex: 0.5).");
            }
        }
        if (current instanceof Boolean) {
            String lower = input.toLowerCase();
            if (lower.equals("true")  || lower.equals("sim") || lower.equals("yes") || lower.equals("1"))
                return true;
            if (lower.equals("false") || lower.equals("nao") || lower.equals("não") || lower.equals("no") || lower.equals("0"))
                return false;
            throw new IllegalArgumentException("Valor inválido! Usa: true/false, sim/não.");
        }
        // String: allow § color codes entered as &
        return input.replace("&", "§");
    }

    private String typeName(Object value) {
        if (value == null)               return "nulo";
        if (value instanceof Boolean)    return "boolean (true/false)";
        if (value instanceof Integer)    return "inteiro";
        if (value instanceof Long)       return "inteiro longo";
        if (value instanceof Double)     return "decimal";
        if (value instanceof Float)      return "decimal";
        if (value instanceof String)     return "texto";
        if (value instanceof List)       return "lista";
        return value.getClass().getSimpleName();
    }
}
