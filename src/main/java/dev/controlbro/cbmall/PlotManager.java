package dev.controlbro.cbmall;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class PlotManager {
    private final CBMallPlugin plugin;
    private final File file;
    private final Map<Integer, Plot> plots = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> recovery = new HashMap<>();
    private int nextId = 1;

    public PlotManager(CBMallPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public Collection<Plot> all() {
        return Collections.unmodifiableCollection(plots.values());
    }

    public Plot get(int id) {
        return plots.get(id);
    }

    public Plot at(Location location) {
        return plots.values().stream().filter(p -> p.contains(location)).findFirst().orElse(null);
    }

    public Plot ownedBy(UUID owner) {
        return plots.values().stream().filter(p -> owner.equals(p.owner)).findFirst().orElse(null);
    }

    public List<ItemStack> recovery(UUID player) {
        return recovery.computeIfAbsent(player, ignored -> new ArrayList<>());
    }

    public boolean hasRecovery(UUID player) {
        return !recovery(player).isEmpty();
    }

    public Plot create(Location a, Location b) {
        Plot plot = new Plot(nextId++, a, b);
        for (Plot other : plots.values()) {
            if (overlaps(plot, other))
                throw new IllegalArgumentException(
                        "That selection overlaps plot " + other.id + ".");
        }
        capture(plot);
        plots.put(plot.id, plot);
        save();
        return plot;
    }

    private boolean overlaps(Plot a, Plot b) {
        return a.world.equals(b.world) && a.minX <= b.maxX && a.maxX >= b.minX && a.minY <= b.maxY
                && a.maxY >= b.minY && a.minZ <= b.maxZ && a.maxZ >= b.minZ;
    }

    private void capture(Plot p) {
        World world = Objects.requireNonNull(p.getWorld(), "Selection world is not loaded");
        for (int x = p.minX; x <= p.maxX; x++)
            for (int y = p.minY; y <= p.maxY; y++)
                for (int z = p.minZ; z <= p.maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    List<ItemStack> items = new ArrayList<>();
                    if (block.getState() instanceof InventoryHolder holder)
                        for (ItemStack item : holder.getInventory().getContents())
                            if (item != null)
                                items.add(item.clone());
                    p.original.put(p.key(x, y, z),
                            new BlockSnapshot(block.getType().name(),
                                    block.getBlockData().getAsString(), items));
                }
    }

    public void remove(Plot p) {
        plots.remove(p.id);
        save();
    }

    public void assign(Plot p, UUID owner) {
        p.owner = owner;
        p.members.clear();
        p.unlockedContainers.clear();
        p.lastActive = System.currentTimeMillis();
        save();
    }

    public void reset(Plot p, boolean returnItems) {
        UUID formerOwner = p.owner;
        World world = p.getWorld();
        if (world == null)
            throw new IllegalStateException("World " + p.world + " is not loaded.");
        List<ItemStack> returned = formerOwner == null ? new ArrayList<>() : recovery(formerOwner);
        for (int x = p.minX; x <= p.maxX; x++)
            for (int y = p.minY; y <= p.maxY; y++)
                for (int z = p.minZ; z <= p.maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    BlockSnapshot old = p.original.get(p.key(x, y, z));
                    if (old == null)
                        continue;
                    if (returnItems && formerOwner != null) {
                        if (block.getState() instanceof InventoryHolder holder)
                            returned.addAll(inventoryDifference(holder, old.inventory()));
                        if (!block.getType().isAir() && block.getType().isItem()
                                && (!block.getType().name().equals(old.material())
                                        || !block.getBlockData().getAsString().equals(
                                                old.blockData())))
                            returned.add(new ItemStack(block.getType()));
                    }
                    if (block.getState() instanceof InventoryHolder holder)
                        holder.getInventory().clear();
                    block.setType(Material.valueOf(old.material()), false);
                    block.setBlockData(Bukkit.createBlockData(old.blockData()), false);
                    BlockState state = block.getState();
                    if (state instanceof InventoryHolder holder) {
                        holder.getInventory().clear();
                        holder.getInventory().addItem(old.inventory()
                                        .stream()
                                        .map(ItemStack::clone)
                                        .toArray(ItemStack[] ::new));
                        state.update(true, false);
                    }
                }
        p.owner = null;
        p.members.clear();
        p.unlockedContainers.clear();
        p.lastActive = 0;
        save();
    }

    private List<ItemStack> inventoryDifference(InventoryHolder holder, List<ItemStack> originals) {
        // Only return items added by the tenant. Returning the original contents too would let a
        // player duplicate items that are restored to the shop a few lines above.
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : holder.getInventory().getContents())
            if (item != null)
                result.add(item.clone());
        for (ItemStack original : originals) {
            int remaining = original.getAmount();
            for (Iterator<ItemStack> it = result.iterator(); it.hasNext() && remaining > 0;) {
                ItemStack candidate = it.next();
                if (!candidate.isSimilar(original))
                    continue;
                int used = Math.min(remaining, candidate.getAmount());
                remaining -= used;
                candidate.setAmount(candidate.getAmount() - used);
                if (candidate.getAmount() == 0)
                    it.remove();
            }
        }
        return result;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Plot p : plots.values()) {
            String base = "plots." + p.id;
            y.set(base + ".world", p.world);
            y.set(base + ".bounds", List.of(p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ));
            y.set(base + ".owner", p.owner == null ? null : p.owner.toString());
            y.set(base + ".members", p.members.stream().map(UUID::toString).toList());
            y.set(base + ".unlocked-containers", new ArrayList<>(p.unlockedContainers));
            y.set(base + ".last-active", p.lastActive);
            for (var e : p.original.entrySet()) {
                String s = base + ".original." + e.getKey();
                y.set(s + ".material", e.getValue().material());
                y.set(s + ".data", e.getValue().blockData());
                y.set(s + ".inventory", e.getValue().inventory());
            }
        }
        recovery.forEach((uuid, items) -> y.set("recovery." + uuid, items));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save data.yml", e);
        }
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next-id", 1);
        ConfigurationSection root = y.getConfigurationSection("plots");
        if (root != null)
            for (String key : root.getKeys(false)) {
                String b = "plots." + key;
                List<Integer> n = y.getIntegerList(b + ".bounds");
                if (n.size() != 6)
                    continue;
                Plot p = new Plot(Integer.parseInt(key), y.getString(b + ".world"), n.get(0),
                        n.get(1), n.get(2), n.get(3), n.get(4), n.get(5));
                String owner = y.getString(b + ".owner");
                if (owner != null)
                    p.owner = UUID.fromString(owner);
                for (String u : y.getStringList(b + ".members")) p.members.add(UUID.fromString(u));
                p.unlockedContainers.addAll(y.getStringList(b + ".unlocked-containers"));
                p.lastActive = y.getLong(b + ".last-active");
                ConfigurationSection o = y.getConfigurationSection(b + ".original");
                if (o != null)
                    for (String pos : o.getKeys(false)) {
                        String q = b + ".original." + pos;
                        List<ItemStack> inv = new ArrayList<>();
                        for (Object value : y.getList(q + ".inventory", List.of()))
                            if (value instanceof ItemStack item)
                                inv.add(item);
                        p.original.put(pos,
                                new BlockSnapshot(y.getString(q + ".material"),
                                        y.getString(q + ".data"), inv));
                    }
                plots.put(p.id, p);
            }
        ConfigurationSection r = y.getConfigurationSection("recovery");
        if (r != null)
            for (String key : r.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                for (Object value : y.getList("recovery." + key, List.of()))
                    if (value instanceof ItemStack item)
                        items.add(item);
                recovery.put(UUID.fromString(key), items);
            }
    }
}
