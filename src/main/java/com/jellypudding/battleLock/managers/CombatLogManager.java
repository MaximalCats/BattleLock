package com.jellypudding.battleLock.managers;

import com.jellypudding.battleLock.BattleLock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogManager {

    private final BattleLock plugin;
    private final CombatManager combatManager;
    private final DataManager dataManager;
    private final Map<UUID, Integer> combatLogNPCs;
    private final Map<Integer, UUID> entityPlayerMap;
    private final Map<UUID, ItemStack[]> playerInventories;
    private final Map<UUID, Boolean> processingNPCDeath;
    private final Map<UUID, Integer> scheduledTasks; // Track scheduled despawn tasks
    private final Map<UUID, Location> npcLocations; // Store NPC locations for item dropping
    private final int logoutDespawnTime;
    private final NamespacedKey combatLogKey;

    public CombatLogManager(BattleLock plugin, CombatManager combatManager, DataManager dataManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.dataManager = dataManager;
        this.combatLogNPCs = new ConcurrentHashMap<>();
        this.entityPlayerMap = new ConcurrentHashMap<>();
        this.playerInventories = new ConcurrentHashMap<>();
        this.processingNPCDeath = new ConcurrentHashMap<>();
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.npcLocations = new ConcurrentHashMap<>();
        this.logoutDespawnTime = plugin.getConfig().getInt("combat-log-despawn-time", 30) * 20; // Convert to ticks
        this.combatLogKey = new NamespacedKey(plugin, "combat_log_player_id");
    }

    /**
     * Create a combat log NPC when a player logs out during combat
     *
     * @param player The player who logged out
     */
    public void createCombatLogNPC(Player player) {
        UUID playerId = player.getUniqueId();

        // Prevent double creation
        if (combatLogNPCs.containsKey(playerId)) {
            return;
        }

        // Cancel any existing scheduled task for this player
        if (scheduledTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(scheduledTasks.get(playerId));
            scheduledTasks.remove(playerId);
        }

        Location location = player.getLocation();

        // Store player inventory.
        ItemStack[] inventory = player.getInventory().getContents().clone();
        playerInventories.put(playerId, inventory);

        // Create an NPC at the player's location
        Villager npc = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);

        // Setup the NPC with player data
        npc.customName(player.displayName());
        npc.setCustomNameVisible(true);
        npc.setAI(false);
        npc.setInvulnerable(false);
        npc.setSilent(true);
        npc.setHealth(Math.min(player.getHealth(), 20.0));
        npc.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 10, false, false));
        npc.getPersistentDataContainer().set(combatLogKey, PersistentDataType.STRING, playerId.toString());

        // Register the NPC
        combatLogNPCs.put(playerId, npc.getEntityId());
        entityPlayerMap.put(npc.getEntityId(), playerId);
        npcLocations.put(playerId, npc.getLocation());

        // Schedule NPC removal and store the task ID
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> removeCombatLogNPC(playerId, false), logoutDespawnTime);
        scheduledTasks.put(playerId, taskId);

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

        // Cancel any scheduled task for this player
        if (scheduledTasks.containsKey(playerId)) {
            Bukkit.getScheduler().cancelTask(scheduledTasks.get(playerId));
            scheduledTasks.remove(playerId);
        }

        int entityId = combatLogNPCs.get(playerId);

        // Handle punishment FIRST (before entity cleanup) so it works for both live and dead entities
        if (died && playerInventories.containsKey(playerId)) {
            // Get drop location (prefer live entity, fallback to stored location)
            Location dropLocation = null;
            for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                    .flatMap(world -> world.getEntities().stream())
                    .filter(e -> e.getEntityId() == entityId)
                    .toList()) {
                dropLocation = entity.getLocation();
                break;
            }

            // Fallback to stored location if entity is gone (environmental deaths)
            if (dropLocation == null) {
                dropLocation = npcLocations.get(playerId);
            }

            // Drop items at the determined location
            if (dropLocation != null) {
                for (ItemStack item : playerInventories.get(playerId)) {
                    if (item != null) {
                        dropLocation.getWorld().dropItemNaturally(dropLocation, item);
                    }
                }
            }

            // Mark in persistent storage that this player's NPC was killed
            dataManager.markNpcKilled(playerId);
        }

        // Remove entity if it still exists
        for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                .flatMap(world -> world.getEntities().stream())
                .filter(e -> e.getEntityId() == entityId)
                .toList()) {
            entity.remove();
        }

        // Clean up all tracking data
        combatLogNPCs.remove(playerId);
        entityPlayerMap.remove(entityId);
        playerInventories.remove(playerId);
        processingNPCDeath.remove(playerId);
        npcLocations.remove(playerId);

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
                // NPC was killed, ensure inventory is cleared and remove combat tag.
                player.getInventory().clear();
                combatManager.untagPlayer(player);
                player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));
                plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC.");
            } else {
                // NPC survived, return inventory only if we have it stored.
                ItemStack[] storedInventory = playerInventories.get(playerId);
                if (storedInventory != null) {
                    player.getInventory().clear(); // Clear first to prevent any potential duplication.
                    player.getInventory().setContents(storedInventory);
                }

                if (combatManager.isPlayerTagged(player)) {
                    int timeRemaining = combatManager.getTimeUntilTagExpires(player);
                    player.sendMessage(Component.text("Your combat log NPC has been removed. You are still in combat for " + timeRemaining + " more seconds.", NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("Your combat log NPC has been removed. You are no longer in combat and may log out safely.", NamedTextColor.GREEN));
                }
            }
            return;
        }

        // Check for punishment record from previous server session
        if (dataManager.wasNpcKilled(playerId)) {
            // NPC was killed in a previous session, clear their inventory
            player.getInventory().clear();
            combatManager.untagPlayer(player);
            player.sendMessage(Component.text("Your combat log NPC was killed while you were offline. You have lost your items.", NamedTextColor.RED));

            // Remove the record now that it's been processed
            dataManager.removeKilledNpcRecord(playerId);

            plugin.getLogger().info(player.getName() + " lost items due to a killed combat log NPC (previous session).");
        }
    }

    /**
     * Handle an NPC being damaged or killed
     *
     * @param entityId The entity ID of the NPC
     */
    public void handleNPCDeath(int entityId) {
        if (!entityPlayerMap.containsKey(entityId)) {
            return;
        }

        UUID playerId = entityPlayerMap.get(entityId);

        // Prevent double processing using atomic check-and-set
        if (processingNPCDeath.putIfAbsent(playerId, true) != null) {
            return; // Already being processed
        }

        removeCombatLogNPC(playerId, true);
    }

    /**
     * Remove all combat log NPCs (for plugin shutdown)
     */
    public void removeAllCombatLogs() {
        // Cancel all scheduled tasks
        for (int taskId : scheduledTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Create a copy to avoid ConcurrentModificationException
        for (UUID playerId : new HashMap<>(combatLogNPCs).keySet()) {
            removeCombatLogNPC(playerId, false);
        }

        // Clear all tracking data
        scheduledTasks.clear();
        npcLocations.clear();
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

    /**
     * Get the NamespacedKey used for marking combat log NPCs
     *
     * @return The NamespacedKey for combat log NPCs
     */
    public NamespacedKey getCombatLogKey() {
        return combatLogKey;
    }
}
