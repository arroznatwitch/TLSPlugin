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

import java.util.*;

public class TrackerCompassListener implements Listener {

    private final Tlsplugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> usos = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeTrackers = new HashMap<>();
    private final Map<UUID, String> lastLoreState = new HashMap<>();

    private final int cooldownSegundos;
    private final int maxUsos;
    private final boolean opInfinito;
    private String msgUsar, msgCooldown, msgLimite, msgAlvoNaoEncontrado, msgActionBar, msgDesativado;
    private List<String> baseLore;

    private static final String COMPASS_ID = "tls_plugin:tls_tracker_compass";
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

        if (!(opInfinito && p.isOp())) {
            long expira = cooldowns.getOrDefault(id, 0L);
            if (now < expira) {
                long restante = (expira - now) / 1000L;
                p.sendMessage(msgCooldown.replace("{tempo}", String.valueOf(restante)));
                return;
            }
        }

        if (!(opInfinito && p.isOp())) {
            int usados = usos.getOrDefault(id, 0);
            if (maxUsos > 0 && usados >= maxUsos) {
                p.sendMessage(msgLimite);
                return;
            }
            usos.put(id, usados + 1);
        }

        startTracking(p);
        Player alvoInicial = findNearestTarget(p);
        String nomeAlvo = alvoInicial != null ? alvoInicial.getName() : "?";
        p.sendMessage(msgUsar.replace("{alvo}", nomeAlvo));

        if (!(opInfinito && p.isOp())) {
            cooldowns.put(id, now + (cooldownSegundos * 1000L));
        }
    }

    @EventHandler
    public void onItemChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        // Para o rastreamento só se o NOVO slot não for um tracker. Como podes ter
        // vários trackers, trocar de um tracker para outro mantém o track ativo;
        // só para mesmo quando deixas de segurar um tracker.
        ItemStack newItem = p.getInventory().getItem(e.getNewSlot());
        CustomStack custom = newItem != null ? CustomStack.byItemStack(newItem) : null;
        boolean novoEhTracker = custom != null && COMPASS_ID.equals(custom.getNamespacedID());
        if (!novoEhTracker && activeTrackers.containsKey(p.getUniqueId())) {
            stopTracking(p);
        }
    }

    private void startTracking(Player p) {
        stopTracking(p);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack hand = p.getInventory().getItemInMainHand();
                CustomStack custom = CustomStack.byItemStack(hand);
                if (custom == null || !COMPASS_ID.equals(custom.getNamespacedID())) {
                    stopTracking(p);
                    return;
                }

                Player alvo = findNearestTarget(p);
                if (alvo == null) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(msgAlvoNaoEncontrado));
                    return;
                }

                double distance = p.getLocation().distance(alvo.getLocation());
                String arrow = getDirectionArrow(p, alvo);
                String message = msgActionBar
                        .replace("{alvo}", alvo.getName())
                        .replace("{distancia}", String.valueOf((int) distance))
                        .replace("{seta}", arrow);

                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            }
        };

        task.runTaskTimer(plugin, 0L, 5L);
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

                    String cooldownText = (now < expira)
                            ? "§c" + ((expira - now) / 1000L) + "s"
                            : "§aPronto!";
                    String usosText = String.valueOf(usos.getOrDefault(id, 0));
                    String maxUsosText = maxUsos > 0 ? String.valueOf(maxUsos) : "∞";

                    // Só atualiza a lore se o estado mudou
                    String stateKey = cooldownText + "|" + usosText;
                    if (!stateKey.equals(lastLoreState.get(id))) {
                        lastLoreState.put(id, stateKey);

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
                    }

                    // Segurança: se o jogador já não segura a bússola, para o rastreamento.
                    // O rastreamento SÓ arranca com o botão direito (ver onUse) — nunca
                    // automaticamente só por se estar a segurar a bússola. Assim, ao trocar
                    // de slot / largar a bússola, o track para e NÃO volta a arrancar sozinho.
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    CustomStack handCustom = CustomStack.byItemStack(hand);
                    boolean segurando = handCustom != null && COMPASS_ID.equals(handCustom.getNamespacedID());
                    if (!segurando && activeTrackers.containsKey(id)) {
                        stopTracking(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopTracking(Player p) {
        BukkitRunnable task = activeTrackers.remove(p.getUniqueId());
        if (task != null) {
            task.cancel();
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msgDesativado));
        }
    }

    private Player findNearestTarget(Player p) {
        Player alvo = null;
        double melhor = Double.MAX_VALUE;
        Team teamP = p.getScoreboard().getEntryTeam(p.getName());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            if (other.isOp()) continue;
            switch (other.getGameMode()) {
                case SURVIVAL: case ADVENTURE: break;
                default: continue;
            }
            Team teamO = other.getScoreboard().getEntryTeam(other.getName());
            if (teamP != null && teamP.equals(teamO)) continue;
            double d;
            try { d = p.getLocation().distance(other.getLocation()); }
            catch (IllegalArgumentException ex) { continue; }
            if (d < melhor) { melhor = d; alvo = other; }
        }
        return alvo;
    }

    private String getDirectionArrow(Player player, Player target) {
        Location pLoc = player.getLocation();
        Location tLoc = target.getLocation();

        double dx = tLoc.getX() - pLoc.getX();
        double dz = tLoc.getZ() - pLoc.getZ();
        double angleToTarget = Math.toDegrees(Math.atan2(dx, -dz));
        if (angleToTarget < 0) angleToTarget += 360;

        double playerFacing = (pLoc.getYaw() + 180) % 360;
        double relative = (angleToTarget - playerFacing + 360) % 360;
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }

    public void cleanup() {
        for (BukkitRunnable task : activeTrackers.values()) task.cancel();
        activeTrackers.clear();
        lastLoreState.clear();
    }
}