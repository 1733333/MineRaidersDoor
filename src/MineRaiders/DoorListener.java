package MineRaiders;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class DoorListener implements Listener {
    private final MRD plugin;
    private final MenuManager menuManager;

    public DoorListener(MRD plugin) {
        this.plugin = plugin;
        this.menuManager = new MenuManager(plugin);
    }

    private boolean isKeyForMaterial(ItemStack item, Material doorMaterial) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        String materialName = doorMaterial.name();
        return lore.stream().anyMatch(line ->
                ChatColor.stripColor(line).equalsIgnoreCase(materialName));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        Block block = e.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();

        // ========== 1. 选区工具处理 ==========
        if (item != null && item.getType() == Material.WOODEN_AXE && item.hasItemMeta() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.AQUA + "门选区工具")) {
            e.setCancelled(true);
            Selection sel = plugin.playerSelections.computeIfAbsent(p, k -> new Selection());

            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                sel.world = p.getWorld();
                sel.x1 = loc.getBlockX();
                sel.y1 = loc.getBlockY();
                sel.z1 = loc.getBlockZ();
                sel.hasPos1 = true;
                p.sendMessage(ChatColor.GREEN + "已选择第一个点: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                sel.world = p.getWorld();
                sel.x2 = loc.getBlockX();
                sel.y2 = loc.getBlockY();
                sel.z2 = loc.getBlockZ();
                sel.hasPos2 = true;
                p.sendMessage(ChatColor.GREEN + "已选择第二个点: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            }

            if (sel.isComplete()) {
                p.sendMessage(ChatColor.AQUA + "选区完成！可以使用 /door create <id> [blocktype] 创建门。");
            }
            return;
        }

        // ========== 2. 按钮绑定与触发处理 ==========
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && block.getBlockData() instanceof Switch) {
            // 2.1 绑定模式处理
            if (plugin.bindingPlayers.containsKey(p)) {
                e.setCancelled(true);
                String doorId = plugin.bindingPlayers.remove(p);
                if (doorId == null) {
                    // 解除绑定模式
                    if (plugin.buttonToDoor.remove(loc) != null) {
                        p.sendMessage(ChatColor.GREEN + "按钮绑定已解除");
                        plugin.saveButtons();
                    } else {
                        p.sendMessage(ChatColor.RED + "该按钮未绑定任何门");
                    }
                } else {
                    // 绑定门到按钮
                    if (plugin.buttonToDoor.containsKey(loc)) {
                        p.sendMessage(ChatColor.RED + "该按钮已被绑定，请先解除");
                        return;
                    }
                    DoorData data = plugin.getDoor(block.getWorld(), doorId);
                    if (data == null) {
                        p.sendMessage(ChatColor.RED + "门不存在或不在当前世界");
                        return;
                    }
                    if (!data.getWorld().equals(block.getWorld())) {
                        p.sendMessage(ChatColor.RED + "门和按钮必须在同一世界");
                        return;
                    }
                    plugin.buttonToDoor.put(loc, doorId);
                    p.sendMessage(ChatColor.GREEN + "按钮已绑定到门 " + doorId);
                    plugin.saveButtons();
                }
                return;
            }

            // 2.2 普通按钮点击触发门
            String doorId = plugin.buttonToDoor.get(loc);
            if (doorId != null) {
                DoorData data = plugin.getDoor(loc.getWorld(), doorId);
                if (data == null) {
                    plugin.buttonToDoor.remove(loc);
                    plugin.saveButtons();
                    return;
                }
                if (data.isAnimating()) {
                    p.sendMessage(ChatColor.RED + "门正在移动，请稍后");
                    return;
                }
                Location midLoc = data.getMiddleLocation();
                double distance = loc.distanceSquared(midLoc);
                String message;
                if(distance > 10) {
                    message = ChatColor.GREEN + "远处传来门移动的声音";
                }else {
                    message = ChatColor.AQUA + "门正在移动";
                }
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(message));
                plugin.toggleDoor(loc.getWorld().getName(), doorId);
                return;
            }
        }

        // ========== 3. 手持钥匙右键门 ==========
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            for (Map.Entry<String, Map<String, DoorData>> worldEntry : plugin.doors.entrySet()) {
                String worldName = worldEntry.getKey();
                for (Map.Entry<String, DoorData> doorEntry : worldEntry.getValue().entrySet()) {
                    DoorData data = doorEntry.getValue();
                    if (!data.getWorld().equals(p.getWorld())) continue;
                    if (loc.getBlockX() < data.getMinX() || loc.getBlockX() > data.getMaxX() ||
                            loc.getBlockY() < data.getMinY() || loc.getBlockY() > data.getMaxY() ||
                            loc.getBlockZ() < data.getMinZ() || loc.getBlockZ() > data.getMaxZ()) {
                        continue;
                    }

                    if (isKeyForMaterial(item, data.getBlockType())) {
                        if (data.isAnimating()) {
                            p.sendMessage(ChatColor.RED + "门正在移动，请稍后");
                            return;
                        }
                        plugin.toggleDoor(worldName, doorEntry.getKey());
                        p.sendMessage(ChatColor.GREEN + "你使用钥匙打开了门。");
                        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1, 1);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1, 1);
                        if(p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR){
                            int amount = item.getAmount();
                            item.setAmount(amount - 1);
                        }
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof MenuManager.DoorMainMenuHolder) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            menuManager.handleMainMenuClick(p, e.getRawSlot(), ((MenuManager.DoorMainMenuHolder) holder).getPage(), e.getCurrentItem());
        } else if (holder instanceof MenuManager.DoorActionMenuHolder) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;
            menuManager.handleActionMenuClick(p, ((MenuManager.DoorActionMenuHolder) holder).getWorldName(),
                    ((MenuManager.DoorActionMenuHolder) holder).getDoorId(), e.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof MenuManager.DoorMainMenuHolder || holder instanceof MenuManager.DoorActionMenuHolder) {
            e.setCancelled(true);
        }
    }
}