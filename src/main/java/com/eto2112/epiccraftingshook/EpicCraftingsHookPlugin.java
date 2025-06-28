package com.eto2112.epiccraftingshook;

import com.eto2112.epiccraftingshook.listeners.EnhancedCraftingMenuListener;
import com.eto2112.epiccraftingshook.commands.HookCommand;
import com.eto2112.epiccraftingshook.utils.CommandExecutor;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

public class EpicCraftingsHookPlugin extends JavaPlugin {

    private static EpicCraftingsHookPlugin instance;
    private CommandExecutor commandExecutor;
    private ConfigManager configManager;
    private EnhancedCraftingMenuListener menuListener;

    @Override
    public void onEnable() {
        instance = this;

        // Check if EpicCraftingsPlus is loaded
        if (!isEpicCraftingsPlusLoaded()) {
            getLogger().severe("EpicCraftingsPlus not found! This plugin requires EpicCraftingsPlus to work.");
            getLogger().severe("Please make sure EpicCraftingsPlus is installed and loaded before this plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("EpicCraftingsPlus found! Initializing hook...");

        // Initialize managers
        try {
            configManager = new ConfigManager(this);
            commandExecutor = new CommandExecutor(this);

            // Initialize listener
            menuListener = new EnhancedCraftingMenuListener(this);

            getLogger().info("Managers initialized successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin managers: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start cleanup task for cooldowns
        startCleanupTask();

        // Print debug info if enabled
        if (configManager.isDebugEnabled()) {
            getLogger().info("Debug mode is enabled!");
            new BukkitRunnable() {
                @Override
                public void run() {
                    configManager.debugInfo();
                }
            }.runTaskLater(this, 20L); // Run after 1 second
        }

        getLogger().info("EpicCraftingsRequireItemHook has been enabled successfully!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Author: " + getDescription().getAuthors().toString());
    }

    @Override
    public void onDisable() {
        // Clean up any remaining tasks
        if (menuListener != null) {
            menuListener.cleanupCooldowns();
        }

        getLogger().info("EpicCraftingsRequireItemHook has been disabled!");
    }

    private boolean isEpicCraftingsPlusLoaded() {
        return getServer().getPluginManager().getPlugin("EpicCraftingsPlus") != null;
    }

    private void registerListeners() {
        try {
            PluginManager pm = getServer().getPluginManager();
            pm.registerEvents(menuListener, this);
            getLogger().info("Event listeners registered successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to register event listeners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        try {
            if (getCommand("echook") != null) {
                getCommand("echook").setExecutor(new HookCommand(this));
                getLogger().info("Commands registered successfully!");
            } else {
                getLogger().warning("Command 'echook' not found in plugin.yml!");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startCleanupTask() {
        // Run cleanup task every 5 minutes to remove expired cooldowns
        new BukkitRunnable() {
            @Override
            public void run() {
                if (menuListener != null) {
                    menuListener.cleanupCooldowns();
                }
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L); // 5 minutes = 6000 ticks
    }

    // Static getters
    public static EpicCraftingsHookPlugin getInstance() {
        return instance;
    }

    // Instance getters
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public EnhancedCraftingMenuListener getMenuListener() {
        return menuListener;
    }

    // Utility methods for other classes
    public boolean isPluginEnabled() {
        return isEnabled();
    }

    public void reloadPluginConfig() {
        try {
            if (configManager != null) {
                configManager.reloadConfiguration();
                getLogger().info("Plugin configuration reloaded!");
            }
        } catch (Exception e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}