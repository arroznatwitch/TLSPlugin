package com.tlsplugin.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

// CLASSE DE UTILIDADE PARA DEFINIR STRINGS CMD (1.21+)
public class ItemUtils {

    public static ItemStack setCustomModelData(ItemStack item, String cmdValue) {
        if (item == null || item.getType().isAir()) {
            return item;
        }

        // Usa ItemMeta.editMeta para modificar o componente, o método correto na 1.21+
        item.editMeta(meta -> {
            // Verifica se a API suporta o novo componente
            if (meta instanceof CustomModelDataComponent cmdMeta) {
                // Define a lista de strings CMD
                cmdMeta.setStrings(List.of(cmdValue));
            }
        });

        return item;
    }

    public static ItemStack applyLore(ItemStack item, List<String> lore) {
        if (item == null || item.getType().isAir() || lore == null) return item;
        item.editMeta(meta -> {
            meta.setLore(lore.stream().map(s -> s.replace("&", "§")).collect(Collectors.toList()));
        });
        return item;
    }

    public static ItemStack updateDynamicLore(ItemStack item, List<String> baseLore, String cooldownText, String usosText, String maxUsosText) {
        if (item == null || item.getType().isAir() || baseLore == null) return item;
        
        List<String> newLore = new ArrayList<>();
        for (String line : baseLore) {
            String updated = line.replace("{cooldown}", cooldownText != null ? cooldownText : "")
                                 .replace("{usos}", usosText != null ? usosText : "")
                                 .replace("{max_usos}", maxUsosText != null ? maxUsosText : "")
                                 .replace("&", "§");
            newLore.add(updated);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> currentLore = meta.getLore();
            // Comparação profunda de conteúdo para evitar setItemMeta desnecessário
            if (currentLore != null && currentLore.size() == newLore.size()) {
                boolean identical = true;
                for (int i = 0; i < currentLore.size(); i++) {
                    if (!currentLore.get(i).equals(newLore.get(i))) {
                        identical = false;
                        break;
                    }
                }
                if (identical) return item; 
            }
            
            // Tenta usar editMeta para ver se o Paper minimiza o bobbing (embora no fundo seja setItemMeta)
            item.editMeta(m -> m.setLore(newLore));
        }
        return item;
    }
}