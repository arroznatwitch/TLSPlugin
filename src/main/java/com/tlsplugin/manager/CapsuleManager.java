package com.tlsplugin.manager;

import com.tlsplugin.Tlsplugin;
import com.tlsplugin.utils.TeamWoolItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CapsuleManager {

    private final Tlsplugin    plugin;
    private final SpawnManager spawnManager;

    // Ordem: primárias nos cantos, secundárias nos meios.
    private static final String[] TEAM_ORDER = {
            "red", "orange", "yellow", "pink", "blue", "grey", "green", "purple"
    };

    private static final Map<String, Material> CONCRETE = new LinkedHashMap<>();
    private static final Map<String, Material> GLASS    = new LinkedHashMap<>();
    static {
        CONCRETE.put("red",    Material.RED_CONCRETE);
        CONCRETE.put("blue",   Material.BLUE_CONCRETE);
        CONCRETE.put("green",  Material.GREEN_CONCRETE);
        CONCRETE.put("yellow", Material.YELLOW_CONCRETE);
        CONCRETE.put("pink",   Material.PINK_CONCRETE);
        CONCRETE.put("grey",   Material.GRAY_CONCRETE);
        CONCRETE.put("purple", Material.PURPLE_CONCRETE);
        CONCRETE.put("orange", Material.ORANGE_CONCRETE);

        GLASS.put("red",    Material.RED_STAINED_GLASS);
        GLASS.put("blue",   Material.BLUE_STAINED_GLASS);
        GLASS.put("green",  Material.GREEN_STAINED_GLASS);
        GLASS.put("yellow", Material.YELLOW_STAINED_GLASS);
        GLASS.put("pink",   Material.PINK_STAINED_GLASS);
        GLASS.put("grey",   Material.GRAY_STAINED_GLASS);
        GLASS.put("purple", Material.PURPLE_STAINED_GLASS);
        GLASS.put("orange", Material.ORANGE_STAINED_GLASS);
    }

    // Guarda as localizações dos centros das cápsulas para poder parti-las depois.
    // world → lista de centros (X, Y, Z)
    private final List<int[]> capsuleCenters = new ArrayList<>();
    private World             lastWorld      = null;

    public CapsuleManager(Tlsplugin plugin, SpawnManager spawnManager) {
        this.plugin       = plugin;
        this.spawnManager = spawnManager;
    }

    public static final class Result {
        public final int      capsulas;
        public final String   modo;
        public final BlockFace frente;
        public Result(int capsulas, String modo, BlockFace frente) {
            this.capsulas = capsulas;
            this.modo     = modo;
            this.frente   = frente;
        }
    }

    public Result generate(World world) {
        return generate(world, plugin.getConfig().getString("modo_jogo", "final"));
    }

    public Result generate(World world, String modo) {
        final BlockFace front     = parseFacing(plugin.getConfig().getString("tlscapsulas.frente", "SOUTH"));
        final int       interiorH = Math.max(1, plugin.getConfig().getInt("tlscapsulas.altura_interior", 2));
        final boolean   autoY     = plugin.getConfig().getBoolean("tlscapsulas.auto_y", true);
        final int       defaultY  = plugin.getConfig().getInt("tlscapsulas.default_y", 64);
        final float     yaw       = yawFromFacing(front);

        ConfigurationSection modoSec = plugin.getConfig().getConfigurationSection("tlscapsulas.modos." + modo);
        if (modoSec == null) {
            plugin.getLogger().severe("[TLS] tlscapsulas.modos." + modo + " não encontrado no config!");
            return new Result(0, modo, front);
        }
        List<Map<?, ?>> posicoes = modoSec.getMapList("posicoes");
        if (posicoes == null || posicoes.isEmpty()) {
            plugin.getLogger().severe("[TLS] tlscapsulas.modos." + modo + ".posicoes está vazio!");
            return new Result(0, modo, front);
        }

        plugin.getTeamManager().ensureTeamsExist();

        capsuleCenters.clear();
        lastWorld = world;

        int built = 0;
        int count = Math.min(TEAM_ORDER.length, posicoes.size());

        for (int i = 0; i < count; i++) {
            String  team = TEAM_ORDER[i];
            Map<?, ?> pos = posicoes.get(i);

            int configX = toInt(pos.get("x"), 0);
            int configZ = toInt(pos.get("z"), 0);

            // Encontra posição sólida próxima (procura em espiral se for água/ar)
            int[] solid = findSolidNearby(world, configX, configZ, defaultY, autoY, team);
            int x = solid[0];
            int y = solid[1];
            int z = solid[2];

            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);

            buildCapsule(world, team, x, y, z, interiorH);
            capsuleCenters.add(new int[]{x, y, z});
            built++;

            // Spawn no centro do interior 3×3 (bloco y+1 = chão do interior)
            Location spawn = new Location(world, x + 0.5, y + 1, z + 0.5, yaw, 0f);
            spawnManager.setSpawn(team, spawn);
        }

        saveState();   // persiste os centros pra sobreviver a restarts antes do start
        return new Result(built, modo, front);
    }

    /**
     * Parte todas as cápsulas geradas — chama ao iniciar o jogo (startCycle).
     * Remove todos os blocos de concrete e vidro da cápsula, deixando só ar.
     */
    public void breakAll() {
        // Se a memória está vazia (servidor reiniciou depois do generate()), recupera do disco.
        if (lastWorld == null || capsuleCenters.isEmpty()) {
            loadState();
        }
        if (lastWorld == null || capsuleCenters.isEmpty()) return;

        int interiorH = Math.max(1, plugin.getConfig().getInt("tlscapsulas.altura_interior", 2));
        for (int[] center : capsuleCenters) {
            breakCapsule(lastWorld, center[0], center[1], center[2], interiorH);
        }
        plugin.getLogger().info("[TLS] " + capsuleCenters.size() + " cápsulas partidas.");
    }

    /** Limpa o estado persistido — chamar no reset/stop do jogo para não reaproveitar centros antigos. */
    public void clearState() {
        capsuleCenters.clear();
        lastWorld = null;
        File file = new File(plugin.getDataFolder(), "capsulas.yml");
        if (file.exists()) file.delete();
    }

    private void saveState() {
        File file = new File(plugin.getDataFolder(), "capsulas.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        if (lastWorld != null) yaml.set("world", lastWorld.getName());
        List<String> list = new ArrayList<>();
        for (int[] c : capsuleCenters) list.add(c[0] + ";" + c[1] + ";" + c[2]);
        yaml.set("centros", list);
        try { yaml.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadState() {
        File file = new File(plugin.getDataFolder(), "capsulas.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world");
        if (worldName != null) lastWorld = Bukkit.getWorld(worldName);
        capsuleCenters.clear();
        for (String s : yaml.getStringList("centros")) {
            String[] parts = s.split(";");
            if (parts.length == 3) {
                capsuleCenters.add(new int[]{
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                });
            }
        }
    }

    // ── Construção ────────────────────────────────────────────────────────────

    /**
     * Cápsula 5×5 exterior com interior 3×3 livre.
     * Chão: concrete da cor (5×5).
     * Paredes: vidro da cor apenas no anel exterior (5×5 menos o 3×3 interior).
     * Teto: vidro da cor (5×5).
     * Interior: ar (3×3 × interiorH).
     */
    private void buildCapsule(World world, String team, int cx, int baseY, int cz, int interiorH) {
        Material concrete = CONCRETE.getOrDefault(team, Material.WHITE_CONCRETE);
        Material glass    = GLASS.getOrDefault(team,    Material.WHITE_STAINED_GLASS);

        int minX = cx - 2, maxX = cx + 2;
        int minZ = cz - 2, maxZ = cz + 2;
        int roofY = baseY + interiorH + 1;

        // Chão 5×5
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                set(world, x, baseY, z, concrete);

        // Paredes e interior
        for (int y = baseY + 1; y <= baseY + interiorH; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    // Anel exterior = parede de vidro; interior 3×3 = ar
                    boolean exterior = (x == minX || x == maxX || z == minZ || z == maxZ);
                    set(world, x, y, z, exterior ? glass : Material.AIR);
                }
            }
        }

        // Teto 5×5
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                set(world, x, roofY, z, glass);
    }

    private void breakCapsule(World world, int cx, int baseY, int cz, int interiorH) {
        int minX = cx - 2, maxX = cx + 2;
        int minZ = cz - 2, maxZ = cz + 2;
        int roofY = baseY + interiorH + 1;

        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                // Chão
                clearIfCapsule(world, x, baseY, z);
                // Teto
                clearIfCapsule(world, x, roofY, z);
            }

        for (int y = baseY + 1; y <= baseY + interiorH; y++)
            for (int x = minX; x <= maxX; x++)
                for (int z = minZ; z <= maxZ; z++)
                    clearIfCapsule(world, x, y, z);
    }

    /** Remove o bloco se for concrete ou vidro colorido (não toca em outros blocos). */
    private void clearIfCapsule(World world, int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        Material m = b.getType();
        if (CONCRETE.containsValue(m) || GLASS.containsValue(m)) {
            b.setType(Material.AIR, false);
        }
    }

    private void set(World world, int x, int y, int z, Material mat) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != mat) b.setType(mat, false);
    }

    // ── Procura de terreno sólido ─────────────────────────────────────────────

    /**
     * Encontra posição sólida próxima de (configX, configZ), priorizando sempre
     * a direção do (0,0). Raio máximo de 75 blocos.
     * Se tiver de mover, avisa os OPs com a cor da cápsula e as coordenadas.
     * Nunca coloca a cápsula em cima de água.
     */
    private int[] findSolidNearby(World world, int configX, int configZ, int defaultY, boolean autoY, String team) {
        int maxRadius = 75;

        // Direção preferida: em direção ao (0,0)
        int prefX = configX == 0 ? 0 : (configX > 0 ? -1 : 1);
        int prefZ = configZ == 0 ? 0 : (configZ > 0 ? -1 : 1);

        // Para cada raio, tenta primeiro na diagonal do (0,0), depois o anel completo
        for (int radius = 0; radius <= maxRadius; radius++) {
            // Lista de candidatos ordenados: diagonal preferida primeiro, depois o resto do anel
            List<int[]> candidates = new ArrayList<>();

            // Diagonal preferida no topo da lista
            if (radius > 0) {
                candidates.add(new int[]{configX + prefX * radius, configZ + prefZ * radius});
            } else {
                candidates.add(new int[]{configX, configZ});
            }

            // Resto do anel
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if (dx == prefX * radius && dz == prefZ * radius) continue; // já está
                    candidates.add(new int[]{configX + dx, configZ + dz});
                }
            }

            for (int[] cand : candidates) {
                int x = cand[0], z = cand[1];
                if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);
                int y = getSolidY(world, x, z, defaultY, autoY);
                if (y >= 0) {
                    if (radius > 0) {
                        String color = "§f" + team;
                        String msg = "§f[§bTLS§f] §e⚠ Cápsula §b" + team + " §emovida §7("
                                + configX + "," + configZ + ")§e → §7(" + x + "," + z
                                + ") §7(+" + radius + " blocos do centro)";
                        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                            if (p.isOp()) p.sendMessage(msg);
                        }
                        plugin.getLogger().info("[TLS] Cápsula " + team + " reposicionada: ("
                                + configX + "," + configZ + ") → (" + x + "," + z + ") raio=" + radius);
                    }
                    return new int[]{x, y, z};
                }
            }
        }

        // Fallback total — avisa OPs
        String msg = "§f[§bTLS§f] §c⚠ Cápsula §b" + team + " §cnão encontrou terreno sólido em "
                + maxRadius + " blocos! Usou Y=" + defaultY + " nas coords originais.";
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.isOp()) p.sendMessage(msg);
        }
        plugin.getLogger().warning("[TLS] Sem terreno para cápsula " + team
                + " em (" + configX + "," + configZ + ")");
        return new int[]{configX, defaultY, configZ};
    }

    /**
     * Devolve o Y do bloco sólido mais alto em (x, z), ignorando água, lava, neve e ar.
     * Devolve -1 se não encontrar (coluna de água, void, etc.).
     */
    private int getSolidY(World world, int x, int z, int defaultY, boolean autoY) {
        if (!autoY) return defaultY;
        int maxY = world.getMaxHeight() - 1;
        for (int y = maxY; y >= world.getMinHeight(); y--) {
            Material mat = world.getBlockAt(x, y, z).getType();
            if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) continue;
            if (mat == Material.WATER || mat == Material.LAVA) return -1; // é água/lava — tenta outro
            if (mat == Material.SNOW) continue;
            if (mat.isSolid()) return y + 1; // y+1 = bloco de chão da cápsula
        }
        return -1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float yawFromFacing(BlockFace f) {
        return switch (f) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default    -> 0f;
        };
    }

    private static BlockFace parseFacing(String s) {
        if (s == null) return BlockFace.SOUTH;
        return switch (s.trim().toUpperCase()) {
            case "NORTH" -> BlockFace.NORTH;
            case "EAST"  -> BlockFace.EAST;
            case "WEST"  -> BlockFace.WEST;
            default      -> BlockFace.SOUTH;
        };
    }

    private static int toInt(Object o, int fallback) {
        if (o == null) return fallback;
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }
}