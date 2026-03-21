package MineRaiders;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MenuManager {
    private final MRD plugin;

    public static class DoorMainMenuHolder implements InventoryHolder {
        private final int page;
        public DoorMainMenuHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class DoorActionMenuHolder implements InventoryHolder {
        private final String worldName;
        private final String doorId;
        public DoorActionMenuHolder(String worldName, String doorId) {
            this.worldName = worldName;
            this.doorId = doorId;
        }
        public String getWorldName() { return worldName; }
        public String getDoorId() { return doorId; }
        @Override public Inventory getInventory() { return null; }
    }

    public MenuManager(MRD plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player, int page) {
        List<Map.Entry<String, String>> entries = plugin.getAllDoorEntries();
        int totalPages = (int) Math.ceil(entries.size() / 45.0);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(new DoorMainMenuHolder(page), 54,
                "门管理菜单 (第 " + (page + 1) + "/" + totalPages + " 页)");

        int start = page * 45;
        int end = Math.min(start + 45, entries.size());
        for (int i = start; i < end; i++) {
            String worldName = entries.get(i).getKey();
            String id = entries.get(i).getValue();
            DoorData data = plugin.getDoor(worldName, id);
            if (data == null) continue;

            ItemStack item = new ItemStack(data.getBlockType());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + "门: " + id);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "世界: " + worldName);
            lore.add(ChatColor.GRAY + "坐标: (" + data.getMinX() + "," + data.getMinY() + "," + data.getMinZ() +
                    ") -> (" + data.getMaxX() + "," + data.getMaxY() + "," + data.getMaxZ() + ")");
            lore.add(ChatColor.GRAY + "方块: " + data.getBlockType().name());
            lore.add(ChatColor.GRAY + "状态: " + (data.isOpen() ? ChatColor.GREEN + "关闭" : ChatColor.RED + "开启"));
            lore.add(ChatColor.GRAY + "绑定按钮数: " + getButtonCount(worldName, id));
            lore.add("");
            lore.add(ChatColor.GOLD + "点击进入管理");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i - start, item);
        }

        // 底部控制栏
        inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.AQUA + "上一页"));
        inv.setItem(46, createMenuItem(Material.ARROW, ChatColor.AQUA + "下一页"));
        inv.setItem(49, createMenuItem(Material.EMERALD, ChatColor.GREEN + "创建新门",
                Collections.singletonList(ChatColor.GRAY + "点击使用命令创建")));
        inv.setItem(50, createMenuItem(Material.REDSTONE_BLOCK, ChatColor.RED + "关闭所有门",
                Collections.singletonList(ChatColor.GRAY + "点击使所有门变为关闭状态（方块出现）")));
        inv.setItem(51, createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "开启所有门",
                Collections.singletonList(ChatColor.GRAY + "点击使所有门变为开启状态（方块消失）")));
        inv.setItem(53, createMenuItem(Material.BARRIER, ChatColor.RED + "关闭菜单"));

        player.openInventory(inv);
        plugin.menuPage.put(player, page);
    }

    public void openActionMenu(Player player, String worldName, String doorId) {
        DoorData data = plugin.getDoor(worldName, doorId);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "该门已不存在！");
            return;
        }

        Inventory inv = Bukkit.createInventory(new DoorActionMenuHolder(worldName, doorId), 27, "管理门: " + doorId);

        ItemStack info = new ItemStack(data.getBlockType());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "门: " + doorId);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "世界: " + worldName);
        lore.add(ChatColor.GRAY + "坐标: (" + data.getMinX() + "," + data.getMinY() + "," + data.getMinZ() +
                ") -> (" + data.getMaxX() + "," + data.getMaxY() + "," + data.getMaxZ() + ")");
        lore.add(ChatColor.GRAY + "方块: " + data.getBlockType().name());
        lore.add(ChatColor.GRAY + "状态: " + (data.isOpen() ? ChatColor.GREEN + "关闭" : ChatColor.RED + "开启"));
        lore.add(ChatColor.GRAY + "绑定按钮数: " + getButtonCount(worldName, doorId));
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        inv.setItem(9, createMenuItem(Material.LEVER, ChatColor.GOLD + "开关门",
                Collections.singletonList(ChatColor.GRAY + "点击切换门的状态")));
        inv.setItem(11, createMenuItem(Material.TNT, ChatColor.RED + "删除门",
                Collections.singletonList(ChatColor.GRAY + "点击删除此门（不可恢复）")));
        inv.setItem(15, createMenuItem(Material.STONE_BUTTON, ChatColor.GREEN + "绑定按钮",
                Collections.singletonList(ChatColor.GRAY + "点击进入绑定模式，右键点击按钮")));
        inv.setItem(17, createMenuItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "传送到门",
                Collections.singletonList(ChatColor.GRAY + "点击传送至门顶部")));
        inv.setItem(22, createMenuItem(Material.ARROW, ChatColor.AQUA + "返回主菜单"));

        player.openInventory(inv);
    }

    public void handleMainMenuClick(Player player, int slot, int page, ItemStack current) {
        if (slot >= 45 && slot <= 53) {
            if (slot == 45) openMainMenu(player, page - 1);
            else if (slot == 46) openMainMenu(player, page + 1);
            else if (slot == 49) {
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "请使用 /door create 命令创建门");
            } else if (slot == 50) {
                player.closeInventory();
                plugin.setAllDoors(true);
                player.sendMessage(ChatColor.GREEN + "已将所有门关闭");
            } else if (slot == 51) {
                player.closeInventory();
                plugin.setAllDoors(false);
                player.sendMessage(ChatColor.GREEN + "已将所有门开启");
            } else if (slot == 53) player.closeInventory();
            return;
        }

        if (slot < 45) {
            ItemMeta meta = current.getItemMeta();
            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) return;

            String worldName = null;
            String doorId = null;
            for (String line : lore) {
                if (line.contains(ChatColor.GRAY + "世界: ")) {
                    worldName = ChatColor.stripColor(line).replace("世界: ", "");
                }
            }
            String displayName = meta.getDisplayName();
            if (displayName.startsWith(ChatColor.YELLOW + "门: ")) {
                doorId = displayName.substring((ChatColor.YELLOW + "门: ").length());
            }
            if (worldName != null && doorId != null && plugin.getDoor(worldName, doorId) != null) {
                openActionMenu(player, worldName, doorId);
            } else {
                player.sendMessage(ChatColor.RED + "该门已不存在，刷新列表");
                openMainMenu(player, page);
            }
        }
    }

    public void handleActionMenuClick(Player player, String worldName, String doorId, int slot) {
        DoorData data = plugin.getDoor(worldName, doorId);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "该门已不存在，返回主菜单");
            player.closeInventory();
            openMainMenu(player, 0);
            return;
        }

        switch (slot) {
            case 9:
                if (!data.isAnimating()) {
                    plugin.toggleDoor(worldName, doorId);
                    player.sendMessage(ChatColor.GREEN + "门 " + doorId + " 已切换");
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "门正在移动，请稍后");
                }
                break;
            case 11:
                player.closeInventory();
                if (plugin.removeDoorBoolean(worldName, doorId)) {
                    player.sendMessage(ChatColor.GREEN + "门 " + doorId + " 已删除");
                }
                openMainMenu(player, 0);
                break;
            case 15:
                player.closeInventory();
                plugin.bindingPlayers.put(player, doorId);
                player.sendMessage(ChatColor.GREEN + "请右键点击一个按钮将其绑定到门 " + doorId);
                break;
            case 17:
                player.closeInventory();
                teleportToDoor(player, data);
                break;
            case 22:
                openMainMenu(player, 0);
                break;
        }
    }

    private void teleportToDoor(Player player, DoorData data) {
        World world = data.getWorld();
        if (!player.getWorld().equals(world)) {
            player.sendMessage(ChatColor.RED + "你不在门所在的世界！");
            return;
        }

        int minX = data.getMinX(), maxX = data.getMaxX();
        int minY = data.getMinY(), maxY = data.getMaxY();
        int minZ = data.getMinZ(), maxZ = data.getMaxZ();

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        List<Location> candidates = new ArrayList<>();
        candidates.add(new Location(world, maxX + 1, minY, centerZ));
        candidates.add(new Location(world, minX - 1, minY, centerZ));
        candidates.add(new Location(world, centerX, minY, maxZ + 1));
        candidates.add(new Location(world, centerX, minY, minZ - 1));

        for (Location candidate : candidates) {
            Location safe = findSafeGround(candidate);
            if (safe != null) {
                player.teleport(safe);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1, 1);
                player.sendMessage(ChatColor.GREEN + "已传送到门附近。");
                return;
            }
        }

        double fallbackX = (minX + maxX) / 2.0 + 0.5;
        double fallbackZ = (minZ + maxZ) / 2.0 + 0.5;
        int startY = maxY + 1;
        Location fallback = new Location(world, fallbackX, startY, fallbackZ);
        for (int offset = 0; offset < 10; offset++) {
            Location check = fallback.clone().add(0, offset, 0);
            if (check.getBlock().isPassable()) {
                player.teleport(check);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1, 1);
                player.sendMessage(ChatColor.GREEN + "已传送到门顶部。");
                return;
            }
        }

        player.sendMessage(ChatColor.RED + "门附近无安全位置，无法传送。");
    }

    private Location findSafeGround(Location start) {
        for (int i = 0; i < 10; i++) {
            Location check = start.clone().subtract(0, i, 0);
            Block block = check.getBlock();
            Block below = check.clone().subtract(0, 1, 0).getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();

            if (block.isPassable() && below.getType().isSolid() && head.isPassable()) {
                return check;
            }
        }
        return null;
    }

    private int getButtonCount(String worldName, String doorId) {
        return (int) plugin.buttonToDoor.entrySet().stream()
                .filter(e -> e.getKey().getWorld().getName().equals(worldName) && e.getValue().equals(doorId))
                .count();
    }

    private ItemStack createMenuItem(Material material, String name) {
        return createMenuItem(material, name, null);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}