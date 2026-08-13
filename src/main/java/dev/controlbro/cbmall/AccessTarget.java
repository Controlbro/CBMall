package dev.controlbro.cbmall;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.inventory.DoubleChestInventory;

final class AccessTarget {
    private AccessTarget() {}

    static boolean isUnlockable(Block block) {
        BlockData data = block.getBlockData();
        return block.getState() instanceof Container
                || data instanceof Door
                || data instanceof TrapDoor
                || data instanceof Gate;
    }

    static Set<String> keys(Plot plot, Block block) {
        Set<String> keys = new LinkedHashSet<>();
        add(keys, plot, block);

        if (block.getBlockData() instanceof Door door) {
            int otherHalfY = door.getHalf() == org.bukkit.block.data.Bisected.Half.TOP ? -1 : 1;
            add(keys, plot, block.getRelative(0, otherHalfY, 0));
        } else if (block.getState() instanceof Chest chest
                && chest.getInventory() instanceof DoubleChestInventory inventory
                && inventory.getHolder() instanceof DoubleChest doubleChest) {
            if (doubleChest.getLeftSide() instanceof Chest left)
                add(keys, plot, left.getBlock());
            if (doubleChest.getRightSide() instanceof Chest right)
                add(keys, plot, right.getBlock());
        }
        return keys;
    }

    static boolean isUnlocked(Plot plot, Block block) {
        return isUnlockable(block)
                && keys(plot, block).stream().anyMatch(plot.unlockedContainers::contains);
    }

    private static void add(Set<String> keys, Plot plot, Block block) {
        keys.add(plot.key(block.getX(), block.getY(), block.getZ()));
    }
}
