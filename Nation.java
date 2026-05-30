package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages the 5 legendary items.
 *
 * 1. OATH KEEPER   – Overworld, non-oathbreakers only
 * 2. OATH BREAKER  – Nether,    oathbreakers only
 * 3. NETHERITE KIT – Split half Overworld / half Nether, anyone
 * 4. BOOK OF COMMAND – Sky (high Y), anyone, one use
 * 5. OBSIDIAN SWORD  – Ancient City chest, dragon killer only
 *
 * Items are placed into chests at random hard-to-find coordinates.
 * Coordinates are saved to legendary.yml so they persist across restarts.
 */
public class LegendaryItemManager {

    // NBT tag keys for item identification
    public static final String TAG_OATH_KEEPER    = "oath_keeper";
    public static final String TAG_OATH_BREAKER   = "oath_breaker";
    public static final String TAG_BOOK_OF_CMD    = "book_of_command";
    public static final String TAG_OBSIDIAN_SWORD = "obsidian_sword";
    public static final String TAG_NETHERITE_KIT  = "netherite_kit_piece";
    public static final String TAG_LEGENDARY       = "legendary_item";

    private final NationsSMP plugin;
    private final NationManager nationManager;
    private final FileConfiguration legendaryConfig;

    public LegendaryItemManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        this.legendaryConfig = plugin.getDataManager().getLegendaryConfig();
        ensureItemsSpawned();
    }

    // ── Spawn items once (on first server start) ──────────────────────────────

    private void ensureItemsSpawned() {
        if (legendaryConfig.getBoolean("spawned", false)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnOathKeeper();
            spawnOathBreaker();
            spawnNetheriteKit();
            spawnBookOfCommand();
            // Obsidian Sword is already in a vanilla Ancient City chest
            // We'll inject it when a player opens a chest in the Deep Dark
            legendaryConfig.set("spawned", true);
            plugin.getDataManager().saveLegendary();
            plugin.getLogger().info("Legendary items spawned.");
        }, 100L);
    }

    // ── OATH KEEPER ──────────────────────────────────────────────────────────

    private void spawnOathKeeper() {
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        // Random coordinates far from spawn, below ground
        Random r = new Random();
        int x = (r.nextBoolean() ? 1 : -1) * (800 + r.nextInt(1200));
        int z = (r.nextBoolean() ? 1 : -1) * (800 + r.nextInt(1200));
        int y = 30 + r.nextInt(20);
        Location loc = new Location(world, x, y, z);
        placeChestWithItem(loc, buildOathKeeperItem());
        legendaryConfig.set("oath_keeper.x", x);
        legendaryConfig.set("oath_keeper.y", y);
        legendaryConfig.set("oath_keeper.z", z);
        plugin.getLogger().info("OATH KEEPER placed at " + x + "," + y + "," + z);
    }

    public ItemStack buildOathKeeperItem() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return sword;
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "⚔ OATH KEEPER ⚔");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Forged in the fires of honour.",
            ChatColor.WHITE + "Wielded only by the true of word.",
            "",
            ChatColor.YELLOW + "Damage: " + ChatColor.WHITE + "Extreme",
            ChatColor.GREEN + "Only the honourable may wield this.",
            ChatColor.RED + "Oathbreakers cannot touch it."
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 10, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 3, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        // Tag it
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_OATH_KEEPER), PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(meta);
        return sword;
    }

    // ── OATH BREAKER MACE ────────────────────────────────────────────────────

    private void spawnOathBreaker() {
        World nether = Bukkit.getWorld("world_nether");
        if (nether == null) return;
        Random r = new Random();
        int x = (r.nextBoolean() ? 1 : -1) * (600 + r.nextInt(800));
        int z = (r.nextBoolean() ? 1 : -1) * (600 + r.nextInt(800));
        int y = 40 + r.nextInt(30);
        Location loc = new Location(nether, x, y, z);
        placeChestWithItem(loc, buildOathBreakerItem());
        legendaryConfig.set("oath_breaker.x", x);
        legendaryConfig.set("oath_breaker.y", y);
        legendaryConfig.set("oath_breaker.z", z);
        plugin.getLogger().info("OATH BREAKER placed at nether " + x + "," + y + "," + z);
    }

    public ItemStack buildOathBreakerItem() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        if (meta == null) return mace;
        meta.setDisplayName(ChatColor.DARK_RED + "" + ChatColor.BOLD + "🔨 OATH BREAKER 🔨");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Born from treachery and flame.",
            ChatColor.WHITE + "Only the condemned may lift it.",
            "",
            ChatColor.YELLOW + "Damage: " + ChatColor.WHITE + "Extreme",
            ChatColor.DARK_RED + "Only Oathbreakers may wield this.",
            ChatColor.GREEN + "Honourable players cannot touch it."
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 10, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_OATH_BREAKER), PersistentDataType.BYTE, (byte) 1);
        mace.setItemMeta(meta);
        return mace;
    }

    // ── FULL NETHERITE KIT ───────────────────────────────────────────────────

    private void spawnNetheriteKit() {
        World world  = Bukkit.getWorld("world");
        World nether = Bukkit.getWorld("world_nether");
        if (world == null || nether == null) return;
        Random r = new Random();

        Material[] overworldPieces = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE};
        Material[] netherPieces    = {Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};

        for (Material piece : overworldPieces) {
            int x = (r.nextBoolean() ? 1 : -1) * (1000 + r.nextInt(2000));
            int z = (r.nextBoolean() ? 1 : -1) * (1000 + r.nextInt(2000));
            placeChestWithItem(new Location(world, x, 20 + r.nextInt(15), z), buildKitPiece(piece));
        }
        for (Material piece : netherPieces) {
            int x = (r.nextBoolean() ? 1 : -1) * (800 + r.nextInt(1200));
            int z = (r.nextBoolean() ? 1 : -1) * (800 + r.nextInt(1200));
            placeChestWithItem(new Location(nether, x, 30 + r.nextInt(20), z), buildKitPiece(piece));
        }
    }

    private ItemStack buildKitPiece(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = mat.name().replace("NETHERITE_", "").replace("_", " ");
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "🛡 " + name + " (Legendary Kit)");
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "Part of the Legendary Netherite Kit.",
            ChatColor.WHITE + "Collect all 4 pieces.",
            ChatColor.YELLOW + "Defence: Extreme"
        ));
        meta.addEnchant(Enchantment.PROTECTION, 6, true);
        meta.addEnchant(Enchantment.FIRE_PROTECTION, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_NETHERITE_KIT), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── BOOK OF COMMAND ──────────────────────────────────────────────────────

    private void spawnBookOfCommand() {
        World world = Bukkit.getWorld("world");
        if (world == null) return;
        Random r = new Random();
        int x = (r.nextBoolean() ? 1 : -1) * (1500 + r.nextInt(2000));
        int z = (r.nextBoolean() ? 1 : -1) * (1500 + r.nextInt(2000));
        int y = 200 + r.nextInt(80); // Very high in the sky
        Location loc = new Location(world, x, y, z);
        placeChestWithItem(loc, buildBookOfCommand());
        legendaryConfig.set("book_of_command.x", x);
        legendaryConfig.set("book_of_command.y", y);
        legendaryConfig.set("book_of_command.z", z);
        plugin.getLogger().info("BOOK OF COMMAND placed at " + x + "," + y + "," + z + " (sky)");
    }

    public ItemStack buildBookOfCommand() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        var meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        if (meta == null) return book;
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "📖 BOOK OF COMMAND 📖");
        meta.setTitle("Book of Command");
        meta.setAuthor("The Server");
        meta.addPage(ChatColor.DARK_PURPLE + "This book holds supreme power.\n\n"
            + ChatColor.BLACK + "One word, one death.\n\nType:\n/bookcommand <player>\n\n"
            + ChatColor.RED + "The book vanishes after use.\nUse wisely.");
        meta.setLore(Arrays.asList(
            ChatColor.DARK_PURPLE + "One use — kills any player.",
            ChatColor.GRAY + "All their loot is transferred to you.",
            ChatColor.RED + "Vanishes after use.",
            ChatColor.DARK_GRAY + "[Right-click to read / use]"
        ));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_BOOK_OF_CMD), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    // ── OBSIDIAN SWORD (injected into deep dark) ──────────────────────────────

    public ItemStack buildObsidianSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return sword;
        meta.setDisplayName(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "🗡 OBSIDIAN SWORD 🗡");
        meta.setLore(Arrays.asList(
            ChatColor.DARK_GRAY + "Forged in the void beneath.",
            ChatColor.WHITE + "Cannot harm the living.",
            "",
            ChatColor.DARK_RED + "One-shots the Dragon.",
            ChatColor.RED + "WARNING: Glitch hunts its holder.",
            ChatColor.DARK_RED + "50 hours after looting, you die.",
            "",
            ChatColor.DARK_GRAY + "Leave it in the chest... or dare."
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, TAG_OBSIDIAN_SWORD), PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(meta);
        return sword;
    }

    // ── Item identification helpers ───────────────────────────────────────────

    public boolean isLegendaryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(new NamespacedKey(plugin, TAG_LEGENDARY), PersistentDataType.BYTE);
    }

    public boolean hasTag(ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(new NamespacedKey(plugin, tag), PersistentDataType.BYTE);
    }

    public boolean isOathKeeper(ItemStack item)    { return hasTag(item, TAG_OATH_KEEPER); }
    public boolean isOathBreaker(ItemStack item)   { return hasTag(item, TAG_OATH_BREAKER); }
    public boolean isBookOfCommand(ItemStack item) { return hasTag(item, TAG_BOOK_OF_CMD); }
    public boolean isObsidianSword(ItemStack item) { return hasTag(item, TAG_OBSIDIAN_SWORD); }
    public boolean isNetheriteKitPiece(ItemStack item) { return hasTag(item, TAG_NETHERITE_KIT); }

    // ── Chest placement ──────────────────────────────────────────────────────

    private void placeChestWithItem(Location loc, ItemStack item) {
        World world = loc.getWorld();
        if (world == null) return;
        // Ensure chunks are loaded
        world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true);
        org.bukkit.block.Block block = world.getBlockAt(loc);
        block.setType(Material.CHEST);
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            chest.getInventory().setItem(new Random().nextInt(27), item);
            chest.update();
        }
    }

    // ── Access control enforcement (called from PlayerInteractListener) ───────

    /**
     * If a player tries to pick up / hold a legendary item that they're not
     * allowed to have, remove it from their inventory and drop it.
     */
    public void enforceItemRestrictions(Player player) {
        Nation nation = nationManager.getNation(player.getUniqueId());
        if (nation == null) return;

        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;

            if (isOathKeeper(item) && nation.isOathbreaker()) {
                inv.setItem(i, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.RED + "⚔ The OATH KEEPER rejects you, Oathbreaker!");
            }
            if (isOathBreaker(item) && !nation.isOathbreaker()) {
                inv.setItem(i, null);
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.RED + "🔨 The OATH BREAKER refuses a honourable hand!");
            }
        }
    }
}
