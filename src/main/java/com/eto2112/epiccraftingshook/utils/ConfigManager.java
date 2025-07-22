package com.eto2112.epiccraftingshook.utils;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigManager {

    private final EpicCraftingsHookPlugin plugin;

    // Thread-safe configuration storage
    private volatile FileConfiguration config;
    private final AtomicReference<Map<Integer, Integer>> slotMapping = new AtomicReference<>();
    private final AtomicReference<Map<String, Map<Integer, List<String>>>> itemCommands = new AtomicReference<>();

    // Performance caches with thread-safe access
    private final Map<String, List<String>> commandCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> positionCache = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> requireSlotCache = new ConcurrentHashMap<>();

    // Configuration value caches to avoid repeated config access
    private volatile boolean debugEnabled;
    private volatile boolean cooldownEnabled;
    private volatile int cooldownDuration;
    private volatile String configVersion;
    private volatile Set<String> configuredRecipes;

    // Cache invalidation flag
    private volatile boolean cacheValid = false;

    public ConfigManager(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // Load configuration synchronously
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load all configuration data
        loadSlotMappingOptimized();
        loadItemCommandsOptimized();
        cacheConfigurationValues();

        // Rebuild performance caches
        rebuildCaches();

        // Mark caches as valid
        cacheValid = true;

        plugin.getLogger().info("Configuration loaded successfully!");

        // Notify listener to clear its caches if it exists
        if (plugin.getMenuListener() != null) {
            plugin.getMenuListener().clearCaches();
        }
    }

    // Optimized slot mapping loading with better data structures
    private void loadSlotMappingOptimized() {
        Map<Integer, Integer> newMapping = new HashMap<>();
        ConfigurationSection mappingSection = config.getConfigurationSection("slot-mapping.positions");

        if (mappingSection != null) {
            // Use bulk operations for better performance
            Set<String> keys = mappingSection.getKeys(false);
            for (String key : keys) {
                try {
                    int position = Integer.parseInt(key);
                    int slot = mappingSection.getInt(key);
                    newMapping.put(position, slot);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid slot mapping: " + key);
                }
            }
        }

        // Default mapping if none configured - using batch put operations
        if (newMapping.isEmpty()) {
            // Using traditional approach for Java 17 compatibility
            newMapping.put(1, 10);  newMapping.put(2, 11);  newMapping.put(3, 12);  newMapping.put(4, 13);
            newMapping.put(5, 19);  newMapping.put(6, 20);  newMapping.put(7, 21);  newMapping.put(8, 22);
            newMapping.put(9, 28);  newMapping.put(10, 29); newMapping.put(11, 30); newMapping.put(12, 31);
        }

        // Atomic update
        slotMapping.set(Collections.unmodifiableMap(newMapping));
    }

    // Optimized command loading with reduced object creation
    private void loadItemCommandsOptimized() {
        Map<String, Map<Integer, List<String>>> newCommands = new HashMap<>();
        ConfigurationSection commandsSection = config.getConfigurationSection("items-command");

        if (commandsSection != null) {
            Set<String> recipeKeys = commandsSection.getKeys(false);

            for (String recipeKey : recipeKeys) {
                ConfigurationSection recipeSection = commandsSection.getConfigurationSection(recipeKey);
                if (recipeSection == null) continue;

                Map<Integer, List<String>> recipeCommands = new HashMap<>();
                Set<String> slotKeys = recipeSection.getKeys(false);

                for (String slotKey : slotKeys) {
                    try {
                        int slot = Integer.parseInt(slotKey);
                        List<String> commands = parseCommandsOptimized(recipeSection.get(slotKey));

                        if (!commands.isEmpty()) {
                            // Store immutable list for better performance
                            recipeCommands.put(slot, Collections.unmodifiableList(commands));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid slot number in " + recipeKey + ": " + slotKey);
                    }
                }

                if (!recipeCommands.isEmpty()) {
                    newCommands.put(recipeKey, Collections.unmodifiableMap(recipeCommands));
                }
            }
        }

        // Atomic update with immutable map
        itemCommands.set(Collections.unmodifiableMap(newCommands));
        plugin.getLogger().info("Loaded commands for " + newCommands.size() + " recipes");
    }

    // Optimized command parsing with reduced string operations
    private List<String> parseCommandsOptimized(Object commandsObj) {
        if (commandsObj == null) return Collections.emptyList();

        List<String> commands = new ArrayList<>();

        if (commandsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rawCommands = (List<Object>) commandsObj;

            for (Object cmd : rawCommands) {
                if (cmd != null) {
                    String cmdStr = cmd.toString();
                    if (!cmdStr.trim().isEmpty()) {
                        commands.add(cmdStr);
                    }
                }
            }
        } else if (commandsObj instanceof String) {
            String cmdStr = (String) commandsObj;
            if (!cmdStr.trim().isEmpty()) {
                commands.add(cmdStr);
            }
        }

        return commands;
    }

    // Cache frequently accessed configuration values
    private void cacheConfigurationValues() {
        debugEnabled = config.getBoolean("settings.debug", false);
        cooldownEnabled = config.getBoolean("commands.cooldown.enabled", true);
        cooldownDuration = config.getInt("commands.cooldown.duration", 1);
        configVersion = config.getString("version", "unknown");

        // Cache configured recipes set
        Map<String, Map<Integer, List<String>>> commands = itemCommands.get();
        configuredRecipes = commands != null ?
                Collections.unmodifiableSet(new HashSet<>(commands.keySet())) :
                Collections.emptySet();
    }

    // Rebuild performance caches for faster lookups
    private void rebuildCaches() {
        // Clear existing caches
        commandCache.clear();
        positionCache.clear();
        requireSlotCache.clear();

        Map<Integer, Integer> mapping = slotMapping.get();
        if (mapping != null) {
            // Pre-compute position lookups
            mapping.forEach((position, slot) -> {
                positionCache.put(slot, position);
                requireSlotCache.put(slot, true);
            });

            // Fill in false values for non-require slots (up to standard inventory size)
            for (int i = 0; i < 54; i++) {
                requireSlotCache.putIfAbsent(i, false);
            }
        }
    }

    // Core getter methods with optimized lookups
    public List<String> getCommandsForSlot(String recipeKey, int inventorySlot) {
        if (!cacheValid || recipeKey == null) return Collections.emptyList();

        // Create cache key
        String cacheKey = recipeKey + ":" + inventorySlot;

        // Check cache first
        List<String> cached = commandCache.get(cacheKey);
        if (cached != null) return cached;

        // Compute result
        Integer position = positionCache.get(inventorySlot);
        List<String> result = Collections.emptyList();

        if (position != null) {
            Map<String, Map<Integer, List<String>>> commands = itemCommands.get();
            if (commands != null) {
                Map<Integer, List<String>> recipeCommands = commands.get(recipeKey);
                if (recipeCommands != null) {
                    result = recipeCommands.getOrDefault(position, Collections.emptyList());
                }
            }
        }

        // Cache result (including empty results to avoid repeated computation)
        commandCache.put(cacheKey, result);
        return result;
    }

    public boolean isRequireItemSlot(int slot) {
        return requireSlotCache.getOrDefault(slot, false);
    }

    // Cached configuration getters
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }

    public int getCooldownDuration() {
        return cooldownDuration;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public Set<String> getConfiguredRecipes() {
        return configuredRecipes;
    }

    // Admin command helpers with cached results
    public List<String> getCommandsForItem(String recipeKey, int position) {
        if (!cacheValid || recipeKey == null) return Collections.emptyList();

        Map<String, Map<Integer, List<String>>> commands = itemCommands.get();
        if (commands != null) {
            Map<Integer, List<String>> recipeCommands = commands.get(recipeKey);
            if (recipeCommands != null) {
                return recipeCommands.getOrDefault(position, Collections.emptyList());
            }
        }
        return Collections.emptyList();
    }

    public int getSlotFromPosition(int position) {
        Map<Integer, Integer> mapping = slotMapping.get();
        return mapping != null ? mapping.getOrDefault(position, -1) : -1;
    }

    // Async configuration reload for better performance
    public void reloadConfiguration() {
        // Mark caches as invalid immediately
        cacheValid = false;

        // Perform reload asynchronously to avoid blocking
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    loadConfig();

                    // Log completion on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            plugin.getLogger().info("Configuration reloaded asynchronously!");
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    plugin.getLogger().severe("Error during async config reload: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // Optimized debug info with cached data
    public void debugInfo() {
        if (!debugEnabled) return;

        plugin.getLogger().info("=== Configuration Debug Info ===");
        plugin.getLogger().info("Version: " + configVersion);
        plugin.getLogger().info("Configured recipes: " + configuredRecipes);
        plugin.getLogger().info("Debug enabled: " + debugEnabled);
        plugin.getLogger().info("Cooldown enabled: " + cooldownEnabled);
        plugin.getLogger().info("Cooldown duration: " + cooldownDuration);
        plugin.getLogger().info("Cache valid: " + cacheValid);
        plugin.getLogger().info("Cached commands: " + commandCache.size());
        plugin.getLogger().info("Position cache: " + positionCache.size());

        // Debug each recipe with cached data
        Map<String, Map<Integer, List<String>>> commands = itemCommands.get();
        if (commands != null) {
            commands.forEach((recipeKey, recipeCommands) -> {
                plugin.getLogger().info("Recipe '" + recipeKey + "' has " + recipeCommands.size() + " configured slots");
                recipeCommands.forEach((position, commandList) -> {
                    plugin.getLogger().info("  Position " + position + ": " + commandList.size() + " commands");
                });
            });
        }
    }

    // Cache management methods
    public void clearCommandCache() {
        commandCache.clear();
        if (debugEnabled) {
            plugin.getLogger().info("Command cache cleared");
        }
    }

    public int getCommandCacheSize() {
        return commandCache.size();
    }

    // Dummy methods for compatibility (optimized to return cached values)
    public boolean isSoundEnabled() {
        return false;
    }

    public boolean areParticlesEnabled() {
        return false;
    }

    public boolean shouldCancelClick() {
        return true;
    }
}