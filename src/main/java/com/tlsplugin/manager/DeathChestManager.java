package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * Baú de morte: qualquer morte deixa um baú no local com os itens do jogador.
 *
 * <p><b>Porque é que a marca fica no próprio baú:</b> o baú é um {@code TileState}, por isso
 * pode guardar dados no seu {@link org.bukkit.persistence.PersistentDataContainer} — e esses
 * dados são gravados com o mundo. Assim a proteção (não partir com machado, imune a TNT)
 * continua a funcionar depois de o servidor reiniciar, ao contrário de metadata em memória
 * ou de uma lista de coordenadas num ficheiro à parte, que ficariam dessincronizadas.</p>
 *
 * <p><b>Empilhamento com o revive:</b> em modo equipas o revive já põe uma Sea Lantern no
 * sítio da morte. Para os dois não se sobreporem, o baú fica no bloco da morte e a lantern
 * sobe um bloco (ver DeathListener), ficando: baú em baixo, lantern por cima, hologramas
 * acima de ambos.</p>
 */
public class DeathChestManager implements Listener {

    private final Tlsplugin plugin;
    /** Marca o baú como baú de morte e guarda o nome do dono. */
    private final NamespacedKey chaveDono;
    /** Liga o holograma ao baú a que pertence (para o limpar quando o baú desaparece). */
    private final NamespacedKey chaveHologramaDe;

    public DeathChestManager(Tlsplugin plugin) {
        this.plugin           = plugin;
        this.chaveDono        = new NamespacedKey(plugin, "bau_morte_dono");
        this.chaveHologramaDe = new NamespacedKey(plugin, "bau_morte_holograma");
    }

    // ── Criação ───────────────────────────────────────────────────────────────

    /**
     * Coloca o baú com os itens. Devolve o bloco usado, ou {@code null} se não deu.
     *
     * @param drops itens a guardar — normalmente {@code event.getDrops()}
     */
    public Block place(Player vitima, Location base, List<ItemStack> drops) {
        World world = base.getWorld();
        if (world == null) return null;

        Block bloco = encontrarLocalLivre(base);
        if (bloco == null) return null;

        bloco.setType(Material.CHEST);

        // Força SINGLE: se dois jogadores morrerem em blocos adjacentes, os baús não podem
        // juntar-se num baú duplo — isso misturaria os itens dos dois e daria uma só
        // proteção partilhada em vez de uma por baú.
        org.bukkit.block.data.BlockData dados = bloco.getBlockData();
        if (dados instanceof org.bukkit.block.data.type.Chest dadosBau) {
            dadosBau.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            bloco.setBlockData(dadosBau, false);
        }

        BlockState estado = bloco.getState();
        if (!(estado instanceof Chest bau)) return null;
        bau.getPersistentDataContainer().set(chaveDono, PersistentDataType.STRING, vitima.getName());
        bau.update(true, false);

        // Encher: o baú tem 27 slots e um inventário completo pode trazer mais pilhas do
        // que isso, por isso o que sobrar cai no chão em vez de desaparecer.
        BlockState novo = bloco.getState();
        if (novo instanceof Chest bauAberto) {
            for (ItemStack item : drops) {
                if (item == null || item.getType().isAir()) continue;
                Map<Integer, ItemStack> sobra = bauAberto.getBlockInventory().addItem(item);
                for (ItemStack resto : sobra.values()) {
                    world.dropItemNaturally(bloco.getLocation().add(0.5, 1, 0.5), resto);
                }
            }
        }

        return bloco;
    }

    /** Cria o holograma informativo por cima do baú. */
    public void createHologram(Block bau, Player vitima, double alturaExtra) {
        Location loc = bau.getLocation().clone().add(0.5, 1.2 + alturaExtra, 0.5);
        World world = loc.getWorld();
        if (world == null) return;

        String killer = vitima.getKiller() != null ? vitima.getKiller().getName() : null;
        String texto = plugin.getConfig()
                .getString("bau_morte.holograma", "§6💀 Baú de {jogador} §7| §f{morte}")
                .replace("{jogador}", vitima.getName())
                .replace("{morte}", killer != null ? "morto por " + killer : "morreu")
                .replace("{killer}", killer != null ? killer : "—");

        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(texto);
        stand.getPersistentDataContainer().set(
                chaveHologramaDe, PersistentDataType.STRING, chaveLocal(bau.getLocation()));
    }

    /**
     * Procura o primeiro bloco onde cabe o baú, a partir da posição da morte. Se o jogador
     * morreu dentro de um bloco (ex: a afogar-se ou preso), sobe até 3 blocos em vez de
     * substituir terreno sólido.
     */
    private Block encontrarLocalLivre(Location base) {
        Block bloco = base.getBlock();
        for (int i = 0; i < 4; i++) {
            if (podeSubstituir(bloco.getType())) return bloco;
            bloco = bloco.getRelative(0, 1, 0);
        }
        // Nada livre por cima — usa o bloco da morte à mesma para não perder os itens.
        return base.getBlock();
    }

    private boolean podeSubstituir(Material m) {
        return m.isAir() || m == Material.WATER || m == Material.LAVA
                || m == Material.SHORT_GRASS || m == Material.TALL_GRASS
                || m == Material.SNOW || m == Material.FERN || m == Material.DEAD_BUSH;
    }

    // ── Proteção ──────────────────────────────────────────────────────────────

    private boolean isBauDeMorte(Block bloco) {
        if (bloco == null || bloco.getType() != Material.CHEST) return false;
        BlockState estado = bloco.getState();
        return estado instanceof Chest bau
                && bau.getPersistentDataContainer().has(chaveDono, PersistentDataType.STRING);
    }

    /**
     * Só é possível partir com a mão vazia. Com machado (ou qualquer ferramenta) o baú
     * aguenta — assim ninguém "limpa" o baú de outra equipa à pressa a meio de uma luta.
     */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block bloco = e.getBlock();
        if (!isBauDeMorte(bloco)) return;

        boolean apenasMao = plugin.getConfig().getBoolean("bau_morte.apenas_mao", true);
        ItemStack naMao = e.getPlayer().getInventory().getItemInMainHand();
        boolean maoVazia = naMao == null || naMao.getType().isAir();

        if (apenasMao && !maoVazia) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getConfig().getString(
                    "bau_morte.mensagem_precisa_mao",
                    "§f[§bTLS§f] §cEste baú só pode ser partido com a §lmão vazia§c."));
            return;
        }

        removerHologramas(bloco);
    }

    /** TNT e outras explosões não levam o baú. */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("bau_morte.imune_explosao", true)) return;
        e.blockList().removeIf(this::isBauDeMorte);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("bau_morte.imune_explosao", true)) return;
        e.blockList().removeIf(this::isBauDeMorte);
    }

    // ── Limpeza ───────────────────────────────────────────────────────────────

    /**
     * Remove o(s) holograma(s) ligados a este baú. Procura por entidades à volta em vez de
     * guardar referências em memória, para continuar a funcionar depois de um reinício.
     */
    private void removerHologramas(Block bau) {
        String chave = chaveLocal(bau.getLocation());
        Location centro = bau.getLocation().add(0.5, 1.5, 0.5);
        for (Entity ent : bau.getWorld().getNearbyEntities(centro, 2.5, 4.0, 2.5)) {
            if (!(ent instanceof ArmorStand stand)) continue;
            String tag = stand.getPersistentDataContainer()
                    .get(chaveHologramaDe, PersistentDataType.STRING);
            if (chave.equals(tag)) stand.remove();
        }
    }

    private String chaveLocal(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
