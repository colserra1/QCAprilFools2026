package org.queercraft.qCAprilFools2026;

import java.util.UUID;

public class SpookyMannequin {
    public final UUID entityId;
    public final UUID targetId;
    public final UUID driverId;

    public int missingTicks = 0;
    public long lastAttack = 0;
    public final long spawnTime;
    public boolean deathRattle = false;
    public boolean frozen = false;

    public SpookyMannequin(UUID entityId, UUID targetId, UUID driverId) {
        this.entityId = entityId;
        this.targetId = targetId;
        this.driverId = driverId;
        this.spawnTime = System.currentTimeMillis();
    }

    public void setDeathRattle(boolean deathRattle) {
        this.deathRattle = deathRattle;
    }

    public boolean getDeathRattle() {
        return deathRattle;
    }
}
