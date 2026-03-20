package MineRaiders;

import MineRaiders.MRD;
import MineRaiders.MenuManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class DoorCommandExecutor implements CommandExecutor {
    private final MRD plugin;

    public DoorCommandExecutor(MRD plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令");
            return true;
        }
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "wand" -> giveWand(sender);
            case "create" -> createDoor(sender, args);
            case "toggle" -> toggleDoor(sender, args);
            case "remove" -> removeDoor(sender, args);
            case "bind" -> bindButton(sender, args);
            case "unbind" -> unbindButton(sender, args);
            case "menu" -> openMenu(sender);
            default -> {
                sender.sendMessage("未知子命令，可用: wand, create, toggle, remove, bind, unbind, menu");
                yield false;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 门插件指令帮助 =====");
        sender.sendMessage(ChatColor.YELLOW + "/door wand");
        sender.sendMessage(ChatColor.GRAY + "  获取木棍，用于选择门的两个对角点。");
        sender.sendMessage(ChatColor.YELLOW + "/door create <id> [blocktype]");
        sender.sendMessage(ChatColor.GRAY + "  使用当前选区创建门，可指定方块类型（默认 POLISHED_TUFF_WALL）。");
        sender.sendMessage(ChatColor.YELLOW + "/door toggle <id>");
        sender.sendMessage(ChatColor.GRAY + "  开关指定ID的门。");
        sender.sendMessage(ChatColor.YELLOW + "/door remove <id>");
        sender.sendMessage(ChatColor.GRAY + "  删除指定ID的门。");
        sender.sendMessage(ChatColor.YELLOW + "/door bind <id>");
        sender.sendMessage(ChatColor.GRAY + "  进入绑定模式，手持物品右键点击按钮绑定到该门。");
        sender.sendMessage(ChatColor.YELLOW + "/door unbind");
        sender.sendMessage(ChatColor.GRAY + "  解除按钮绑定模式。");
        sender.sendMessage(ChatColor.YELLOW + "/door menu");
        sender.sendMessage(ChatColor.GRAY + "  打开门管理菜单。");
        sender.sendMessage(ChatColor.GOLD + "===========================");
    }

    private boolean giveWand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("只有玩家可以使用此命令");
            return false;
        }
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "门选区工具");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "左键选择第一个点",
                ChatColor.GRAY + "右键选择第二个点",
                ChatColor.GRAY + "然后使用 /door create <id> [blocktype] 创建门"
        ));
        wand.setItemMeta(meta);
        p.getInventory().addItem(wand);
        p.sendMessage(ChatColor.GREEN + "你获得了门选区工具。使用左键/右键点击方块选择两个对角点。");
        return true;
    }

    private boolean createDoor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("只有玩家可以使用创建命令");
            return false;
        }
        if (args.length < 2) {
            p.sendMessage("用法: /door create <id> [blocktype]");
            return false;
        }
        String id = args[1];
        if (plugin.doors.containsKey(id)) {
            p.sendMessage("ID已存在，请换一个");
            return false;
        }

        Material blockType = Material.POLISHED_TUFF_WALL;
        if (args.length >= 3) {
            String typeName = args[2].toUpperCase();
            Material mat = Material.getMaterial(typeName);
            if (mat == null || !mat.isBlock()) {
                p.sendMessage("无效的方块类型，使用默认 POLISHED_TUFF_WALL");
            } else {
                blockType = mat;
            }
        }

        Selection sel = plugin.playerSelections.get(p);
        if (sel == null || !sel.isComplete()) {
            p.sendMessage("请先使用木斧选择两个点！");
            return false;
        }
        if (!sel.world.equals(p.getWorld())) {
            p.sendMessage("选区必须在当前世界！");
            return false;
        }

        int minX = Math.min(sel.x1, sel.x2);
        int maxX = Math.max(sel.x1, sel.x2);
        int minY = Math.min(sel.y1, sel.y2);
        int maxY = Math.max(sel.y1, sel.y2);
        int minZ = Math.min(sel.z1, sel.z2);
        int maxZ = Math.max(sel.z1, sel.z2);

        if ((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 500) {
            p.sendMessage("门太大，请限制在500方块内");
            return true;
        }

        boolean success = plugin.createDoor(id, p.getWorld(), minX, maxX, minY, maxY, minZ, maxZ, blockType);
        if (success) {
            p.sendMessage("门已创建，ID: " + id + "，方块类型: " + blockType.name());
            plugin.playerSelections.remove(p);
        } else {
            p.sendMessage("创建失败，ID可能已存在（但已检查）");
        }
        return true;
    }

    private boolean toggleDoor(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("用法: /door toggle <id>");
            return false;
        }
        String id = args[1];
        if (!plugin.doors.containsKey(id)) {
            sender.sendMessage("门不存在");
            return false;
        }
        plugin.toggleDoor(id);
        return true;
    }

    private boolean removeDoor(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("用法: /door remove <id>");
            return false;
        }
        String id = args[1];
        if (plugin.removeDoor(id)) {
            sender.sendMessage("门已删除，ID: " + id);
        } else {
            sender.sendMessage("门不存在");
        }
        return true;
    }

    private boolean bindButton(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("只有玩家可以使用此命令");
            return false;
        }
        if (args.length != 2) {
            sender.sendMessage("用法: /door bind <id>");
            return false;
        }
        String id = args[1];
        if (!plugin.doors.containsKey(id)) {
            sender.sendMessage("门不存在");
            return false;
        }
        plugin.bindingPlayers.put(p, id);
        p.sendMessage(ChatColor.GREEN + "请右键点击一个按钮将其绑定到门 " + id);
        return true;
    }

    private boolean unbindButton(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("只有玩家可以使用此命令");
            return false;
        }
        plugin.bindingPlayers.put(p, null); // null 表示解除绑定模式
        p.sendMessage(ChatColor.GREEN + "请右键点击一个按钮以解除其绑定");
        return true;
    }

    private boolean openMenu(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("只有玩家可以使用此命令");
            return false;
        }
        new MenuManager(plugin).openMainMenu(p, 0);
        return true;
    }
}