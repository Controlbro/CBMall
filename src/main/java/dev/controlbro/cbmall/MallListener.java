package dev.controlbro.cbmall;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class MallListener implements Listener {
    private final CBMallPlugin plugin;
    private final Map<UUID, Integer> currentPlot = new HashMap<>();
    MallListener(CBMallPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean admin(Player p) {
        return p.hasPermission("cbmall.admin");
    }

    private boolean denied(Player p, Location location) {
        Plot plot = plugin.plots().at(location);
        return plot != null && !plot.mayBuild(p.getUniqueId(), admin(p));
    }

    private void deniedMessage(Player p) {
        p.sendMessage(plugin.color(plugin.prefix() + "&cThis shop is protected."));
    }

    private boolean wand(Player p) {
        ItemStack i = p.getInventory().getItemInMainHand();
        return i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(
                        plugin.wandKey(), PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void select(PlayerInteractEvent e) {
        if (!admin(e.getPlayer()) || !wand(e.getPlayer()) || e.getClickedBlock() == null)
            return;
        if (e.getHand() != EquipmentSlot.HAND)
            return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        e.setCancelled(true);
        Selection s = plugin.selections().computeIfAbsent(
                e.getPlayer().getUniqueId(), u -> new Selection());
        if (e.getAction() == Action.LEFT_CLICK_BLOCK)
            s.first = e.getClickedBlock().getLocation();
        else
            s.second = e.getClickedBlock().getLocation();
        e.getPlayer().sendMessage(plugin.color(plugin.prefix() + "&aSet point "
                + (e.getAction() == Action.LEFT_CLICK_BLOCK ? "1" : "2") + " to &e"
                + format(e.getClickedBlock().getLocation())));
    }

    private String format(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deniedMessage(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deniedMessage(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void multiPlace(BlockMultiPlaceEvent e) {
        if (e.getReplacedBlockStates().stream().anyMatch(
                    s -> denied(e.getPlayer(), s.getLocation()))) {
            e.setCancelled(true);
            deniedMessage(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void bucketEmpty(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void bucketFill(PlayerBucketFillEvent e) {
        if (denied(e.getPlayer(), e.getBlock().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Plot plot = plugin.plots().at(e.getClickedBlock().getLocation());
        if (plot == null || plot.mayBuild(e.getPlayer().getUniqueId(), admin(e.getPlayer())))
            return;
        if ((e.getClickedBlock().getState() instanceof Chest
                        || e.getClickedBlock().getState() instanceof Barrel)
                && plot.unlockedContainers.contains(plot.key(e.getClickedBlock().getX(),
                        e.getClickedBlock().getY(), e.getClickedBlock().getZ())))
            return;
        if (e.getClickedBlock().getState() instanceof Sign)
            return;
        e.setCancelled(true);
        deniedMessage(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void entityInteract(PlayerInteractEntityEvent e) {
        if (denied(e.getPlayer(), e.getRightClicked().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void armor(PlayerArmorStandManipulateEvent e) {
        if (denied(e.getPlayer(), e.getRightClicked().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void entityDamage(EntityDamageByEntityEvent e) {
        if (plugin.plots().at(e.getEntity().getLocation()) == null)
            return;
        if (e.getDamager() instanceof Player p
                && !plugin.plots()
                        .at(e.getEntity().getLocation())
                        .mayBuild(p.getUniqueId(), admin(p)))
            e.setCancelled(true);
        else if (!(e.getDamager() instanceof Player))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hanging(HangingBreakByEntityEvent e) {
        Plot p = plugin.plots().at(e.getEntity().getLocation());
        if (p == null)
            return;
        if (!(e.getRemover() instanceof Player player)
                || !p.mayBuild(player.getUniqueId(), admin(player)))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void hangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && denied(e.getPlayer(), e.getEntity().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void entityChange(EntityChangeBlockEvent e) {
        Plot plot = plugin.plots().at(e.getBlock().getLocation());
        if (plot != null && (!(e.getEntity() instanceof Player player)
                        || !plot.mayBuild(player.getUniqueId(), admin(player))))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.plots().at(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.plots().at(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void burn(BlockBurnEvent e) {
        if (plugin.plots().at(e.getBlock().getLocation()) != null)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (plugin.plots().at(e.getBlock().getLocation()) != null)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fade(BlockFadeEvent e) {
        if (plugin.plots().at(e.getBlock().getLocation()) != null)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void spread(BlockSpreadEvent e) {
        if (plugin.plots().at(e.getBlock().getLocation()) != null)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void fluid(BlockFromToEvent e) {
        if (plugin.plots().at(e.getToBlock().getLocation()) != null)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(
                    b -> crossesBoundary(b, b.getRelative(e.getDirection()))))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(
                    b -> crossesBoundary(b, b.getRelative(e.getDirection().getOppositeFace()))))
            e.setCancelled(true);
    }

    private boolean crossesBoundary(Block from, Block to) {
        return plugin.plots().at(from.getLocation()) != plugin.plots().at(to.getLocation());
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        if (e.getTo() == null
                || e.getFrom().getBlockX() == e.getTo().getBlockX()
                        && e.getFrom().getBlockY() == e.getTo().getBlockY()
                        && e.getFrom().getBlockZ() == e.getTo().getBlockZ())
            return;
        Plot p = plugin.plots().at(e.getTo());
        Integer before = currentPlot.get(e.getPlayer().getUniqueId());
        Integer now = p == null ? null : p.id;
        if (!Objects.equals(before, now)) {
            if (now == null)
                currentPlot.remove(e.getPlayer().getUniqueId());
            else
                currentPlot.put(e.getPlayer().getUniqueId(), now);
            if (p != null)
                plugin.showPlot(e.getPlayer(), p);
        }
        if (p != null && e.getPlayer().getUniqueId().equals(p.owner))
            p.lastActive = System.currentTimeMillis();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        plugin.notifyRecovery(e.getPlayer());
        Plot own = plugin.plots().ownedBy(e.getPlayer().getUniqueId());
        if (own != null) {
            own.lastActive = System.currentTimeMillis();
            plugin.plots().save();
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        currentPlot.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void recoveryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CBMallPlugin.RecoveryHolder holder))
            return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)
                || !holder.owner.equals(player.getUniqueId()) || e.getCurrentItem() == null)
            return;
        if (e.getRawSlot() == CBMallPlugin.RecoveryHolder.PREVIOUS_PAGE_SLOT && holder.page > 0) {
            plugin.openRecovery(player, holder.page - 1);
            return;
        }
        if (e.getRawSlot() == CBMallPlugin.RecoveryHolder.NEXT_PAGE_SLOT) {
            plugin.openRecovery(player, holder.page + 1);
            return;
        }
        if (e.getRawSlot() >= CBMallPlugin.RecoveryHolder.ITEMS_PER_PAGE)
            return;
        ItemStack clicked = e.getCurrentItem().clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(clicked);
        int given = clicked.getAmount()
                - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (given <= 0)
            return;
        List<ItemStack> stored = plugin.plots().recovery(player.getUniqueId());
        int remaining = given;
        for (Iterator<ItemStack> it = stored.iterator(); it.hasNext() && remaining > 0;) {
            ItemStack item = it.next();
            if (!item.isSimilar(clicked))
                continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() == 0)
                it.remove();
        }
        plugin.plots().save();
        plugin.openRecovery(player, holder.page);
    }
}
