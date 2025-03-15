package com.jellypudding.battleLock.managers;

import com.jellypudding.battleLock.BattleLock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {
    
    private final BattleLock plugin;
    private final Map<UUID, Long> taggedPlayers;
    private final int combatTagDuration;
    
    public CombatManager(BattleLock plugin) {
        this.plugin = plugin;
        this.taggedPlayers = new HashMap<>();
        this.combatTagDuration = plugin.getConfig().getInt("combat-tag-duration", 15) * 1000; // Convert to milliseconds
    }
    
    /**
     * Tag a player as being in combat
     * 
     * @param player The player to tag
     */
    public void tagPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        boolean wasTagged = isPlayerTagged(player);
        
        taggedPlayers.put(playerId, System.currentTimeMillis() + combatTagDuration);
        
        if (!wasTagged) {
            player.sendMessage(Component.text("You are now in combat! Do not log out or you will be punished!", NamedTextColor.RED));
        }
    }
    
    /**
     * Untag a player from combat
     * 
     * @param player The player to untag
     */
    public void untagPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (taggedPlayers.containsKey(playerId)) {
            taggedPlayers.remove(playerId);
            player.sendMessage(Component.text("You are no longer in combat. You may now log out safely.", NamedTextColor.GREEN));
        }
    }
    
    /**
     * Check if a player is tagged as being in combat
     * 
     * @param player The player to check
     * @return True if the player is in combat, false otherwise
     */
    public boolean isPlayerTagged(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (!taggedPlayers.containsKey(playerId)) {
            return false;
        }
        
        long expireTime = taggedPlayers.get(playerId);
        
        if (System.currentTimeMillis() > expireTime) {
            taggedPlayers.remove(playerId);
            player.sendMessage(Component.text("You are no longer in combat. You may now log out safely.", NamedTextColor.GREEN));
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the time in seconds until a player's combat tag expires
     * 
     * @param player The player to check
     * @return The time in seconds until the tag expires, or 0 if not tagged
     */
    public int getTimeUntilTagExpires(Player player) {
        UUID playerId = player.getUniqueId();
        
        if (!taggedPlayers.containsKey(playerId)) {
            return 0;
        }
        
        long expireTime = taggedPlayers.get(playerId);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime > expireTime) {
            taggedPlayers.remove(playerId);
            return 0;
        }
        
        return (int) ((expireTime - currentTime) / 1000);
    }
} 