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
    private Map<String, String> customTitles;
    private List<String> titlePatterns;

    public ConfigManager(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();

        // Reload config from file
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Load all configuration sections
        loadSlotMapping();
        loadItemCommands();
        loadMenuDetection();

        plugin.getLogger().info("Configuration loaded successfully!");
        if (isDebugEnabled()) {
            plugin.getLogger().info("Debug mode enabled");
        }
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
                    plugin.getLogger().warning("Invalid slot mapping key: " + key);
                }
            }
        }

        // Default mapping if none configured
        if (slotMapping.isEmpty()) {
            slotMapping.put(1, 10);
            slotMapping.put(2, 11);
            slotMapping.put(3, 12);
            slotMapping.put(4, 13);
            slotMapping.put(5, 19);
            slotMapping.put(6, 20);
            slotMapping.put(7, 21);
            slotMapping.put(8, 22);
            slotMapping.put(9, 28);
            slotMapping.put(10, 29);
            slotMapping.put(11, 30);
            slotMapping.put(12, 31);
        }

        if (isDebugEnabled()) {
            plugin.getLogger().info("Loaded slot mapping: " + slotMapping);
        }
    }

    private void loadItemCommands() {
        itemCommands = new HashMap<>();
        ConfigurationSection commandsSection = config.getConfigurationSection("items-command");

        if (commandsSection != null) {
            for (String craftingFile : commandsSection.getKeys(false)) {
                if (isDebugEnabled()) {
                    plugin.getLogger().info("Loading commands for file: '" + craftingFile + "'");
                }

                Map<Integer, List<String>> fileCommands = new HashMap<>();
                ConfigurationSection fileSection = commandsSection.getConfigurationSection(craftingFile);

                if (fileSection != null) {
                    for (String slotKey : fileSection.getKeys(false)) {
                        if (isDebugEnabled()) {
                            plugin.getLogger().info("Processing slot key: '" + slotKey + "' for file: '" + craftingFile + "'");
                        }

                        try {
                            int slot = Integer.parseInt(slotKey);
                            Object commandsObj = fileSection.get(slotKey);

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
                                fileCommands.put(slot, commands);
                                if (isDebugEnabled()) {
                                    plugin.getLogger().info("Added " + commands.size() + " commands for slot " + slot + " in " + craftingFile);
                                }
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid slot number in " + craftingFile + ": " + slotKey);
                        }
                    }
                } else {
                    if (isDebugEnabled()) {
                        plugin.getLogger().warning("No configuration section found for: " + craftingFile);
                    }
                }

                if (!fileCommands.isEmpty()) {
                    itemCommands.put(craftingFile, fileCommands);
                    if (isDebugEnabled()) {
                        plugin.getLogger().info("Successfully loaded " + fileCommands.size() + " slot configurations for " + craftingFile);
                    }
                }
            }
        } else {
            plugin.getLogger().warning("No 'items-command' section found in config.yml");
        }

        plugin.getLogger().info("Loaded item commands for " + itemCommands.size() + " crafting files");
        if (isDebugEnabled()) {
            plugin.getLogger().info("Loaded crafting files: " + itemCommands.keySet());
        }
    }

    private void loadMenuDetection() {
        // Load title patterns
        titlePatterns = config.getStringList("menu-detection.title-patterns");
        if (titlePatterns.isEmpty()) {
            titlePatterns = Arrays.asList("craft", "chế tạo", "recipe", "công thức");
        }

        // Initialize empty custom titles map
        customTitles = new HashMap<>();

        if (isDebugEnabled()) {
            plugin.getLogger().info("Loaded title patterns: " + titlePatterns);
        }
    }

    // Getter methods

    public List<String> getCommandsForItem(String craftingFile, int slotPosition) {
        Map<Integer, List<String>> fileCommands = itemCommands.get(craftingFile);
        if (fileCommands != null) {
            return fileCommands.getOrDefault(slotPosition, new ArrayList<>());
        }
        return new ArrayList<>();
    }

    public List<String> getCommandsForSlot(String craftingFile, int inventorySlot) {
        // Convert inventory slot to position number
        int position = getPositionFromSlot(inventorySlot);
        if (position > 0) {
            return getCommandsForItem(craftingFile, position);
        }
        return new ArrayList<>();
    }

    public int getSlotFromPosition(int position) {
        return slotMapping.getOrDefault(position, -1);
    }

    public int getPositionFromSlot(int slot) {
        for (Map.Entry<Integer, Integer> entry : slotMapping.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public boolean isRequireItemSlot(int slot) {
        return slotMapping.containsValue(slot);
    }

    public List<String> getTitlePatterns() {
        return new ArrayList<>(titlePatterns);
    }

    public String getCustomTitle(String craftingFile) {
        return customTitles.get(craftingFile);
    }

    public boolean isStrictMatching() {
        return config.getBoolean("menu-detection.strict-matching", false);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("settings.debug", false);
    }

    public boolean shouldCancelClick() {
        return config.getBoolean("settings.cancel-click", true);
    }

    public int getCommandDelay() {
        return config.getInt("settings.command-delay", 2);
    }

    public boolean isSoundEnabled() {
        return config.getBoolean("settings.click-sound.enabled", true);
    }

    public String getClickSound() {
        return config.getString("settings.click-sound.sound", "UI_BUTTON_CLICK");
    }

    public float getSoundVolume() {
        return (float) config.getDouble("settings.click-sound.volume", 0.5);
    }

    public float getSoundPitch() {
        return (float) config.getDouble("settings.click-sound.pitch", 1.0);
    }

    public boolean areParticlesEnabled() {
        return config.getBoolean("settings.click-particles.enabled", true);
    }

    public String getClickParticle() {
        return config.getString("settings.click-particles.particle", "VILLAGER_HAPPY");
    }

    public int getParticleCount() {
        return config.getInt("settings.click-particles.count", 5);
    }

    public int getMaxCommandsPerClick() {
        return config.getInt("commands.max-commands-per-click", 10);
    }

    public boolean isAsyncExecutionEnabled() {
        return config.getBoolean("commands.async-execution", true);
    }

    public boolean isCooldownEnabled() {
        return config.getBoolean("commands.cooldown.enabled", true);
    }

    public int getCooldownDuration() {
        return config.getInt("commands.cooldown.duration", 1);
    }

    public boolean shouldLogErrors() {
        return config.getBoolean("commands.error-handling.log-errors", true);
    }

    public boolean shouldNotifyPlayerOnError() {
        return config.getBoolean("commands.error-handling.notify-player", false);
    }

    public String getFallbackMessage() {
        return config.getString("commands.error-handling.fallback-message",
                "&cSomething went wrong! Please contact an administrator.");
    }

    // Utility methods

    public Set<String> getConfiguredCraftingFiles() {
        return new HashSet<>(itemCommands.keySet());
    }

    public void reloadConfiguration() {
        loadConfig();
        plugin.getLogger().info("Configuration reloaded!");
    }

    public String getConfigVersion() {
        return config.getString("version", "unknown");
    }

    public void debugInfo() {
        if (!isDebugEnabled()) return;

        plugin.getLogger().info("=== Configuration Debug Info ===");
        plugin.getLogger().info("Version: " + getConfigVersion());
        plugin.getLogger().info("Configured crafting files: " + getConfiguredCraftingFiles());
        plugin.getLogger().info("Slot mapping: " + slotMapping);
        plugin.getLogger().info("Title patterns: " + titlePatterns);
        plugin.getLogger().info("Cancel click: " + shouldCancelClick());
        plugin.getLogger().info("Command delay: " + getCommandDelay());
        plugin.getLogger().info("Max commands per click: " + getMaxCommandsPerClick());

        // Debug each crafting file
        for (Map.Entry<String, Map<Integer, List<String>>> entry : itemCommands.entrySet()) {
            plugin.getLogger().info("File '" + entry.getKey() + "' has " + entry.getValue().size() + " configured slots");
            for (Map.Entry<Integer, List<String>> slotEntry : entry.getValue().entrySet()) {
                plugin.getLogger().info("  Slot " + slotEntry.getKey() + ": " + slotEntry.getValue().size() + " commands");
            }
        }
    }
}