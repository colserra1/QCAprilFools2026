package org.queercraft.qCAprilFools2026;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Objects;
import java.util.logging.Logger;

public final class QCAprilFools2026 extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        BukkitScheduler scheduler = Bukkit.getScheduler();
        Logger logger = getLogger();

        saveDefaultConfig();
        FileConfiguration config = getConfig();

        logger.info("Enabling QCAprilFools2026...");
        MannequinManager manager = new MannequinManager(this);
        manager.start();

        Objects.requireNonNull(getCommand("spawnmannequins"))
                .setExecutor(new SpawnMannequinsCommand(this, scheduler, manager, logger));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("QCAprilFools2026 disabled!");
    }
}
