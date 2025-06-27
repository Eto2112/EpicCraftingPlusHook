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
                    sender.sendMessage(ChatColor.RED + "Usage: /echook test <crafting_id>");
                }
                break;
            case "debug":
                handleDebug(sender);
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
        sender.sendMessage(ChatColor.YELLOW + "/echook test <crafting_id>" + ChatColor.WHITE + " - Test crafting configuration");
        sender.sendMessage(ChatColor.YELLOW + "/echook debug" + ChatColor.WHITE + " - Show debug information");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.getConfigManager().reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "EpicCraftingsRequireItemHook configuration reloaded successfully!");
            plugin.getLogger().info("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading configuration: " + e.getMessage());
            plugin.getLogger().severe("Error reloading configuration: " + e.getMessage());
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Plugin Information ===");
        sender.sendMessage(ChatColor.YELLOW + "Plugin: " + ChatColor.WHITE + plugin.getDescription().getName());
        sender.sendMessage(ChatColor.YELLOW + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + plugin.getDescription().getAuthors().toString());
        sender.sendMessage(ChatColor.YELLOW + "Config Version: " + ChatColor.WHITE + plugin.getConfigManager().getConfigVersion());
        sender.sendMessage(ChatColor.YELLOW + "EpicCraftingsPlus: " + ChatColor.WHITE +
                (plugin.getServer().getPluginManager().getPlugin("EpicCraftingsPlus") != null ?
                        ChatColor.GREEN + "Found" : ChatColor.RED + "Not Found"));
        sender.sendMessage(ChatColor.YELLOW + "Configured Files: " + ChatColor.WHITE +
                plugin.getConfigManager().getConfiguredCraftingFiles().size());
    }

    private void handleTest(CommandSender sender, String craftingId) {
        sender.sendMessage(ChatColor.YELLOW + "Testing configuration for: " + craftingId);

        try {
            // Add .yml extension if not present
            if (!craftingId.endsWith(".yml")) {
                craftingId += ".yml";
            }

            boolean hasCommands = false;
            for (int i = 1; i <= 12; i++) {
                List<String> commands = plugin.getConfigManager().getCommandsForItem(craftingId, i);
                if (!commands.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "Position " + i + " (slot " +
                            plugin.getConfigManager().getSlotFromPosition(i) + "): " +
                            commands.size() + " commands");
                    hasCommands = true;

                    // Show first command as example
                    if (!commands.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "  Example: " + commands.get(0));
                    }
                }
            }

            if (!hasCommands) {
                sender.sendMessage(ChatColor.RED + "No commands configured for " + craftingId);
            } else {
                sender.sendMessage(ChatColor.GREEN + "Configuration test completed!");
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error testing configuration: " + e.getMessage());
        }
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Debug Information ===");
        plugin.getConfigManager().debugInfo();
        sender.sendMessage(ChatColor.GREEN + "Debug information printed to console!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subCommands = List.of("reload", "info", "test", "debug");

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String partial = args[1].toLowerCase();
            for (String craftingFile : plugin.getConfigManager().getConfiguredCraftingFiles()) {
                if (craftingFile.toLowerCase().startsWith(partial)) {
                    completions.add(craftingFile);
                }
            }
        }

        return completions;
    }
}