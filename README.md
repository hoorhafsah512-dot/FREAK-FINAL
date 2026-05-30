package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Detects when the first player enters The End and triggers castle construction.
 */
public class WorldChangeListener implements Listener {

    private final NationsSMP plugin;

    public WorldChangeListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        World to = event.getPlayer().getWorld();
        if (to.getEnvironment() == World.Environment.THE_END) {
            if (!plugin.getDragonManager().isCastleBuilt()) {
                plugin.getDragonManager().ensureCastleBuilt(to);
                org.bukkit.Bukkit.broadcastMessage(
                    org.bukkit.ChatColor.DARK_PURPLE + "The first nation has entered The End...");
                org.bukkit.Bukkit.broadcastMessage(
                    org.bukkit.ChatColor.GOLD + "The Iron Throne awaits.");
            }
        }
    }
}
