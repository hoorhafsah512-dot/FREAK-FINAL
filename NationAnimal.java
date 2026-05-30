package com.nationssmp.managers;

import com.nationssmp.NationsSMP;
import com.nationssmp.data.Nation;
import com.nationssmp.data.OathRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles all YAML-based persistence.
 * Files:
 *   nations.yml   – one section per player UUID
 *   oaths.yml     – list of active oaths
 *   lands.yml     – chunk ownership
 *   legendary.yml – legendary item locations and state
 *   glitch.yml    – obsidian sword timer state
 *   throne.yml    – current throne location + dragon owner
 */
public class DataManager {

    private final NationsSMP plugin;

    private File nationsFile, oathsFile, landsFile, legendaryFile, glitchFile, throneFile;
    private FileConfiguration nationsConfig, oathsConfig, landsConfig,
                              legendaryConfig, glitchConfig, throneConfig;

    public DataManager(NationsSMP plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        nationsFile   = new File(dataFolder, "nations.yml");
        oathsFile     = new File(dataFolder, "oaths.yml");
        landsFile     = new File(dataFolder, "lands.yml");
        legendaryFile = new File(dataFolder, "legendary.yml");
        glitchFile    = new File(dataFolder, "glitch.yml");
        throneFile    = new File(dataFolder, "throne.yml");

        nationsConfig   = loadOrCreate(nationsFile);
        oathsConfig     = loadOrCreate(oathsFile);
        landsConfig     = loadOrCreate(landsFile);
        legendaryConfig = loadOrCreate(legendaryFile);
        glitchConfig    = loadOrCreate(glitchFile);
        throneConfig    = loadOrCreate(throneFile);
    }

    private FileConfiguration loadOrCreate(File file) {
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create " + file.getName(), e);
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    // ── Save helpers ──────────────────────────────────────────────────────────

    public void saveNations()    { save(nationsConfig,   nationsFile); }
    public void saveOaths()      { save(oathsConfig,     oathsFile); }
    public void saveLands()      { save(landsConfig,     landsFile); }
    public void saveLegendary()  { save(legendaryConfig, legendaryFile); }
    public void saveGlitch()     { save(glitchConfig,    glitchFile); }
    public void saveThrone()     { save(throneConfig,    throneFile); }

    private void save(FileConfiguration cfg, File file) {
        try { cfg.save(file); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }

    // ── Nation persistence ────────────────────────────────────────────────────

    public void saveNation(Nation n) {
        String path = "nations." + n.getPlayerUUID();
        nationsConfig.set(path + ".playerName",    n.getPlayerName());
        nationsConfig.set(path + ".nationName",    n.getNationName());
        nationsConfig.set(path + ".title",         n.getTitle());
        nationsConfig.set(path + ".motto",         n.getMotto());
        nationsConfig.set(path + ".animalKey",     n.getAnimalKey());
        nationsConfig.set(path + ".buildingBlock", n.getBuildingBlock());
        nationsConfig.set(path + ".oathbreaker",   n.isOathbreaker());
        nationsConfig.set(path + ".martyr",        n.isMartyr());
        nationsConfig.set(path + ".setupComplete", n.isSetupComplete());
        nationsConfig.set(path + ".botCount",      n.getBotCount());
        nationsConfig.set(path + ".allyNation",    n.getAllyNationName());
        nationsConfig.set(path + ".companionNpcId",n.getCompanionNpcId());

        // bot roles: npc-id => role-name
        ConfigurationSection rolesSection = nationsConfig.createSection(path + ".botRoles");
        for (Map.Entry<Integer, String> e : n.getBotRoles().entrySet()) {
            rolesSection.set(String.valueOf(e.getKey()), e.getValue());
        }

        // claimed chunks
        nationsConfig.set(path + ".chunks", new ArrayList<>(n.getClaimedChunks()));

        saveNations();
    }

    public Nation loadNation(String uuid) {
        String path = "nations." + uuid;
        if (!nationsConfig.contains(path)) return null;

        String playerName = nationsConfig.getString(path + ".playerName", "Unknown");
        Nation n = new Nation(uuid, playerName);
        n.setNationName(nationsConfig.getString(path + ".nationName"));
        n.setTitle(nationsConfig.getString(path + ".title"));
        n.setMotto(nationsConfig.getString(path + ".motto"));
        n.setAnimalKey(nationsConfig.getString(path + ".animalKey"));
        n.setBuildingBlock(nationsConfig.getString(path + ".buildingBlock"));
        n.setOathbreaker(nationsConfig.getBoolean(path + ".oathbreaker", false));
        n.setMartyr(nationsConfig.getBoolean(path + ".martyr", false));
        n.setSetupComplete(nationsConfig.getBoolean(path + ".setupComplete", false));
        n.setBotCount(nationsConfig.getInt(path + ".botCount", 30));
        n.setAllyNationName(nationsConfig.getString(path + ".allyNation"));
        n.setCompanionNpcId(nationsConfig.getInt(path + ".companionNpcId", -1));

        ConfigurationSection rolesSection = nationsConfig.getConfigurationSection(path + ".botRoles");
        if (rolesSection != null) {
            for (String key : rolesSection.getKeys(false)) {
                try {
                    n.getBotRoles().put(Integer.parseInt(key), rolesSection.getString(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        List<String> chunks = nationsConfig.getStringList(path + ".chunks");
        n.getClaimedChunks().addAll(chunks);

        return n;
    }

    /** Load every nation from disk. */
    public Map<String, Nation> loadAllNations() {
        Map<String, Nation> map = new HashMap<>();
        ConfigurationSection sec = nationsConfig.getConfigurationSection("nations");
        if (sec == null) return map;
        for (String uuid : sec.getKeys(false)) {
            Nation n = loadNation(uuid);
            if (n != null) map.put(uuid, n);
        }
        return map;
    }

    /** Remove a nation from disk entirely. */
    public void deleteNation(String uuid) {
        nationsConfig.set("nations." + uuid, null);
        saveNations();
    }

    // ── Oath persistence ──────────────────────────────────────────────────────

    public void saveOath(OathRecord oath) {
        String key = oath.getInitiatorNation().toLowerCase() + "_" + oath.getReceiverNation().toLowerCase();
        oathsConfig.set("oaths." + key + ".initiator", oath.getInitiatorNation());
        oathsConfig.set("oaths." + key + ".receiver",  oath.getReceiverNation());
        oathsConfig.set("oaths." + key + ".createdAt", oath.getCreatedAt());
        saveOaths();
    }

    public List<OathRecord> loadAllOaths() {
        List<OathRecord> list = new ArrayList<>();
        ConfigurationSection sec = oathsConfig.getConfigurationSection("oaths");
        if (sec == null) return list;
        for (String key : sec.getKeys(false)) {
            String ini  = oathsConfig.getString("oaths." + key + ".initiator");
            String recv = oathsConfig.getString("oaths." + key + ".receiver");
            long   at   = oathsConfig.getLong("oaths." + key + ".createdAt", 0);
            if (ini != null && recv != null) list.add(new OathRecord(ini, recv, at));
        }
        return list;
    }

    public void deleteOath(String nationA, String nationB) {
        String key1 = nationA.toLowerCase() + "_" + nationB.toLowerCase();
        String key2 = nationB.toLowerCase() + "_" + nationA.toLowerCase();
        oathsConfig.set("oaths." + key1, null);
        oathsConfig.set("oaths." + key2, null);
        saveOaths();
    }

    // ── Legendary / Glitch / Throne config accessors ──────────────────────────

    public FileConfiguration getLegendaryConfig() { return legendaryConfig; }
    public FileConfiguration getGlitchConfig()    { return glitchConfig; }
    public FileConfiguration getThroneConfig()    { return throneConfig; }
    public FileConfiguration getLandsConfig()     { return landsConfig; }
}
