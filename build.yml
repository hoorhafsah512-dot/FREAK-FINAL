package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks in the animal-selection and block-selection GUIs
 * during the first-login setup flow.
 */
public class InventoryClickListener implements Listener {

    private static final String ANIMAL_TITLE = ChatColor.DARK_PURPLE + "Choose Your Nation Animal";
    private static final String BLOCK_TITLE  = ChatColor.DARK_AQUA   + "Choose Your Nation Block";

    private final NationsSMP plugin;

    public InventoryClickListener(NationsSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        String title = event.getView().getTitle();

        // ── Animal selection GUI ──────────────────────────────────────────────
        if (ANIMAL_TITLE.equals(title)) {
            event.setCancelled(true);
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation == null || nation.isSetupComplete()) return;
            if (event.getCurrentItem().getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "That animal is already taken!");
                return;
            }
            plugin.getNationManager().handleAnimalChoice(player, event.getSlot());
        }

        // ── Block selection GUI ───────────────────────────────────────────────
        if (BLOCK_TITLE.equals(title)) {
            event.setCancelled(true);
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation == null || nation.isSetupComplete()) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked.getType() == Material.BARRIER) {
                player.sendMessage(ChatColor.RED + "That block is already taken!");
                return;
            }
            plugin.getNationManager().handleBlockChoice(player, clicked.getType());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
        if (nation == null || nation.isSetupComplete()) return;

        // If they closed mid-setup, reopen after a tick
        if (ANIMAL_TITLE.equals(title) &&
                nation.getPendingStep() == Nation.SetupStep.AWAITING_ANIMAL_SELECTION) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "You must choose an animal to continue setup.");
                plugin.getNationManager().openAnimalGUI(player);
            }, 20L);
        }
        if (BLOCK_TITLE.equals(title) &&
                nation.getPendingStep() == Nation.SetupStep.AWAITING_BLOCK_SELECTION) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.RED + "You must choose a building block to continue setup.");
                plugin.getNationManager().openBlockGUI(player);
            }, 20L);
        }
    }
}
