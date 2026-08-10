package dev.controlbro.cbmall;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class Plot {
    public final int id;
    public final String world;
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public UUID owner;
    public final Set<UUID> members = new HashSet<>();
    public final Set<String> unlockedContainers = new HashSet<>();
    public long lastActive;
    public final Map<String, BlockSnapshot> original = new HashMap<>();

    public Plot(int id, Location firstCorner, Location secondCorner) {
        this.id = id;
        world = Objects.requireNonNull(firstCorner.getWorld()).getName();
        minX = Math.min(firstCorner.getBlockX(), secondCorner.getBlockX());
        maxX = Math.max(firstCorner.getBlockX(), secondCorner.getBlockX());
        minY = Math.min(firstCorner.getBlockY(), secondCorner.getBlockY());
        maxY = Math.max(firstCorner.getBlockY(), secondCorner.getBlockY());
        minZ = Math.min(firstCorner.getBlockZ(), secondCorner.getBlockZ());
        maxZ = Math.max(firstCorner.getBlockZ(), secondCorner.getBlockZ());
    }

    public Plot(int id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.id = id;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public boolean contains(Location location) {
        return location.getWorld() != null && location.getWorld().getName().equals(world)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public boolean mayBuild(UUID uuid, boolean admin) {
        return admin || uuid.equals(owner) || members.contains(uuid);
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
