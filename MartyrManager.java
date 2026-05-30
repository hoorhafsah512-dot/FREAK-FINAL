package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.Nation.SetupStep;
import com.nationssmp.data.NationAnimal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for nation data.
 * Also drives the first-login setup flow.
 */
public class NationManager {

    private final NationsSMP plugin;
    private final DataManager data;

    /** uuid → Nation (live cache) */
    private final Map<String, Nation> nations = new ConcurrentHashMap<>();

    /** nation name (lowercase) → uuid  –  for fast lookup */
    private final Map<String, String> nameIndex = new ConcurrentHashMap<>();

    /** Animals already chosen (lowercase key) */
    private final Set<String> takenAnimals = ConcurrentHashMap.newKeySet();

    /** Building blocks already chosen (material name) */
    private final Set<String> takenBlocks = ConcurrentHashMap.newKeySet();

    // The 50 stone-type block options
    private static final Material[] BLOCK_OPTIONS = {
        Material.STONE, Material.COBBLESTONE, Material.SMOOTH_STONE, Material.STONE_BRICKS,
        Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.MOSSY_COBBLESTONE,
        Material.ANDESITE, Material.POLISHED_ANDESITE, Material.DIORITE, Material.POLISHED_DIORITE,
        Material.GRANITE, Material.POLISHED_GRANITE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
        Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
        Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE,
        Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS, Material.CALCITE,
        Material.BASALT, Material.SMOOTH_BASALT, Material.POLISHED_BASALT, Material.BLACKSTONE,
        Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
        Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE,
        Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE,
        Material.CHISELED_SANDSTONE, Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE,
        Material.CUT_RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE,
        Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
        Material.MUD_BRICKS, Material.PACKED_MUD, Material.TERRACOTTA,
        Material.BRICKS, Material.CHISELED_TUFF, Material.CHISELED_STONE_BRICKS
    };

    public NationManager(NationsSMP plugin, DataManager data) {
        this.plugin = plugin;
        this.data   = data;
        loadAll();
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    private void loadAll() {
        nations.clear();
        nameIndex.clear();
        takenAnimals.clear();
        takenBlocks.clear();
        data.loadAllNations().forEach((uuid, nation) -> {
            nations.put(uuid, nation);
            if (nation.getNationName() != null)
                nameIndex.put(nation.getNationName().toLowerCase(), uuid);
            if (nation.getAnimalKey() != null)
                takenAnimals.add(nation.getAnimalKey().toLowerCase());
            if (nation.getBuildingBlock() != null)
                takenBlocks.add(nation.getBuildingBlock());
        });
        plugin.getLogger().info("Loaded " + nations.size() + " nations.");
    }

    public void saveAll() {
        nations.values().forEach(data::saveNation);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Nation getNation(UUID uuid)          { return nations.get(uuid.toString()); }
    public Nation getNation(String uuid)         { return nations.get(uuid); }
    public Nation getNationByName(String name)  {
        String uuid = nameIndex.get(name.toLowerCase());
        return uuid != null ? nations.get(uuid) : null;
    }

    public Collection<Nation> getAllNations()   { return nations.values(); }
    public boolean nationNameTaken(String name){ return nameIndex.containsKey(name.toLowerCase()); }
    public boolean animalTaken(String key)     { return takenAnimals.contains(key.toLowerCase()); }
    public boolean blockTaken(String mat)      { return takenBlocks.contains(mat); }
    public Material[] getBlockOptions()        { return BLOCK_OPTIONS; }

    // ── Create ────────────────────────────────────────────────────────────────

    /** Create a brand-new nation entry for a first-time player. */
    public Nation createNation(Player player) {
        String uuid = player.getUniqueId().toString();
        Nation n = new Nation(uuid, player.getName());
        nations.put(uuid, n);
        return n;
    }

    public void finaliseNation(Nation n) {
        nameIndex.put(n.getNationName().toLowerCase(), n.getPlayerUUID());
        takenAnimals.add(n.getAnimalKey().toLowerCase());
        takenBlocks.add(n.getBuildingBlock());
        n.setSetupComplete(true);
        data.saveNation(n);
    }

    public void saveNation(Nation n) {
        data.saveNation(n);
    }

    // ── First-login setup flow ────────────────────────────────────────────────

    /**
     * Start the setup wizard for a player who has never played before.
     * We drive them through chat prompts, capturing each response.
     */
    public void beginSetup(Player player) {
        Nation n = createNation(player);
        n.setPendingStep(SetupStep.AWAITING_NATION_NAME);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚔ Welcome to NationsSMP ⚔");
        player.sendMessage(ChatColor.YELLOW + "You must establish your nation before you can play.");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Step 1/5 » " + ChatColor.AQUA
            + "Type your NATION NAME in chat (no spaces):");
    }

    /**
     * Handle a chat message from a player who is mid-setup.
     * Returns true if the message was consumed by the setup flow.
     */
    public boolean handleSetupInput(Player player, String message) {
        Nation n = getNation(player.getUniqueId());
        if (n == null || n.isSetupComplete()) return false;

        SetupStep step = n.getPendingStep();
        if (step == SetupStep.NONE) return false;

        String input = message.trim();

        switch (step) {
            case AWAITING_NATION_NAME: {
                if (input.length() < 2 || input.length() > 24) {
                    player.sendMessage(ChatColor.RED + "Nation name must be 2-24 characters. Try again:");
                    return true;
                }
                if (input.contains(" ")) {
                    player.sendMessage(ChatColor.RED + "Nation name cannot contain spaces. Try again:");
                    return true;
                }
                if (nationNameTaken(input)) {
                    player.sendMessage(ChatColor.RED + "That nation name is taken! Choose another:");
                    return true;
                }
                n.setNationName(input);
                n.setPendingStep(SetupStep.AWAITING_TITLE);
                player.sendMessage(ChatColor.GREEN + "✔ Nation: " + ChatColor.GOLD + input);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "Step 2/5 » " + ChatColor.AQUA
                    + "Type your TITLE (e.g. 'The Unconquered', 'Shadow of the East'):");
                return true;
            }
            case AWAITING_TITLE: {
                if (input.length() < 2 || input.length() > 40) {
                    player.sendMessage(ChatColor.RED + "Title must be 2-40 characters. Try again:");
                    return true;
                }
                n.setTitle(input);
                n.setPendingStep(SetupStep.AWAITING_MOTTO);
                player.sendMessage(ChatColor.GREEN + "✔ Title: " + ChatColor.GOLD + input);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "Step 3/5 » " + ChatColor.AQUA
                    + "Type your NATION WORDS / MOTTO (GOT-style, e.g. 'From Ash We Rise'):");
                return true;
            }
            case AWAITING_MOTTO: {
                if (input.length() < 3 || input.length() > 60) {
                    player.sendMessage(ChatColor.RED + "Motto must be 3-60 characters. Try again:");
                    return true;
                }
                n.setMotto(input);
                n.setPendingStep(SetupStep.AWAITING_ANIMAL_SELECTION);
                player.sendMessage(ChatColor.GREEN + "✔ Motto: " + ChatColor.GOLD + input);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "Step 4/5 » " + ChatColor.AQUA
                    + "Opening ANIMAL selection menu...");
                Bukkit.getScheduler().runTask(plugin, () -> openAnimalGUI(player));
                return true;
            }
            default:
                return false;
        }
    }

    // ── Animal GUI ────────────────────────────────────────────────────────────

    public void openAnimalGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Choose Your Nation Animal");
        int slot = 0;
        for (NationAnimal animal : NationAnimal.values()) {
            if (slot >= 27) break;
            boolean taken = animalTaken(animal.getKey());
            ItemStack icon = new ItemStack(taken ? Material.BARRIER : animal.getGuiIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((taken ? ChatColor.RED + "✘ " : ChatColor.GREEN + "✔ ")
                    + animal.getEmoji() + " " + animal.getDisplayName());
                meta.setLore(taken ? Collections.singletonList(ChatColor.RED + "Already chosen by another nation!")
                    : Collections.singletonList(ChatColor.GRAY + "Click to choose " + animal.getDisplayName()));
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        player.openInventory(inv);
    }

    // ── Block GUI ─────────────────────────────────────────────────────────────

    public void openBlockGUI(Player player) {
        int rows = (int) Math.ceil(BLOCK_OPTIONS.length / 9.0);
        rows = Math.min(rows + 1, 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, ChatColor.DARK_AQUA + "Choose Your Nation Block");
        for (int i = 0; i < BLOCK_OPTIONS.length && i < rows * 9; i++) {
            Material mat = BLOCK_OPTIONS[i];
            boolean taken = blockTaken(mat.name());
            ItemStack icon = new ItemStack(taken ? Material.BARRIER : mat);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String pretty = mat.name().replace("_", " ");
                meta.setDisplayName((taken ? ChatColor.RED + "✘ " : ChatColor.GREEN + "✔ ") + pretty);
                meta.setLore(taken
                    ? Collections.singletonList(ChatColor.RED + "Already chosen by another nation!")
                    : Arrays.asList(ChatColor.GRAY + "You'll receive 64x " + pretty,
                                    ChatColor.GRAY + "Click to select."));
                icon.setItemMeta(meta);
            }
            inv.setItem(i, icon);
        }
        player.openInventory(inv);
    }

    /** Called by InventoryClickListener when a player clicks the animal GUI. */
    public void handleAnimalChoice(Player player, int slot) {
        NationAnimal[] animals = NationAnimal.values();
        if (slot < 0 || slot >= animals.length) return;
        NationAnimal chosen = animals[slot];
        if (animalTaken(chosen.getKey())) {
            player.sendMessage(ChatColor.RED + "That animal is already taken! Choose another.");
            Bukkit.getScheduler().runTask(plugin, () -> openAnimalGUI(player));
            return;
        }
        Nation n = getNation(player.getUniqueId());
        if (n == null) return;
        n.setAnimalKey(chosen.getKey());
        n.setPendingStep(SetupStep.AWAITING_BLOCK_SELECTION);
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "✔ Animal: " + chosen.getEmoji() + " " + chosen.getDisplayName());
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Step 5/5 » " + ChatColor.AQUA + "Opening BLOCK selection menu...");
        Bukkit.getScheduler().runTask(plugin, () -> openBlockGUI(player));
    }

    /** Called by InventoryClickListener when a player clicks the block GUI. */
    public void handleBlockChoice(Player player, Material mat) {
        if (blockTaken(mat.name())) {
            player.sendMessage(ChatColor.RED + "That block is already taken! Choose another.");
            Bukkit.getScheduler().runTask(plugin, () -> openBlockGUI(player));
            return;
        }
        Nation n = getNation(player.getUniqueId());
        if (n == null) return;
        n.setBuildingBlock(mat.name());
        n.setPendingStep(SetupStep.NONE);
        player.closeInventory();

        // Give them their stack
        player.getInventory().addItem(new ItemStack(mat, 64));

        finaliseNation(n);
        broadcastNationBorn(player, n);
        plugin.getBotManager().spawnBotsForPlayer(player, n);
        plugin.getAnimalCompanionManager().spawnCompanion(player, n);
    }

    private void broadcastNationBorn(Player player, Nation n) {
        NationAnimal animal = NationAnimal.byKey(n.getAnimalKey());
        String msg = ChatColor.GOLD + "⚔ A new nation rises! "
            + animal.getEmoji() + " " + ChatColor.YELLOW + n.getNationName()
            + ChatColor.WHITE + " under " + ChatColor.AQUA + player.getName()
            + ChatColor.WHITE + ", " + n.getTitle()
            + ChatColor.GRAY + " — \"" + n.getMotto() + "\"";
        Bukkit.broadcastMessage(msg);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Transfer all of nation A's data to nation B (inheritance). */
    public void transferEverything(Nation from, Nation to, Player toPlayer) {
        // Transfer claimed chunks
        to.getClaimedChunks().addAll(from.getClaimedChunks());
        from.getClaimedChunks().clear();

        // Bots will be handled by BotManager
        // Resources would need inventory management (done elsewhere)

        toPlayer.sendMessage(ChatColor.GOLD + "⚔ You have inherited the nation of "
            + from.getNationName() + "!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "📜 "
            + from.getNationName() + " has been inherited by " + to.getNationName() + "!");

        data.saveNation(from);
        data.saveNation(to);
    }
}
