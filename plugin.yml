package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Handles player join / quit events.
 */
public class PlayerJoinListener implements Listener {

    private final NationsSMP plugin;

    public PlayerJoinListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());

        if (nation == null) {
            // Brand-new player — start setup
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getNationManager().beginSetup(player), 20L);
            return;
        }

        // Returning player — restore state
        if (nation.isSetupComplete()) {
            restorePlayer(player, nation);
        } else {
            // They logged out mid-setup — restart it
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getNationManager().beginSetup(player), 20L);
        }
    }

    private void restorePlayer(Player player, Nation nation) {
        // Restore name tag
        if (nation.isOathbreaker()) {
            player.setDisplayName(ChatColor.DARK_RED + "[OATHBREAKER] " + player.getName());
            player.setPlayerListName(ChatColor.DARK_RED + "OATHBREAKER " + player.getName());
        } else {
            player.setDisplayName(ChatColor.YELLOW + nation.getNationName()
                + " " + player.getName());
        }

        // Restore companion
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            plugin.getAnimalCompanionManager().spawnCompanion(player, nation), 40L);

        // Restore bots if they had any
        if (nation.getBotCount() > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getBotManager().spawnBotsForPlayer(player, nation), 60L);
        }

        // Enforce legendary item restrictions
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            plugin.getLegendaryItemManager().enforceItemRestrictions(player), 40L);

        player.sendMessage(ChatColor.GOLD + "Welcome back, "
            + com.nationssmp.data.NationAnimal.byKey(nation.getAnimalKey()).getEmoji()
            + " " + nation.getNationName() + "!");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Despawn companion and bots cleanly on quit to avoid orphan NPCs
        plugin.getAnimalCompanionManager().despawnCompanion(player.getUniqueId());
        plugin.getBotManager().despawnBotsForPlayer(player.getUniqueId());
        // Save nation state
        Nation n = plugin.getNationManager().getNation(player.getUniqueId());
        if (n != null) plugin.getNationManager().saveNation(n);
    }
}
