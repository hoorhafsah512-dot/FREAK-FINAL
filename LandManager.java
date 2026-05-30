package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages the Iron Throne and the Ender Dragon allegiance system.
 *
 * Flow:
 *  1. A castle structure is auto-built the first time a player enters The End.
 *  2. Inside the castle sits the Iron Throne (a special block + armorstand).
 *  3. A player who right-clicks the throne receives Dragon coordinates.
 *  4. The throne relocates to a new spot in the castle (or a new random End location).
 *  5. The dragon fights for whoever last sat on the throne.
 *  6. When throne is sat again, dragon switches loyalty.
 *  7. Obsidian Sword one-shots the dragon.
 */
public class DragonManager {

    private final NationsSMP plugin;
    private final NationManager nationManager;
    private FileConfiguration throneConfig;

    private UUID dragonEntityUUID = null;
    private String throneOwnerUUID = null;   // player UUID who sat last
    private Location throneLocation = null;
    private Location castleOrigin   = null;
    private boolean castleBuilt     = false;

    // Throne block tag
    private final Set<String> throneBlocks = new HashSet<>();

    public DragonManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        this.throneConfig = plugin.getDataManager().getThroneConfig();
        loadState();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadState() {
        castleBuilt     = throneConfig.getBoolean("castleBuilt", false);
        throneOwnerUUID = throneConfig.getString("throneOwner");
        String dragonStr = throneConfig.getString("dragonUUID");
        if (dragonStr != null) {
            try { dragonEntityUUID = UUID.fromString(dragonStr); } catch (Exception ignored) {}
        }
        if (throneConfig.contains("throne.world")) {
            World w = Bukkit.getWorld(throneConfig.getString("throne.world", "world_the_end"));
            if (w != null) {
                throneLocation = new Location(w,
                    throneConfig.getDouble("throne.x"),
                    throneConfig.getDouble("throne.y"),
                    throneConfig.getDouble("throne.z"));
            }
        }
        if (throneConfig.contains("castle.world")) {
            World w = Bukkit.getWorld(throneConfig.getString("castle.world", "world_the_end"));
            if (w != null) {
                castleOrigin = new Location(w,
                    throneConfig.getDouble("castle.x"),
                    throneConfig.getDouble("castle.y"),
                    throneConfig.getDouble("castle.z"));
            }
        }
        // Load throne block positions
        List<String> tBlocks = throneConfig.getStringList("throneBlocks");
        throneBlocks.addAll(tBlocks);
    }

    private void saveState() {
        throneConfig.set("castleBuilt",  castleBuilt);
        throneConfig.set("throneOwner",  throneOwnerUUID);
        throneConfig.set("dragonUUID",   dragonEntityUUID != null ? dragonEntityUUID.toString() : null);
        if (throneLocation != null) {
            throneConfig.set("throne.world", throneLocation.getWorld().getName());
            throneConfig.set("throne.x", throneLocation.getX());
            throneConfig.set("throne.y", throneLocation.getY());
            throneConfig.set("throne.z", throneLocation.getZ());
        }
        if (castleOrigin != null) {
            throneConfig.set("castle.world", castleOrigin.getWorld().getName());
            throneConfig.set("castle.x", castleOrigin.getX());
            throneConfig.set("castle.y", castleOrigin.getY());
            throneConfig.set("castle.z", castleOrigin.getZ());
        }
        throneConfig.set("throneBlocks", new ArrayList<>(throneBlocks));
        plugin.getDataManager().saveThrone();
    }

    // ── Castle building ───────────────────────────────────────────────────────

    /**
     * Build the End castle when a player first enters The End.
     * Called from PlayerChangedWorldListener.
     */
    public void ensureCastleBuilt(World endWorld) {
        if (castleBuilt) return;
        castleBuilt = true;
        castleOrigin = new Location(endWorld, 150, 65, 150);
        Bukkit.getScheduler().runTask(plugin, () -> {
            buildCastle(castleOrigin);
            // Place throne inside
            throneLocation = castleOrigin.clone().add(7, 4, 7);
            placeThroneStructure(throneLocation);
            saveState();
            plugin.getLogger().info("Iron Throne castle built in The End at " + castleOrigin);
        });
    }

    /** Build a simple castle shell around the origin. */
    private void buildCastle(Location origin) {
        World w = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int size = 15; // 15x15 footprint

        // Floor
        for (int x = 0; x <= size; x++) for (int z = 0; z <= size; z++) {
            w.getBlockAt(ox + x, oy, oz + z).setType(Material.OBSIDIAN);
        }
        // Walls (4 sides, 5 high)
        for (int h = 1; h <= 8; h++) {
            for (int i = 0; i <= size; i++) {
                setWall(w, ox + i, oy + h, oz, Material.PURPUR_BLOCK);
                setWall(w, ox + i, oy + h, oz + size, Material.PURPUR_BLOCK);
                setWall(w, ox, oy + h, oz + i, Material.PURPUR_BLOCK);
                setWall(w, ox + size, oy + h, oz + i, Material.PURPUR_BLOCK);
            }
        }
        // Corner pillars (taller)
        for (int h = 1; h <= 12; h++) {
            for (int[] corner : new int[][]{{0,0},{0,size},{size,0},{size,size}}) {
                w.getBlockAt(ox + corner[0], oy + h, oz + corner[1]).setType(Material.PURPUR_PILLAR);
            }
        }
        // Entrance gap (south wall middle)
        for (int h = 1; h <= 3; h++) {
            w.getBlockAt(ox + 7, oy + h, oz).setType(Material.AIR);
            w.getBlockAt(ox + 8, oy + h, oz).setType(Material.AIR);
        }
    }

    private void setWall(World w, int x, int y, int z, Material mat) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() == Material.AIR) b.setType(mat);
    }

    // ── Throne structure ──────────────────────────────────────────────────────

    private void placeThroneStructure(Location loc) {
        World w = loc.getWorld();
        // Base: 3x3 platform of purpur
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block b = w.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() - 1, loc.getBlockZ() + dz);
            b.setType(Material.PURPUR_SLAB);
        }
        // Throne seat: note_block (custom look via resource pack)
        Block seat = w.getBlockAt(loc);
        seat.setType(Material.OBSIDIAN);
        throneBlocks.add(blockKey(seat));

        // Throne back: purpur pillar
        Block back = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ() + 1);
        back.setType(Material.PURPUR_PILLAR);
        throneBlocks.add(blockKey(back));

        // Armrests
        Block armL = w.getBlockAt(loc.getBlockX() - 1, loc.getBlockY(), loc.getBlockZ());
        Block armR = w.getBlockAt(loc.getBlockX() + 1, loc.getBlockY(), loc.getBlockZ());
        armL.setType(Material.PURPUR_SLAB);
        armR.setType(Material.PURPUR_SLAB);
        throneBlocks.add(blockKey(armL));
        throneBlocks.add(blockKey(armR));

        // Golden crown ArmorStand above the throne
        Location standLoc = loc.clone().add(0.5, 1.0, 0.5);
        w.spawn(standLoc, ArmorStand.class, stand -> {
            stand.setCustomName(ChatColor.GOLD + "✦ THE IRON THRONE ✦");
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setVisible(false);
            var eq = stand.getEquipment();
            eq.setHelmet(new org.bukkit.inventory.ItemStack(Material.GOLDEN_HELMET));
        });

        saveState();
    }

    // ── Throne interaction ────────────────────────────────────────────────────

    /**
     * Called when a player right-clicks the throne block.
     */
    public void onThroneInteract(Player player) {
        Nation nation = nationManager.getNation(player.getUniqueId());
        if (nation == null || !nation.isSetupComplete()) {
            player.sendMessage(ChatColor.RED + "You have no nation. The throne ignores you.");
            return;
        }

        String previousOwnerUUID = throneOwnerUUID;
        throneOwnerUUID = player.getUniqueId().toString();

        // Tell the player where the dragon is
        Location dragonLoc = findOrSpawnDragon(player.getWorld());
        if (dragonLoc != null) {
            int dx = dragonLoc.getBlockX(), dy = dragonLoc.getBlockY(), dz = dragonLoc.getBlockZ();
            player.sendMessage(ChatColor.GOLD + "✦ THE IRON THRONE ACCEPTS YOU ✦");
            player.sendMessage(ChatColor.YELLOW + "The Dragon stirs at: "
                + ChatColor.WHITE + dx + ", " + dy + ", " + dz);
            player.sendMessage(ChatColor.GRAY + "Command it with: " + ChatColor.WHITE + "/summondragon");
        }

        // Broadcast takeover
        String emoji = nation.getAnimalKey() != null
            ? com.nationssmp.data.NationAnimal.byKey(nation.getAnimalKey()).getEmoji() : "⚔";
        Bukkit.broadcastMessage(emoji + " " + ChatColor.GOLD + player.getName()
            + " OF " + nation.getNationName().toUpperCase()
            + ChatColor.YELLOW + " HAS CLAIMED THE IRON THRONE!");
        if (previousOwnerUUID != null) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "The Dragon has switched allegiance!");
        }

        // Relocate throne
        Bukkit.getScheduler().runTaskLater(plugin, () -> relocateThrone(), 60L);
        saveState();
    }

    /** Move the throne to a new random location within the castle. */
    private void relocateThrone() {
        if (castleOrigin == null) return;
        // Remove old throne structure
        if (throneLocation != null) {
            World w = throneLocation.getWorld();
            w.getBlockAt(throneLocation).setType(Material.AIR);
            w.getBlockAt(throneLocation.clone().add(0, 1, 1)).setType(Material.AIR);
        }
        throneBlocks.clear();

        // New position within castle (random within 3-11 range on both axes)
        Random r = new Random();
        int ox = castleOrigin.getBlockX();
        int oy = castleOrigin.getBlockY();
        int oz = castleOrigin.getBlockZ();
        int newX = ox + 3 + r.nextInt(9);
        int newZ = oz + 3 + r.nextInt(9);
        throneLocation = new Location(castleOrigin.getWorld(), newX, oy + 3, newZ);
        placeThroneStructure(throneLocation);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "The Iron Throne has vanished to a new location...");
        saveState();
    }

    // ── Dragon ────────────────────────────────────────────────────────────────

    private Location findOrSpawnDragon(World endWorld) {
        // Try to find existing dragon
        if (dragonEntityUUID != null) {
            for (Entity e : endWorld.getEntities()) {
                if (e instanceof EnderDragon && e.getUniqueId().equals(dragonEntityUUID)) {
                    return e.getLocation();
                }
            }
        }
        // Spawn a new one
        EnderDragon dragon = (EnderDragon) endWorld.spawnEntity(
            new Location(endWorld, 0, 80, 0), EntityType.ENDER_DRAGON);
        dragonEntityUUID = dragon.getUniqueId();
        saveState();
        return dragon.getLocation();
    }

    /**
     * Make the dragon attack the target (called when throne owner uses /summondragon).
     */
    public void summonDragonForPlayer(Player player) {
        if (throneOwnerUUID == null || !throneOwnerUUID.equals(player.getUniqueId().toString())) {
            player.sendMessage(ChatColor.RED + "You do not sit the Iron Throne. The dragon ignores you.");
            return;
        }
        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld == null) {
            player.sendMessage(ChatColor.RED + "The End is not loaded.");
            return;
        }
        Location dragonLoc = findOrSpawnDragon(endWorld);
        player.sendMessage(ChatColor.GOLD + "The Dragon stirs at: "
            + dragonLoc.getBlockX() + ", " + dragonLoc.getBlockY() + ", " + dragonLoc.getBlockZ());
        player.sendMessage(ChatColor.GRAY + "Enter The End to command it in battle.");

        // Start dragon AI loop that targets enemies of the throne owner
        startDragonAI(player);
    }

    private void startDragonAI(Player owner) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!owner.isOnline()) { cancel(); return; }
                // Dragon only acts in The End
                World w = owner.getWorld();
                if (w.getEnvironment() != World.Environment.THE_END) return;

                EnderDragon dragon = findDragonEntity(w);
                if (dragon == null) { cancel(); return; }

                // Attack nearest enemy player (not the owner, not allied)
                Nation ownerNation = nationManager.getNation(owner.getUniqueId());
                Player target = null;
                double closest = 80;
                for (Player p : w.getPlayers()) {
                    if (p.equals(owner)) continue;
                    Nation pNation = nationManager.getNation(p.getUniqueId());
                    boolean allied = ownerNation != null && pNation != null
                        && ownerNation.getAllyNationName() != null
                        && ownerNation.getAllyNationName().equalsIgnoreCase(pNation.getNationName());
                    if (!allied) {
                        double d = p.getLocation().distance(dragon.getLocation());
                        if (d < closest) { closest = d; target = p; }
                    }
                }
                if (target != null) {
                    target.damage(10, dragon);
                    dragon.teleport(target.getLocation().clone().add(0, 10, 0));
                }
            }
        }.runTaskTimer(plugin, 60L, 60L);
    }

    private EnderDragon findDragonEntity(World w) {
        if (dragonEntityUUID == null) return null;
        for (Entity e : w.getEntities()) {
            if (e instanceof EnderDragon d && d.getUniqueId().equals(dragonEntityUUID)) return d;
        }
        return null;
    }

    /** Called when Obsidian Sword hits dragon – instant kill. */
    public void onObsidianSwordHitDragon(Player wielder, EnderDragon dragon) {
        dragon.setHealth(0);
        dragon.remove();
        dragonEntityUUID = null;
        saveState();

        Nation n = nationManager.getNation(wielder.getUniqueId());
        String nation = n != null ? n.getNationName() : wielder.getName();
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "🐉 THE DRAGON HAS FALLEN!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + wielder.getName() + " OF " + nation.toUpperCase()
            + " has slain the Dragon with the Obsidian Sword!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "The Iron Throne awaits a new king...");
        Bukkit.broadcastMessage("");
    }

    // ── Indestructible throne blocks ──────────────────────────────────────────

    public boolean isThroneBlock(Block block) {
        return throneBlocks.contains(blockKey(block));
    }

    private String blockKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isThroneOwner(Player player) {
        return player.getUniqueId().toString().equals(throneOwnerUUID);
    }

    public Location getThroneLocation() { return throneLocation; }
    public boolean isCastleBuilt()      { return castleBuilt; }
}
