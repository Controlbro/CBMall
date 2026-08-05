package dev.controlbro.cbmall;

import java.time.Duration;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class CBMallPlugin extends JavaPlugin {
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private PlotManager plots;
    private NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();
    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "selection_wand");
        plots = new PlotManager(this);
        MallCommands commands = new MallCommands(this);
        for (String name : List.of("mall", "malladmin", "mallclaim")) {
            Objects.requireNonNull(getCommand(name)).setExecutor(commands);
            Objects.requireNonNull(getCommand(name)).setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(new MallListener(this), this);
        getServer().getScheduler().runTaskTimer(this, this::drawSelections, 10, 10);
        long interval = Math.max(1, getConfig().getLong("inactivity.check-interval-minutes", 60))
                * TICKS_PER_MINUTE;
        getServer().getScheduler().runTaskTimer(this, this::expireInactive, interval, interval);
    }

    @Override
    public void onDisable() {
        if (plots != null)
            plots.save();
    }

    public PlotManager plots() {
        return plots;
    }

    public Map<UUID, Selection> selections() {
        return selections;
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    public String prefix() {
        return getConfig().getString("messages.prefix", "&6[CBMall] &r");
    }

    public String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public void showPlot(Player player, Plot p) {
        if (p.owner != null) {
            String owner = Optional.ofNullable(Bukkit.getOfflinePlayer(p.owner).getName())
                    .orElse("Unknown");
            String message = getConfig()
                                     .getString("messages.claimed-subtitle", "&7Owner: &f%owner%")
                                     .replace("%owner%", owner);
            player.sendActionBar(Component.text(color(message)));
            return;
        }

        String title = getConfig().getString("messages.unclaimed-title", "&6Shop available");
        String sub = getConfig()
                             .getString("messages.unclaimed-subtitle",
                                     "&fUse &e/mall claim %plot% &fto claim this plot")
                             .replace("%plot%", String.valueOf(p.id));
        player.showTitle(Title.title(Component.text(color(title)), Component.text(color(sub)),
                Title.Times.times(
                        Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    public void showBorder(Player player, Plot plot) {
        if (plot.getWorld() == null) {
            player.sendMessage(color(prefix() + "&cPlot world is not loaded."));
            return;
        }
        for (int i = 0; i < 6; i++) {
            getServer().getScheduler().runTaskLater(
                    this, () -> drawPlotBorder(player, plot), i * 10L);
        }
    }

    public void openRecovery(Player p) {
        openRecovery(p, 0);
    }

    public void openRecovery(Player p, int page) {
        List<ItemStack> items = plots.recovery(p.getUniqueId());
        if (items.isEmpty()) {
            p.closeInventory();
            p.sendMessage(color(prefix() + "&7You have no recovered items."));
            return;
        }
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) RecoveryHolder.ITEMS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        RecoveryHolder holder = new RecoveryHolder(p.getUniqueId(), safePage);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Recovered mall items (" + (safePage + 1) + "/" + totalPages + ")"));
        holder.inventory = inv;
        items.stream()
                .skip((long) safePage * RecoveryHolder.ITEMS_PER_PAGE)
                .limit(RecoveryHolder.ITEMS_PER_PAGE)
                .map(ItemStack::clone)
                .forEach(inv::addItem);
        if (safePage > 0)
            inv.setItem(RecoveryHolder.PREVIOUS_PAGE_SLOT, pageButton(Material.ARROW, "Previous page"));
        if (safePage < totalPages - 1)
            inv.setItem(RecoveryHolder.NEXT_PAGE_SLOT, pageButton(Material.ARROW, "Next page"));
        p.openInventory(inv);
    }

    private ItemStack pageButton(Material material, String name) {
        ItemStack button = new ItemStack(material);
        var meta = button.getItemMeta();
        meta.displayName(Component.text(name));
        button.setItemMeta(meta);
        return button;
    }

    public void notifyRecovery(Player p) {
        if (plots.hasRecovery(p.getUniqueId()))
            p.sendMessage(color(prefix()
                    + ("&eYour inactive shop was reset. Use &6/mallclaim &eto retrieve your "
                            + "items.")));
    }

    private void expireInactive() {
        if (!getConfig().getBoolean("inactivity.enabled", true))
            return;
        long cutoff = System.currentTimeMillis()
                - getConfig().getLong("inactivity.days", 30) * 86_400_000L;
        for (Plot p : new ArrayList<>(plots.all()))
            if (p.owner != null && p.lastActive > 0 && p.lastActive < cutoff) {
                getLogger().info("Resetting inactive plot " + p.id);
                plots.reset(p, getConfig().getBoolean("inactivity.return-items", true));
            }
    }

    private void drawSelections() {
        for (var entry : selections.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            Selection s = entry.getValue();
            if (p == null || !s.complete())
                continue;
            drawBorder(p, s.first.getWorld(), s.first.getBlockX(), s.first.getBlockY(),
                    s.first.getBlockZ(), s.second.getBlockX(), s.second.getBlockY(),
                    s.second.getBlockZ());
        }
    }

    private void drawPlotBorder(Player player, Plot plot) {
        World world = plot.getWorld();
        if (world == null || !world.equals(player.getWorld()))
            return;
        drawBorder(player, world, plot.minX, plot.minY, plot.minZ, plot.maxX, plot.maxY,
                plot.maxZ);
    }

    private void drawBorder(Player p, World world, int firstX, int firstY, int firstZ, int secondX,
            int secondY, int secondZ) {
        if (!world.equals(p.getWorld()))
            return;
        double minX = Math.min(firstX, secondX), maxX = Math.max(firstX, secondX) + 1,
               minY = Math.min(firstY, secondY), maxY = Math.max(firstY, secondY) + 1,
               minZ = Math.min(firstZ, secondZ), maxZ = Math.max(firstZ, secondZ) + 1;
        for (double t = 0; t <= 1; t += 0.08) {
            particle(p, minX + (maxX - minX) * t, minY, minZ);
            particle(p, minX + (maxX - minX) * t, maxY, minZ);
            particle(p, minX + (maxX - minX) * t, minY, maxZ);
            particle(p, minX + (maxX - minX) * t, maxY, maxZ);
            particle(p, minX, minY + (maxY - minY) * t, minZ);
            particle(p, maxX, minY + (maxY - minY) * t, minZ);
            particle(p, minX, minY + (maxY - minY) * t, maxZ);
            particle(p, maxX, minY + (maxY - minY) * t, maxZ);
            particle(p, minX, minY, minZ + (maxZ - minZ) * t);
            particle(p, maxX, minY, minZ + (maxZ - minZ) * t);
            particle(p, minX, maxY, minZ + (maxZ - minZ) * t);
            particle(p, maxX, maxY, minZ + (maxZ - minZ) * t);
        }
    }

    private void particle(Player p, double x, double y, double z) {
        p.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    public static final class RecoveryHolder implements InventoryHolder {
        static final int ITEMS_PER_PAGE = 45;
        static final int PREVIOUS_PAGE_SLOT = 45;
        static final int NEXT_PAGE_SLOT = 53;
        final UUID owner;
        final int page;
        Inventory inventory;
        RecoveryHolder(UUID owner, int page) {
            this.owner = owner;
            this.page = page;
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
