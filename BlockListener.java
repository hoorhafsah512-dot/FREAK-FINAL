package com.nationssmp.data;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Every animal players can choose as their nation animal.
 * Each has a display name, emoji, Material icon for the GUI, and
 * a Citizens2 EntityType for the companion NPC.
 */
public enum NationAnimal {

    LION       ("lion",       "Lion",       "🦁", Material.ORANGE_DYE,     EntityType.WOLF),
    WOLF       ("wolf",       "Wolf",       "🐺", Material.GRAY_DYE,        EntityType.WOLF),
    EAGLE      ("eagle",      "Eagle",      "🦅", Material.FEATHER,         EntityType.PHANTOM),
    DRAGON     ("dragon",     "Dragon",     "🐉", Material.DRAGON_EGG,      EntityType.ENDER_DRAGON),
    BEAR       ("bear",       "Bear",       "🐻", Material.BROWN_DYE,       EntityType.POLAR_BEAR),
    TIGER      ("tiger",      "Tiger",      "🐯", Material.ORANGE_TERRACOTTA, EntityType.CAT),
    FOX        ("fox",        "Fox",        "🦊", Material.ORANGE_DYE,      EntityType.FOX),
    SERPENT    ("serpent",    "Serpent",    "🐍", Material.LIME_DYE,        EntityType.GUARDIAN),
    SCORPION   ("scorpion",   "Scorpion",   "🦂", Material.YELLOW_DYE,      EntityType.BEE),
    BULL       ("bull",       "Bull",       "🦬", Material.BROWN_TERRACOTTA,EntityType.COW),
    HORSE      ("horse",      "Horse",      "🐴", Material.SADDLE,          EntityType.HORSE),
    BUTTERFLY  ("butterfly",  "Butterfly",  "🦋", Material.PINK_DYE,        EntityType.BEE),
    SHARK      ("shark",      "Shark",      "🦈", Material.LIGHT_BLUE_DYE,  EntityType.GUARDIAN),
    ELEPHANT   ("elephant",   "Elephant",   "🐘", Material.GRAY_CONCRETE,   EntityType.IRON_GOLEM),
    RHINO      ("rhino",      "Rhino",      "🦏", Material.LIGHT_GRAY_CONCRETE, EntityType.IRON_GOLEM),
    OWL        ("owl",        "Owl",        "🦉", Material.GRAY_DYE,        EntityType.PARROT),
    LEOPARD    ("leopard",    "Leopard",    "🐆", Material.YELLOW_TERRACOTTA, EntityType.CAT),
    STAG       ("stag",       "Stag",       "🦌", Material.OAK_SAPLING,     EntityType.GOAT),
    CROCODILE  ("crocodile",  "Crocodile",  "🐊", Material.LIME_TERRACOTTA, EntityType.TURTLE),
    BAT        ("bat",        "Bat",        "🦇", Material.BLACK_DYE,       EntityType.BAT);

    private final String key;
    private final String displayName;
    private final String emoji;
    private final Material guiIcon;
    private final EntityType entityType;

    NationAnimal(String key, String displayName, String emoji,
                 Material guiIcon, EntityType entityType) {
        this.key        = key;
        this.displayName = displayName;
        this.emoji      = emoji;
        this.guiIcon    = guiIcon;
        this.entityType = entityType;
    }

    public String getKey()          { return key; }
    public String getDisplayName()  { return displayName; }
    public String getEmoji()        { return emoji; }
    public Material getGuiIcon()    { return guiIcon; }
    public EntityType getEntityType(){ return entityType; }

    public String getFullLabel() {
        return emoji + " " + displayName;
    }

    /** Look up by key string (case-insensitive). Returns LION as fallback. */
    public static NationAnimal byKey(String key) {
        if (key == null) return LION;
        for (NationAnimal a : values()) {
            if (a.key.equalsIgnoreCase(key)) return a;
        }
        return LION;
    }
}
