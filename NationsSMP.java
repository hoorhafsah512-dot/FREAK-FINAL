package com.nationssmp.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Defines every bot role with its display name, health, damage, speed,
 * equipment and description. Stats are balanced but specialised.
 */
public enum BotRole {

    SOLDIER("Soldier", "⚔", 30, 5.0, 1.0,
            new Material[]{Material.IRON_SWORD, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_HELMET, Material.SHIELD},
            "Heavy melee fighter. High HP, medium speed."),

    ARCHER("Archer", "🏹", 16, 4.0, 1.2,
            new Material[]{Material.BOW, Material.LEATHER_CHESTPLATE, Material.ARROW},
            "Ranged attacker. Low HP, high speed."),

    MAGE("Mage", "🧙", 18, 6.0, 0.95,
            new Material[]{Material.BLAZE_ROD, Material.SPLASH_POTION, Material.GLASS_BOTTLE},
            "Area damage dealer. Throws splash potions."),

    MINER("Miner", "⛏", 20, 3.0, 1.0,
            new Material[]{Material.DIAMOND_PICKAXE, Material.TORCH, Material.IRON_INGOT},
            "Mines resources for the nation automatically."),

    FARMER("Farmer", "🌾", 14, 2.0, 1.0,
            new Material[]{Material.DIAMOND_HOE, Material.WHEAT_SEEDS, Material.BREAD},
            "Harvests and plants crops near the nation base."),

    LUMBERJACK("Lumberjack", "🪓", 20, 4.0, 1.0,
            new Material[]{Material.DIAMOND_AXE, Material.OAK_LOG},
            "Chops trees for wood resources."),

    GUARD("Guard", "🛡", 40, 4.0, 0.85,
            new Material[]{Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_HELMET, Material.DIAMOND_BOOTS, Material.IRON_SWORD},
            "Highest HP. Patrols and protects the master."),

    ASSASSIN("Assassin", "🗡", 14, 9.0, 1.4,
            new Material[]{Material.NETHERITE_SWORD, Material.LEATHER_CHESTPLATE},
            "Extreme damage, low HP. Targets enemies first."),

    ALCHEMIST("Alchemist", "🧪", 16, 2.0, 1.0,
            new Material[]{Material.BREWING_STAND, Material.POTION, Material.GLASS_BOTTLE},
            "Heals nearby allies. Non-combat support."),

    SCOUT("Scout", "🐴", 16, 3.0, 1.5,
            new Material[]{Material.LEATHER_BOOTS, Material.MAP},
            "Fastest unit. Explores and reports enemy positions."),

    TRADER("Trader", "💰", 16, 2.0, 1.0,
            new Material[]{Material.GOLD_INGOT, Material.CHEST},
            "Carries loot and auto-deposits into storage."),

    BUILDER("Builder", "🏗", 20, 2.0, 1.0,
            new Material[]{Material.OAK_PLANKS, Material.STONE, Material.BRICKS},
            "Collects building materials. Non-combat.");

    private final String displayName;
    private final String icon;
    private final double maxHealth;
    private final double damage;
    private final double speedMultiplier;
    private final Material[] equipment;
    private final String description;

    BotRole(String displayName, String icon, double maxHealth, double damage,
             double speedMultiplier, Material[] equipment, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speedMultiplier = speedMultiplier;
        this.equipment = equipment;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    public double getMaxHealth() { return maxHealth; }
    public double getDamage() { return damage; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public Material[] getEquipment() { return equipment; }
    public String getDescription() { return description; }

    /** The primary weapon material for this role (first equipment slot). */
    public Material getPrimaryWeapon() {
        return equipment.length > 0 ? equipment[0] : Material.AIR;
    }

    /** Return the role matching a string name (case-insensitive). */
    public static BotRole fromString(String name) {
        for (BotRole role : values()) {
            if (role.name().equalsIgnoreCase(name) || role.displayName.equalsIgnoreCase(name)) {
                return role;
            }
        }
        return null;
    }

    public List<Material> getEquipmentList() {
        return Arrays.asList(equipment);
    }
}
