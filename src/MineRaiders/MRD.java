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
    // 数据存储：外层Map Key = 世界名，内层Map Key = 门ID
    public final Map<String, Map<String, DoorData>> doors = new HashMap<>();
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
        Objects.requireNonNull(getCommand("door")).setTabCompleter(new DoorCommandExecutor(this));;
        getServer().getPluginManager().registerEvents(new DoorListener(this), this);

        getLogger().info("门插件已启用");
    }

    @Override
    public void onDisable() {
        saveDoors();
        saveButtons();
        getServer().getScheduler().cancelTasks(this);
        backupFiles();
        getLogger().info("门插件已禁用");
    }

    // ========== 门数据访问辅助方法 ==========
    public DoorData getDoor(String worldName, String id) {
        Map<String, DoorData> worldDoors = doors.get(worldName);
        return worldDoors != null ? worldDoors.get(id) : null;
    }

    public DoorData getDoor(World world, String id) {
        return getDoor(world.getName(), id);
    }

    public void putDoor(String worldName, String id, DoorData data) {
        doors.computeIfAbsent(worldName, k -> new HashMap<>()).put(id, data);
    }

    public DoorData removeDoor(String worldName, String id) {
        Map<String, DoorData> worldDoors = doors.get(worldName);
        if (worldDoors != null) {
            return worldDoors.remove(id);
        }
        return null;
    }

    // 获取所有门（用于菜单显示）
    public List<Map.Entry<String, String>> getAllDoorEntries() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, DoorData>> worldEntry : doors.entrySet()) {
            String worldName = worldEntry.getKey();
            for (String id : worldEntry.getValue().keySet()) {
                entries.add(new AbstractMap.SimpleEntry<>(worldName, id));
            }
        }
        return entries;
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
            putDoor(worldName, id, data);
        }
        getLogger().info("已加载 " + doors.values().stream().mapToInt(Map::size).sum() + " 个门");
    }

    void saveDoors() {
        doorsConfig = new YamlConfiguration();
        for (Map.Entry<String, Map<String, DoorData>> worldEntry : doors.entrySet()) {
            String worldName = worldEntry.getKey();
            for (Map.Entry<String, DoorData> doorEntry : worldEntry.getValue().entrySet()) {
                String id = doorEntry.getKey();
                DoorData data = doorEntry.getValue();
                doorsConfig.set(id + ".world", worldName);
                doorsConfig.set(id + ".minX", data.getMinX());
                doorsConfig.set(id + ".maxX", data.getMaxX());
                doorsConfig.set(id + ".minY", data.getMinY());
                doorsConfig.set(id + ".maxY", data.getMaxY());
                doorsConfig.set(id + ".minZ", data.getMinZ());
                doorsConfig.set(id + ".maxZ", data.getMaxZ());
                doorsConfig.set(id + ".open", data.isOpen());
                doorsConfig.set(id + ".blockType", data.getBlockType().name());
            }
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
                if (doorId != null && getDoor(world, doorId) != null) {
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
        String worldName = world.getName();
        if (getDoor(worldName, id) != null) return false;

        // 填充方块
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    b.setType(blockType);
                    if (blockType.name().contains("WALL")) {
                        setWallTall(b);
                    }
                    updateNeighbors(world, x, y, z);
                }
            }
        }

        DoorData data = new DoorData(world, minX, maxX, minY, maxY, minZ, maxZ, blockType);
        putDoor(worldName, id, data);
        saveDoors();
        return true;
    }

    public boolean removeDoorBoolean(String worldName, String id) {
        DoorData data = removeDoor(worldName, id);
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

        // 删除该门的所有按钮绑定
        buttonToDoor.entrySet().removeIf(entry -> entry.getValue().equals(id) && entry.getKey().getWorld().equals(world));
        saveButtons();
        saveDoors();
        return true;
    }

    public void toggleDoor(String worldName, String id) {
        toggleDoor(worldName, id, 4);
    }

    public void toggleDoor(String worldName, String id, int delayPerLayer) {
        DoorData data = getDoor(worldName, id);
        if (data == null || data.isAnimating()) return;

        data.setAnimating(true);
        World world = data.getWorld();
        int minX = data.getMinX(), maxX = data.getMaxX();
        int minY = data.getMinY(), maxY = data.getMaxY();
        int minZ = data.getMinZ(), maxZ = data.getMaxZ();
        boolean isOpen = data.isOpen();
        boolean newOpen = !isOpen;
        Material blockType = data.getBlockType();

        int layers = maxY - minY + 1;
        int[] currentLayer = {0};
        int[] direction = {isOpen ? 1 : -1};
        int[] y = {isOpen ? minY : maxY};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentLayer[0] >= layers) {
                    data.setOpen(newOpen);
                    data.setAnimating(false);
                    data.setTask(null);
                    saveDoors();
                    cancel();
                    return;
                }

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block b = world.getBlockAt(x, y[0], z);
                        b.setType(isOpen ? Material.AIR : blockType);
                        if (!isOpen && blockType.name().contains("WALL")) {
                            setWallTall(b);
                        }
                        updateNeighbors(world, x, y[0], z);
                    }
                }
                Sound s = Sound.BLOCK_IRON_BREAK;
                float f = 1f;
                if (y[0] == minY) {
                    for (int i = 0; i < 10; i++) {
                        double x = minX + Math.random() * (maxX - minX + 1);
                        double z = minZ + Math.random() * (maxZ - minZ + 1);
                        world.spawnParticle(Particle.CLOUD, x, minY, z, 5, 0.3, 0.3, 0.3, 0.02);
                    }
                    s = Sound.BLOCK_CHAIN_BREAK;
                    f = 0.8f;
                }
                Location center = new Location(world, (minX + maxX) / 2.0, y[0], (minZ + maxZ) / 2.0);
                world.playSound(center, s, 3, f);
                world.playSound(center, s, 3, f);

                y[0] += direction[0];
                currentLayer[0]++;
            }
        }.runTaskTimer(this, 0L, delayPerLayer);

        data.setTask(task);
    }

    public void setAllDoors(boolean open) {
        for (Map.Entry<String, Map<String, DoorData>> worldEntry : doors.entrySet()) {
            String worldName = worldEntry.getKey();
            for (String id : worldEntry.getValue().keySet()) {
                DoorData data = getDoor(worldName, id);
                if (data != null && data.isOpen() != open && !data.isAnimating()) {
                    toggleDoor(worldName, id);
                }
            }
        }
    }

    // ========== 工具方法 ==========
    public static void updateNeighbors(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z - 1).getState().update(true, true);
        world.getBlockAt(x, y, z + 1).getState().update(true, true);
        world.getBlockAt(x - 1, y, z).getState().update(true, true);
        world.getBlockAt(x + 1, y, z).getState().update(true, true);
        world.getBlockAt(x, y + 1, z).getState().update(true, true);
        world.getBlockAt(x, y - 1, z).getState().update(true, true);
    }

    private void setWallTall(Block block) {
        if (block.getBlockData() instanceof Wall wall) {
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
        if (isFullBlock(neighbor)) {
            wall.setHeight(face, Wall.Height.TALL);
        } else {
            wall.setHeight(face, Wall.Height.NONE);
        }
    }

    public void reload() {
        for (Map.Entry<String, Map<String, DoorData>> worldEntry : doors.entrySet()) {
            for (DoorData data : worldEntry.getValue().values()) {
                if (data.getTask() != null) {
                    data.getTask().cancel();
                    data.setAnimating(false);
                    data.setTask(null);
                }
            }
        }
        doors.clear();
        buttonToDoor.clear();
        loadDoors();
        loadButtons();
        getLogger().info("门配置已重新加载");
    }
    public boolean isFullBlock(Block b){
        Material type = b.getType();
        String name = type.toString();
        if(type == Material.FIRE)return false;
        if(type == Material.AIR)return false;
        if(type == Material.LIGHT)return false;
        if(type == Material.IRON_BARS)return false;
        if(type == Material.POWDER_SNOW)return false;
        if(!type.isSolid())return false;
        if(name.contains("BUTTON")) {
            return false;
        }
        if(name.contains("PANE")) {
            return false;
        }
        if(name.contains("TRAPDOOR")){
            return false;
        }
        if(name.contains("CARPET")){
            return false;
        }
        if(name.contains("SIGN")){
            return false;
        }
        if(name.contains("FENCE")){
            return false;
        }
        return true;
    }
    private void backupFiles() {
        File dataFolder = getDataFolder();
        File backupDir = new File(dataFolder, "backup");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 精确到分钟的时间戳，格式: 20260324_1430
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(new java.util.Date());

        File doorsFile = new File(dataFolder, "doors.yml");
        if (doorsFile.exists()) {
            File backupDoors = new File(backupDir, "doors_" + timestamp + ".yml");
            try {
                java.nio.file.Files.copy(doorsFile.toPath(), backupDoors.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("已备份 doors.yml 到 " + backupDoors.getName());
            } catch (IOException e) {
                getLogger().warning("备份 doors.yml 失败: " + e.getMessage());
            }
        }

        File buttonsFile = new File(dataFolder, "buttons.yml");
        if (buttonsFile.exists()) {
            File backupButtons = new File(backupDir, "buttons_" + timestamp + ".yml");
            try {
                java.nio.file.Files.copy(buttonsFile.toPath(), backupButtons.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("已备份 buttons.yml 到 " + backupButtons.getName());
            } catch (IOException e) {
                getLogger().warning("备份 buttons.yml 失败: " + e.getMessage());
            }
        }
    }
    /**
     * 判断指定位置是否属于任何门（所有世界）
     * @param loc 要检查的位置
     * @return 如果该位置位于某个门的区域内则返回 true，否则 false
     */
    public boolean isLocationInDoor(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        String worldName = loc.getWorld().getName();
        Map<String, DoorData> worldDoors = doors.get(worldName);
        if (worldDoors == null) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (DoorData data : worldDoors.values()) {
            if (x >= data.getMinX() && x <= data.getMaxX() &&
                    y >= data.getMinY() && y <= data.getMaxY() &&
                    z >= data.getMinZ() && z <= data.getMaxZ()) {
                return true;
            }
        }
        return false;
    }

}