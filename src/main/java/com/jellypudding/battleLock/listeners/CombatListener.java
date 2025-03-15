package com.jellypudding.battleLock.listeners;

import com.jellypudding.battleLock.BattleLock;
import com.jellypudding.battleLock.managers.CombatManager;
import com.jellypudding.battleLock.managers.CombatLogManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatListener implements Listener {
    
    private final BattleLock plugin;
    private final CombatManager combatManager;
    
    public CombatListener(BattleLock plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            // Check if the damaged entity is a combat log NPC
            CombatLogManager combatLogManager = plugin.getCombatLogManager();
            if (combatLogManager.isCombatLogNPC(event.getEntity().getEntityId())) {
                // If it's killed, handle NPC death
                if (event.getFinalDamage() >= ((org.bukkit.entity.LivingEntity) event.getEntity()).getHealth()) {
                    combatLogManager.handleNPCDeath(event.getEntity().getEntityId());
                }
            }
            return;
        }
        
        // Get the attacker
        Entity damager = event.getDamager();
        Player attacker = null;
        
        // Direct player attack
        if (damager instanceof Player) {
            attacker = (Player) damager;
        }
        // Projectile attack (arrows, etc.)
        else if (damager instanceof Projectile) {
            ProjectileSource source = ((Projectile) damager).getShooter();
            if (source instanceof Player) {
                attacker = (Player) source;
            }
        }
        
        // If it's PvP combat, tag both players
        if (attacker != null && !attacker.equals(victim)) {
            combatManager.tagPlayer(victim);
            combatManager.tagPlayer(attacker);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        // If a player dies, remove their combat tag
        if (event.getEntity() instanceof Player player) {
            combatManager.untagPlayer(player);
        }
        
        // Check if a combat log NPC was killed
        CombatLogManager combatLogManager = plugin.getCombatLogManager();
        if (combatLogManager.isCombatLogNPC(event.getEntity().getEntityId())) {
            combatLogManager.handleNPCDeath(event.getEntity().getEntityId());
        }
    }
} 