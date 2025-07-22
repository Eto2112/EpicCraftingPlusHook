package com.eto2112.epiccraftingshook;

import com.eto2112.epiccraftingshook.listeners.EnhancedCraftingMenuListener;
import com.eto2112.epiccraftingshook.commands.HookCommand;
import com.eto2112.epiccraftingshook.utils.CommandExecutor;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class EpicCraftingsHookPlugin extends JavaPlugin {

    // Singleton instance with thread-safe access
    private static volatile EpicCraftingsHookPlugin instance;

    // Core components with lazy initialization
    private volatile CommandExecutor commandExecutor;
    private volatile ConfigManager configManager;
    private volatile EnhancedCraftingMenuListener menuListener;
    private volatile HookCommand hookCommand;

    // Task management for proper cleanup
    private BukkitTask cleanupTask;
    private BukkitTask cacheMaintenanceTask;

    // Plugin state management
    private final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // Constants for better performance
    private static final String EPIC_CRAFTINGS_PLUS = "EpicCraftingsPlus";
    private static final String ECHOOK_COMMAND = "echook";
    private static final long CLEANUP_INTERVAL = 6000L; // 5 minutes
    private static final long CACHE_MAINTENANCE_INTERVAL = 12000L; // 10 minutes

    @Override
    public void onEnable() {
        instance = this;

        // Initialize plugin asynchronously for faster startup
        CompletableFuture.runAsync(this::initializePluginAsync)
                .thenRun(() -> {
                    // Complete initialization on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            completeInitialization();
                        }
                    }.runTask(this);
                })
                .exceptionally(throwable -> {
                    getLogger().severe("Failed to initialize plugin: " + throwable.getMessage());
                    getServer().getPluginManager().disablePlugin(this);
                    return null;
                });
    }

    // Async initialization to reduce startup time
    private void initializePluginAsync() {
        try {
            // Check dependencies first
            if (!checkDependencies()) {
                throw new IllegalStateException("Required dependencies not found");
            }

            // Initialize core components
            initializeComponents();

            // Mark as initialized
            isInitialized.set(true);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during async initialization", e);
            throw new RuntimeException(e);
        }
    }

    // Fast dependency checking
    private boolean checkDependencies() {
        if (getServer().getPluginManager().getPlugin(EPIC_CRAFTINGS_PLUS) == null) {
            getLogger().severe("EpicCraftingsPlus not found! Disabling plugin.");
            return false;
        }
        return true;
    }

    // Optimized component initialization
    private void initializeComponents() {
        try {
            // Initialize configuration manager first (required by others)
            configManager = new ConfigManager(this);

            // Initialize command executor
            commandExecutor = new CommandExecutor(this);

            // Initialize listener
            menuListener = new EnhancedCraftingMenuListener(this);

            // Initialize command handler
            hookCommand = new HookCommand(this);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing components", e);
            throw e;
        }
    }

    // Complete initialization on main thread
    private void completeInitialization() {
        try {
            // Register components that require main thread
            registerEventListeners();
            registerCommands();

            // Start maintenance tasks
            startMaintenanceTasks();

            // Mark plugin as enabled
            isEnabled.set(true);

            getLogger().info("EpicCraftingsRequireItemHook enabled successfully!");

            // Log startup information if debug is enabled
            if (configManager != null && configManager.isDebugEnabled()) {
                logStartupInfo();
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error completing initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    // Optimized event listener registration
    private void registerEventListeners() {
        if (menuListener != null) {
            getServer().getPluginManager().registerEvents(menuListener, this);
        } else {
            throw new IllegalStateException("Menu listener not initialized");
        }
    }

    // Optimized command registration with null checking
    private void registerCommands() {
        org.bukkit.command.PluginCommand command = getCommand(ECHOOK_COMMAND);
        if (command != null && hookCommand != null) {
            command.setExecutor(hookCommand);
            command.setTabCompleter(hookCommand);
        } else {
            getLogger().warning("Failed to register command: " + ECHOOK_COMMAND);
        }
    }

    // Start periodic maintenance tasks
    private void startMaintenanceTasks() {
        // Cleanup task for cooldowns and temporary data
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCleanupMaintenance();
            }
        }.runTaskTimerAsynchronously(this, CLEANUP_INTERVAL, CLEANUP_INTERVAL);

        // Cache maintenance task
        cacheMaintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCacheMaintenance();
            }
        }.runTaskTimerAsynchronously(this, CACHE_MAINTENANCE_INTERVAL, CACHE_MAINTENANCE_INTERVAL);
    }

    // Perform cleanup maintenance
    private void performCleanupMaintenance() {
        try {
            if (menuListener != null) {
                menuListener.cleanupCooldowns();
            }

            if (commandExecutor != null) {
                commandExecutor.cleanupCache();
            }

            if (hookCommand != null) {
                hookCommand.cleanupCaches();
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during cleanup maintenance", e);
        }
    }

    // Perform cache maintenance
    private void performCacheMaintenance() {
        try {
            if (configManager != null) {
                configManager.clearCommandCache();
            }

            if (commandExecutor != null) {
                commandExecutor.clearPlaceholderCache();
            }

            // Force garbage collection if memory usage is high
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();

            if (usedMemory > maxMemory * 0.8) { // 80% memory usage
                if (configManager != null && configManager.isDebugEnabled()) {
                    getLogger().info("High memory usage detected, performing garbage collection");
                }
                System.gc();
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during cache maintenance", e);
        }
    }

    // Log detailed startup information
    private void logStartupInfo() {
        getLogger().info("=== Plugin Startup Information ===");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Java Version: " + System.getProperty("java.version"));
        getLogger().info("Server Version: " + getServer().getVersion());
        getLogger().info("Bukkit Version: " + getServer().getBukkitVersion());

        // Component status
        getLogger().info("Components initialized: " + isInitialized.get());
        getLogger().info("Config Manager: " + (configManager != null ? "OK" : "FAILED"));
        getLogger().info("Command Executor: " + (commandExecutor != null ? "OK" : "FAILED"));
        getLogger().info("Menu Listener: " + (menuListener != null ? "OK" : "FAILED"));
        getLogger().info("Hook Command: " + (hookCommand != null ? "OK" : "FAILED"));

        // Configuration status
        if (configManager != null) {
            getLogger().info("Configured recipes: " + configManager.getConfiguredRecipes().size());
            getLogger().info("Debug mode: " + configManager.isDebugEnabled());
            getLogger().info("Cooldown enabled: " + configManager.isCooldownEnabled());
        }

        getLogger().info("=== Startup Complete ===");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling EpicCraftingsRequireItemHook...");

        // Mark as disabled immediately
        isEnabled.set(false);

        // Stop maintenance tasks
        stopMaintenanceTasks();

        // Cleanup resources asynchronously
        CompletableFuture.runAsync(this::cleanupResources)
                .thenRun(() -> {
                    getLogger().info("EpicCraftingsRequireItemHook disabled successfully!");
                })
                .exceptionally(throwable -> {
                    getLogger().warning("Error during cleanup: " + throwable.getMessage());
                    return null;
                });
    }

    // Stop all maintenance tasks
    private void stopMaintenanceTasks() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        if (cacheMaintenanceTask != null && !cacheMaintenanceTask.isCancelled()) {
            cacheMaintenanceTask.cancel();
            cacheMaintenanceTask = null;
        }
    }

    // Cleanup resources on disable
    private void cleanupResources() {
        try {
            // Clear all caches with null safety
            if (configManager != null) {
                configManager.clearCommandCache();
            }

            if (commandExecutor != null) {
                commandExecutor.clearPlaceholderCache();
            }

            if (menuListener != null) {
                menuListener.clearCaches();
                menuListener.cleanupCooldowns();
            }

            if (hookCommand != null) {
                hookCommand.cleanupCaches();
            }

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during resource cleanup", e);
        }
    }

    // Thread-safe singleton access
    public static EpicCraftingsHookPlugin getInstance() {
        return instance;
    }

    // Getters with null safety and initialization checking
    public ConfigManager getConfigManager() {
        if (!isInitialized.get() && configManager == null) {
            throw new IllegalStateException("Plugin is not initialized");
        }
        return configManager;
    }

    public CommandExecutor getCommandExecutor() {
        if (!isInitialized.get() && commandExecutor == null) {
            throw new IllegalStateException("Plugin is not initialized");
        }
        return commandExecutor;
    }

    public EnhancedCraftingMenuListener getMenuListener() {
        return menuListener; // Can be null during initialization
    }

    // Plugin state checking methods
    public boolean isPluginEnabled() {
        return isEnabled.get();
    }

    public boolean isPluginInitialized() {
        return isInitialized.get();
    }

    // Safe component access for external use
    public boolean hasConfigManager() {
        return configManager != null && isEnabled.get();
    }

    public boolean hasCommandExecutor() {
        return commandExecutor != null && isEnabled.get();
    }

    public boolean hasMenuListener() {
        return menuListener != null && isEnabled.get();
    }

    // Memory usage information for debugging
    public String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        return String.format("Memory: %d/%d MB (%.1f%% used)",
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                (double) usedMemory / maxMemory * 100);
    }

    // Performance statistics for debugging
    public String getPerformanceStats() {
        StringBuilder stats = new StringBuilder();

        if (configManager != null) {
            stats.append("Config cache: ").append(configManager.getCommandCacheSize()).append(" entries\n");
        }

        if (commandExecutor != null) {
            stats.append("Command cache: ").append(commandExecutor.getPlaceholderCacheSize()).append(" entries\n");
        }

        stats.append(getMemoryInfo());

        return stats.toString();
    }

    // Force cleanup method for admin commands
    public void forceCleanup() {
        CompletableFuture.runAsync(() -> {
            performCleanupMaintenance();
            performCacheMaintenance();

            if (configManager != null && configManager.isDebugEnabled()) {
                getLogger().info("Forced cleanup completed");
            }
        });
    }
}