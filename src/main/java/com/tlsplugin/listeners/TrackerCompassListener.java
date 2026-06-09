package com.tlsplugin.listeners;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.ItemUtils;
import dev.lone.itemsadder.api.CustomStack;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;

public class TrackerCompassListener implements Listener {

    private final Tlsplugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> usos = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeTrackers = new HashMap<>();

    // CONFIG
    private final int cooldownSegundos;
    private final int maxUsos;
    private final boolean opInfinito;
    private String msgUsar, msgCooldown, msgLimite, msgAlvoNaoEncontrado, msgActionBar, msgDesativado;
    private List<String> baseLore;

    private static final String COMPASS_ID = "tls_plugin:tls_tracker_compass";

    // Setas para direções (8 direções)
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public TrackerCompassListener(Tlsplugin plugin) {
        this.plugin = plugin;

        this.cooldownSegundos = plugin.getConfig().getInt("compass_tracker.cooldown_segundos", 180);
        this.maxUsos = plugin.getConfig().getInt("compass_tracker.max_usos", 3);
        this.opInfinito = plugin.getConfig().getBoolean("compass_tracker.op_infinito", true);

        this.msgUsar = plugin.getConfig().getString("compass_tracker.mensagem_usar",
                "§eRastreamento ativado! Segura a bússola para ver a direção.");
        this.msgCooldown = plugin.getConfig().getString("compass_tracker.mensagem_cooldown",
                "§cA bússola está em cooldown! Espera {tempo}s.");
        this.msgLimite = plugin.getConfig().getString("compass_tracker.mensagem_limite",
                "§cJá usaste a bússola o número máximo de vezes.");
        
        reloadRecipe();
        startLoreUpdater();
    }

    public void reloadRecipe() {
        this.baseLore = plugin.getConfig().getStringList("compass_tracker.lore");
        this.msgUsar = plugin.getConfig().getString("compass_tracker.mensagem_usar", "§eRastreamento ativado! Segura a bússola para ver a direção.");
        this.msgAlvoNaoEncontrado = plugin.getConfig().getString("compass_tracker.mensagem_alvo_nao_encontrado", "§c✖ Nenhum alvo encontrado");
        this.msgActionBar = plugin.getConfig().getString("compass_tracker.mensagem_action_bar", "§fAlvo: §b{alvo} §7| §fDistância: §b{distancia}m §7| {seta}");
        this.msgDesativado = plugin.getConfig().getString("compass_tracker.mensagem_rastreamento_desativado", "§7Rastreamento desativado");

        NamespacedKey key = new NamespacedKey(plugin, "tls_tracker_compass");
        Bukkit.removeRecipe(key);

        CustomStack cs = CustomStack.getInstance(COMPASS_ID);
        if (cs == null) {
            plugin.getLogger().warning("[TrackerCompass] CustomStack " + COMPASS_ID + " não encontrado!");
            return;
        }

        ItemStack result = cs.getItemStack().clone();
        
        // Aplica lore inicial do config
        if (baseLore != null && !baseLore.isEmpty()) {
            ItemUtils.applyLore(result, baseLore);
        }

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(" G ", "GCG", " G ");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('C', Material.COMPASS);

        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("[TrackerCompass] Receita registrada com lore!");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null) return;

        Player p = e.getPlayer();
        CustomStack custom = CustomStack.byItemStack(e.getItem());
        if (custom == null || !COMPASS_ID.equals(custom.getNamespacedID())) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown
        if (!(opInfinito && p.isOp())) {
            long expira = cooldowns.getOrDefault(id, 0L);
            if (now < expira) {
                long restante = (expira - now) / 1000L;
                p.sendMessage(msgCooldown.replace("{tempo}", String.valueOf(restante)));
                return;
            }
        }

        // Usos
        if (!(opInfinito && p.isOp())) {
            int usados = usos.getOrDefault(id, 0);
            if (maxUsos > 0 && usados >= maxUsos) {
                p.sendMessage(msgLimite);
                return;
            }
            usos.put(id, usados + 1);
        }

        // Ativar rastreamento
        startTracking(p);
        p.sendMessage(msgUsar);

        // Atualiza cooldown
        if (!(opInfinito && p.isOp())) {
            cooldowns.put(id, now + (cooldownSegundos * 1000L));
        }
    }

    @EventHandler
    public void onItemChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack oldItem = p.getInventory().getItem(e.getPreviousSlot());

        // Se saiu da bússola, para o rastreamento na action bar
        if (oldItem != null) {
            CustomStack custom = CustomStack.byItemStack(oldItem);
            if (custom != null && COMPASS_ID.equals(custom.getNamespacedID())) {
                stopTracking(p);
            }
        }
    }

    private void startTracking(Player p) {
        stopTracking(p); // Para qualquer rastreamento anterior

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                // Verifica se ainda está segurando a bússola na mão principal
                ItemStack hand = p.getInventory().getItemInMainHand();
                CustomStack custom = CustomStack.byItemStack(hand);

                if (custom == null || !COMPASS_ID.equals(custom.getNamespacedID())) {
                    stopTracking(p);
                    return;
                }

                // Encontra o alvo mais próximo
                Player alvo = findNearestTarget(p);

                if (alvo == null) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(msgAlvoNaoEncontrado));
                    return;
                }

                // Calcula direção e distância
                Location pLoc = p.getLocation();
                Location aLoc = alvo.getLocation();

                double distance = pLoc.distance(aLoc);
                String arrow = getDirectionArrow(p, alvo);

                // Envia ActionBar com seta e informação do config
                String message = msgActionBar.replace("{alvo}", alvo.getName())
                        .replace("{distancia}", String.valueOf((int) distance))
                        .replace("{seta}", arrow);

                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(message));
            }
        };

        task.runTaskTimer(plugin, 0L, 5L); // Atualiza a cada 5 ticks (4x por segundo)
        activeTrackers.put(p.getUniqueId(), task);
    }

    private void startLoreUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    long now = System.currentTimeMillis();
                    long expira = cooldowns.getOrDefault(id, 0L);

                    String cooldownText;
                    if (now < expira) {
                        long restante = (expira - now) / 1000L;
                        cooldownText = "§c" + restante + "s";
                    } else {
                        cooldownText = "§aPronto!";
                    }

                    String usosText = String.valueOf(usos.getOrDefault(id, 0));
                    String maxUsosText = maxUsos > 0 ? String.valueOf(maxUsos) : "∞";

                    // Percorre TODO o inventário, incluindo mão esquerda e cursor
                    List<ItemStack> itemsToCheck = new ArrayList<>();
                    itemsToCheck.addAll(Arrays.asList(p.getInventory().getContents()));
                    itemsToCheck.add(p.getInventory().getItemInOffHand());
                    itemsToCheck.add(p.getItemOnCursor());

                    for (ItemStack item : itemsToCheck) {
                        if (item == null || item.getType() == Material.AIR) continue;
                        
                        CustomStack custom = CustomStack.byItemStack(item);
                        if (custom != null && COMPASS_ID.equals(custom.getNamespacedID())) {
                            ItemUtils.updateDynamicLore(item, baseLore, cooldownText, usosText, maxUsosText);
                        }
                    }

                    // Inicia a Action Bar se tiver na mão principal
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    CustomStack handCustom = CustomStack.byItemStack(hand);
                    if (handCustom != null && COMPASS_ID.equals(handCustom.getNamespacedID())) {
                        if (!activeTrackers.containsKey(id)) {
                            startTracking(p);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopTracking(Player p) {
        BukkitRunnable task = activeTrackers.remove(p.getUniqueId());
        if (task != null) {
            task.cancel();
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(msgDesativado));
        }
    }

    private Player findNearestTarget(Player p) {
        Player alvo = null;
        double melhor = Double.MAX_VALUE;
        Team teamP = p.getScoreboard().getEntryTeam(p.getName());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;

            // Ignora OP
            if (other.isOp()) continue;


            // Ignora quem não está vivo/jogável
            switch (other.getGameMode()) {
                case SURVIVAL:
                case ADVENTURE:
                    break;
                default:
                    continue; // CREATIVE ou SPECTATOR
            }

            // Ignora mesmo time
            Team teamO = other.getScoreboard().getEntryTeam(other.getName());
            if (teamP != null && teamP.equals(teamO)) continue;

            double d;
            try {
                d = p.getLocation().distance(other.getLocation());
            } catch (IllegalArgumentException ex) {
                continue;
            }

            if (d < melhor) {
                melhor = d;
                alvo = other;
            }
        }

        return alvo;
    }

    private String getDirectionArrow(Player player, Player target) {
        Location pLoc = player.getLocation();
        Location tLoc = target.getLocation();

        // Vetor da direção do player
        Vector playerDir = pLoc.getDirection().setY(0).normalize();

        // Vetor do player para o target
        Vector toTarget = tLoc.toVector().subtract(pLoc.toVector()).setY(0).normalize();

        // Calcula o ângulo entre os vetores
        double dot = playerDir.dot(toTarget);
        double angle = Math.toDegrees(Math.acos(dot));

        // Determina se é esquerda ou direita usando cross product
        Vector cross = playerDir.getCrossProduct(toTarget);
        if (cross.getY() < 0) {
            angle = 360 - angle;
        }

        // Converte para índice de seta (8 direções)
        int index = (int) Math.round(angle / 45.0) % 8;

        return ARROWS[index];
    }

    // Limpar rastreamentos ao desabilitar plugin
    public void cleanup() {
        for (BukkitRunnable task : activeTrackers.values()) {
            task.cancel();
        }
        activeTrackers.clear();
    }
}