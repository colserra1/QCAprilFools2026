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
                if (sender.hasPermission("spawnmannequins")) {
                    handleGlobalSpawnCommand(sender);
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                }
                break;
            case "reload":
                if (sender.hasPermission("spawnmannequins.reload")) {
                    handleReloadCommand(sender);
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                }
                break;
            default:
                if (sender.hasPermission("spawnmannequins")) {
                    handleIndividualSpawnCommand(sender, arg);
                } else {
                    sender.sendMessage("You do not have permission to use this command.");
                }
                break;
        }
    }

    public void handleIndividualSpawnCommand(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            createMannequin(player, sender);
            logger.info("Spawned mannequin for "+player.getName());
            sender.sendMessage("Spawned mannequin for "+player.getName());
        }else{
            sender.sendMessage("§cPlayer not found.");
        }
    }

    public void handleGlobalSpawnCommand(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            createMannequin(player, sender);
        }
        logger.info("Spawned mannequins for all players.");
        sender.sendMessage("Spawned mannequins for all players.");
    }

    public void createMannequin(Player player, CommandSender sender) {
        try {
            String targetWorld = player.getWorld().getName();
            if (targetWorld.equalsIgnoreCase("world") || targetWorld.equalsIgnoreCase("world_nether")) {
                //logger.info("Target is in unsupported world, not spawning mannequin");
                //logger.info("World: " + targetWorld +
                //        " Environment: " + player.getWorld().getEnvironment());
                Location spawnLoc = getSafeSpawnLocation(player);

                Mannequin mannequin = (Mannequin) player.getWorld()
                        .spawnEntity(spawnLoc, EntityType.MANNEQUIN);

                mannequin.addScoreboardTag("hostile_mannequin");

                // Set skin to random other player or target if only player online
                Player skinSource = getRandomSkinPlayer(player);

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

                mannequin.setProfile(ResolvableProfile.resolvableProfile(skinSource.getPlayerProfile()));
                if(plugin.getConfig().getBoolean("features.nametags")){
                    mannequin.setCustomName(skinSource.getName());
                    mannequin.setCustomNameVisible(true);
                }

                manager.register(mannequin.getUniqueId(), player.getUniqueId(), driver.getUniqueId());
            }
        } catch (IllegalArgumentException e) {
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
        if (sender.hasPermission("spawnmannequins.reload")) {
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
        //logger.info("Initial player location: " + base);
        World world = player.getWorld();

        for (int attempt = 0; attempt < 30; attempt++) {

            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int spawnDistance = plugin.getConfig().getInt("settings.spawnDistance");
            double distance = ThreadLocalRandom.current().nextDouble(spawnDistance, spawnDistance +10);
            //logger.info("Config distance: " + spawnDistance);
            //logger.info("Actual distance: " + distance);

            Location loc = base.clone().add(
                    Math.cos(angle) * distance,
                    base.getY(),
                    Math.sin(angle) * distance
            );
            //logger.info("Initial spawn location: " + loc);

            Location safe = findSafeGroundNearY(loc, base.getBlockY());

            if (safe != null) {
                //logger.info("Final spawn location: " + safe);
                return safe;
            }

        }

        // fallback to surface
        //logger.info("No safe spawn location, defaulting to surface");
        return getSurfaceFallback(player);
    }

    private Location findSafeGroundNearY(Location loc, int targetY) {

        World world = loc.getWorld();

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Search up and down around player Y
        for (int offset = 0; offset < 12; offset++) {

            int[] ys = new int[] {
                    targetY + offset,
                    targetY - offset
            };

            for (int y : ys) {

                if (y < minY || y > maxY) continue;

                Location ground = new Location(world, loc.getX(), y, loc.getZ());
                //logger.info("Spawn candidate found: " + ground);
                if (isSafeSpawnBlock(ground)) {
                    return ground.add(0.5, 1, 0.5);
                }
            }
        }

        return null;
    }

    private boolean isSafeSpawnBlock(Location ground) {

        Material block = ground.getBlock().getType();

        // Must be solid
        if (!block.isSolid()) return false;

        // Reject liquids
        if (block == Material.WATER || block == Material.LAVA) return false;

        // Reject annoying/dangerous blocks
        if (block == Material.CACTUS || block == Material.MAGMA_BLOCK) return false;

        // Check space above
        Material above = ground.clone().add(0, 1, 0).getBlock().getType();
        Material above2 = ground.clone().add(0, 2, 0).getBlock().getType();
        //logger.info("Valid candidate found: " + ground);
        return above.isAir() && above2.isAir();
    }

    private Location getSurfaceFallback(Player player) {

        Location base = player.getLocation();
        World world = player.getWorld();

        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double distance = plugin.getConfig().getInt("spawnDistance");

        Location loc = base.clone().add(
                Math.cos(angle) * distance,
                0,
                Math.sin(angle) * distance
        );

        int y = world.getHighestBlockYAt(loc);
        loc.setY(y);

        return loc.add(0.5, 1, 0.5);
    }
}
