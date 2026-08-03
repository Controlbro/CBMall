package dev.controlbro.cbmall;

import java.util.List;
import org.bukkit.inventory.ItemStack;

public record BlockSnapshot(String material, String blockData, List<ItemStack> inventory) {
}
