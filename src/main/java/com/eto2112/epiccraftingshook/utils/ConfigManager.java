package com.eto2112.epiccraftingshook.utils;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ConfigManager {

    private final EpicCraftingsHookPlugin plugin;
    private FileConfiguration config;
    private Map<Integer, Integer> slotMapping;
    private Map<String, Map<Integer, List<String>>> itemCommands;

    public ConfigManager(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadSlotMapping();
        loadItemCommands();

        plugin.getLogger().info("Configuration loaded successfully!");
    }

    private void loadSlotMapping() {
        slotMapping = new HashMap<>();
        ConfigurationSection mappingSection = config.getConfigurationSection("slot-mapping.positions");

        if (mappingSection != null) {
            for (String key : mappingSection.getKeys(false)) {
                try {
                    int position = Integer.parseInt(key);
                    int slot = mappingSection.getInt(key);
                    slotMapping.put(position, slot);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid slot mapping: " + key);
                }
            }
        }

        // Default mapping if none configured
        if (slotMapping.isEmpty()) {
            slotMapping.put(1, 10); slotMapping.put(2, 11); slotMapping.put(3, 12); slotMapping.put(4, 13);
            slotMapping.put(5, 19); slotMapping.put(6, 20); slotMapping.put(7, 21); slotMapping.put(8, 22);
            slotMapping.put(9, 28); slotMapping.put(10, 29); slotMapping.put(11, 30); slotMapping.put(12, 31);
        }
    }

    private void loadItemCommands() {
        itemCommands = new HashMap<>();
        ConfigurationSection commandsSection = config.getConfigurationSection("items-command");

        if (commandsSection != null) {
            for (String recipeKey : commandsSection.getKeys(false)) {
                Map<Integer, List<String>> recipeCommands = new HashMap<>();
                ConfigurationSection recipeSection = commandsSection.getConfigurationSection(recipeKey);

                if (recipeSection != null) {
                    for (String slotKey : recipeSection.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotKey);
                            Object commandsObj = recipeSection.get(slotKey);

                            List<String> commands = new ArrayList<>();
                            if (commandsObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Object> rawCommands = (List<Object>) commandsObj;
                                for (Object cmd : rawCommands) {
                                    if (cmd != null) {
                                        commands.add(cmd.toString());
                                    }
                                }
                            } else if (commandsObj instanceof String) {
                                commands.add((String) commandsObj);
                            }

                            if (!commands.isEmpty()) {
                                recipeCommands.put(slot, commands);
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid slot number in " + recipeKey + ": " + slotKey);
                        }
                    }
                }

                if (!recipeCommands.isEmpty()) {
                    itemCommands.put(recipeKey, recipeCommands);
                }
            }
        }

        plugin.getLogger().info("Loaded commands for " + itemCommands.size() + " recipes");
    }

    // Core getter methods
    public List<String> getCommandsForSlot(String recipeKey, int inventorySlot) {
        int position = getPositionFromSlot(inventorySlot);
        if (position > 0) {
            Map<Integer, List<String>> recipeCommands = itemCommands.get(recipeKey);
            if (recipeCommands != null) {
                return recipeCommands.getOrDefault(position, new ArrayList<>());
            }
        }
        return new ArrayList<>();
    }

    public boolean isRequireItemSlot(int slot) {
        return slotMapping.containsValue(slot);
    }

    private int getPositionFromSlot(int slot) {
        for (Map.Entry<Integer, Integer> entry : slotMapping.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return -1;
    }

    // Configuration getters
    public boolean isDebugEnabled() {
        return config.getBoolean("settings.debug", false);
    }

    public boolean isCooldownEnabled() {
        return config.getBoolean("commands.cooldown.enabled", true);
    }

    public int getCooldownDuration() {
        return config.getInt("commands.cooldown.duration", 1);
    }

    // Admin command helpers
    public Set<String> getConfiguredRecipes() {
        return new HashSet<>(itemCommands.keySet());
    }

    public List<String> getCommandsForItem(String recipeKey, int position) {
        Map<Integer, List<String>> recipeCommands = itemCommands.get(recipeKey);
        if (recipeCommands != null) {
            return recipeCommands.getOrDefault(position, new ArrayList<>());
        }
        return new ArrayList<>();
    }

    public int getSlotFromPosition(int position) {
        return slotMapping.getOrDefault(position, -1);
    }

    public void reloadConfiguration() {
        loadConfig();
        plugin.getLogger().info("Configuration reloaded!");
    }

    public String getConfigVersion() {
        return config.getString("version", "unknown");
    }

    // Additional methods needed by HookCommand
    public void debugInfo() {
        if (!isDebugEnabled()) return;

        plugin.getLogger().info("=== Configuration Debug Info ===");
        plugin.getLogger().info("Version: " + getConfigVersion());
        plugin.getLogger().info("Configured recipes: " + getConfiguredRecipes());
        plugin.getLogger().info("Slot mapping: " + slotMapping);
        plugin.getLogger().info("Debug enabled: " + isDebugEnabled());
        plugin.getLogger().info("Cooldown enabled: " + isCooldownEnabled());
        plugin.getLogger().info("Cooldown duration: " + getCooldownDuration());

        // Debug each recipe
        for (Map.Entry<String, Map<Integer, List<String>>> entry : itemCommands.entrySet()) {
            plugin.getLogger().info("Recipe '" + entry.getKey() + "' has " + entry.getValue().size() + " configured slots");
            for (Map.Entry<Integer, List<String>> slotEntry : entry.getValue().entrySet()) {
                plugin.getLogger().info("  Position " + slotEntry.getKey() + ": " + slotEntry.getValue().size() + " commands");
            }
        }
    }

    // Dummy methods for HookCommand compatibility
    public boolean isSoundEnabled() { return false; }
    public boolean areParticlesEnabled() { return false; }
    public boolean shouldCancelClick() { return true; }
}