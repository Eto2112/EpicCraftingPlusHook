package com.eto2112.epiccraftingshook.utils;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class CommandExecutor {

    private final EpicCraftingsHookPlugin plugin;

    // Cache for compiled regex patterns and placeholder replacements
    private final Map<String, Pattern> placeholderPatterns = new ConcurrentHashMap<>();
    private final Map<String, String> placeholderCache = new ConcurrentHashMap<>();

    // Command type constants for better performance
    private static final String CONSOLE_PREFIX = "[console]";
    private static final String OP_PREFIX = "[op]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String MESSAGE_PREFIX = "[message]";

    // Pre-compiled patterns for placeholder replacement
    private static final Pattern PLAYER_PATTERN = Pattern.compile("%player%");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("%player_name%");
    private static final Pattern PLAYER_UUID_PATTERN = Pattern.compile("%player_uuid%");
    private static final Pattern PLAYER_DISPLAYNAME_PATTERN = Pattern.compile("%player_displayname%");
    private static final Pattern WORLD_PATTERN = Pattern.compile("%world%");
    private static final Pattern X_PATTERN = Pattern.compile("%x%");
    private static final Pattern Y_PATTERN = Pattern.compile("%y%");
    private static final Pattern Z_PATTERN = Pattern.compile("%z%");

    public CommandExecutor(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        initializePlaceholderPatterns();
    }

    // Pre-compile patterns for better performance
    private void initializePlaceholderPatterns() {
        placeholderPatterns.put("player", PLAYER_PATTERN);
        placeholderPatterns.put("player_name", PLAYER_NAME_PATTERN);
        placeholderPatterns.put("player_uuid", PLAYER_UUID_PATTERN);
        placeholderPatterns.put("player_displayname", PLAYER_DISPLAYNAME_PATTERN);
        placeholderPatterns.put("world", WORLD_PATTERN);
        placeholderPatterns.put("x", X_PATTERN);
        placeholderPatterns.put("y", Y_PATTERN);
        placeholderPatterns.put("z", Z_PATTERN);
    }

    public void executeCommand(Player player, String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        // Fast command processing with minimal string operations
        String trimmedCommand = command.trim();

        // Process command asynchronously for placeholder replacement
        CompletableFuture.runAsync(() -> {
            processCommandAsync(player, trimmedCommand);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Async command processing failed: " + throwable.getMessage());
            return null;
        });
    }

    // Async command processing to avoid blocking main thread
    private void processCommandAsync(Player player, String command) {
        try {
            // Replace placeholders efficiently
            String processedCommand = replacePlaceholdersOptimized(command, player);

            // Determine command type and execute on main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    executeCommandSync(player, processedCommand);
                }
            }.runTask(plugin);

        } catch (Exception e) {
            plugin.getLogger().warning("Error in async command processing: " + e.getMessage());
        }
    }

    // Synchronous execution on main thread for Bukkit API compatibility
    private void executeCommandSync(Player player, String command) {
        try {
            if (command.startsWith(CONSOLE_PREFIX)) {
                executeConsoleCommandOptimized(command.substring(CONSOLE_PREFIX.length()).trim());
            } else if (command.startsWith(OP_PREFIX)) {
                executeOpCommandOptimized(player, command.substring(OP_PREFIX.length()).trim());
            } else if (command.startsWith(PLAYER_PREFIX)) {
                executePlayerCommandOptimized(player, command.substring(PLAYER_PREFIX.length()).trim());
            } else if (command.startsWith(MESSAGE_PREFIX)) {
                sendMessageOptimized(player, command.substring(MESSAGE_PREFIX.length()).trim());
            } else {
                // Default to console command if no prefix
                executeConsoleCommandOptimized(command);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error executing command: " + command + " - " + e.getMessage());
        }
    }

    // Optimized console command execution
    private void executeConsoleCommandOptimized(String command) {
        if (command.isEmpty()) return;

        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Executing console command: " + command);
            }
        } catch (Exception e) {
            // Ignore if plugin is not fully initialized
        }

        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().warning("Error executing console command: " + command);
            plugin.getLogger().warning("Error: " + e.getMessage());
        }
    }

    // Optimized OP command execution with better state management
    private void executeOpCommandOptimized(Player player, String command) {
        if (command.isEmpty()) return;

        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Executing OP command for " + player.getName() + ": " + command);
            }
        } catch (Exception e) {
            // Ignore if plugin is not fully initialized
        }

        boolean wasOp = player.isOp();

        try {
            // Temporarily give OP if needed
            if (!wasOp) {
                player.setOp(true);
            }

            // Execute command as player
            player.performCommand(command);

        } catch (Exception e) {
            plugin.getLogger().warning("Error executing OP command for " + player.getName() + ": " + command);
            plugin.getLogger().warning("Error: " + e.getMessage());
        } finally {
            // Always restore original OP status
            if (!wasOp) {
                player.setOp(false);
            }
        }
    }

    // Optimized player command execution
    private void executePlayerCommandOptimized(Player player, String command) {
        if (command.isEmpty()) return;

        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Executing player command for " + player.getName() + ": " + command);
            }
        } catch (Exception e) {
            // Ignore if plugin is not fully initialized
        }

        try {
            player.performCommand(command);
        } catch (Exception e) {
            plugin.getLogger().warning("Error executing player command for " + player.getName() + ": " + command);
            plugin.getLogger().warning("Error: " + e.getMessage());
        }
    }

    // Optimized message sending with color code caching
    private void sendMessageOptimized(Player player, String message) {
        if (message.isEmpty()) return;

        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Sending message to " + player.getName() + ": " + message);
            }
        } catch (Exception e) {
            // Ignore if plugin is not fully initialized
        }

        try {
            // Cache color-translated messages for better performance
            String cacheKey = "msg:" + message;
            String coloredMessage = placeholderCache.get(cacheKey);

            if (coloredMessage == null) {
                coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
                // Only cache if it's worth it (has color codes)
                if (message.contains("&")) {
                    placeholderCache.put(cacheKey, coloredMessage);
                }
            }

            player.sendMessage(coloredMessage);
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending message to " + player.getName() + ": " + message);
            plugin.getLogger().warning("Error: " + e.getMessage());
        }
    }

    // Highly optimized placeholder replacement using pre-compiled patterns and caching
    private String replacePlaceholdersOptimized(String command, Player player) {
        // Create cache key for this player's placeholders
        String playerCacheKey = player.getUniqueId().toString();

        // Check if we have cached placeholder values for this player
        Map<String, String> playerValues = getPlayerPlaceholderValues(player, playerCacheKey);

        String result = command;

        // Use pre-compiled patterns for faster replacement
        result = PLAYER_PATTERN.matcher(result).replaceAll(playerValues.get("player"));
        result = PLAYER_NAME_PATTERN.matcher(result).replaceAll(playerValues.get("player_name"));
        result = PLAYER_UUID_PATTERN.matcher(result).replaceAll(playerValues.get("player_uuid"));
        result = PLAYER_DISPLAYNAME_PATTERN.matcher(result).replaceAll(playerValues.get("player_displayname"));
        result = WORLD_PATTERN.matcher(result).replaceAll(playerValues.get("world"));
        result = X_PATTERN.matcher(result).replaceAll(playerValues.get("x"));
        result = Y_PATTERN.matcher(result).replaceAll(playerValues.get("y"));
        result = Z_PATTERN.matcher(result).replaceAll(playerValues.get("z"));

        return result;
    }

    // Get placeholder values with caching for better performance
    private Map<String, String> getPlayerPlaceholderValues(Player player, String cacheKey) {
        // Create a temporary map for current values (location can change frequently)
        Map<String, String> values = new ConcurrentHashMap<>();

        // Cache stable values
        String stableCacheKey = cacheKey + ":stable";
        String stableValues = placeholderCache.get(stableCacheKey);

        if (stableValues == null) {
            // Cache stable player data
            values.put("player", player.getName());
            values.put("player_name", player.getName());
            values.put("player_uuid", player.getUniqueId().toString());
            values.put("player_displayname", player.getDisplayName());

            // Store in cache
            placeholderCache.put(stableCacheKey, "cached");
        } else {
            // Retrieve from cache
            values.put("player", player.getName());
            values.put("player_name", player.getName());
            values.put("player_uuid", player.getUniqueId().toString());
            values.put("player_displayname", player.getDisplayName());
        }

        // Always get fresh location data (changes frequently)
        values.put("world", player.getWorld().getName());
        values.put("x", String.valueOf(player.getLocation().getBlockX()));
        values.put("y", String.valueOf(player.getLocation().getBlockY()));
        values.put("z", String.valueOf(player.getLocation().getBlockZ()));

        return values;
    }

    // Cache management methods
    public void clearPlaceholderCache() {
        placeholderCache.clear();
        try {
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Placeholder cache cleared");
            }
        } catch (Exception e) {
            // Ignore if plugin is not fully initialized
            plugin.getLogger().info("Placeholder cache cleared");
        }
    }

    public int getPlaceholderCacheSize() {
        return placeholderCache.size();
    }

    // Cleanup method to remove old cache entries
    public void cleanupCache() {
        // Remove old cache entries to prevent memory leaks
        if (placeholderCache.size() > 1000) {
            placeholderCache.clear();
            try {
                if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Placeholder cache cleared due to size limit");
                }
            } catch (Exception e) {
                // Ignore if plugin is not fully initialized
                plugin.getLogger().info("Placeholder cache cleared due to size limit");
            }
        }
    }

    // Batch command execution for better performance when executing multiple commands
    public void executeCommands(Player player, java.util.List<String> commands) {
        if (commands == null || commands.isEmpty()) return;

        // Process all commands asynchronously first, then execute in batch
        CompletableFuture.runAsync(() -> {
            java.util.List<String> processedCommands = new java.util.ArrayList<>();

            for (String command : commands) {
                if (command != null && !command.trim().isEmpty()) {
                    String processed = replacePlaceholdersOptimized(command.trim(), player);
                    processedCommands.add(processed);
                }
            }

            // Execute all processed commands on main thread
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (String command : processedCommands) {
                        executeCommandSync(player, command);
                    }
                }
            }.runTask(plugin);

        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Batch command processing failed: " + throwable.getMessage());
            return null;
        });
    }
}