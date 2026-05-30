package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks chunk ownership.
 * When a player kills another, the killer claims the dead player's land.
 * Claimed land can be built on freely – buildings remain breakable.
 */
public class LandManager {

    private final NationsSMP plugin;
    private final NationManager nationManager;
    private final DataManager dataManager;

    /** "world:cx:cz" → owner UUID */
    private final Map<String, String> chunkOwners = new ConcurrentHashMap<>();

    public LandManager(NationsSMP plugin, NationManager nationManager, DataManager dataManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        this.dataManager = dataManager;
        loadLands();
    }

    private void loadLands() {
        var cfg = dataManager.getLandsConfig();
        var sec = cfg.getConfigurationSection("lands");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String owner = cfg.getString("lands." + key);
            if (owner != null) chunkOwners.put(key, owner);
        }
        plugin.getLogger().info("Loaded " + chunkOwners.size() + " claimed chunk(s).");
    }

    private void saveLands() {
        var cfg = dataManager.getLandsConfig();
        cfg.set("lands", null); // clear
        chunkOwners.forEach((k, v) -> cfg.set("lands." + k, v));
        dataManager.saveLands();
    }

    public String chunkKey(String worldName, int cx, int cz) {
        return worldName + ":" + cx + ":" + cz;
    }

    public String getOwnerUUID(Chunk chunk) {
        return chunkOwners.get(chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean isOwned(Chunk chunk) {
        return chunkOwners.containsKey(chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean isOwnedBy(Chunk chunk, UUID playerUUID) {
        String owner = getOwnerUUID(chunk);
        return owner != null && owner.equals(playerUUID.toString());
    }

    /** Claim all chunks owned by a dead player and transfer them to the killer. */
    public void transferLandOnKill(Player killed, Player killer) {
        Nation killedNation = nationManager.getNation(killed.getUniqueId());
        Nation killerNation = nationManager.getNation(killer.getUniqueId());
        if (killedNation == null || killerNation == null) return;

        String killedUID = killed.getUniqueId().toString();
        String killerUID = killer.getUniqueId().toString();

        int count = 0;
        for (Map.Entry<String, String> entry : new HashMap<>(chunkOwners).entrySet()) {
            if (killedUID.equals(entry.getValue())) {
                chunkOwners.put(entry.getKey(), killerUID);
                count++;
            }
        }

        // Sync to nation objects
        Set<String> toTransfer = new HashSet<>(killedNation.getClaimedChunks());
        killedNation.getClaimedChunks().clear();
        killerNation.getClaimedChunks().addAll(toTransfer);

        nationManager.saveNation(killedNation);
        nationManager.saveNation(killerNation);
        saveLands();

        if (count > 0) {
            NationAnimalEmoji killerEmoji = new NationAnimalEmoji(killerNation);
            Bukkit.broadcastMessage(killerEmoji.emoji + " " + ChatColor.YELLOW
                + killerNation.getNationName() + ChatColor.WHITE + " has claimed "
                + count + " chunk(s) of " + killedNation.getNationName() + "'s territory!");
        }

        // Also claim the chunk where the kill happened
        Chunk killChunk = killed.getLocation().getChunk();
        claimChunk(killer, killChunk);
    }

    /** Claim a single chunk for a player. */
    public void claimChunk(Player player, Chunk chunk) {
        String key = chunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        chunkOwners.put(key, player.getUniqueId().toString());
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n != null) {
            n.claimChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
        saveLands();
    }

    /** Release all land owned by a player (on nation wipe). */
    public void releaseAllLand(Player player) {
        String uid = player.getUniqueId().toString();
        chunkOwners.entrySet().removeIf(e -> uid.equals(e.getValue()));
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n != null) n.getClaimedChunks().clear();
        saveLands();
    }

    private static class NationAnimalEmoji {
        String emoji;
        NationAnimalEmoji(Nation n) {
            this.emoji = n.getAnimalKey() != null
                ? com.nationssmp.data.NationAnimal.byKey(n.getAnimalKey()).getEmoji()
                : "⚔";
        }
    }
}
