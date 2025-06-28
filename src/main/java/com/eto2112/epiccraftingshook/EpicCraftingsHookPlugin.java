package com.eto2112.epiccraftingshook;

import com.eto2112.epiccraftingshook.listeners.EnhancedCraftingMenuListener;
import com.eto2112.epiccraftingshook.commands.HookCommand;
import com.eto2112.epiccraftingshook.utils.CommandExecutor;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class EpicCraftingsHookPlugin extends JavaPlugin {

    private static EpicCraftingsHookPlugin instance;
    private CommandExecutor commandExecutor;
    private ConfigManager configManager;
    private EnhancedCraftingMenuListener menuListener;

    @Override
    public void onEnable() {
        instance = this;

        // Check dependencies
        if (getServer().getPluginManager().getPlugin("EpicCraftingsPlus") == null) {
            getLogger().severe("EpicCraftingsPlus not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize components
        configManager = new ConfigManager(this);
        commandExecutor = new CommandExecutor(this);
        menuListener = new EnhancedCraftingMenuListener(this);

        // Register listeners and commands
        getServer().getPluginManager().registerEvents(menuListener, this);
        getCommand("echook").setExecutor(new HookCommand(this));

        // Start periodic cleanup task (every 5 minutes)
        new BukkitRunnable() {
            @Override
            public void run() {
                menuListener.cleanupCooldowns();
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);

        getLogger().info("EpicCraftingsRequireItemHook enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EpicCraftingsRequireItemHook disabled!");
    }

    // Getters
    public static EpicCraftingsHookPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }
    public EnhancedCraftingMenuListener getMenuListener() { return menuListener; }
}