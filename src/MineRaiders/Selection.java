package MineRaiders;

import org.bukkit.World;

public class Selection {
    public World world;
    public int x1, y1, z1;
    public int x2, y2, z2;
    public boolean hasPos1 = false;
    public boolean hasPos2 = false;

    public boolean isComplete() {
        return hasPos1 && hasPos2;
    }
}