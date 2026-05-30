package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.BotRole;
import com.nationssmp.data.Nation;
import com.nationssmp.data.NationAnimal;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all player bot entities using vanilla Bukkit Zombies/Skeletons.
 * No Citizens dependency — pure vanilla entity API.
 */
public class BotManager {

    private static final String META_BOT_OWNER = "bot_owner";
    private static final String META_BOT_ROLE  = "bot_role";
    private static final String META_IS_BOT    = "is_nations_bot";

    private final NationsSMP plugin;
    private final NationManager nationManager;

    /** uuid → list of entity UUIDs */
    private final Map<String, List<UUID>> playerBots = new ConcurrentHashMap<>();

    /** entity UUID → player uuid (master) */
    private final Map<UUID, String> botOwner = new ConcurrentHashMap<>();

    /** entity UUID → assigned role */
    private final Map<UUID, BotRole> botRoles = new ConcurrentHashMap<>();

    private final Set<String> followMode = ConcurrentHashMap.newKeySet();
    private final Set<String> stayMode   = ConcurrentHashMap.newKeySet();
    private final Set<String> attackMode = ConcurrentHashMap.newKeySet();

    private BukkitRunnable aiTask;

    public BotManager(NationsSMP plugin, NationManager nationManager) {
        this.plugin = plugin;
        this.nationManager = nationManager;
        startAILoop();
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    public void spawnBotsForPlayer(Player player, Nation nation) {
        despawnBotsForPlayer(player.getUniqueId());

        List<UUID> ids = new ArrayList<>();
        int count = nation.getBotCount();

        BotRole[] defaults = new BotRole[count];
        Arrays.fill(defaults, BotRole.SOLDIER);
        if (count >= 10) {
            for (int i = 5;  i < 10 && i < count; i++) defaults[i] = BotRole.ARCHER;
            for (int i = 10; i < 15 && i < count; i++) defaults[i] = BotRole.MINER;
            for (int i = 15; i < 20 && i < count; i++) defaults[i] = BotRole.GUARD;
            for (int i = 20; i < 25 && i < count; i++) defaults[i] = BotRole.FARMER;
            for (int i = 25; i < count; i++)            defaults[i] = BotRole.BUILDER;
        }

        Location base = player.getLocation();

        for (int i = 0; i < count; i++) {
            BotRole role = i < defaults.length ? defaults[i] : BotRole.SOLDIER;
            Location spawnLoc = base.clone().add(
                (Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);

            Zombie bot = (Zombie) player.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
            bot.setCustomName(ChatColor.GRAY + "[" + role.getIcon() + "] " + role.getDisplayName());
            bot.setCustomNameVisible(true);
            bot.setRemoveWhenFarAway(false);
            bot.setMetadata(META_IS_BOT,   new FixedMetadataValue(plugin, true));
            bot.setMetadata(META_BOT_OWNER,new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            bot.setMetadata(META_BOT_ROLE, new FixedMetadataValue(plugin, role.name()));
            bot.setShouldBurnInDay(false);

            applyRoleStats(bot, role);
            equipBot(bot, role);

            ids.add(bot.getUniqueId());
            botOwner.put(bot.getUniqueId(), player.getUniqueId().toString());
            botRoles.put(bot.getUniqueId(), role);
        }

        playerBots.put(player.getUniqueId().toString(), ids);
        followMode.add(player.getUniqueId().toString());
        nationManager.saveNation(nation);
        player.sendMessage(ChatColor.GREEN + "⚔ " + count + " bots have been summoned to your side!");
    }

    public void despawnBotsForPlayer(UUID playerUUID) {
        String uid = playerUUID.toString();
        List<UUID> ids = playerBots.remove(uid);
        if (ids == null) return;
        for (UUID botId : ids) {
            Entity e = Bukkit.getEntity(botId);
            if (e != null) e.remove();
            botOwner.remove(botId);
            botRoles.remove(botId);
        }
        followMode.remove(uid);
        stayMode.remove(uid);
        attackMode.remove(uid);
    }

    private void applyRoleStats(Zombie bot, BotRole role) {
        try {
            var hp = bot.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(role.getMaxHealth()); bot.setHealth(role.getMaxHealth()); }
            var speed = bot.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0.23 * role.getSpeedMultiplier());
        } catch (Exception ignored) {}
    }

    private void equipBot(Zombie bot, BotRole role) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) return;
        Material[] mats = role.getEquipment();
        eq.setItemInMainHand(mats.length > 0 ? new ItemStack(mats[0]) : null);
        eq.setChestplate(mats.length > 1 ? new ItemStack(mats[1]) : null);
        eq.setLeggings(mats.length > 2 ? new ItemStack(mats[2]) : null);
        eq.setHelmet(mats.length > 3 ? new ItemStack(mats[3]) : null);
        eq.setItemInMainHandDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setHelmetDropChance(0f);
    }

    // ── AI Loop ───────────────────────────────────────────────────────────────

    private void startAILoop() {
        int ticks = plugin.getConfig().getInt("bot-ai-tick", 20);
        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, List<UUID>> entry : playerBots.entrySet()) {
                    String uid = entry.getKey();
                    Player master = Bukkit.getPlayer(UUID.fromString(uid));
                    if (master == null || !master.isOnline()) continue;

                    Iterator<UUID> it = entry.getValue().iterator();
                    while (it.hasNext()) {
                        UUID botId = it.next();
                        Entity entity = Bukkit.getEntity(botId);
                        if (entity == null || !entity.isValid() || entity.isDead()) {
                            it.remove();
                            botOwner.remove(botId);
                            botRoles.remove(botId);
                            continue;
                        }
                        if (!(entity instanceof Zombie bot)) continue;

                        if (followMode.contains(uid) && !stayMode.contains(uid)) {
                            followMaster(bot, master);
                        }
                        if (!stayMode.contains(uid)) {
                            attackNearbyEnemy(bot, master);
                        }
                    }
                }
            }
        };
        aiTask.runTaskTimer(plugin, 20L, ticks);
    }

    private void followMaster(Zombie bot, Player master) {
        double maxDist = plugin.getConfig().getDouble("bot-max-follow-distance", 60);
        double dist = bot.getLocation().distance(master.getLocation());
        if (dist > maxDist) {
            bot.teleport(master.getLocation().clone().add(
                (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3));
        } else if (dist > 4) {
            bot.getPathfinder().moveTo(master);
        }
    }

    private void attackNearbyEnemy(Zombie bot, Player master) {
        double range = plugin.getConfig().getDouble("bot-combat-range", 8);
        double dmg   = plugin.getConfig().getDouble("bot-damage", 4.0);
        BotRole role = botRoles.get(bot.getUniqueId());
        if (role != null) dmg = role.getDamage();

        LivingEntity target = null;
        double closest = range;
        for (Entity nearby : bot.getNearbyEntities(range, range, range)) {
            if (nearby instanceof Monster || nearby instanceof Slime) {
                double d = nearby.getLocation().distance(bot.getLocation());
                if (d < closest) { closest = d; target = (LivingEntity) nearby; }
            }
            if (nearby instanceof Player enemy && !enemy.equals(master)) {
                Nation myNation = nationManager.getNation(master.getUniqueId());
                Nation theirNation = nationManager.getNation(enemy.getUniqueId());
                if (myNation != null && theirNation != null) {
                    boolean allied = myNation.getAllyNationName() != null
                        && myNation.getAllyNationName().equalsIgnoreCase(theirNation.getNationName());
                    if (!allied && attackMode.contains(master.getUniqueId().toString())) {
                        double d = nearby.getLocation().distance(bot.getLocation());
                        if (d < closest) { closest = d; target = (LivingEntity) enemy; }
                    }
                }
            }
        }

        if (target != null) {
            bot.getPathfinder().moveTo(target);
            target.damage(dmg, bot);
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public void setFollow(Player player) {
        String uid = player.getUniqueId().toString();
        followMode.add(uid);
        stayMode.remove(uid);
        player.sendMessage(ChatColor.GREEN + "⚔ Bots are now following you.");
    }

    public void setStay(Player player) {
        String uid = player.getUniqueId().toString();
        stayMode.add(uid);
        followMode.remove(uid);
        for (UUID botId : getBotIds(player)) {
            Entity e = Bukkit.getEntity(botId);
            if (e instanceof Zombie z) z.getPathfinder().stopPathfinding();
        }
        player.sendMessage(ChatColor.YELLOW + "⚔ Bots are holding position.");
    }

    public void setAttack(Player player) {
        attackMode.add(player.getUniqueId().toString());
        player.sendMessage(ChatColor.RED + "⚔ Bots are now attacking all nearby enemies!");
    }

    public void setDefend(Player player) {
        attackMode.remove(player.getUniqueId().toString());
        stayMode.remove(player.getUniqueId().toString());
        followMode.add(player.getUniqueId().toString());
        player.sendMessage(ChatColor.AQUA + "⚔ Bots are forming a defensive perimeter.");
    }

    public void sendMine(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⛏ Miner bots are heading underground to gather resources.");
        for (UUID botId : getBotIds(player)) {
            if (botRoles.get(botId) != BotRole.MINER) continue;
            Entity e = Bukkit.getEntity(botId);
            if (e instanceof Zombie z) {
                Location below = player.getLocation().clone().subtract(0, 5, 0);
                z.getPathfinder().moveTo(below);
            }
        }
    }

    public boolean assignRole(Player player, int botIndex, BotRole role) {
        List<UUID> ids = getBotIds(player);
        if (botIndex < 0 || botIndex >= ids.size()) return false;
        UUID botId = ids.get(botIndex);
        botRoles.put(botId, role);
        Entity e = Bukkit.getEntity(botId);
        if (e instanceof Zombie z) {
            z.setCustomName(ChatColor.GRAY + "[" + role.getIcon() + "] " + role.getDisplayName());
            equipBot(z, role);
            applyRoleStats(z, role);
        }
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n != null) {
            n.getBotRoles().put(botIndex, role.name());
            nationManager.saveNation(n);
        }
        return true;
    }

    public void sendStatusMessage(Player player) {
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n == null) return;
        player.sendMessage(ChatColor.GOLD + "── Army Status ──────────────────");
        player.sendMessage(ChatColor.YELLOW + "Bots alive: " + ChatColor.WHITE + getBotIds(player).size() + "/" + n.getBotCount());
        player.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + getCurrentMode(player));
        Map<BotRole, Integer> roleCounts = new HashMap<>();
        for (UUID id : getBotIds(player)) {
            BotRole r = botRoles.getOrDefault(id, BotRole.SOLDIER);
            roleCounts.merge(r, 1, Integer::sum);
        }
        roleCounts.forEach((r, c) ->
            player.sendMessage("  " + r.getIcon() + " " + r.getDisplayName() + ": " + c));
        player.sendMessage(ChatColor.GOLD + "──────────────────────────────");
    }

    public void spamTitleAndMotto(Player player) {
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n == null) return;
        NationAnimal animalEnum = NationAnimal.byKey(n.getAnimalKey());
        String shout = animalEnum.getEmoji() + " ALL HAIL " + player.getName()
            + ", " + n.getTitle() + " OF " + n.getNationName().toUpperCase()
            + "! \"" + n.getMotto() + "\"";
        List<UUID> ids = getBotIds(player);
        int delay = 0;
        for (UUID botId : ids) {
            int d = delay;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                Bukkit.broadcastMessage(ChatColor.GRAY + "[Bot] " + shout), d);
            delay += 1;
        }
    }

    public void sendBotList(Player player) {
        List<UUID> ids = getBotIds(player);
        player.sendMessage(ChatColor.GOLD + "── Bot List (" + ids.size() + " bots) ──");
        for (int i = 0; i < ids.size(); i++) {
            BotRole r = botRoles.getOrDefault(ids.get(i), BotRole.SOLDIER);
            player.sendMessage(ChatColor.GRAY + "#" + i + " → " + r.getIcon() + " " + r.getDisplayName());
        }
    }

    public int killBots(Player player, int count) {
        List<UUID> ids = playerBots.getOrDefault(player.getUniqueId().toString(), new ArrayList<>());
        int killed = 0;
        Iterator<UUID> it = ids.iterator();
        while (it.hasNext() && killed < count) {
            UUID botId = it.next();
            Entity e = Bukkit.getEntity(botId);
            if (e != null) e.remove();
            botOwner.remove(botId);
            botRoles.remove(botId);
            it.remove();
            killed++;
        }
        Nation n = nationManager.getNation(player.getUniqueId());
        if (n != null) {
            n.setBotCount(Math.max(0, n.getBotCount() - killed));
            nationManager.saveNation(n);
        }
        return killed;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public List<UUID> getBotIds(Player player) {
        return playerBots.getOrDefault(player.getUniqueId().toString(), Collections.emptyList());
    }

    private String getCurrentMode(Player player) {
        String uid = player.getUniqueId().toString();
        if (stayMode.contains(uid))   return "HOLD POSITION";
        if (attackMode.contains(uid)) return "ATTACK";
        return "FOLLOW";
    }

    public boolean isNationsBot(Entity entity) {
        return entity.hasMetadata(META_IS_BOT);
    }

    public boolean isOwnerOfBot(UUID botId, UUID playerUUID) {
        return playerUUID.toString().equals(botOwner.get(botId));
    }

    public void shutdown() {
        if (aiTask != null) aiTask.cancel();
    }
}
