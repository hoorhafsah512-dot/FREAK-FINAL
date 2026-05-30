package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the nation animal companion for each player.
 * Uses vanilla Bukkit entities — no Citizens dependency.
 */
public class AnimalCompanionManager {

    private static final String META_COMPANION_OWNER = "companion_owner";
    private static final String META_IS_COMPANION    = "is_nations_companion";

    private final NationsSMP plugin;
    private final NationManager nationManager;

    /** player uuid → companion entity UUID */
    private final Map<String, UUID> companions = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> aiTasks = new ConcurrentHashMap<>();

    public AnimalCompanionManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public void spawnCompanion(Player player, Nation nation) {
        if (nation.getAnimalKey() == null) return;
        despawnCompanion(player.getUniqueId());

        NationAnimal animal = NationAnimal.byKey(nation.getAnimalKey());
        Location loc = player.getLocation().clone().add(1.5, 0, 1.5);

        LivingEntity entity;
        try {
            entity = (LivingEntity) player.getWorld().spawnEntity(loc, animal.getEntityType());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn companion for " + player.getName() + ": " + e.getMessage());
            return;
        }

        String npcName = ChatColor.YELLOW + animal.getEmoji() + " " + nation.getNationName()
            + "'s " + animal.getDisplayName();
        entity.setCustomName(npcName);
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setMetadata(META_IS_COMPANION,    new FixedMetadataValue(plugin, true));
        entity.setMetadata(META_COMPANION_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        applyCompanionStats(entity);

        // Tame wolves if possible
        if (entity instanceof Wolf wolf) {
            wolf.setTamed(true);
            wolf.setOwner(player);
        }

        companions.put(player.getUniqueId().toString(), entity.getUniqueId());
        startCompanionAI(player, entity);
    }

    private void applyCompanionStats(LivingEntity entity) {
        double hp = plugin.getConfig().getDouble("companion-health", 100.0);
        try {
            var attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(hp);
                entity.setHealth(hp);
            }
        } catch (Exception ignored) {}
    }

    private void startCompanionAI(Player player, LivingEntity entity) {
        BukkitTask old = aiTasks.remove(player.getUniqueId().toString());
        if (old != null) old.cancel();

        double dmg = plugin.getConfig().getDouble("companion-damage", 6.0);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }

                Entity current = Bukkit.getEntity(companions.getOrDefault(
                    player.getUniqueId().toString(), new UUID(0, 0)));

                if (current == null || !current.isValid() || current.isDead()) {
                    // Respawn companion
                    cancel();
                    Bukkit.getScheduler().runTaskLater(plugin,
                        () -> respawnCompanion(player), 60L);
                    return;
                }

                if (!(current instanceof LivingEntity companion)) return;

                // Follow player
                double dist = companion.getLocation().distance(player.getLocation());
                if (dist > 50) {
                    companion.teleport(player.getLocation().clone().add(2, 0, 0));
                } else if (dist > 4) {
                    companion.getPathfinder().moveTo(player);
                }

                // Attack nearby hostiles
                for (Entity nearby : companion.getNearbyEntities(8, 8, 8)) {
                    if (nearby instanceof Monster || nearby instanceof Slime) {
                        companion.getPathfinder().moveTo(nearby);
                        ((LivingEntity) nearby).damage(dmg, companion);
                        break;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        aiTasks.put(player.getUniqueId().toString(), task);
    }

    public void despawnCompanion(UUID playerUUID) {
        String uid = playerUUID.toString();
        BukkitTask task = aiTasks.remove(uid);
        if (task != null) task.cancel();
        UUID entityId = companions.remove(uid);
        if (entityId == null) return;
        Entity e = Bukkit.getEntity(entityId);
        if (e != null) e.remove();
    }

    public void respawnCompanion(Player player) {
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n == null || !n.isSetupComplete()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnCompanion(player, n), 40L);
    }

    public boolean isCompanion(Entity entity) {
        return entity.hasMetadata(META_IS_COMPANION);
    }
}
