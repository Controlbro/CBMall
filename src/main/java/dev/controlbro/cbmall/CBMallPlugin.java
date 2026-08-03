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

    public void openRecovery(Player p) {
        List<ItemStack> items = plots.recovery(p.getUniqueId());
        if (items.isEmpty()) {
            p.closeInventory();
            p.sendMessage(color(prefix() + "&7You have no recovered items."));
            return;
        }
        RecoveryHolder holder = new RecoveryHolder(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Recovered mall items"));
        holder.inventory = inv;
        items.stream().limit(54).map(ItemStack::clone).forEach(inv::addItem);
        p.openInventory(inv);
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
            Location a = s.first, b = s.second;
            if (!a.getWorld().equals(p.getWorld()))
                continue;
            double minX = Math.min(a.getBlockX(), b.getBlockX()),
                   maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1,
                   minY = Math.min(a.getBlockY(), b.getBlockY()),
                   maxY = Math.max(a.getBlockY(), b.getBlockY()) + 1,
                   minZ = Math.min(a.getBlockZ(), b.getBlockZ()),
                   maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
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
    }

    private void particle(Player p, double x, double y, double z) {
        p.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    public static final class RecoveryHolder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        RecoveryHolder(UUID owner) {
            this.owner = owner;
        }
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
