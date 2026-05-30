package com.nationssmp.listeners;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles block break / place / interact events.
 *
 * Protections:
 *  - Martyr grave blocks  → indestructible always
 *  - Trophy head blocks   → indestructible always
 *  - Iron Throne blocks   → indestructible always
 *
 * Special interactions:
 *  - Right-click throne block → DragonManager.onThroneInteract()
 *  - Right-click chest in Deep Dark → may inject Obsidian Sword
 *  - Place skull item → TrophyManager.onSkullPlaced()
 */
public class BlockListener implements Listener {

    private final NationsSMP plugin;

    // Whether we have already injected the obsidian sword (once per server lifetime)
    private boolean obsidianInjected = false;

    public BlockListener(NationsSMP plugin) {
        this.plugin = plugin;
        obsidianInjected = plugin.getDataManager().getGlitchConfig()
            .getBoolean("obsidianInjected", false);
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (plugin.getMartyrManager().isGraveBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GRAY + "This grave is eternal.");
            return;
        }
        if (plugin.getTrophyManager().isTrophyBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GRAY + "This trophy cannot be destroyed.");
            return;
        }
        if (plugin.getDragonManager().isThroneBlock(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "The Iron Throne is indestructible.");
            return;
        }
    }

    // ── Block place ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block    = event.getBlockPlaced();

        // Track trophy heads
        if (plugin.getTrophyManager().isTrophyItem(item)) {
            plugin.getTrophyManager().onSkullPlaced(block, item);
        }
    }

    // ── Player interact ───────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block  block  = event.getClickedBlock();
        if (block == null) return;

        // Iron Throne right-click
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && plugin.getDragonManager().isThroneBlock(block)) {
            event.setCancelled(true);
            plugin.getDragonManager().onThroneInteract(player);
            return;
        }

        // Chest open in Deep Dark — inject Obsidian Sword if not yet done
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && block.getType() == Material.CHEST
                && !obsidianInjected
                && isInDeepDark(block.getLocation())) {
            if (block.getState() instanceof Chest chest) {
                Inventory inv = chest.getInventory();
                ItemStack sword = plugin.getLegendaryItemManager().buildObsidianSword();
                inv.setItem(new java.util.Random().nextInt(27), sword);
                chest.update();
                obsidianInjected = true;
                plugin.getDataManager().getGlitchConfig().set("obsidianInjected", true);
                plugin.getDataManager().saveGlitch();
                plugin.getLogger().info("Obsidian Sword injected into Ancient City chest.");
            }
            return;
        }

        // Book of Command – right-click with it
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.getLegendaryItemManager().isBookOfCommand(hand)
                && event.getAction() == Action.RIGHT_CLICK_AIR
                || plugin.getLegendaryItemManager().isBookOfCommand(hand)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Handled in BookCommandListener / PlayerInteractListener
        }
    }

    // ── Legendary item pickup ─────────────────────────────────────────────────

    @EventHandler
    public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();

        // Oath Keeper — reject oathbreakers
        if (plugin.getLegendaryItemManager().isOathKeeper(item)) {
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation != null && nation.isOathbreaker()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "⚔ The OATH KEEPER recoils from your dishonour!");
                return;
            }
        }

        // Oath Breaker — reject honourable
        if (plugin.getLegendaryItemManager().isOathBreaker(item)) {
            Nation nation = plugin.getNationManager().getNation(player.getUniqueId());
            if (nation != null && !nation.isOathbreaker()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "🔨 The OATH BREAKER refuses a honourable hand!");
                return;
            }
        }

        // Obsidian Sword first loot
        if (plugin.getLegendaryItemManager().isObsidianSword(item)) {
            if (!plugin.getGlitchManager().isTimerActive()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getGlitchManager().onSwordFirstLooted(player));
            } else {
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getGlitchManager().onSwordTransferred(player));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isInDeepDark(Location loc) {
        if (loc.getWorld() == null) return false;
        try {
            // Use Bukkit's biome API — works for Paper 1.20.4
            return loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                   == Biome.DEEP_DARK;
        } catch (Exception e) {
            return false;
        }
    }
}
