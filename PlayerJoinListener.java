package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles martyr grave generation and protection.
 * Grave structure (built at death location):
 *
 *    [Crown ArmorStand]
 *         [Sign]
 *    [Obsidian base 3x1]
 *
 * All grave blocks are tracked and protected from breaking.
 */
public class MartyrManager {

    private final NationsSMP plugin;
    private final NationManager nationManager;

    /** Set of block locations that are indestructible grave blocks */
    private final Set<String> graveBlocks = ConcurrentHashMap.newKeySet();

    public MartyrManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        loadGraves();
    }

    private void loadGraves() {
        var cfg = plugin.getDataManager().getLegendaryConfig(); // reuse for misc data
        List<String> saved = cfg.getStringList("graves");
        graveBlocks.addAll(saved);
    }

    private void saveGraves() {
        var cfg = plugin.getDataManager().getLegendaryConfig();
        cfg.set("graves", new ArrayList<>(graveBlocks));
        plugin.getDataManager().saveLegendary();
    }

    // ── Build a grave ─────────────────────────────────────────────────────────

    public void buildMartyrGrave(Player dead, Player killer, Nation deadNation) {
        Location deathLoc = dead.getLocation();
        World world = deathLoc.getWorld();
        if (world == null) return;

        // Find a solid ground location
        Location base = findGround(deathLoc);

        // Build obsidian base (3 blocks)
        for (int dx = -1; dx <= 1; dx++) {
            Block b = world.getBlockAt(base.clone().add(dx, 0, 0));
            b.setType(Material.OBSIDIAN);
            trackGraveBlock(b);
        }

        // Central grave marker
        Block center = world.getBlockAt(base);
        center.setType(Material.OBSIDIAN);
        trackGraveBlock(center);

        // Place a soul lantern on top
        Block lanternBlock = world.getBlockAt(base.clone().add(0, 1, 0));
        lanternBlock.setType(Material.SOUL_LANTERN);
        trackGraveBlock(lanternBlock);

        // Sign on north face of lantern block
        Block signBlock = world.getBlockAt(base.clone().add(0, 2, 0));
        signBlock.setType(Material.OAK_WALL_SIGN);
        trackGraveBlock(signBlock);

        // Write the sign using Paper 1.20.4 Sign API
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (signBlock.getState() instanceof Sign sign) {
                String allyName = deadNation.getAllyNationName() != null
                    ? deadNation.getAllyNationName() : "Their Ally";
                var front = sign.getSide(Side.FRONT);
                front.setLine(0, ChatColor.DARK_RED + "✦ MARTYR ✦");
                front.setLine(1, ChatColor.WHITE + dead.getName());
                front.setLine(2, ChatColor.GOLD + (deadNation.getTitle() != null
                    ? deadNation.getTitle() : ""));
                front.setLine(3, ChatColor.GRAY + "For " + allyName);
                sign.update(true);
            }
        }, 2L);

        // Spawn ceremonial crown ArmorStand
        Location crownLoc = base.clone().add(0.5, 3.0, 0.5);
        world.spawn(crownLoc, ArmorStand.class, stand -> {
            stand.setCustomName(ChatColor.GOLD + "✦ " + dead.getName() + " ✦");
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setVisible(true);
            // Place golden helmet as crown
            ItemStack crown = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta meta = crown.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Martyr's Crown");
                crown.setItemMeta(meta);
            }
            stand.getEquipment().setHelmet(crown);
        });

        // Save grave block locations
        saveGraves();

        // Server broadcast
        NationAnimal animal = NationAnimal.byKey(deadNation.getAnimalKey());
        String allyName = deadNation.getAllyNationName() != null
            ? deadNation.getAllyNationName() : "their Ally";
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "✦═══════════════════════════════✦");
        Bukkit.broadcastMessage(ChatColor.WHITE + "  " + animal.getEmoji() + " "
            + dead.getName() + ", " + ChatColor.YELLOW + deadNation.getTitle());
        Bukkit.broadcastMessage(ChatColor.GRAY + "  of " + deadNation.getNationName()
            + " has fallen as a " + ChatColor.GOLD + "MARTYR");
        Bukkit.broadcastMessage(ChatColor.WHITE + "  defending " + ChatColor.AQUA + allyName);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  \"" + deadNation.getMotto() + "\"");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  Their grave stands eternal.");
        Bukkit.broadcastMessage(ChatColor.GOLD + "✦═══════════════════════════════✦");
        Bukkit.broadcastMessage("");
    }

    // ── Grief protection ──────────────────────────────────────────────────────

    public boolean isGraveBlock(Block block) {
        return graveBlocks.contains(blockKey(block));
    }

    private void trackGraveBlock(Block block) {
        graveBlocks.add(blockKey(block));
    }

    private String blockKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private Location findGround(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc;
        Location search = loc.clone();
        // Search downward to find solid ground
        for (int y = (int) search.getY(); y > 0; y--) {
            Block b = world.getBlockAt((int) search.getX(), y, (int) search.getZ());
            if (b.getType().isSolid()) {
                return new Location(world, search.getX(), y + 1, search.getZ());
            }
        }
        return search;
    }
}
