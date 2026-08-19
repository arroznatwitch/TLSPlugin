package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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

import java.util.ArrayList;
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
    /** Identificador partilhado pelas duas metades do baú e pelo holograma. */
    private final NamespacedKey chaveAncora;

    public DeathChestManager(Tlsplugin plugin) {
        this.plugin           = plugin;
        this.chaveDono        = new NamespacedKey(plugin, "bau_morte_dono");
        this.chaveHologramaDe = new NamespacedKey(plugin, "bau_morte_holograma");
        this.chaveAncora      = new NamespacedKey(plugin, "bau_morte_ancora");
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

        // Armadura e hotbar primeiro, para quem chegar ao baú ver logo o equipamento.
        List<ItemStack> ordenados = ordenarItens(vitima, drops);

        // Um inventário cheio (36 + 4 armaduras + offhand) não cabe nos 27 slots de um baú
        // simples, por isso passamos a duplo quando é preciso e há espaço ao lado.
        Block segundo = null;
        if (ordenados.size() > 27) segundo = encontrarAdjacenteLivre(bloco);

        String ancora = chaveLocal(bloco.getLocation());

        if (segundo != null) {
            // Regra do vanilla: com type=LEFT a outra metade está em facing.horário; com
            // type=RIGHT está em facing.anti-horário. Com facing NORTE, LEFT liga a ESTE.
            criarMetade(bloco,  org.bukkit.block.data.type.Chest.Type.LEFT,  BlockFace.NORTH);
            criarMetade(segundo, org.bukkit.block.data.type.Chest.Type.RIGHT, BlockFace.NORTH);
            marcar(bloco,  vitima.getName(), ancora);
            marcar(segundo, vitima.getName(), ancora);
        } else {
            // SINGLE forçado: dois jogadores a morrer em blocos adjacentes não podem formar
            // um baú duplo acidental, que misturaria os itens e daria uma proteção só.
            criarMetade(bloco, org.bukkit.block.data.type.Chest.Type.SINGLE, BlockFace.NORTH);
            marcar(bloco, vitima.getName(), ancora);
        }

        // Com baú duplo é preciso getInventory() (54 slots); no simples usamos
        // getBlockInventory() para nunca escrever na metade de outro baú por engano.
        BlockState estado = bloco.getState();
        if (estado instanceof Chest bauAberto) {
            var inv = (segundo != null) ? bauAberto.getInventory() : bauAberto.getBlockInventory();
            for (ItemStack item : ordenados) {
                if (item == null || item.getType().isAir()) continue;
                Map<Integer, ItemStack> sobra = inv.addItem(item);
                for (ItemStack resto : sobra.values()) {
                    world.dropItemNaturally(bloco.getLocation().add(0.5, 1, 0.5), resto);
                }
            }
        }

        return bloco;
    }

    /** Aplica material + tipo/orientação a uma metade do baú. */
    private void criarMetade(Block bloco, org.bukkit.block.data.type.Chest.Type tipo, BlockFace facing) {
        bloco.setType(Material.CHEST);
        org.bukkit.block.data.BlockData dados = bloco.getBlockData();
        if (dados instanceof org.bukkit.block.data.type.Chest dadosBau) {
            dadosBau.setType(tipo);
            dadosBau.setFacing(facing);
            bloco.setBlockData(dadosBau, false);
        }
    }

    /** Marca o bloco como baú de morte (dono + âncora partilhada pelas duas metades). */
    private void marcar(Block bloco, String dono, String ancora) {
        BlockState estado = bloco.getState();
        if (!(estado instanceof Chest bau)) return;
        bau.getPersistentDataContainer().set(chaveDono,   PersistentDataType.STRING, dono);
        bau.getPersistentDataContainer().set(chaveAncora, PersistentDataType.STRING, ancora);
        bau.update(true, false);
    }

    /**
     * Ordena os itens: armadura, mão secundária, item na mão, resto da hotbar e por fim o
     * inventário. Casamos cada posição com o drop correspondente em vez de usar o
     * inventário diretamente, porque o que realmente cai é a lista de drops (outros
     * plugins podem tirar ou acrescentar coisas).
     */
    private List<ItemStack> ordenarItens(Player vitima, List<ItemStack> drops) {
        List<ItemStack> prioridade = new ArrayList<>();
        var inv = vitima.getInventory();

        ItemStack[] armadura = inv.getArmorContents();
        for (int i = armadura.length - 1; i >= 0; i--) prioridade.add(armadura[i]); // capacete → botas
        prioridade.add(inv.getItemInOffHand());
        prioridade.add(inv.getItemInMainHand());
        for (int i = 0; i < 9; i++)  prioridade.add(inv.getItem(i));   // hotbar
        for (int i = 9; i < 36; i++) prioridade.add(inv.getItem(i));   // resto

        List<ItemStack> restantes = new ArrayList<>(drops);
        List<ItemStack> ordenados = new ArrayList<>();
        for (ItemStack alvo : prioridade) {
            if (alvo == null || alvo.getType().isAir()) continue;
            for (java.util.Iterator<ItemStack> it = restantes.iterator(); it.hasNext(); ) {
                ItemStack candidato = it.next();
                if (candidato != null && candidato.isSimilar(alvo)
                        && candidato.getAmount() == alvo.getAmount()) {
                    ordenados.add(candidato);
                    it.remove();
                    break;
                }
            }
        }
        ordenados.addAll(restantes); // o que não bateu certo vai no fim, sem se perder
        return ordenados;
    }

    /**
     * Bloco para a segunda metade do baú duplo. Só pode ser o de ESTE: as metades são
     * criadas como LEFT viradas a NORTE, e o vanilla liga um LEFT à metade que está no
     * sentido horário do facing — para NORTE, isso é ESTE. Se estiver ocupado, fica
     * simples e o excesso cai no chão.
     */
    private Block encontrarAdjacenteLivre(Block bloco) {
        Block lado = bloco.getRelative(BlockFace.EAST);
        return podeSubstituir(lado.getType()) ? lado : null;
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

        String ancora = lerAncora(bau);
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(texto);
        stand.getPersistentDataContainer().set(chaveHologramaDe, PersistentDataType.STRING, ancora);
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

    // ── Auto-remoção quando fica vazio ────────────────────────────────────────

    /** Ao fechar o baú já vazio, ele desaparece com o holograma. */
    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        verificarVazio(e.getInventory());
    }

    /**
     * Também verifica ao clicar, para o baú sumir assim que sai o último item em vez de
     * só quando o jogador fecha. Um tick depois, para o inventário já estar atualizado.
     */
    @EventHandler
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        org.bukkit.inventory.Inventory inv = e.getInventory();
        Bukkit.getScheduler().runTask(plugin, () -> verificarVazio(inv));
    }

    private void verificarVazio(org.bukkit.inventory.Inventory inv) {
        if (inv == null) return;

        java.util.List<Block> metades = new ArrayList<>();
        org.bukkit.inventory.InventoryHolder dono = inv.getHolder();
        if (dono instanceof org.bukkit.block.DoubleChest duplo) {
            if (duplo.getLeftSide()  instanceof Chest esq) metades.add(esq.getBlock());
            if (duplo.getRightSide() instanceof Chest dir) metades.add(dir.getBlock());
        } else if (dono instanceof Chest simples) {
            metades.add(simples.getBlock());
        } else {
            return;
        }

        if (metades.isEmpty() || !isBauDeMorte(metades.get(0))) return;

        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) return; // ainda tem coisas
        }

        removerHologramas(metades.get(0)); // antes de apagar os blocos, senão perde a âncora
        for (Block metade : metades) {
            if (isBauDeMorte(metade)) metade.setType(Material.AIR);
        }
    }

    // ── Limpeza ───────────────────────────────────────────────────────────────

    /**
     * Remove o(s) holograma(s) ligados a este baú. Procura por entidades à volta em vez de
     * guardar referências em memória, para continuar a funcionar depois de um reinício.
     */
    private String lerAncora(Block bloco) {
        BlockState estado = bloco.getState();
        if (estado instanceof Chest bau) {
            String a = bau.getPersistentDataContainer().get(chaveAncora, PersistentDataType.STRING);
            if (a != null) return a;
        }
        return chaveLocal(bloco.getLocation());
    }

    private void removerHologramas(Block bau) {
        String chave = lerAncora(bau);
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
