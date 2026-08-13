package dev.controlbro.cbmall;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MallCommands implements CommandExecutor, TabCompleter {
    private final CBMallPlugin plugin;
    public MallCommands(CBMallPlugin plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(plugin.color(plugin.prefix() + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mallclaim")) {
            if (!(sender instanceof Player p)) {
                msg(sender, "Players only.");
                return true;
            }
            if (args.length > 1) {
                msg(p, "&cUsage: /mallclaim [page]");
                return true;
            }
            int page = 1;
            if (args.length == 1) {
                try {
                    page = Math.max(1, Integer.parseInt(args[0]));
                } catch (NumberFormatException e) {
                    msg(p, "&cUsage: /mallclaim [page]");
                    return true;
                }
            }
            plugin.openRecovery(p, page - 1);
            return true;
        }
        if (command.getName().equalsIgnoreCase("malladmin"))
            return admin(sender, args);
        if (!(sender instanceof Player p)) {
            msg(sender, "Players only.");
            return true;
        }
        if (args.length == 0) {
            msg(p,
                    "&e/mall claim <plotid>&7, &e/mall addmember <player>&7, &e/mall removemember "
                            + "<player>&7, &e/mall members&7, &e/mall viewborder&7, &e/mall unlock&7, "
                            + "&e/mall lock");
            return true;
        }
        Plot own = plugin.plots().ownedBy(p.getUniqueId());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> {
                if (args.length < 2) {
                    msg(p, "&cUsage: /mall claim <plotid>");
                    break;
                }
                Plot plot = parsePlot(args[1]);
                if (plot == null) {
                    msg(p, "&cUnknown plot.");
                    break;
                }
                if (plot.owner != null) {
                    msg(p, "&cThat plot is already claimed.");
                    break;
                }
                if (own != null) {
                    msg(p, "&cYou already own plot " + own.id + ".");
                    break;
                }
                plugin.plots().assign(plot, p.getUniqueId());
                msg(p, "&aYou claimed plot &e" + plot.id + "&a!");
            }
            case "addmember", "removemember" -> {
                if (own == null) {
                    msg(p, "&cYou do not own a plot.");
                    break;
                }
                if (args.length < 2) {
                    msg(p, "&cProvide a player name.");
                    break;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (args[0].equalsIgnoreCase("addmember")) {
                    if (target.getUniqueId().equals(p.getUniqueId())) {
                        msg(p, "&cYou are already the owner.");
                        break;
                    }
                    own.members.add(target.getUniqueId());
                    msg(p, "&aAdded &e" + target.getName() + " &ato plot " + own.id + ".");
                } else {
                    own.members.remove(target.getUniqueId());
                    msg(p, "&aRemoved &e" + args[1] + " &afrom plot " + own.id + ".");
                }
                plugin.plots().save();
            }
            case "viewborder" -> {
                if (own == null) {
                    msg(p, "&cYou do not own a plot.");
                    break;
                }
                plugin.showBorder(p, own);
                msg(p, "&aShowing border for plot &e" + own.id + "&a.");
            }
            case "members" -> {
                if (own == null) {
                    msg(p, "&cYou do not own a plot.");
                    break;
                }
                String names =
                        own.members.stream()
                                .map(u
                                        -> Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName())
                                                .orElse(u.toString()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("none");
                msg(p, "&7Trusted members: &f" + names);
            }
            case "unlock" -> {
                if (own == null) {
                    msg(p, "&cYou do not own a plot.");
                    break;
                }
                Block target = p.getTargetBlockExact(5);
                if (target == null || !own.contains(target.getLocation())
                        || !AccessTarget.isUnlockable(target)) {
                    msg(p, "&cLook at a container, door, trapdoor, or fence gate in your plot.");
                    break;
                }
                Set<String> keys = AccessTarget.keys(own, target);
                if (keys.stream().allMatch(own.unlockedContainers::contains)) {
                    msg(p, "&eThat target is already unlocked.");
                    break;
                }
                own.unlockedContainers.addAll(keys);
                plugin.plots().save();
                msg(p, "&aUnlocked this target for everyone.");
            }
            case "lock" -> {
                if (own == null) {
                    msg(p, "&cYou do not own a plot.");
                    break;
                }
                Block target = p.getTargetBlockExact(5);
                if (target == null || !own.contains(target.getLocation())
                        || !AccessTarget.isUnlockable(target)) {
                    msg(p, "&cLook at a container, door, trapdoor, or fence gate in your plot.");
                    break;
                }
                Set<String> keys = AccessTarget.keys(own, target);
                if (keys.stream().noneMatch(own.unlockedContainers::contains)) {
                    msg(p, "&eThat target is already locked.");
                    break;
                }
                own.unlockedContainers.removeAll(keys);
                plugin.plots().save();
                msg(p, "&aLocked this target.");
            }
            default -> msg(p, "&cUnknown subcommand. Use /mall for help.");
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cbmall.admin")) {
            msg(sender, "&cNo permission.");
            return true;
        }
        if (args.length == 0) {
            msg(sender,
                    "&e/malladmin wand|createshop|resetshop <id>|removeplot <id>|assign <id> "
                            + "<player>|unassign <player>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cPlayers only.");
                    break;
                }
                ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
                ItemMeta meta = wand.getItemMeta();
                meta.displayName(Component.text("CBMall Selection Wand"));
                meta.getPersistentDataContainer().set(
                        plugin.wandKey(), PersistentDataType.BYTE, (byte) 1);
                wand.setItemMeta(meta);
                p.getInventory().addItem(wand);
                msg(p, "&aLeft-click point 1; right-click point 2.");
            }
            case "createshop" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cPlayers only.");
                    break;
                }
                Selection s = plugin.selections().get(p.getUniqueId());
                if (s == null || !s.complete()) {
                    msg(p, "&cSelect two points first.");
                    break;
                }
                try {
                    Plot plot = plugin.plots().create(s.first, s.second);
                    plugin.selections().remove(p.getUniqueId());
                    msg(p, "&aCreated shop plot with ID &e" + plot.id + "&a.");
                } catch (IllegalArgumentException | IllegalStateException e) {
                    msg(p, "&c" + e.getMessage());
                }
            }
            case "removeplot" -> {
                if (args.length < 2) {
                    msg(sender, "&cUsage: /malladmin removeplot <plotid>");
                    break;
                }
                Plot p = parsePlot(args[1]);
                if (p == null) {
                    msg(sender, "&cUnknown plot.");
                    break;
                }
                plugin.plots().remove(p);
                msg(sender, "&aRemoved plot &e" + p.id + "&a.");
            }
            case "resetshop" -> {
                if (args.length < 2) {
                    msg(sender, "&cUsage: /malladmin resetshop <plotid>");
                    break;
                }
                Plot p = parsePlot(args[1]);
                if (p == null) {
                    msg(sender, "&cUnknown plot.");
                    break;
                }
                plugin.plots().reset(
                        p, plugin.getConfig().getBoolean("inactivity.return-items", true));
                msg(sender, "&aReset plot &e" + p.id + "&a.");
            }
            case "assign" -> {
                if (args.length < 3) {
                    msg(sender, "&cUsage: /malladmin assign <plotid> <player>");
                    break;
                }
                Plot p = parsePlot(args[1]);
                if (p == null) {
                    msg(sender, "&cUnknown plot.");
                    break;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                plugin.plots().assign(p, target.getUniqueId());
                msg(sender, "&aAssigned plot &e" + p.id + " &ato &e" + args[2] + "&a.");
            }
            case "unassign" -> {
                if (args.length < 2) {
                    msg(sender, "&cUsage: /malladmin unassign <player>");
                    break;
                }
                UUID id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                List<Plot> matches =
                        plugin.plots().all().stream().filter(p -> id.equals(p.owner)).toList();
                if (matches.isEmpty()) {
                    msg(sender, "&cThat player owns no plot.");
                    break;
                }
                matches.forEach(p
                        -> plugin.plots().reset(
                                p, plugin.getConfig().getBoolean("inactivity.return-items", true)));
                msg(sender, "&aUnassigned &e" + args[1] + "&a and reset their plot.");
            }
            default -> msg(sender, "&cUnknown subcommand.");
        }
        return true;
    }

    private Plot parsePlot(String value) {
        try {
            return plugin.plots().get(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (c.getName().equalsIgnoreCase("mallclaim"))
            return List.of();
        if (args.length == 1)
            return c.getName().equalsIgnoreCase("malladmin")
                    ? List.of("wand", "createshop", "resetshop", "removeplot", "assign", "unassign")
                    : List.of(
                            "claim", "addmember", "removemember", "members", "viewborder", "unlock",
                            "lock");
        if (args.length == 2
                && (args[0].equalsIgnoreCase("claim") || args[0].equalsIgnoreCase("resetshop")
                        || args[0].equalsIgnoreCase("removeplot")
                        || args[0].equalsIgnoreCase("assign")))
            return plugin.plots().all().stream().map(p -> String.valueOf(p.id)).toList();
        return List.of();
    }
}
