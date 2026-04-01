package org.queercraft.qCAprilFools2026;

import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DriverDamageListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Zombie zombie)) return;

        if (zombie.getScoreboardTags().contains("mannequin_driver")) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }
}
