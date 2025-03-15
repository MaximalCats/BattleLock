package com.jellypudding.battleLock;

import com.jellypudding.battleLock.listeners.CombatListener;
import com.jellypudding.battleLock.listeners.CommandListener;
import com.jellypudding.battleLock.listeners.PlayerListener;
import com.jellypudding.battleLock.managers.CombatManager;
import com.jellypudding.battleLock.managers.CombatLogManager;
import com.jellypudding.battleLock.managers.DataManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattleLock extends JavaPlugin {
    
    private CombatManager combatManager;
    private CombatLogManager combatLogManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.combatManager = new CombatManager(this);
        this.combatLogManager = new CombatLogManager(this, combatManager, dataManager);

        getServer().getPluginManager().registerEvents(new CombatListener(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new CommandListener(this, combatManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, combatManager, combatLogManager), this);

        getLogger().info("BattleLock has been enabled! Combat logging protection is now active.");
    }

    @Override
    public void onDisable() {
        if (combatLogManager != null) {
            combatLogManager.removeAllCombatLogs();
        }
        
        getLogger().info("BattleLock has been disabled.");
    }
    
    public CombatManager getCombatManager() {
        return combatManager;
    }
    
    public CombatLogManager getCombatLogManager() {
        return combatLogManager;
    }
    
    public DataManager getDataManager() {
        return dataManager;
    }
}
