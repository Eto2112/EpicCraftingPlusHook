package com.eto2112.epiccraftingshook.utils;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class CommandExecutor {

    private final EpicCraftingsHookPlugin plugin;

    public CommandExecutor(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
    }

    public void executeCommand(Player player, String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        command = command.trim();

        // Replace placeholders
        command = replacePlaceholders(command, player);

        if (command.startsWith("[console]")) {
            executeConsoleCommand(command.substring(9).trim());
        } else if (command.startsWith("[op]")) {
            executeOpCommand(player, command.substring(4).trim());
        } else if (command.startsWith("[player]")) {
            executePlayerCommand(player, command.substring(8).trim());
        } else if (command.startsWith("[message]")) {
            sendMessage(player, command.substring(9).trim());
        } else {
            // Default to console command if no prefix
            executeConsoleCommand(command);
        }
    }

    private void executeConsoleCommand(String command) {
        if (command.isEmpty()) return;

        plugin.getLogger().info("Executing console command: " + command);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Exception e) {
                plugin.getLogger().warning("Error executing console command: " + command);
                plugin.getLogger().warning("Error: " + e.getMessage());
            }
        });
    }

    private void executeOpCommand(Player player, String command) {
        if (command.isEmpty()) return;

        plugin.getLogger().info("Executing OP command for " + player.getName() + ": " + command);

        boolean wasOp = player.isOp();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Temporarily give OP
                if (!wasOp) {
                    player.setOp(true);
                }

                // Execute command as player
                player.performCommand(command);

            } catch (Exception e) {
                plugin.getLogger().warning("Error executing OP command for " + player.getName() + ": " + command);
                plugin.getLogger().warning("Error: " + e.getMessage());
            } finally {
                // Restore original OP status
                if (!wasOp) {
                    player.setOp(false);
                }
            }
        });
    }

    private void executePlayerCommand(Player player, String command) {
        if (command.isEmpty()) return;

        plugin.getLogger().info("Executing player command for " + player.getName() + ": " + command);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                player.performCommand(command);
            } catch (Exception e) {
                plugin.getLogger().warning("Error executing player command for " + player.getName() + ": " + command);
                plugin.getLogger().warning("Error: " + e.getMessage());
            }
        });
    }

    private void sendMessage(Player player, String message) {
        if (message.isEmpty()) return;

        plugin.getLogger().info("Sending message to " + player.getName() + ": " + message);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(coloredMessage);
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending message to " + player.getName() + ": " + message);
                plugin.getLogger().warning("Error: " + e.getMessage());
            }
        });
    }

    private String replacePlaceholders(String command, Player player) {
        return command
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%player_displayname%", player.getDisplayName())
                .replace("%world%", player.getWorld().getName())
                .replace("%x%", String.valueOf(player.getLocation().getBlockX()))
                .replace("%y%", String.valueOf(player.getLocation().getBlockY()))
                .replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
    }
}