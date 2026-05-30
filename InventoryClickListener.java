package com.nationssmp.data;

import org.bukkit.Material;

import java.util.*;

/**
 * Stores all data for a single player's nation.
 * Serialised to YAML by DataManager.
 */
public class Nation {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final String playerUUID;
    private String playerName;
    private String nationName;
    private String title;          // e.g. "The Unconquered"
    private String motto;          // GOT-style words
    private String animalKey;      // key from NationAnimal enum
    private String buildingBlock;  // Material name of chosen block

    // ── Status flags ─────────────────────────────────────────────────────────
    private boolean oathbreaker;
    private boolean martyr;
    private boolean setupComplete;

    // ── Bots ─────────────────────────────────────────────────────────────────
    private int botCount;                  // live bots remaining
    private Map<Integer, String> botRoles; // npc-id → role name

    // ── Land ─────────────────────────────────────────────────────────────────
    private final Set<String> claimedChunks; // "world:cx:cz"

    // ── Alliance ─────────────────────────────────────────────────────────────
    private String allyNationName; // null if no ally

    // ── Companion NPC ─────────────────────────────────────────────────────────
    private int companionNpcId = -1; // Citizens2 NPC id

    // ─────────────────────────────────────────────────────────────────────────
    public Nation(String playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.botCount = 30;
        this.botRoles = new HashMap<>();
        this.claimedChunks = new HashSet<>();
        this.oathbreaker = false;
        this.martyr = false;
        this.setupComplete = false;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getPlayerUUID()    { return playerUUID; }
    public String getPlayerName()    { return playerName; }
    public String getNationName()    { return nationName; }
    public String getTitle()         { return title; }
    public String getMotto()         { return motto; }
    public String getAnimalKey()     { return animalKey; }
    public String getBuildingBlock() { return buildingBlock; }
    public boolean isOathbreaker()   { return oathbreaker; }
    public boolean isMartyr()        { return martyr; }
    public boolean isSetupComplete() { return setupComplete; }
    public int getBotCount()         { return botCount; }
    public Map<Integer, String> getBotRoles() { return botRoles; }
    public Set<String> getClaimedChunks()     { return claimedChunks; }
    public String getAllyNationName() { return allyNationName; }
    public int getCompanionNpcId()   { return companionNpcId; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setPlayerName(String n)    { this.playerName = n; }
    public void setNationName(String n)    { this.nationName = n; }
    public void setTitle(String t)         { this.title = t; }
    public void setMotto(String m)         { this.motto = m; }
    public void setAnimalKey(String a)     { this.animalKey = a; }
    public void setBuildingBlock(String b) { this.buildingBlock = b; }
    public void setOathbreaker(boolean v)  { this.oathbreaker = v; }
    public void setMartyr(boolean v)       { this.martyr = v; }
    public void setSetupComplete(boolean v){ this.setupComplete = v; }
    public void setBotCount(int v)         { this.botCount = Math.max(0, v); }
    public void setBotRoles(Map<Integer, String> r) { this.botRoles = r; }
    public void setAllyNationName(String a){ this.allyNationName = a; }
    public void setCompanionNpcId(int id)  { this.companionNpcId = id; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Permanently mark this player as oathbreaker and lock the title. */
    public void applyOathbreakerPunishment() {
        this.oathbreaker = true;
        this.title = "Oathbreaker";
    }

    /** Permanently mark this player as Martyr and prepend it to their title. */
    public void applyMartyrTitle() {
        this.martyr = true;
        if (this.title != null && !this.title.startsWith("Martyr")) {
            this.title = "Martyr, " + this.title;
        }
    }

    /** Kill half the bots. Returns how many were removed. */
    public int killHalfBots() {
        int remove = Math.max(1, botCount / 2);
        botCount = Math.max(0, botCount - remove);
        return remove;
    }

    /** Add a chunk to this nation's territory. */
    public void claimChunk(String worldName, int cx, int cz) {
        claimedChunks.add(worldName + ":" + cx + ":" + cz);
    }

    /** Remove a chunk from this nation's territory. */
    public void unclaimChunk(String worldName, int cx, int cz) {
        claimedChunks.remove(worldName + ":" + cx + ":" + cz);
    }

    /** Check if a chunk belongs to this nation. */
    public boolean ownsChunk(String worldName, int cx, int cz) {
        return claimedChunks.contains(worldName + ":" + cx + ":" + cz);
    }

    /** Full display tag shown in chat / above head. */
    public String getDisplayTag() {
        String animal = animalKey != null ? NationAnimal.byKey(animalKey).getEmoji() + " " : "";
        String nation = nationName != null ? "[" + nationName + "] " : "";
        String t = title != null ? title + " - " : "";
        return animal + nation + playerName + ", " + t.replace(" - ", "");
    }

    /** Short tag for chat prefix. */
    public String getChatPrefix() {
        String animal = animalKey != null ? NationAnimal.byKey(animalKey).getEmoji() : "";
        String nation = nationName != null ? nationName : "?";
        return animal + "[" + nation + "]";
    }

    public boolean hasAlly() {
        return allyNationName != null && !allyNationName.isEmpty();
    }

    /** Whether setup step is pending (so we should capture chat input). */
    private SetupStep pendingStep = SetupStep.NONE;

    public SetupStep getPendingStep() { return pendingStep; }
    public void setPendingStep(SetupStep s) { this.pendingStep = s; }

    public enum SetupStep {
        NONE,
        AWAITING_NATION_NAME,
        AWAITING_TITLE,
        AWAITING_MOTTO,
        AWAITING_ANIMAL_SELECTION,
        AWAITING_BLOCK_SELECTION
    }
}
