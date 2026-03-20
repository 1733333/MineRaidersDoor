package MineRaiders;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MRD extends JavaPlugin {
    // 数据存储
    public final Map<String, DoorData> doors = new HashMap<>();
    final Map<Location, String> buttonToDoor = new HashMap<>();
    final Map<Player, String> bindingPlayers = new HashMap<>();
    final Map<Player, Selection> playerSelections = new HashMap<>();
    final Map<Player, Integer> menuPage = new HashMap<>();

    private File doorsFile;
    private FileConfiguration doorsConfig;
    private File buttonsFile;
    private FileConfiguration buttonsConfig;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        loadDoors();
        loadButtons();

        Objects.requireNonNull(getCommand("door")).setExecutor(new DoorCommandExecutor(this));
        getServer().getPluginManager().registerEvents(new DoorListener(this), this);

        getLogger().info("门插件已启用");
    }

    @Override
    public void onDisable() {
        saveDoors();
        saveButtons();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("门插件已禁用");
    }

    // ========== 数据加载/保存 ==========
    private void loadDoors() {
        doorsFile = new File(getDataFolder(), "doors.yml");
        if (!doorsFile.exists()) saveResource("doors.yml", false);
        doorsConfig = YamlConfiguration.loadConfiguration(doorsFile);
        for (String id : doorsConfig.getKeys(false)) {
            String worldName = doorsConfig.getString(id + ".world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("世界 " + worldName + " 不存在，跳过门 " + id);
                continue;
            }
            int minX = doorsConfig.getInt(id + ".minX");
            int maxX = doorsConfig.getInt(id + ".maxX");
            int minY = doorsConfig.getInt(id + ".minY");
            int maxY = doorsConfig.getInt(id + ".maxY");
            int minZ = doorsConfig.getInt(id + ".minZ");
            int maxZ = doorsConfig.getInt(id + ".maxZ");
            boolean open = doorsConfig.getBoolean(id + ".open");
            String blockTypeName = doorsConfig.getString(id + ".blockType", "POLISHED_TUFF_WALL");
            Material blockType = Material.getMaterial(blockTypeName);
            if (blockType == null || !blockType.isBlock()) blockType = Material.POLISHED_TUFF_WALL;

            DoorData data = new DoorData(world, minX, maxX, minY, maxY, minZ, maxZ, blockType);
            data.setOpen(open);
            doors.put(id, data);
        }
        getLogger().info("已加载 " + doors.size() + " 个门");
    }

    void saveDoors() {
        doorsConfig = new YamlConfiguration();
        for (Map.Entry<String, DoorData> entry : doors.entrySet()) {
            String id = entry.getKey();
            DoorData data = entry.getValue();
            doorsConfig.set(id + ".world", data.getWorld().getName());
            doorsConfig.set(id + ".minX", data.getMinX());
            doorsConfig.set(id + ".maxX", data.getMaxX());
            doorsConfig.set(id + ".minY", data.getMinY());
            doorsConfig.set(id + ".maxY", data.getMaxY());
            doorsConfig.set(id + ".minZ", data.getMinZ());
            doorsConfig.set(id + ".maxZ", data.getMaxZ());
            doorsConfig.set(id + ".open", data.isOpen());
            doorsConfig.set(id + ".blockType", data.getBlockType().name());
        }
        try {
            doorsConfig.save(doorsFile);
        } catch (IOException e) {
            getLogger().warning("保存门数据失败: " + e.getMessage());
        }
    }

    private void loadButtons() {
        buttonsFile = new File(getDataFolder(), "buttons.yml");
        if (!buttonsFile.exists()) saveResource("buttons.yml", false);
        buttonsConfig = YamlConfiguration.loadConfiguration(buttonsFile);
        for (String key : buttonsConfig.getKeys(false)) {
            String[] parts = key.split(",");
            if (parts.length != 4) continue;
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                Location loc = new Location(world, x, y, z);
                String doorId = buttonsConfig.getString(key);
                if (doorId != null && doors.containsKey(doorId)) {
                    buttonToDoor.put(loc, doorId);
                }
            } catch (NumberFormatException ignored) {}
        }
        getLogger().info("已加载 " + buttonToDoor.size() + " 个按钮绑定");
    }

    void saveButtons() {
        buttonsConfig = new YamlConfiguration();
        for (Map.Entry<Location, String> entry : buttonToDoor.entrySet()) {
            Location loc = entry.getKey();
            String key = loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            buttonsConfig.set(key, entry.getValue());
        }
        try {
            buttonsConfig.save(buttonsFile);
        } catch (IOException e) {
            getLogger().warning("保存按钮绑定失败: " + e.getMessage());
        }
    }

    // ========== 核心门操作 ==========
    public boolean createDoor(String id, World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Material blockType) {
        if (doors.containsKey(id)) return false;

        // 填充方块
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    b.setType(blockType);
                    // 如果是墙，设置连接
                    if (blockType.name().contains("WALL")) {
                        setWallTall(b);
                    }
                    updateNeighbors(world, x, y, z);
                }
            }
        }

        DoorData data = new DoorData(world, minX, maxX, minY, maxY, minZ, maxZ, blockType);
        doors.put(id, data);
        saveDoors();
        return true;
    }

    public boolean removeDoor(String id) {
        DoorData data = doors.remove(id);
        if (data == null) return false;

        if (data.getTask() != null) data.getTask().cancel();

        World world = data.getWorld();
        int minX = data.getMinX(), maxX = data.getMaxX();
        int minY = data.getMinY(), maxY = data.getMaxY();
        int minZ = data.getMinZ(), maxZ = data.getMaxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                    updateNeighbors(world, x, y, z);
                }
            }
        }

        buttonToDoor.entrySet().removeIf(entry -> entry.getValue().equals(id));
        saveButtons();
        saveDoors();
        return true;
    }
    // 原有无参 toggleDoor，使用默认延迟
    public void toggleDoor(String id) {
        toggleDoor(id, 4); // 默认每层延迟 2 tick
    }

    // 新增带延迟参数的方法
    public void toggleDoor(String id, int delayPerLayer) {
        DoorData data = doors.get(id);
        if (data == null || data.isAnimating()) return;

        data.setAnimating(true);
        World world = data.getWorld();
        int minX = data.getMinX(), maxX = data.getMaxX();
        int minY = data.getMinY(), maxY = data.getMaxY();
        int minZ = data.getMinZ(), maxZ = data.getMaxZ();
        boolean isOpen = data.isOpen();
        boolean newOpen = !isOpen;
        Material blockType = data.getBlockType();

        int layers = maxY - minY + 1;          // 总层数
        int[] currentLayer = {0};               // 当前已处理的层数（0 ~ layers-1）
        int[] direction = {isOpen ? 1 : -1};    // 开门向上(+1)，关门向下(-1)
        int[] y = {isOpen ? minY : maxY};        // 当前处理的 y 坐标

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentLayer[0] >= layers) {
                    // 动画完成
                    data.setOpen(newOpen);
                    data.setAnimating(false);
                    data.setTask(null);
                    saveDoors();
                    cancel();
                    return;
                }

                // 处理当前层
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block b = world.getBlockAt(x, y[0], z);
                        b.setType(isOpen ? Material.AIR : blockType); // 关门时 isOpen 为 false，走 else 分支
                        if (!isOpen && blockType.name().contains("WALL")) { // 关门且是墙
                            setWallTall(b);
                        }
                        MRD.updateNeighbors(world, x, y[0], z);
                    }
                }
                Sound s = Sound.BLOCK_IRON_BREAK;
                float f = 1f;
                // 粒子效果（仅在最低层）
                if (y[0] == minY) {
                    for (int i = 0; i < 10; i++) {
                        double x = minX + Math.random() * (maxX - minX + 1);
                        double z = minZ + Math.random() * (maxZ - minZ + 1);
                        world.spawnParticle(Particle.CLOUD, x, minY, z, 5, 0.3, 0.3, 0.3, 0.02);
                    }
                    s = Sound.BLOCK_CHAIN_BREAK;
                    f = 0.8f;
                }
                Location center1 = new Location(world, (minX + maxX) / 2.0, y[0], (minZ + maxZ) / 2.0);
                world.playSound(center1,s,1,f);
                world.playSound(center1,s,1,f);
                // 移动到下一层
                y[0] += direction[0];
                currentLayer[0]++;
            }
        }.runTaskTimer(this, 0L, delayPerLayer); // 每 delayPerLayer tick 执行一次

        data.setTask(task);
    }

    // ========== 工具方法 ==========
    public static void updateNeighbors(World world, int x, int y, int z) {
        // 只更新六个邻居，不更新自身（自身已手动设置）
        world.getBlockAt(x, y, z - 1).getState().update(true, true); // 北
        world.getBlockAt(x, y, z + 1).getState().update(true, true); // 南
        world.getBlockAt(x - 1, y, z).getState().update(true, true); // 西
        world.getBlockAt(x + 1, y, z).getState().update(true, true); // 东
        world.getBlockAt(x, y + 1, z).getState().update(true, true); // 上
        world.getBlockAt(x, y - 1, z).getState().update(true, true); // 下
    }
    private void setWallTall(Block block) {
        if (block.getBlockData() instanceof Wall wall) {
            // 检查四个水平方向
            setWallSide(wall, block, BlockFace.NORTH);
            setWallSide(wall, block, BlockFace.EAST);
            setWallSide(wall, block, BlockFace.SOUTH);
            setWallSide(wall, block, BlockFace.WEST);
            wall.setUp(false);
            block.setBlockData(wall);
        }
    }

    private void setWallSide(Wall wall, Block block, BlockFace face) {
        Block neighbor = block.getRelative(face);
        if (neighbor.getType().isSolid()) {
            wall.setHeight(face,Wall.Height.TALL);
        } else {
            wall.setHeight(face,Wall.Height.NONE);
        }
    }

    public void setAllDoors(boolean open) {
        for (Map.Entry<String, DoorData> entry : doors.entrySet()) {
            DoorData data = entry.getValue();
            if (data.isOpen() != open && !data.isAnimating()) {
                toggleDoor(entry.getKey());
            }
        }
    }
}