package com.eto2112.epiccraftingshook.commands;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HookCommand implements CommandExecutor, TabCompleter {

    private final EpicCraftingsHookPlugin plugin;

    // Cache for tab completions and command results
    private final Map<String, List<String>> tabCompletionCache = new ConcurrentHashMap<>();
    private final Map<String, String> commandResultCache = new ConcurrentHashMap<>();

    // Pre-computed constants for better performance
    private static final String PERMISSION_DENIED = ChatColor.RED + "You don't have permission to use this command!";
    private static final String PLUGIN_PREFIX = ChatColor.GOLD + "=== EpicCraftingsRequireItemHook ===";
    private static final String DEBUG_PREFIX = ChatColor.GOLD + "=== Debug Information ===";
    private static final String INFO_PREFIX = ChatColor.GOLD + "=== Plugin Information ===";
    private static final String LIST_PREFIX = ChatColor.GOLD + "=== Configured Items ===";

    // Command constants
    private static final List<String> SUB_COMMANDS = Arrays.asList("reload", "info", "test", "debug", "list");
    private static final String ADMIN_PERMISSION = "echook.admin";

    public HookCommand(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        initializeTabCompletions();
    }

    // Pre-compute static tab completions
    private void initializeTabCompletions() {
        tabCompletionCache.put("subcommands", new ArrayList<>(SUB_COMMANDS));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Fast permission check
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PERMISSION_DENIED);
            return true;
        }

        if (args.length == 0) {
            sendHelpMessageOptimized(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Use switch for better performance than if-else chain
        switch (subCommand) {
            case "reload":
                handleReloadAsync(sender);
                break;
            case "info":
                handleInfoAsync(sender);
                break;
            case "test":
                if (args.length > 1) {
                    handleTestAsync(sender, args[1]);
                } else {
                    sendTestUsage(sender);
                }
                break;
            case "debug":
                handleDebugAsync(sender);
                break;
            case "list":
                handleListAsync(sender);
                break;
            default:
                sendHelpMessageOptimized(sender);
                break;
        }

        return true;
    }

    // Optimized help message with cached formatting
    private void sendHelpMessageOptimized(CommandSender sender) {
        String cacheKey = "help_message";
        String cachedMessage = commandResultCache.get(cacheKey);

        if (cachedMessage == null) {
            StringBuilder help = new StringBuilder();
            help.append(PLUGIN_PREFIX).append("\n");
            help.append(ChatColor.YELLOW).append("/echook reload").append(ChatColor.WHITE).append(" - Reload the plugin configuration\n");
            help.append(ChatColor.YELLOW).append("/echook info").append(ChatColor.WHITE).append(" - Show plugin information\n");
            help.append(ChatColor.YELLOW).append("/echook test <item_id>").append(ChatColor.WHITE).append(" - Test item configuration\n");
            help.append(ChatColor.YELLOW).append("/echook list").append(ChatColor.WHITE).append(" - List all configured items\n");
            help.append(ChatColor.YELLOW).append("/echook debug").append(ChatColor.WHITE).append(" - Show debug information\n");
            help.append(ChatColor.GRAY).append("Item IDs use MMOItems ID format (e.g., BICHNHA)");

            cachedMessage = help.toString();
            commandResultCache.put(cacheKey, cachedMessage);
        }

        // Send each line separately for better formatting
        String[] lines = cachedMessage.split("\n");
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    // Async reload with progress feedback
    private void handleReloadAsync(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading configuration...");

        CompletableFuture.runAsync(() -> {
            try {
                // Clear caches first
                clearCaches();

                // Reload configuration
                plugin.getConfigManager().reloadConfiguration();

                // Send success message on main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
                        plugin.getLogger().info("Configuration reloaded by " + sender.getName());
                    }
                }.runTask(plugin);

            } catch (Exception e) {
                // Send error message on main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
                        plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
                    }
                }.runTask(plugin);
            }
        });
    }

    // Async info gathering with cached results
    private void handleInfoAsync(CommandSender sender) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return gatherPluginInfo();
            } catch (Exception e) {
                plugin.getLogger().warning("Error gathering plugin info: " + e.getMessage());
                return "Error gathering plugin information";
            }
        }).thenAccept(info -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(INFO_PREFIX);
                    String[] lines = info.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }.runTask(plugin);
        });
    }

    // Optimized info gathering
    private String gatherPluginInfo() {
        StringBuilder info = new StringBuilder();

        info.append(ChatColor.YELLOW).append("Plugin: ").append(ChatColor.WHITE)
                .append(plugin.getDescription().getName()).append("\n");
        info.append(ChatColor.YELLOW).append("Version: ").append(ChatColor.WHITE)
                .append(plugin.getDescription().getVersion()).append("\n");
        info.append(ChatColor.YELLOW).append("Author: ").append(ChatColor.WHITE)
                .append(plugin.getDescription().getAuthors().toString()).append("\n");
        info.append(ChatColor.YELLOW).append("Config Version: ").append(ChatColor.WHITE)
                .append(plugin.getConfigManager().getConfigVersion()).append("\n");

        // Check dependencies efficiently
        boolean epicCraftingsFound = plugin.getServer().getPluginManager().getPlugin("EpicCraftingsPlus") != null;
        info.append(ChatColor.YELLOW).append("EpicCraftingsPlus: ")
                .append(epicCraftingsFound ? ChatColor.GREEN + "Found" : ChatColor.RED + "Not Found").append("\n");

        // Get stats
        int itemCount = plugin.getConfigManager().getConfiguredRecipes().size();
        info.append(ChatColor.YELLOW).append("Configured Items: ").append(ChatColor.WHITE).append(itemCount).append("\n");

        boolean debugEnabled = plugin.getConfigManager().isDebugEnabled();
        info.append(ChatColor.YELLOW).append("Debug Mode: ")
                .append(debugEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled");

        return info.toString();
    }

    // Async test with better error handling
    private void handleTestAsync(CommandSender sender, String itemId) {
        String upperItemId = itemId.toUpperCase();
        sender.sendMessage(ChatColor.YELLOW + "Testing configuration for item: " + ChatColor.WHITE + upperItemId);

        CompletableFuture.supplyAsync(() -> {
            return performItemTest(upperItemId);
        }).thenAccept(result -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    String[] lines = result.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }.runTask(plugin);
        }).exceptionally(throwable -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(ChatColor.RED + "Error testing configuration: " + throwable.getMessage());
                }
            }.runTask(plugin);
            return null;
        });
    }

    // Optimized item testing
    private String performItemTest(String itemId) {
        StringBuilder result = new StringBuilder();
        boolean hasCommands = false;

        for (int i = 1; i <= 12; i++) {
            List<String> commands = plugin.getConfigManager().getCommandsForItem(itemId, i);
            if (!commands.isEmpty()) {
                int slot = plugin.getConfigManager().getSlotFromPosition(i);
                result.append(ChatColor.GREEN).append("Position ").append(i)
                        .append(" (slot ").append(slot).append("): ")
                        .append(commands.size()).append(" command(s)\n");
                hasCommands = true;

                // Show first command as example
                if (!commands.isEmpty()) {
                    result.append(ChatColor.GRAY).append("  Example: ").append(commands.get(0)).append("\n");
                }
            }
        }

        if (!hasCommands) {
            result.append(ChatColor.RED).append("No commands configured for item: ").append(itemId).append("\n");
            result.append(ChatColor.YELLOW).append("Available items: ")
                    .append(plugin.getConfigManager().getConfiguredRecipes().toString());
        } else {
            result.append(ChatColor.GREEN).append("Configuration test completed!");
        }

        return result.toString();
    }

    // Async list with pagination support
    private void handleListAsync(CommandSender sender) {
        CompletableFuture.supplyAsync(() -> {
            return generateItemList();
        }).thenAccept(listData -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(LIST_PREFIX);
                    String[] lines = listData.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            sender.sendMessage(line);
                        }
                    }
                }
            }.runTask(plugin);
        });
    }

    // Optimized list generation
    private String generateItemList() {
        StringBuilder list = new StringBuilder();
        Set<String> configuredItems = plugin.getConfigManager().getConfiguredRecipes();

        if (configuredItems.isEmpty()) {
            list.append(ChatColor.RED).append("No items configured!");
            return list.toString();
        }

        list.append(ChatColor.YELLOW).append("Found ").append(configuredItems.size()).append(" configured item(s):\n");

        // Sort items for better presentation
        List<String> sortedItems = new ArrayList<>(configuredItems);
        Collections.sort(sortedItems);

        for (String itemId : sortedItems) {
            // Count configured positions efficiently
            int configuredPositions = 0;
            for (int i = 1; i <= 12; i++) {
                if (!plugin.getConfigManager().getCommandsForItem(itemId, i).isEmpty()) {
                    configuredPositions++;
                }
            }

            list.append(ChatColor.GREEN).append("- ").append(ChatColor.WHITE).append(itemId)
                    .append(ChatColor.GRAY).append(" (").append(configuredPositions).append(" position(s) configured)\n");
        }

        list.append(ChatColor.GRAY).append("Use '/echook test <item_id>' to test a specific item");
        return list.toString();
    }

    // Async debug with comprehensive stats
    private void handleDebugAsync(CommandSender sender) {
        CompletableFuture.supplyAsync(() -> {
            return gatherDebugInfo();
        }).thenAccept(debugInfo -> {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sender.sendMessage(DEBUG_PREFIX);

                    if (!plugin.getConfigManager().isDebugEnabled()) {
                        sender.sendMessage(ChatColor.YELLOW + "Debug mode is currently disabled.");
                        sender.sendMessage(ChatColor.YELLOW + "Enable it in config.yml (settings.debug: true) and reload.");
                    } else {
                        sender.sendMessage(ChatColor.GREEN + "Debug mode is enabled. Check console for detailed output...");
                    }

                    // Print debug info to console
                    plugin.getConfigManager().debugInfo();

                    // Show stats to sender
                    String[] lines = debugInfo.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            sender.sendMessage(line);
                        }
                    }

                    sender.sendMessage(ChatColor.GREEN + "Debug information printed to console!");
                }
            }.runTask(plugin);
        });
    }

    // Comprehensive debug info gathering
    private String gatherDebugInfo() {
        StringBuilder debug = new StringBuilder();

        debug.append(ChatColor.YELLOW).append("Plugin Status:\n");
        debug.append(ChatColor.WHITE).append("- Items configured: ")
                .append(plugin.getConfigManager().getConfiguredRecipes().size()).append("\n");
        debug.append(ChatColor.WHITE).append("- Slot mappings: ").append(getSlotMappingCount()).append("\n");
        debug.append(ChatColor.WHITE).append("- Sound enabled: ")
                .append(plugin.getConfigManager().isSoundEnabled()).append("\n");
        debug.append(ChatColor.WHITE).append("- Particles enabled: ")
                .append(plugin.getConfigManager().areParticlesEnabled()).append("\n");
        debug.append(ChatColor.WHITE).append("- Click cancelling: ")
                .append(plugin.getConfigManager().shouldCancelClick()).append("\n");
        debug.append(ChatColor.WHITE).append("- Command cooldown: ")
                .append(plugin.getConfigManager().getCooldownDuration()).append("s\n");

        // Cache statistics
        debug.append(ChatColor.WHITE).append("- Config cache size: ")
                .append(plugin.getConfigManager().getCommandCacheSize()).append("\n");
        debug.append(ChatColor.WHITE).append("- Command cache size: ")
                .append(plugin.getCommandExecutor().getPlaceholderCacheSize());

        return debug.toString();
    }

    // Optimized slot mapping count
    private int getSlotMappingCount() {
        int count = 0;
        for (int i = 1; i <= 12; i++) {
            if (plugin.getConfigManager().getSlotFromPosition(i) != -1) {
                count++;
            }
        }
        return count;
    }

    // Optimized test usage message
    private void sendTestUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /echook test <item_id>");
        sender.sendMessage(ChatColor.YELLOW + "Example: /echook test BICHNHA");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Use cached sub-commands
            List<String> subCommands = tabCompletionCache.get("subcommands");
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String partial = args[1].toLowerCase();

            // Cache item completions for test command
            String cacheKey = "test_items:" + partial;
            List<String> cached = tabCompletionCache.get(cacheKey);

            if (cached == null) {
                cached = new ArrayList<>();
                for (String itemId : plugin.getConfigManager().getConfiguredRecipes()) {
                    if (itemId.toLowerCase().startsWith(partial)) {
                        cached.add(itemId);
                    }
                }

                // Only cache if we have results and partial is long enough
                if (!cached.isEmpty() && partial.length() >= 2) {
                    tabCompletionCache.put(cacheKey, cached);
                }
            }

            completions.addAll(cached);
        }

        return completions;
    }

    // Cache management
    private void clearCaches() {
        commandResultCache.clear();
        tabCompletionCache.clear();
        initializeTabCompletions(); // Rebuild static completions
    }

    // Cleanup method for periodic maintenance
    public void cleanupCaches() {
        // Clear caches if they get too large
        if (commandResultCache.size() > 50) {
            commandResultCache.clear();
        }
        if (tabCompletionCache.size() > 100) {
            tabCompletionCache.clear();
            initializeTabCompletions();
        }
    }
}