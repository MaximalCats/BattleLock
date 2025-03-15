package com.jellypudding.battleLock.managers;

import com.jellypudding.battleLock.BattleLock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatLogManager {
    
    private final BattleLock plugin;
    private final CombatManager combatManager;
    private final DataManager dataManager;
    private final Map<UUID, Integer> combatLogNPCs;
    private final Map<Integer, UUID> entityPlayerMap;
    private final Map<UUID, ItemStack[]> playerInventories;
    private final int logoutDespawnTime;
    
    public CombatLogManager(BattleLock plugin, CombatManager combatManager, DataManager dataManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.dataManager = dataManager;
        this.combatLogNPCs = new HashMap<>();
        this.entityPlayerMap = new HashMap<>();
        this.playerInventories = new HashMap<>();
        this.logoutDespawnTime = plugin.getConfig().getInt("combat-log-despawn-time", 30) * 20; // Convert to ticks
    }
    
    /**
     * Create a combat log NPC when a player logs out during combat
     * 
     * @param player The player who logged out
     */
    public void createCombatLogNPC(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        
        // Store player inventory
        playerInventories.put(playerId, player.getInventory().getContents());
        
        // Create an NPC at the player's location
        Villager npc = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        
        // Setup the NPC with player data
        npc.customName(Component.text(player.getName()));
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setInvulnerable(false);
        npc.setSilent(true);
        npc.setHealth(player.getHealth());
        npc.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 10, false, false));
        npc.setMetadata("BattleLock_CombatLog", new FixedMetadataValue(plugin, playerId.toString()));
        
        // Register the NPC
        combatLogNPCs.put(playerId, npc.getEntityId());
        entityPlayerMap.put(npc.getEntityId(), playerId);
        
        // Schedule NPC removal
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> removeCombatLogNPC(playerId, false), logoutDespawnTime);
        
        plugin.getLogger().info(player.getName() + " logged out during combat! Created NPC at " + 
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }
    
    /**
     * Remove a combat log NPC
     * 
     * @param playerId The UUID of the player whose NPC should be removed
     * @param died Whether the NPC died (true) or despawned naturally (false)
     */
    public void removeCombatLogNPC(UUID playerId, boolean died) {
        if (!combatLogNPCs.containsKey(playerId)) {
            return;
        }
        
        int entityId = combatLogNPCs.get(playerId);
        
        for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                .flatMap(world -> world.getEntities().stream())
                .filter(e -> e.getEntityId() == entityId)
                .toList()) {
            
            // Drop inventory if the NPC was killed
            if (died && playerInventories.containsKey(playerId)) {
                for (ItemStack item : playerInventories.get(playerId)) {
                    if (item != null) {
                        entity.getWorld().dropItemNaturally(entity.getLocation(), item);
                    }
                }
                playerInventories.remove(playerId);
                
                // Mark in persistent storage that this player's NPC was killed
                dataManager.markNpcKilled(playerId);
            }
            
            entity.remove();
        }
        
        combatLogNPCs.remove(playerId);
        entityPlayerMap.remove(entityId);
        
        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        plugin.getLogger().info(playerName + "'s combat log NPC has been " + (died ? "killed" : "despawned"));
    }
    
    /**
     * Handle a player logging back in after combat logging
     * 
     * @param player The player who logged back in
     */
    public void handlePlayerReturn(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Check for active NPC in current session
        if (combatLogNPCs.containsKey(playerId)) {
            boolean wasKilled = !playerInventories.containsKey(playerId);
            removeCombatLogNPC(playerId, false);
            
            if (wasKilled) {
                // NPC was killed, clear their inventory
                player.getInventory().clear();
                player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));
                plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC (current session)");
            } else {
                // NPC survived, return inventory
                player.getInventory().setContents(playerInventories.get(playerId));
                playerInventories.remove(playerId);
                player.sendMessage(Component.text("Your combat log NPC has been removed.", NamedTextColor.YELLOW));
            }
            return;
        }
        
        // Check for punishment record from previous server session
        if (dataManager.wasNpcKilled(playerId)) {
            // NPC was killed in a previous session, clear their inventory
            player.getInventory().clear();
            player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));
            
            // Remove the record now that it's been processed
            dataManager.removeKilledNpcRecord(playerId);
            
            plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC (previous session)");
        }
    }
    
    /**
     * Handle an NPC being damaged or killed
     * 
     * @param entityId The entity ID of the NPC
     */
    public void handleNPCDeath(int entityId) {
        if (entityPlayerMap.containsKey(entityId)) {
            UUID playerId = entityPlayerMap.get(entityId);
            removeCombatLogNPC(playerId, true);
        }
    }
    
    /**
     * Remove all combat log NPCs (for plugin shutdown)
     */
    public void removeAllCombatLogs() {
        // Create a copy to avoid ConcurrentModificationException
        for (UUID playerId : new HashMap<>(combatLogNPCs).keySet()) {
            removeCombatLogNPC(playerId, false);
        }
    }
    
    /**
     * Check if there's a combat log NPC for a player
     * 
     * @param playerId The UUID of the player to check
     * @return True if a combat log NPC exists, false otherwise
     */
    public boolean hasCombatLogNPC(UUID playerId) {
        return combatLogNPCs.containsKey(playerId);
    }
    
    /**
     * Check if an entity is a combat log NPC
     * 
     * @param entityId The entity ID to check
     * @return True if it's a combat log NPC, false otherwise
     */
    public boolean isCombatLogNPC(int entityId) {
        return entityPlayerMap.containsKey(entityId);
    }
} 