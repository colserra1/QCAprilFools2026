package org.queercraft.qCAprilFools2026;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class SpawnMannequinsCommand extends SafeCommandExecutor {
    private final JavaPlugin plugin;
    private final BukkitScheduler scheduler;
    private final MannequinManager manager;

    public SpawnMannequinsCommand(JavaPlugin plugin, BukkitScheduler scheduler, MannequinManager manager, Logger logger) {
        super(logger);
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.manager = manager;
    }

    @Override
    protected boolean execute(CommandSender sender, Command command, String label, String[] args) {
        command(sender, command, label, args);
        return true;
    }

    public void command(CommandSender sender, Command cmd, String label, String[] args) {
        String arg = args.length > 0 ? args[0].toLowerCase() : "";
        switch (arg) {
            case "":
                handleSpawnCommand(sender);
                break;
            case "reload":
                handleReloadCommand(sender);
                break;
            default:
                break;
        }
    }

    public void handleSpawnCommand(CommandSender sender) {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String targetWorld = player.getWorld().getName();
                if (!(targetWorld.equalsIgnoreCase("world") || targetWorld.equalsIgnoreCase("world_nether"))) {
                    //logger.info("Target is in unsupported world, not spawning mannequin");
                    //logger.info("World: " + targetWorld +
                    //        " Environment: " + player.getWorld().getEnvironment());
                    continue;
                }
                Location spawnLoc = getSafeSpawnLocation(player);

                Mannequin mannequin = (Mannequin) player.getWorld()
                        .spawnEntity(spawnLoc, EntityType.MANNEQUIN);

                mannequin.addScoreboardTag("hostile_mannequin");

                Zombie driver = player.getWorld().spawn(spawnLoc, Zombie.class);
                driver.setInvisible(true);
                driver.setSilent(true);
                driver.setCollidable(false);
                driver.setAI(true);
                driver.setBaby();
                driver.setShouldBurnInDay(false);
                driver.setTarget(player);
                driver.setInvulnerable(true);
                driver.addScoreboardTag("mannequin_driver");
                driver.getEquipment().clear();
                driver.setCanPickupItems(false);
                //driver.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
                driver.setCollidable(false);

                // Give sword
                mannequin.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

                // Set skin to random other player or target if only player online
                Player skinSource = getRandomSkinPlayer(player);
                mannequin.setProfile(ResolvableProfile.resolvableProfile(skinSource.getPlayerProfile()));
                mannequin.setCustomName(skinSource.getName());
                mannequin.setCustomNameVisible(true);

                manager.register(mannequin.getUniqueId(), player.getUniqueId(), driver.getUniqueId());
            }

            sender.sendMessage("Spawned mannequins for all players.");
        } catch (Exception e) {
            logger.severe("An unexpected error occurred while spawning mannequins.:");
            logger.severe("Exception type: " + e.getClass().getName());
            logger.severe("Message: " + e.getMessage());
            for (StackTraceElement stackTraceLine : e.getStackTrace()) {
                logger.severe("    at " + stackTraceLine);
            }
            sender.sendMessage("§cAn unexpected error occurred");
        }
    }

    public void handleReloadCommand(CommandSender sender) {
        if (sender.hasPermission("timeplayed.reload")) {
            plugin.reloadConfig();
            sender.sendMessage("The config has been reloaded.");
        } else {
            sender.sendMessage("You do not have permission to reload the config.");
        }
    }

    private Player getRandomSkinPlayer(Player target) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (players.isEmpty()) return target;

        if (players.size() == 1) {
            return target;
        }

        // Remove target from pool
        players.remove(target);

        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    private Location getSafeSpawnLocation(Player player) {

        Location base = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 10; attempt++) {

            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double distance = ThreadLocalRandom.current().nextDouble(plugin.getConfig().getInt("settings.spawnDistance"), plugin.getConfig().getInt("settings.spawnDistance") + 10);

            Location loc = base.clone().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );

            loc.getChunk().load();
            int highestY = world.getHighestBlockYAt(loc);
            loc.setY(highestY);

            Location safe = findSafeGround(loc);

            if (safe != null) {
                return safe;
            }
        }

        // fallback: just return player location if nothing found
        return player.getLocation();
    }

    private Location findSafeGround(Location loc) {

        World world = loc.getWorld();

        for (int y = loc.getBlockY(); y > world.getMinHeight(); y--) {

            Location ground = new Location(world, loc.getX(), y, loc.getZ());
            Material block = ground.getBlock().getType();

            // Must be solid ground
            if (!block.isSolid()) continue;

            // Reject bad surfaces
            if (block == Material.WATER || block == Material.LAVA) continue;

            // Check space above (2 blocks for entity)
            Material above = ground.clone().add(0, 1, 0).getBlock().getType();
            Material above2 = ground.clone().add(0, 2, 0).getBlock().getType();

            if (!above.isAir() || !above2.isAir()) continue;

            // Found valid spot
            return ground.add(0.5, 1, 0.5);
        }

        return null;
    }
}
