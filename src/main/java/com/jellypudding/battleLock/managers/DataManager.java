package com.jellypudding.battleLock.managers;

import com.jellypudding.battleLock.BattleLock;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.UUID;

public class DataManager {
    
    private final BattleLock plugin;
    private final File dataFolder;
    
    public DataManager(BattleLock plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    /**
     * Mark an NPC as killed
     * 
     * @param playerUuid The UUID of the player whose NPC was killed
     */
    public void markNpcKilled(UUID playerUuid) {
        // Save to individual player file
        File playerFile = getPlayerFile(playerUuid);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        
        config.set("killed", true);
        config.set("timestamp", System.currentTimeMillis());
        
        try {
            config.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + playerUuid, e);
        }
    }
    
    /**
     * Check if a player's NPC was killed
     * 
     * @param playerUuid The UUID of the player to check
     * @return True if the player's NPC was killed, false otherwise
     */
    public boolean wasNpcKilled(UUID playerUuid) {
        // Check file directly
        File playerFile = getPlayerFile(playerUuid);
        if (playerFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
            return config.getBoolean("killed", false);
        }
        
        return false;
    }
    
    /**
     * Remove a player's killed NPC record
     * 
     * @param playerUuid The UUID of the player to remove
     */
    public void removeKilledNpcRecord(UUID playerUuid) {
        // Delete the player file
        File playerFile = getPlayerFile(playerUuid);
        if (playerFile.exists()) {
            if (playerFile.delete()) {
                plugin.getLogger().fine("Deleted player data file for " + playerUuid);
            } else {
                plugin.getLogger().warning("Failed to delete player data file for " + playerUuid);
            }
        }
    }
    
    /**
     * Get the File object for a player's data file
     * 
     * @param playerUuid The UUID of the player
     * @return The File object for the player's data
     */
    private File getPlayerFile(UUID playerUuid) {
        return new File(dataFolder, playerUuid.toString() + ".yml");
    }
} 