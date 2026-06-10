package com.tlsplugin.listeners;

import com.tlsplugin.gui.CraftBookGui;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * CraftBookListener — trata:
 *  1. Clique direito no item tls_plugin:craft_book → abre menu principal
 *  2. Cliques dentro das GUIs do Livro de Receitas → navegação
 *  3. Bloqueia qualquer drag/move de itens nas GUIs
 */
public class CraftBookListener implements Listener {

    private static final String CRAFT_BOOK_ID = "tls_plugin:craft_book";

    // ── 1. Abrir o livro ──────────────────────────────────────────────────
    @EventHandler
    public void onBookUse(PlayerInteractEvent e) {
        // Só clique direito, só mão principal
        if (e.getHand() != EquipmentSlot.HAND) return;

        org.bukkit.event.block.Action action = e.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        // Verificar se é o craft_book do ItemsAdder
        CustomStack cs = CustomStack.byItemStack(item);
        if (cs == null) return;
        if (!CRAFT_BOOK_ID.equals(cs.getNamespacedID())) return;

        e.setCancelled(true);
        CraftBookGui.openMain(e.getPlayer());
    }

    // ── 2. Navegação nas GUIs ─────────────────────────────────────────────
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();

        // Verificar se é um dos nossos menus
        if (!isOurGui(title)) return;

        // Bloquear TUDO por defeito
        e.setCancelled(true);

        // Ignorar cliques fora do inventário superior
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() == player.getInventory()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String clickedName = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";

        // ── Menu Principal ────────────────────────────────────────────────
        if (title.equals(CraftBookGui.PREFIX_MAIN)) {
            if (clickedName.contains("Armas de ")) {
                // Extrair nome do tier: "§6§lArmas de Madeira" → "Madeira"
                String tierName = stripColors(clickedName).replace("Armas de ", "").trim();
                CraftBookGui.openCategory(player, tierName);
            } else if (clickedName.contains("Itens Especiais")) {
                CraftBookGui.openSpecial(player);
            }
            return;
        }

        // ── Menu Categoria ────────────────────────────────────────────────
        if (title.startsWith(CraftBookGui.PREFIX_CATEGORY)) {
            String tierName = title.replace(CraftBookGui.PREFIX_CATEGORY, "").trim();

            if (isBackButton(clickedName)) {
                CraftBookGui.openMain(player);
                return;
            }

            // Nome do item clicado = "§f§lLongsword de Ferro" → extrair weapon name
            String clean = stripColors(clickedName);
            // Formato: "<WeaponName> de <TierName>"
            if (clean.contains(" de ")) {
                String weaponName = clean.substring(0, clean.indexOf(" de ")).trim();
                CraftBookGui.openWeaponRecipe(player, tierName, weaponName);
            }
            return;
        }

        // ── Menu Receita de Arma ──────────────────────────────────────────
        if (title.startsWith(CraftBookGui.PREFIX_RECIPE)) {
            if (isBackButton(clickedName)) {
                // Extrair destino do botão: "§c§l← Voltar — Armas de Ferro"
                String dest = stripColors(clickedName).replace("← Voltar — ", "").trim();
                if (dest.equals("Catálogo")) {
                    CraftBookGui.openMain(player);
                } else if (dest.startsWith("Armas de ")) {
                    String tierName = dest.replace("Armas de ", "").trim();
                    CraftBookGui.openCategory(player, tierName);
                } else if (dest.equals("Itens Especiais")) {
                    CraftBookGui.openSpecial(player);
                } else {
                    CraftBookGui.openMain(player);
                }
            }
            return;
        }

        // ── Menu Itens Especiais ──────────────────────────────────────────
        if (title.equals(CraftBookGui.PREFIX_SPECIAL)) {
            if (isBackButton(clickedName)) {
                CraftBookGui.openMain(player);
                return;
            }

            String clean = stripColors(clickedName);
            String id = specialNameToId(clean);
            if (id != null) {
                CraftBookGui.openSpecialRecipe(player, id);
            }
        }
    }

    // ── 3. Bloquear drag ─────────────────────────────────────────────────
    @EventHandler
    public void onGuiDrag(InventoryDragEvent e) {
        if (isOurGui(e.getView().getTitle())) {
            e.setCancelled(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isOurGui(String title) {
        return title.equals(CraftBookGui.PREFIX_MAIN)
            || title.startsWith(CraftBookGui.PREFIX_CATEGORY)
            || title.startsWith(CraftBookGui.PREFIX_RECIPE)
            || title.equals(CraftBookGui.PREFIX_SPECIAL);
    }

    private boolean isBackButton(String name) {
        return stripColors(name).startsWith("← Voltar");
    }

    /** Remove códigos de cor/formatação (§x). */
    private String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fklmnor]", "").trim();
    }

    private String specialNameToId(String cleanName) {
        switch (cleanName) {
            case "Maçã Dourada Especial": return "tls_plugin:tls_special_apple";
            case "Poção Dourada":         return "tls_plugin:goldpotion_item";
            case "Grappler":              return "tls_plugin:grappler_item";
            case "Kit Médico (Completo)": return "tls_plugin:kit_completo";
            case "Kit Médico (Parcial)":  return "tls_plugin:kit_parcial";
            case "Tracker Compass":       return "tls_plugin:tracker_compass";
            default:                      return null;
        }
    }
}
