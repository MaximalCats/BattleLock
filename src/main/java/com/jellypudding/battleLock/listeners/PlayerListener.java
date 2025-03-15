package com.jellypudding.battleLock.listeners;

import com.jellypudding.battleLock.BattleLock;
import com.jellypudding.battleLock.managers.CombatManager;
import com.jellypudding.battleLock.managers.CombatLogManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final BattleLock plugin;
    private final CombatManager combatManager;
    private final CombatLogManager combatLogManager;

    public PlayerListener(BattleLock plugin, CombatManager combatManager, CombatLogManager combatLogManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.combatLogManager = combatLogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in combat
        if (combatManager.isPlayerTagged(player)) {
            // Player is combat logging - create a combat log NPC
            combatLogManager.createCombatLogNPC(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check for any combat log status (both active NPCs and persisted records)
        combatLogManager.handlePlayerReturn(event.getPlayer());
    }
}