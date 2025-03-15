package com.jellypudding.battleLock.listeners;

import com.jellypudding.battleLock.BattleLock;
import com.jellypudding.battleLock.managers.CombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;

public class CommandListener implements Listener {

    private final BattleLock plugin;
    private final CombatManager combatManager;
    private final List<String> allowedCommands;

    public CommandListener(BattleLock plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        
        // Load allowed commands from config
        List<String> configAllowedCommands = plugin.getConfig().getStringList("allowed-commands");
        this.allowedCommands = configAllowedCommands.isEmpty() 
                ? new ArrayList<>(List.of("tell", "msg", "r", "me"))
                : configAllowedCommands;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in combat
        if (combatManager.isPlayerTagged(player)) {
            String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();
            
            // Allow specific whitelisted commands
            for (String allowedCommand : allowedCommands) {
                if (command.equals(allowedCommand)) {
                    return;
                }
            }
            
            // Block all other commands
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot use commands while in combat. Time remaining: " + 
                    combatManager.getTimeUntilTagExpires(player) + "s", NamedTextColor.RED));
        }
    }
} 