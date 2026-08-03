package dev.controlbro.cbmall;

import org.bukkit.Location;

public final class Selection {
    public Location first;
    public Location second;
    public boolean complete() {
        return first != null && second != null && first.getWorld().equals(second.getWorld());
    }
}
