package org.queercraft.qCAprilFools2026;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MannequinManager {
    private final Plugin plugin;

    private final Map<UUID, SpookyMannequin> mannequins = new ConcurrentHashMap<>();

    private BukkitTask task;

    public MannequinManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 2L);
    }

    public void register(UUID mannequin, UUID target, UUID driver) {
        mannequins.put(mannequin, new SpookyMannequin(mannequin, target, driver));
    }

    public void tick() {

        Iterator<Map.Entry<UUID, SpookyMannequin>> it = mannequins.entrySet().iterator();

        while (it.hasNext()) {

            Map.Entry<UUID, SpookyMannequin> entry = it.next();
            UUID entityId = entry.getKey();
            SpookyMannequin spookyMannequin = entry.getValue();

            Entity entity = Bukkit.getEntity(entityId);
            Player target = Bukkit.getPlayer(spookyMannequin.targetId);
            Zombie driver = (Zombie) Bukkit.getEntity(spookyMannequin.driverId);
            //plugin.getLogger().info("Ticking mannequin " + entityId);

            long now = System.currentTimeMillis();

            // Cleanup invalid entities
            if (entity == null || target == null || target.isDead() || driver == null || driver.isDead()) {
                spookyMannequin.missingTicks++;

                // Allow ~1 second recovery (10 ticks at 2L interval)
                if (spookyMannequin.missingTicks < 10) {
                    continue;
                }

                //plugin.getLogger().info("Entity missing too long, cleaning up");

                if (entity != null) {
                    //plugin.getLogger().info("Mannequin is alive but driver or target are gone, removing mannequin");
                    entity.remove();
                } else {
                    //plugin.getLogger().info("Mannequin became null somehow, cleaning up");
                    if (driver != null) {
                        driver.remove();
                    }
                }
                if (driver == null || driver.isDead()) {
                    if (entity != null) {
                        //plugin.getLogger().info("Mannequin is alive but driver is gone, removing mannequin");
                        entity.remove();
                    }
                }
                if ((entity == null && driver != null) || (target == null || target.isDead())) {
                    //plugin.getLogger().info("Driver is alive but mannequin or target are gone, removing driver");
                    driver.remove();
                }
                //plugin.getLogger().info("Removing map entry");
                it.remove();
                continue;
            }

            driver.setTarget(target);

            if (now - spookyMannequin.spawnTime > 120000 && !spookyMannequin.getDeathRattle()) {
                //plugin.getLogger().info("Mannequin TTL ran out, removing mannequin");
                if (entity != null) {
                    entity.getWorld().playSound(entity.getLocation(),
                            Sound.ENTITY_ENDERMAN_DEATH, 0.5f, 1.5f);
                    spookyMannequin.setDeathRattle(true);
                    entity.remove();
                }
                it.remove();
                driver.remove();
                continue;
            }

            if (plugin.getConfig().getBoolean("features.weepingAngel", true)) {
                boolean beingLookedAt = isPlayerLookingAt(target, entity);

                if (beingLookedAt && !spookyMannequin.frozen) {
                    driver.setAI(false);
                    driver.setVelocity(new Vector(0, 0, 0));
                    spookyMannequin.frozen = true;
                }

                if (!beingLookedAt && spookyMannequin.frozen) {
                    driver.setAI(true);
                    spookyMannequin.frozen = false;
                }
            }

            Location entityLocation = entity.getLocation();
            Location targetLocation = target.getLocation();

            Location dLoc = driver.getLocation();
            Location mLoc = dLoc.clone();
            entity.teleport(mLoc);

            double distance;

            World entityWorld = entity.getWorld();
            World targetWorld = target.getWorld();

            if (!isAllowedWorld(targetWorld)) {
                //plugin.getLogger().info("Target is in unsupported world "+ targetWorld +", pausing mannequin");
                driver.setTarget(null);
                continue;
            } else if (!entityWorld.equals(targetWorld)) {
                //plugin.getLogger().info("Target is in supported world "+ targetWorld +", teleporting mannequin");
                //plugin.getLogger().info(isAllowedWorld(targetWorld)+"");
                distance = 999;
            } else {
                distance = entityLocation.distance(targetLocation);
            }

            if (distance > 200) {
                if (!isAllowedWorld(targetWorld)) {
                    continue;
                }
                //plugin.getLogger().info("Mannequin is more than 200 blocks from target, teleporting");
                Location newLoc = getSafeSpawnLocation(target);
                //plugin.getLogger().info("Teleporting to new location: " + newLoc);
                entity.getLocation().getChunk().load();
                newLoc.getChunk().load();
                driver.teleport(newLoc);
                entity.teleport(newLoc);

                driver.setTarget(target);
                continue;
            }

            lookAt(entity, targetLocation);

            if (distance < 2.5) {
                tryAttack(entity, target, spookyMannequin);
            }
        }
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

    private boolean isPlayerLookingAt(Player player, Entity entity) {

        if (!player.hasLineOfSight(entity)) {
            return false;
        }

        Location eye = player.getEyeLocation();
        Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector()).normalize();

        double dot = eye.getDirection().dot(toEntity);

        // 1.0 = directly looking
        // ~0.98 = tight cone
        // ~0.95 = wider cone
        return dot > 0.97;
    }

    private boolean isAllowedWorld(World world) {
        String name = world.getName();

        return name.equalsIgnoreCase("world")
                || name.equalsIgnoreCase("world_nether");
    }

    private void lookAt(Entity entity, Location target) {
        Location loc = entity.getLocation();

        Vector dir = target.toVector().subtract(loc.toVector());
        loc.setDirection(dir);

        entity.teleport(loc);
    }

    private void tryAttack(Entity entity, Player target, SpookyMannequin spookyMannequin) {

        long now = System.currentTimeMillis();

        // ~0.6s cooldown (player-like)
        if (now - spookyMannequin.lastAttack < 600) return;

        spookyMannequin.lastAttack = now;

        // Swing animation
        if (entity instanceof LivingEntity le) {
            le.swingMainHand();
        }

        // Delay damage slightly (feels like real hit timing)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!target.isOnline() || target.isDead()) return;
            if (entity.isDead()) return;

            if (entity.getLocation().distance(target.getLocation()) <= 3) {

                double damage = 6.0;

                // 10% crit chance
                if (Math.random() < 0.1) {
                    damage = 9.0;
                    target.getWorld().playSound(target.getLocation(),
                            Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                }

                target.damage(damage, entity);

                // Knockback
                Vector kb = target.getLocation().toVector()
                        .subtract(entity.getLocation().toVector())
                        .normalize()
                        .multiply(0.4);

                target.setVelocity(target.getVelocity().add(kb));
            }

        }, 6L); // ~0.3s delay
    }
}
