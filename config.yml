package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Central death handler.
 *
 * On player death:
 *  1. All 30 bots die.
 *  2. Animal companion dies (respawns on next login).
 *  3. If /givebots was pre-staged → inheritance transfer.
 *  4. If death qualifies as Martyr → grave built, title updated.
 *  5. Killer's nation receives all land.
 *  6. Killer receives trophy head.
 *  7. Server broadcast.
 */
public class PlayerDeathListener implements Listener {

    private final NationsSMP plugin;

    /** uuid → nation name they staged /givebots to */
    private final Map<String, String> pendingInheritance = new HashMap<>();

    public PlayerDeathListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    // ── Stage inheritance ─────────────────────────────────────────────────────

    public void stageInheritance(Player player, String targetNationName) {
        pendingInheritance.put(player.getUniqueId().toString(), targetNationName);
        player.sendMessage(ChatColor.YELLOW + "📜 Inheritance staged → "
            + ChatColor.GOLD + targetNationName
            + ChatColor.YELLOW + ". If you die, all your bots and resources transfer there.");
    }

    // ── Player death ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Nation deadNation = plugin.getNationManager().getNation(dead.getUniqueId());
        if (deadNation == null || !deadNation.isSetupComplete()) return;

        // 1. Kill all bots
        plugin.getBotManager().despawnBotsForPlayer(dead.getUniqueId());
        deadNation.setBotCount(0);

        // 2. Kill companion
        plugin.getAnimalCompanionManager().despawnCompanion(dead.getUniqueId());

        // Determine killer
        Player killer = getKiller(dead);

        // 3. Inheritance
        String inheritTarget = pendingInheritance.remove(dead.getUniqueId().toString());
        if (inheritTarget != null) {
            Nation targetNation = plugin.getNationManager().getNationByName(inheritTarget);
            Player targetPlayer = targetNation != null ? Bukkit.getPlayer(
                UUID.fromString(targetNation.getPlayerUUID())) : null;
            if (targetNation != null && targetPlayer != null) {
                plugin.getNationManager().transferEverything(deadNation, targetNation, targetPlayer);
                // Transfer inventory items
                for (ItemStack item : dead.getInventory().getContents()) {
                    if (item != null) targetPlayer.getInventory().addItem(item);
                }
                event.getDrops().clear(); // prevent normal drops
            }
        }

        // 4. Martyr check
        if (killer != null) {
            boolean isMartyr = plugin.getOathManager().isMartyrDeath(dead, killer);
            if (isMartyr) {
                deadNation.applyMartyrTitle();
                plugin.getNationManager().saveNation(deadNation);
                plugin.getMartyrManager().buildMartyrGrave(dead, killer, deadNation);
            }

            // 5. Land transfer to killer
            plugin.getLandManager().transferLandOnKill(dead, killer);

            // 6. Trophy head for killer
            plugin.getTrophyManager().awardTrophyHead(killer, deadNation);

            // 7. Betrayal check — did the killer attack someone they had an oath with?
            plugin.getOathManager().checkForBetrayal(killer, dead);

            // 8. Death broadcast
            broadcastDeath(dead, killer, deadNation);
        }

        // 9. Reset bot count in data
        plugin.getNationManager().saveNation(deadNation);
    }

    // ── Respawn – restore companion ───────────────────────────────────────────

    @org.bukkit.event.EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Nation n = plugin.getNationManager().getNation(player.getUniqueId());
            if (n == null || !n.isSetupComplete()) return;
            // Bots do NOT respawn automatically — player must rebuild army
            plugin.getAnimalCompanionManager().respawnCompanion(player);
            player.sendMessage(ChatColor.YELLOW
                + "Your nation lives on but your army has fallen. "
                + "Your companion has returned.");
        }, 60L);
    }

    // ── Entity damage – oath betrayal + Glitch protection ────────────────────

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Glitch is invulnerable
        if (event.getEntity() instanceof LivingEntity le
                && plugin.getGlitchManager().isGlitch(le)) {
            event.setCancelled(true);
            return;
        }

        // Player hitting player
        if (event.getEntity() instanceof Player victim
                && event.getDamager() instanceof Player attacker) {
            plugin.getOathManager().checkForBetrayal(attacker, victim);
            plugin.getLegendaryItemManager().enforceItemRestrictions(attacker);
        }

        // Obsidian sword hitting dragon
        if (event.getEntity() instanceof EnderDragon dragon
                && event.getDamager() instanceof Player swordWielder) {
            ItemStack hand = swordWielder.getInventory().getItemInMainHand();
            if (plugin.getLegendaryItemManager().isObsidianSword(hand)) {
                event.setCancelled(true);
                plugin.getDragonManager().onObsidianSwordHitDragon(swordWielder, dragon);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Player getKiller(Player dead) {
        var cause = dead.getLastDamageCause();
        if (cause instanceof EntityDamageByEntityEvent edbe) {
            if (edbe.getDamager() instanceof Player p) return p;
            if (edbe.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    private void broadcastDeath(Player dead, Player killer, Nation deadNation) {
        NationAnimal deadAnimal   = NationAnimal.byKey(deadNation.getAnimalKey());
        Nation killerNation       = plugin.getNationManager().getNation(killer.getUniqueId());
        NationAnimal killerAnimal = killerNation != null
            ? NationAnimal.byKey(killerNation.getAnimalKey()) : null;

        String killerStr = killerAnimal != null
            ? killerAnimal.getEmoji() + " " + killer.getName() + " OF " + killerNation.getNationName().toUpperCase()
            : killer.getName();

        Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ " + deadAnimal.getEmoji() + " "
            + dead.getName() + " OF " + deadNation.getNationName().toUpperCase()
            + " HAS FALLEN to " + killerStr + "!");
    }
}
