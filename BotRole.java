package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Manages the GLITCH entity (vanilla Skeleton) and the Obsidian Sword 50-hour death timer.
 * No Citizens dependency — uses vanilla Bukkit entity API.
 */
public class GlitchManager {

    private static final long FIFTY_HOURS_MS  = 50L * 60L * 60L * 1000L;
    private static final String META_IS_GLITCH = "is_glitch_entity";

    private final NationsSMP plugin;
    private final NationManager nationManager;
    private final LegendaryItemManager legendaryItemManager;

    private FileConfiguration glitchConfig;

    private boolean timerActive      = false;
    private long    lootTimeMs       = 0;
    private String  currentHolderUUID = null;
    private UUID    glitchEntityUUID  = null;

    private BukkitRunnable mainTask;
    private BukkitRunnable scanTask;

    public GlitchManager(NationsSMP plugin, NationManager nationManager,
                         LegendaryItemManager legendaryItemManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        this.legendaryItemManager = legendaryItemManager;
        this.glitchConfig = plugin.getDataManager().getGlitchConfig();
        loadState();
        startScanTask();
        if (timerActive) startMainTask();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadState() {
        timerActive        = glitchConfig.getBoolean("active", false);
        lootTimeMs         = glitchConfig.getLong("lootTime", 0);
        currentHolderUUID  = glitchConfig.getString("holderUUID");
        String uuidStr     = glitchConfig.getString("glitchEntityUUID");
        glitchEntityUUID   = uuidStr != null ? tryParseUUID(uuidStr) : null;
    }

    private void saveState() {
        glitchConfig.set("active",           timerActive);
        glitchConfig.set("lootTime",         lootTimeMs);
        glitchConfig.set("holderUUID",       currentHolderUUID);
        glitchConfig.set("glitchEntityUUID", glitchEntityUUID != null ? glitchEntityUUID.toString() : null);
        plugin.getDataManager().saveGlitch();
    }

    // ── Public triggers ───────────────────────────────────────────────────────

    public void onSwordFirstLooted(Player player) {
        if (timerActive) return;
        timerActive       = true;
        lootTimeMs        = System.currentTimeMillis();
        currentHolderUUID = player.getUniqueId().toString();
        saveState();
        spawnGlitch(player.getLocation());
        startMainTask();

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage(ChatColor.GRAY      + "  THE OBSIDIAN SWORD HAS BEEN LOOTED...");
        Bukkit.broadcastMessage(ChatColor.DARK_RED  + "        G̶L̶I̶T̶C̶H̶  A̶W̶A̶K̶E̶N̶S̶.");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        Bukkit.broadcastMessage("");
    }

    public void onSwordTransferred(Player newHolder) {
        currentHolderUUID = newHolder.getUniqueId().toString();
        saveState();
        teleportGlitchToPlayer(newHolder);
        newHolder.sendMessage(ChatColor.DARK_GRAY + "You feel something watching you...");
    }

    // ── Scan task ─────────────────────────────────────────────────────────────

    private void startScanTask() {
        scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!timerActive) return;
                Player holder = findSwordHolder();
                String newUID = holder != null ? holder.getUniqueId().toString() : null;
                if (newUID != null && !newUID.equals(currentHolderUUID)) {
                    currentHolderUUID = newUID;
                    saveState();
                    teleportGlitchToPlayer(holder);
                }
            }
        };
        scanTask.runTaskTimer(plugin, 40L, 40L);
    }

    private Player findSwordHolder() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : p.getInventory().getContents()) {
                if (legendaryItemManager.isObsidianSword(item)) return p;
            }
        }
        return null;
    }

    // ── Main countdown task ───────────────────────────────────────────────────

    private void startMainTask() {
        mainTask = new BukkitRunnable() {
            long lastWarningCheck = 0;

            @Override
            public void run() {
                if (!timerActive) { cancel(); return; }

                long elapsed   = System.currentTimeMillis() - lootTimeMs;
                long remaining = FIFTY_HOURS_MS - elapsed;

                if (remaining <= 0) {
                    triggerGlitchKill();
                    cancel();
                    return;
                }

                long hoursLeft   = remaining / (1000 * 60 * 60);
                long minutesLeft = (remaining / (1000 * 60)) % 60;

                chaseHolder();

                long checkKey = hoursLeft;
                if (checkKey != lastWarningCheck) {
                    lastWarningCheck = checkKey;
                    for (int h : new int[]{45, 48, 49}) {
                        if (hoursLeft == h) broadcastWarning(hoursLeft, minutesLeft);
                    }
                    if (hoursLeft <= 5 && hoursLeft > 0) broadcastWarning(hoursLeft, minutesLeft);
                }
                if (remaining <= 30L * 60 * 1000 && remaining % (60 * 1000) < 1000) {
                    broadcastWarning(hoursLeft, minutesLeft);
                }
            }
        };
        mainTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void broadcastWarning(long hours, long minutes) {
        String time = hours > 0 ? hours + "h " + minutes + "m" : minutes + " minutes";
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "☠ GLITCH: " + ChatColor.GRAY
            + time + " remain before the sword's holder is claimed...");
    }

    // ── Glitch chase logic ────────────────────────────────────────────────────

    private void chaseHolder() {
        if (currentHolderUUID == null) return;
        Player holder;
        try { holder = Bukkit.getPlayer(UUID.fromString(currentHolderUUID)); }
        catch (Exception e) { return; }
        if (holder == null || !holder.isOnline()) return;

        Entity glitch = getGlitchEntity();
        if (glitch == null || !glitch.isValid()) {
            spawnGlitch(holder.getLocation());
            return;
        }

        double dist = glitch.getLocation().distance(holder.getLocation());
        if (dist > 60) {
            glitch.teleport(holder.getLocation().clone().add(3, 0, 3));
        } else if (dist > 3 && glitch instanceof Skeleton sk) {
            sk.getPathfinder().moveTo(holder);
        }

        holder.getWorld().spawnParticle(Particle.WITCH, glitch.getLocation(), 5, 0.5, 0.5, 0.5);
    }

    private void teleportGlitchToPlayer(Player player) {
        Entity glitch = getGlitchEntity();
        if (glitch == null) { spawnGlitch(player.getLocation()); return; }
        glitch.teleport(player.getLocation().clone().add(3, 0, 3));
    }

    // ── Kill at hour 50 ───────────────────────────────────────────────────────

    private void triggerGlitchKill() {
        Player holder = findSwordHolder();
        if (holder == null && currentHolderUUID != null) {
            try { holder = Bukkit.getPlayer(UUID.fromString(currentHolderUUID)); }
            catch (Exception ignored) {}
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        if (holder != null && holder.isOnline()) {
            Nation n = nationManager.getNation(holder.getUniqueId());
            String nationName = n != null ? n.getNationName() : holder.getName();
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "  GLITCH HAS CLAIMED "
                + holder.getName().toUpperCase() + " OF " + nationName.toUpperCase());
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  The sword vanishes. Silence falls.");

            var inv = holder.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (legendaryItemManager.isObsidianSword(inv.getItem(i))) inv.setItem(i, null);
            }
            holder.setHealth(0);
        } else {
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  GLITCH finds no host. The sword dissolves.");
        }
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");

        despawnGlitch();
        timerActive       = false;
        lootTimeMs        = 0;
        currentHolderUUID = null;
        glitchEntityUUID  = null;
        glitchConfig.set("swordRespawned", false);
        saveState();
    }

    // ── Glitch entity ─────────────────────────────────────────────────────────

    private void spawnGlitch(Location loc) {
        despawnGlitch();
        Skeleton glitch = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
        glitch.setCustomName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "G̶L̶I̶T̶C̶H̶");
        glitch.setCustomNameVisible(true);
        glitch.setInvulnerable(true);
        glitch.setRemoveWhenFarAway(false);
        glitch.setSilent(false);
        glitch.setMetadata(META_IS_GLITCH, new FixedMetadataValue(plugin, true));

        // Give it a scary look
        glitch.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        glitch.getEquipment().setHelmetDropChance(0f);

        glitchEntityUUID = glitch.getUniqueId();
        saveState();
    }

    private void despawnGlitch() {
        Entity e = getGlitchEntity();
        if (e != null) e.remove();
        glitchEntityUUID = null;
    }

    private Entity getGlitchEntity() {
        if (glitchEntityUUID == null) return null;
        return Bukkit.getEntity(glitchEntityUUID);
    }

    public boolean isGlitch(Entity entity) {
        return entity.hasMetadata(META_IS_GLITCH);
    }

    public boolean isTimerActive() { return timerActive; }
    public long getLootTimeMs()    { return lootTimeMs; }
    public long getRemainingMs()   {
        if (!timerActive) return -1;
        return FIFTY_HOURS_MS - (System.currentTimeMillis() - lootTimeMs);
    }

    public void shutdown() {
        if (mainTask != null) mainTask.cancel();
        if (scanTask  != null) scanTask.cancel();
    }

    private static UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
