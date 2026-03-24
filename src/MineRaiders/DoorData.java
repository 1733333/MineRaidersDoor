package MineRaiders;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

public class DoorData {
    private final World world;
    private final int minX, maxX, minY, maxY, minZ, maxZ;
    private final Material blockType;
    private boolean open;
    private boolean animating;
    private BukkitTask task;

    public DoorData(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, Material blockType) {
        this.world = world;
        this.minX = minX; this.maxX = maxX;
        this.minY = minY; this.maxY = maxY;
        this.minZ = minZ; this.maxZ = maxZ;
        this.blockType = blockType;
        this.open = true; // 默认门是关闭状态（即方块存在）
        this.animating = false;
        this.task = null;
    }

    public World getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }
    public Material getBlockType() { return blockType; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public boolean isAnimating() { return animating; }
    public void setAnimating(boolean animating) { this.animating = animating; }
    public BukkitTask getTask() { return task; }
    public void setTask(BukkitTask task) { this.task = task; }
    public Location getMiddleLocation(){
        return new Location(world,
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0);
    }
}