package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;

public class SpectatorInspectListener implements Listener {

    // ==========================================
    // BLOQUEAR TELEPORT DE ESPECTADOR PARA OUTRAS TEAMS
    // ==========================================
    @EventHandler
    public void onSpectatorTeleport(PlayerTeleportEvent e) {
        Player spectator = e.getPlayer();
        Tlsplugin plugin = Tlsplugin.getInstance();

        boolean isSpec = spectator.getGameMode() == GameMode.SPECTATOR;
        
        if (!isSpec) return;

        // Só interessa se a causa for "clicar em player no gm3"
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;

        // Descobre se o destino é mesmo um player
        Player target = e.getTo().getWorld().getNearbyEntities(e.getTo(), 0.1, 0.1, 0.1)
                .stream()
                .filter(ent -> ent instanceof Player)
                .map(ent -> (Player) ent)
                .findFirst()
                .orElse(null);

        if (target == null) return;

        // Regra Especial para Espectadores (não-admins)
        if (!spectator.isOp()) {
            // Se habilitado esconder quadrados na barra de XP (que aqui interpretamos como o espectador espiar)
            if (com.tlsplugin.Tlsplugin.getInstance().getConfig().getBoolean("game.esconder_quadrados_xp", true)) {
                e.setCancelled(true);
                spectator.sendMessage("§cVocê não pode espiar outros jogadores enquanto o jogo está a decorrer!");
                return;
            }

            // Verifica times
            Team teamSpectator = spectator.getScoreboard().getEntryTeam(spectator.getName());
            Team teamTarget = target.getScoreboard().getEntryTeam(target.getName());

            if (teamSpectator == null || teamTarget == null || !teamSpectator.equals(teamTarget)) {
                e.setCancelled(true);
                spectator.sendMessage("§cVocê só pode teleportar para membros da sua própria equipe!");
            }
        }
    }

    // ==========================================
    // SISTEMA DE INSPEÇÃO
    // ==========================================
    @EventHandler
    public void onSpectatorClick(PlayerInteractEntityEvent e) {
        Player spectator = e.getPlayer();
        Tlsplugin plugin = Tlsplugin.getInstance();

        boolean isSpec = spectator.getGameMode() == GameMode.SPECTATOR;

        if (!isSpec) return;
        if (!(e.getRightClicked() instanceof Player target)) return;

        // Apenas OP pode inspecionar
        if (!spectator.isOp()) {
            spectator.sendMessage("§cApenas OP pode inspecionar jogadores!");
            return;
        }

        // Criar GUI
        Inventory inv = Bukkit.createInventory(null, 54, "§bInspecionar: " + target.getName());

        // Inventário principal (0–35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = target.getInventory().getItem(i);
            inv.setItem(i, item != null ? item.clone() : new ItemStack(Material.AIR));
        }

        // Divisor branco
        ItemStack dividerWhite = createItem(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int slot = 36; slot <= 44; slot++) inv.setItem(slot, dividerWhite);

        // Armadura
        inv.setItem(45, safeItem(target.getInventory().getHelmet()));
        inv.setItem(46, safeItem(target.getInventory().getChestplate()));
        inv.setItem(47, safeItem(target.getInventory().getLeggings()));
        inv.setItem(48, safeItem(target.getInventory().getBoots()));

        // Offhand
        inv.setItem(49, safeItem(target.getInventory().getItemInOffHand()));

        // Vida
        ItemStack vida = createItem(Material.REDSTONE, "§cVida: " + (int) target.getHealth() + " ❤");
        inv.setItem(50, vida);

        // Fome
        ItemStack fome = createItem(Material.COOKED_BEEF, "§6Fome: " + target.getFoodLevel());
        inv.setItem(51, fome);

        // Divisor ciano
        ItemStack dividerCyan = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        inv.setItem(52, dividerCyan);
        inv.setItem(53, dividerCyan);

        // Abrir GUI para o espectador
        spectator.openInventory(inv);
    }

    private ItemStack safeItem(ItemStack item) {
        return item != null ? item.clone() : new ItemStack(Material.AIR);
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
