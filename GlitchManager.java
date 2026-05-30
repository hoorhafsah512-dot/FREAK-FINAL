package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles trophy animal heads.
 * When a player kills another, the defeated nation's animal head drops as a custom item.
 * It can be hung on any wall and never destroyed once placed (tracked like grave blocks).
 */
public class TrophyManager {

    private final NationsSMP plugin;
    private final NationManager nationManager;

    private final Set<String> trophyBlocks = new HashSet<>();

    public TrophyManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        loadTrophies();
    }

    private void loadTrophies() {
        List<String> saved = plugin.getDataManager().getLegendaryConfig().getStringList("trophyBlocks");
        trophyBlocks.addAll(saved);
    }

    private void saveTrophies() {
        plugin.getDataManager().getLegendaryConfig().set("trophyBlocks", new ArrayList<>(trophyBlocks));
        plugin.getDataManager().saveLegendary();
    }

    // ── Generate trophy head ──────────────────────────────────────────────────

    /**
     * Create a trophy head item for the defeated nation and give it to the killer.
     */
    public void awardTrophyHead(Player killer, Nation killedNation) {
        NationAnimal animal = NationAnimal.byKey(killedNation.getAnimalKey());
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(ChatColor.DARK_RED + "☠ Trophy: " + animal.getEmoji()
            + " Head of " + killedNation.getNationName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Nation: " + killedNation.getNationName());
        lore.add(ChatColor.GRAY + "Animal: " + animal.getEmoji() + " " + animal.getDisplayName());
        lore.add(ChatColor.GOLD + "Title: " + killedNation.getTitle());
        lore.add(ChatColor.GRAY + "Motto: \"" + killedNation.getMotto() + "\"");
        lore.add("");
        lore.add(ChatColor.RED + "Fallen on: " + date);
        lore.add(ChatColor.DARK_GRAY + "[Trophy Head — Hang on any wall]");
        meta.setLore(lore);

        // Mark as trophy item with a persistent data tag
        var dc = meta.getPersistentDataContainer();
        dc.set(new NamespacedKey(plugin, "trophy_nation"), org.bukkit.persistence.PersistentDataType.STRING,
            killedNation.getNationName());
        dc.set(new NamespacedKey(plugin, "is_trophy"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

        head.setItemMeta(meta);
        killer.getInventory().addItem(head);
        killer.sendMessage(ChatColor.GOLD + "🏆 You have claimed the trophy head of "
            + animal.getEmoji() + " " + killedNation.getNationName() + "!");
    }

    // ── Trophy block protection ───────────────────────────────────────────────

    /** Call when a player places a skull block – check if it's a trophy and track it. */
    public void onSkullPlaced(Block block, ItemStack item) {
        if (!isTrophyItem(item)) return;
        trophyBlocks.add(blockKey(block));
        saveTrophies();
    }

    /** Returns true if the block is a placed trophy skull (indestructible). */
    public boolean isTrophyBlock(Block block) {
        return trophyBlocks.contains(blockKey(block));
    }

    public boolean isTrophyItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(new NamespacedKey(plugin, "is_trophy"), org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private String blockKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}
