package com.eto2112.epiccraftingshook.commands;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HookCommand implements CommandExecutor, TabCompleter {

    private final EpicCraftingsHookPlugin plugin;

    public HookCommand(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("echook.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "test":
                if (args.length > 1) {
                    handleTest(sender, args[1]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /echook test <item_id>");
                    sender.sendMessage(ChatColor.YELLOW + "Example: /echook test BICHNHA");
                }
                break;
            case "debug":
                handleDebug(sender);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== EpicCraftingsRequireItemHook ===");
        sender.sendMessage(ChatColor.YELLOW + "/echook reload" + ChatColor.WHITE + " - Reload the plugin configuration");
        sender.sendMessage(ChatColor.YELLOW + "/echook info" + ChatColor.WHITE + " - Show plugin information");
        sender.sendMessage(ChatColor.YELLOW + "/echook test <item_id>" + ChatColor.WHITE + " - Test item configuration");
        sender.sendMessage(ChatColor.YELLOW + "/echook list" + ChatColor.WHITE + " - List all configured items");
        sender.sendMessage(ChatColor.YELLOW + "/echook debug" + ChatColor.WHITE + " - Show debug information");
        sender.sendMessage(ChatColor.GRAY + "Item IDs use MMOItems ID format (e.g., BICHNHA)");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.getConfigManager().reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "EpicCraftingsRequireItemHook configuration reloaded successfully!");
            plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Plugin Information ===");
        sender.sendMessage(ChatColor.YELLOW + "Plugin: " + ChatColor.WHITE + plugin.getDescription().getName());
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + plugin.getDescription().getAuthors().toString());
        sender.sendMessage(ChatColor.YELLOW + "Config Version: " + ChatColor.WHITE + plugin.getConfigManager().getConfigVersion());

        // Check EpicCraftingsPlus status
        boolean epicCraftingsFound = plugin.getServer().getPluginManager().getPlugin("EpicCraftingsPlus") != null;
        sender.sendMessage(ChatColor.YELLOW + "EpicCraftingsPlus: " +
                (epicCraftingsFound ? ChatColor.GREEN + "Found" : ChatColor.RED + "Not Found"));

        // Show configured items count
        int itemCount = plugin.getConfigManager().getConfiguredRecipes().size();
        sender.sendMessage(ChatColor.YELLOW + "Configured Items: " + ChatColor.WHITE + itemCount);

        // Show debug status
        boolean debugEnabled = plugin.getConfigManager().isDebugEnabled();
        sender.sendMessage(ChatColor.YELLOW + "Debug Mode: " +
                (debugEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
    }

    private void handleTest(CommandSender sender, String itemId) {
        sender.sendMessage(ChatColor.YELLOW + "Testing configuration for item: " + ChatColor.WHITE + itemId);

        try {
            // Convert to uppercase to match config format
            String upperItemId = itemId.toUpperCase();

            boolean hasCommands = false;
            for (int i = 1; i <= 12; i++) {
                List<String> commands = plugin.getConfigManager().getCommandsForItem(upperItemId, i);
                if (!commands.isEmpty()) {
                    int slot = plugin.getConfigManager().getSlotFromPosition(i);
                    sender.sendMessage(ChatColor.GREEN + "Position " + i + " (slot " + slot + "): " +
                            commands.size() + " command(s)");
                    hasCommands = true;

                    // Show first command as example
                    if (!commands.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "  Example: " + commands.get(0));
                    }
                }
            }

            if (!hasCommands) {
                sender.sendMessage(ChatColor.RED + "No commands configured for item: " + upperItemId);
                sender.sendMessage(ChatColor.YELLOW + "Available items: " +
                        plugin.getConfigManager().getConfiguredRecipes().toString());
            } else {
                sender.sendMessage(ChatColor.GREEN + "Configuration test completed!");
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error testing configuration: " + e.getMessage());
            plugin.getLogger().warning("Error in test command: " + e.getMessage());
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Configured Items ===");

        var configuredItems = plugin.getConfigManager().getConfiguredRecipes();

        if (configuredItems.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No items configured!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Found " + configuredItems.size() + " configured item(s):");

        for (String itemId : configuredItems) {
            // Count how many positions have commands
            int configuredPositions = 0;
            for (int i = 1; i <= 12; i++) {
                if (!plugin.getConfigManager().getCommandsForItem(itemId, i).isEmpty()) {
                    configuredPositions++;
                }
            }

            sender.sendMessage(ChatColor.GREEN + "- " + ChatColor.WHITE + itemId +
                    ChatColor.GRAY + " (" + configuredPositions + " position(s) configured)");
        }

        sender.sendMessage(ChatColor.GRAY + "Use '/echook test <item_id>' to test a specific item");
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Debug Information ===");

        if (!plugin.getConfigManager().isDebugEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Debug mode is currently disabled.");
            sender.sendMessage(ChatColor.YELLOW + "Enable it in config.yml (settings.debug: true) and reload.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Debug mode is enabled. Check console for detailed output...");
        }

        plugin.getConfigManager().debugInfo();
        sender.sendMessage(ChatColor.GREEN + "Debug information printed to console!");

        // Show some basic debug info to the sender
        sender.sendMessage(ChatColor.YELLOW + "Plugin Status:");
        sender.sendMessage(ChatColor.WHITE + "- Items configured: " + plugin.getConfigManager().getConfiguredRecipes().size());
        sender.sendMessage(ChatColor.WHITE + "- Slot mappings: " + getSlotMappingCount());
        sender.sendMessage(ChatColor.WHITE + "- Sound enabled: " + plugin.getConfigManager().isSoundEnabled());
        sender.sendMessage(ChatColor.WHITE + "- Particles enabled: " + plugin.getConfigManager().areParticlesEnabled());
        sender.sendMessage(ChatColor.WHITE + "- Click cancelling: " + plugin.getConfigManager().shouldCancelClick());
        sender.sendMessage(ChatColor.WHITE + "- Command cooldown: " + plugin.getConfigManager().getCooldownDuration() + "s");
    }

    private int getSlotMappingCount() {
        int count = 0;
        for (int i = 1; i <= 12; i++) {
            if (plugin.getConfigManager().getSlotFromPosition(i) != -1) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("echook.admin")) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subCommands = List.of("reload", "info", "test", "debug", "list");

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String partial = args[1].toLowerCase();
            for (String itemId : plugin.getConfigManager().getConfiguredRecipes()) {
                if (itemId.toLowerCase().startsWith(partial)) {
                    completions.add(itemId);
                }
            }
        }

        return completions;
    }
}