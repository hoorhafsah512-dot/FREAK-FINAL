package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.OathRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages oaths (alliances) between nations.
 * Detects betrayal and applies the oathbreaker punishment.
 */
public class OathManager {

    private final NationsSMP plugin;
    private final NationManager nationManager;
    private final BotManager botManager;
    private final DataManager dataManager;

    /** nationName (lowercase) → OathRecord */
    private final Map<String, OathRecord> oathsByNation = new ConcurrentHashMap<>();

    public OathManager(NationsSMP plugin, NationManager nationManager,
                       BotManager botManager, DataManager dataManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        this.botManager = botManager;
        this.dataManager = dataManager;
        loadOaths();
    }

    private void loadOaths() {
        List<OathRecord> records = dataManager.loadAllOaths();
        for (OathRecord r : records) {
            oathsByNation.put(r.getInitiatorNation().toLowerCase(), r);
            oathsByNation.put(r.getReceiverNation().toLowerCase(), r);
        }
        plugin.getLogger().info("Loaded " + records.size() + " oath(s).");
    }

    // ── Form an oath ──────────────────────────────────────────────────────────

    /**
     * Parse and process the oath command.
     * Format: /I BEAR OATH TO <playerName> <title> OF <nationName>
     * The title can be multiple words, so we search for "OF" as a delimiter.
     */
    public boolean processOathCommand(Player speaker, String rawArgs) {
        // rawArgs should be: <playerName> <title words...> OF <nationName>
        String[] parts = rawArgs.split(" ");
        if (parts.length < 4) {
            speaker.sendMessage(ChatColor.RED
                + "Usage: /I BEAR OATH TO <playerName> <title> OF <nationName>");
            return false;
        }

        // Find "OF" keyword (case-insensitive)
        int ofIndex = -1;
        for (int i = 2; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("OF")) { ofIndex = i; break; }
        }
        if (ofIndex < 0 || ofIndex >= parts.length - 1) {
            speaker.sendMessage(ChatColor.RED + "Missing 'OF' in oath. Usage: /I BEAR OATH TO <player> <title> OF <nation>");
            return false;
        }

        String targetPlayerName = parts[0];
        // title = everything between parts[1] and ofIndex-1
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 1; i < ofIndex; i++) {
            if (i > 1) titleBuilder.append(" ");
            titleBuilder.append(parts[i]);
        }
        String declaredTitle  = titleBuilder.toString();
        String declaredNation = parts[ofIndex + 1];

        // Validate target player
        Player target = Bukkit.getPlayerExact(targetPlayerName);
        if (target == null || !target.isOnline()) {
            speaker.sendMessage(ChatColor.RED + "Player " + targetPlayerName + " is not online.");
            return false;
        }
        if (target.equals(speaker)) {
            speaker.sendMessage(ChatColor.RED + "You cannot swear an oath to yourself.");
            return false;
        }

        Nation speakerNation = nationManager.getNation(speaker.getUniqueId());
        Nation targetNation  = nationManager.getNation(target.getUniqueId());

        if (speakerNation == null || !speakerNation.isSetupComplete()) {
            speaker.sendMessage(ChatColor.RED + "You have not established your nation yet.");
            return false;
        }
        if (targetNation == null || !targetNation.isSetupComplete()) {
            speaker.sendMessage(ChatColor.RED + "That player has not established their nation yet.");
            return false;
        }

        // Validate title and nation match
        if (!targetNation.getNationName().equalsIgnoreCase(declaredNation)) {
            speaker.sendMessage(ChatColor.RED + "Nation name mismatch. Their nation is: "
                + targetNation.getNationName());
            return false;
        }
        if (!targetNation.getTitle().equalsIgnoreCase(declaredTitle)) {
            speaker.sendMessage(ChatColor.RED + "Title mismatch. Their title is: "
                + targetNation.getTitle());
            return false;
        }

        // Check existing oaths
        if (areAllied(speakerNation.getNationName(), targetNation.getNationName())) {
            speaker.sendMessage(ChatColor.YELLOW + "Your nations are already allied!");
            return false;
        }

        // Form the oath
        OathRecord oath = new OathRecord(speakerNation.getNationName(), targetNation.getNationName());
        oathsByNation.put(speakerNation.getNationName().toLowerCase(), oath);
        oathsByNation.put(targetNation.getNationName().toLowerCase(), oath);
        speakerNation.setAllyNationName(targetNation.getNationName());
        targetNation.setAllyNationName(speakerNation.getNationName());
        nationManager.saveNation(speakerNation);
        nationManager.saveNation(targetNation);
        dataManager.saveOath(oath);

        NationAnimalHelper speakerAnimal = getAnimalEmoji(speakerNation);
        NationAnimalHelper targetAnimal  = getAnimalEmoji(targetNation);

        Bukkit.broadcastMessage(ChatColor.GOLD + "⚜ AN OATH HAS BEEN SWORN! ⚜");
        Bukkit.broadcastMessage(speakerAnimal.emoji + " " + ChatColor.YELLOW + speakerNation.getNationName()
            + ChatColor.WHITE + " bears oath to "
            + targetAnimal.emoji + " " + ChatColor.YELLOW + targetNation.getNationName() + ChatColor.WHITE + "!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "\"" + speakerNation.getMotto() + "\" allies with \""
            + targetNation.getMotto() + "\"");

        return true;
    }

    // ── Betrayal detection ────────────────────────────────────────────────────

    /**
     * Call this whenever one player damages another.
     * If the attacker's nation has sworn an oath to the victim's nation, betrayal is triggered.
     */
    public void checkForBetrayal(Player attacker, Player victim) {
        if (attacker == null || victim == null) return;
        Nation attackerNation = nationManager.getNation(attacker.getUniqueId());
        Nation victimNation   = nationManager.getNation(victim.getUniqueId());
        if (attackerNation == null || victimNation == null) return;
        if (!attackerNation.isSetupComplete() || !victimNation.isSetupComplete()) return;

        if (areAllied(attackerNation.getNationName(), victimNation.getNationName())) {
            triggerBetrayal(attacker, attackerNation, victimNation);
        }
    }

    /**
     * Also check if a player is stealing from allied land.
     * Call from BlockBreakListener / PlayerInteractListener.
     */
    public void checkStealingFromAlly(Player thief, String chunkOwnerUUID) {
        Nation thiefNation = nationManager.getNation(thief.getUniqueId());
        Nation ownerNation = nationManager.getNation(chunkOwnerUUID);
        if (thiefNation == null || ownerNation == null) return;
        if (areAllied(thiefNation.getNationName(), ownerNation.getNationName())) {
            // Stealing from ally is a betrayal
            triggerBetrayal(thief, thiefNation, ownerNation);
        }
    }

    private void triggerBetrayal(Player oathbreaker, Nation breakerNation, Nation victimNation) {
        // Only punish once (don't re-punish an already oathbreaker)
        if (breakerNation.isOathbreaker()) return;

        breakerNation.applyOathbreakerPunishment();

        // Kill half their bots (calculate count BEFORE killing so the number is correct)
        int halfBots = Math.max(1, breakerNation.getBotCount() / 2);
        botManager.killBots(oathbreaker, halfBots);

        // Halve inventory resources
        halvePlayerResources(oathbreaker);

        // Dissolve the oath
        dissolveOath(breakerNation.getNationName(), victimNation.getNationName());
        breakerNation.setAllyNationName(null);
        victimNation.setAllyNationName(null);

        nationManager.saveNation(breakerNation);
        nationManager.saveNation(victimNation);

        // Update oathbreaker's name tag
        oathbreaker.setDisplayName(ChatColor.DARK_RED + "[OATHBREAKER] " + oathbreaker.getName());
        oathbreaker.setPlayerListName(ChatColor.DARK_RED + "OATHBREAKER " + oathbreaker.getName());

        String breakerAnimal = getAnimalEmoji(breakerNation).emoji;
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ BETRAYAL! ☠");
        Bukkit.broadcastMessage(breakerAnimal + " " + ChatColor.RED + oathbreaker.getName()
            + " OF " + breakerNation.getNationName().toUpperCase()
            + " IS AN OATHBREAKER!");
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "Their title is forever " + ChatColor.BOLD + "OATHBREAKER!");
        Bukkit.broadcastMessage(ChatColor.RED + "They have lost half their army and half their resources.");

        oathbreaker.sendMessage(ChatColor.DARK_RED + "You have broken your oath. Your title is now permanently: OATHBREAKER.");
        oathbreaker.sendMessage(ChatColor.RED + "You have lost bots and half your resources.");
    }

    private void halvePlayerResources(Player player) {
        player.getInventory().forEach(item -> {
            if (item != null && item.getAmount() > 1) {
                item.setAmount(item.getAmount() / 2);
            }
        });
    }

    // ── Martyr check ──────────────────────────────────────────────────────────

    /**
     * Returns true if the player died defending an ally
     * (i.e. was killed while fighting in an area adjacent to their ally's land,
     *  or simply has an active oath and was killed by an enemy of their ally).
     * In practice: player has active oath AND killer belongs to neither allied nation.
     */
    public boolean isMartyrDeath(Player dead, Player killer) {
        if (killer == null) return false;
        Nation deadNation   = nationManager.getNation(dead.getUniqueId());
        Nation killerNation = nationManager.getNation(killer.getUniqueId());
        if (deadNation == null || killerNation == null) return false;
        if (deadNation.getAllyNationName() == null) return false;
        // Martyr: dead had an ally, killer is neither the dead's nation nor the ally's nation
        boolean killerIsAlly = killerNation.getNationName().equalsIgnoreCase(deadNation.getAllyNationName());
        return !killerIsAlly;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    public boolean areAllied(String nationA, String nationB) {
        OathRecord r = oathsByNation.get(nationA.toLowerCase());
        if (r == null) return false;
        return r.isBetween(nationA, nationB);
    }

    private void dissolveOath(String nationA, String nationB) {
        oathsByNation.remove(nationA.toLowerCase());
        oathsByNation.remove(nationB.toLowerCase());
        dataManager.deleteOath(nationA, nationB);
    }

    private static class NationAnimalHelper {
        String emoji;
        NationAnimalHelper(String e) { this.emoji = e; }
    }

    private NationAnimalHelper getAnimalEmoji(Nation n) {
        if (n.getAnimalKey() == null) return new NationAnimalHelper("⚔");
        return new NationAnimalHelper(com.nationssmp.data.NationAnimal.byKey(n.getAnimalKey()).getEmoji());
    }
}
