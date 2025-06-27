package com.eto2112.epiccraftingshook;

import com.eto2112.epiccraftingshook.listeners.EnhancedCraftingMenuListener;
import com.eto2112.epiccraftingshook.commands.HookCommand;
import com.eto2112.epiccraftingshook.utils.CommandExecutor;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Bukkit;

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
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        configManager = new ConfigManager(this);
        commandExecutor = new CommandExecutor(this);

        // Initialize listener
        menuListener = new EnhancedCraftingMenuListener(this);

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        getLogger().info("EpicCraftingsRequireItemHook has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("EpicCraftingsRequireItemHook has been disabled!");
    }

    private boolean isEpicCraftingsPlusLoaded() {
        return getServer().getPluginManager().getPlugin("EpicCraftingsPlus") != null;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(menuListener, this);
    }

    private void registerCommands() {
        getCommand("echook").setExecutor(new HookCommand(this));
    }

    public static EpicCraftingsHookPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }
}