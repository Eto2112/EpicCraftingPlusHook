package com.eto2112.epiccraftingshook.listeners;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnhancedCraftingMenuListener implements Listener {

    private final EpicCraftingsHookPlugin plugin;
    private final ConfigManager configManager;

    // Track which crafting menu each player has open
    private final Map<UUID, String> playerCraftingMenus = new ConcurrentHashMap<>();

    // Command cooldown tracking
    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();

    // Cache for recipe data to avoid repeated file reads
    private final Map<String, Map<String, Object>> recipeCache = new ConcurrentHashMap<>();

    public EnhancedCraftingMenuListener(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();

        // DEBUG: Log menu opens
        if (configManager.isDebugEnabled()) {
            String cleanTitle = ChatColor.stripColor(title);
            plugin.getLogger().info("DEBUG: Player " + player.getName() + " opened menu: '" + cleanTitle + "'");
        }

        // If this is a crafting menu, detect the recipe after a short delay
        if (isCraftingMenu(title)) {
            // Delay recipe detection to allow inventory to fully load
            new BukkitRunnable() {
                @Override
                public void run() {
                    String detectedRecipe = detectRecipeFromItems(event.getView());
                    if (detectedRecipe != null) {
                        playerCraftingMenus.put(player.getUniqueId(), detectedRecipe);

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("DETECTED: Player " + player.getName() + " is crafting: " + detectedRecipe);
                        }
                    } else {
                        // Fallback to default if no recipe detected
                        playerCraftingMenus.put(player.getUniqueId(), "default_crafting");

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("FALLBACK: Using default_crafting for " + player.getName());
                        }
                    }
                }
            }.runTaskLater(plugin, 3L); // Wait 3 ticks for inventory to load
        }
    }

    private String detectRecipeFromItems(org.bukkit.inventory.InventoryView view) {
        if (view == null || view.getTopInventory() == null) {
            return null;
        }

        try {
            // Get EpicCraftingsPlus craftings folder
            File epicCraftingsFolder = new File(plugin.getDataFolder().getParentFile(), "EpicCraftingsPlus");
            File craftingsFolder = new File(epicCraftingsFolder, "craftings");

            if (!craftingsFolder.exists() || !craftingsFolder.isDirectory()) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("EpicCraftingsPlus craftings folder not found: " + craftingsFolder.getPath());
                }
                return null;
            }

            // Get current items in require item slots
            Map<Integer, String> currentItems = getCurrentRequireItems(view);

            if (currentItems.isEmpty()) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("No require items found in current menu");
                }
                return null;
            }

            // Check all YAML files in craftings folder
            File[] yamlFiles = craftingsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (yamlFiles == null) {
                return null;
            }

            for (File yamlFile : yamlFiles) {
                String recipeName = yamlFile.getName().replace(".yml", "");

                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Checking recipe file: " + recipeName);
                }

                if (matchesRecipeRequirements(yamlFile, currentItems)) {
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("RECIPE MATCH: Current items match " + recipeName);
                    }
                    return recipeName;
                }
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error detecting recipe from files: " + e.getMessage());
            }
        }

        return null;
    }

    private Map<Integer, String> getCurrentRequireItems(org.bukkit.inventory.InventoryView view) {
        Map<Integer, String> items = new HashMap<>();

        // Check require item slots: 10-13, 19-22, 28-31
        int[] requireSlots = {10, 11, 12, 13, 19, 20, 21, 22, 28, 29, 30, 31};

        for (int slot : requireSlots) {
            try {
                ItemStack item = view.getTopInventory().getItem(slot);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String itemName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                    items.put(slot, itemName);

                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("DEBUG: Found require item at slot " + slot + ": '" + itemName + "'");
                    }
                }
            } catch (Exception e) {
                // Skip this slot if there's an error
            }
        }

        return items;
    }

    private boolean matchesRecipeRequirements(File recipeFile, Map<Integer, String> currentItems) {
        try {
            // Load or get cached recipe data
            Map<String, Object> recipeData = getRecipeData(recipeFile);
            if (recipeData == null) {
                return false;
            }

            // Get requires section
            Object requiresObj = recipeData.get("requires");
            if (!(requiresObj instanceof List)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> requires = (List<Map<String, Object>>) requiresObj;

            // Check if any of the current items match the recipe requirements
            for (Map<String, Object> requirement : requires) {
                String reqName = getRequirementDisplayName(requirement);
                if (reqName != null) {
                    // Check if any current item matches this requirement
                    for (String currentItemName : currentItems.values()) {
                        if (itemNamesMatch(reqName, currentItemName)) {
                            if (configManager.isDebugEnabled()) {
                                plugin.getLogger().info("ITEM MATCH: '" + currentItemName + "' matches requirement '" + reqName + "' in " + recipeFile.getName());
                            }
                            return true;
                        }
                    }
                }
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error checking recipe " + recipeFile.getName() + ": " + e.getMessage());
            }
        }

        return false;
    }

    private Map<String, Object> getRecipeData(File recipeFile) {
        String fileName = recipeFile.getName();

        // Check cache first
        if (recipeCache.containsKey(fileName)) {
            return recipeCache.get(fileName);
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(recipeFile);
            Map<String, Object> data = config.getValues(false);

            // Cache the data
            recipeCache.put(fileName, data);

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Loaded recipe data for: " + fileName);
            }

            return data;
        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error loading recipe file " + fileName + ": " + e.getMessage());
            }
            return null;
        }
    }

    private String getRequirementDisplayName(Map<String, Object> requirement) {
        try {
            // Get the name from the requirement
            Object nameObj = requirement.get("name");
            if (nameObj instanceof String) {
                String name = (String) nameObj;
                // Translate color codes and clean
                return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
            }

            // Fallback: try to get from id if name is not available
            Object idObj = requirement.get("id");
            if (idObj instanceof String) {
                String id = (String) idObj;
                // Parse id format: "MATERIAL;amount:32;name:DisplayName"
                if (id.contains("name:")) {
                    String[] parts = id.split("name:");
                    if (parts.length > 1) {
                        String namePart = parts[1].split(";")[0];
                        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', namePart));
                    }
                }
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error getting requirement display name: " + e.getMessage());
            }
        }

        return null;
    }

    private boolean itemNamesMatch(String recipeName, String currentName) {
        if (recipeName == null || currentName == null) {
            return false;
        }

        String cleanRecipeName = recipeName.toLowerCase().trim();
        String cleanCurrentName = currentName.toLowerCase().trim();

        // Exact match
        if (cleanRecipeName.equals(cleanCurrentName)) {
            return true;
        }

        // Contains match (either direction)
        if (cleanRecipeName.contains(cleanCurrentName) || cleanCurrentName.contains(cleanRecipeName)) {
            return true;
        }

        // Remove special characters and check again
        String simpleRecipeName = cleanRecipeName.replaceAll("[\\[\\](){}0-9\\s]", "");
        String simpleCurrentName = cleanCurrentName.replaceAll("[\\[\\](){}0-9\\s]", "");

        if (simpleRecipeName.length() > 2 && simpleCurrentName.length() > 2) {
            if (simpleRecipeName.contains(simpleCurrentName) || simpleCurrentName.contains(simpleRecipeName)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            String recipe = playerCraftingMenus.remove(player.getUniqueId());

            if (recipe != null && configManager.isDebugEnabled()) {
                plugin.getLogger().info("Player " + player.getName() + " closed crafting menu: " + recipe);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if this is a crafting menu
        if (!isCraftingMenu(event.getView().getTitle())) {
            return;
        }

        // Get the current recipe
        String currentRecipe = playerCraftingMenus.get(player.getUniqueId());
        if (currentRecipe == null) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: No recipe tracked for player " + player.getName());
            }
            return;
        }

        int clickedSlot = event.getSlot();

        // DEBUG: Log all clicks when debug is enabled
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: Player " + player.getName() + " clicked slot " + clickedSlot + " in recipe: " + currentRecipe);
        }

        // Check if this slot is configured as a require item slot
        if (!configManager.isRequireItemSlot(clickedSlot)) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: Slot " + clickedSlot + " is not a require item slot");
            }
            return;
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }

        // Get commands for this slot in the current recipe
        List<String> commands = configManager.getCommandsForSlot(currentRecipe, clickedSlot);
        if (commands.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("No commands configured for slot " + clickedSlot + " in recipe " + currentRecipe);
            }
            return;
        }

        // Cancel the click event if configured
        if (configManager.shouldCancelClick()) {
            event.setCancelled(true);
        }

        // Apply cooldown
        if (configManager.isCooldownEnabled()) {
            commandCooldowns.put(player.getUniqueId(),
                    System.currentTimeMillis() + (configManager.getCooldownDuration() * 1000L));
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Player " + player.getName() + " clicked require item at slot " +
                    clickedSlot + " in recipe " + currentRecipe + " (position: " +
                    configManager.getPositionFromSlot(clickedSlot) + ")");
            plugin.getLogger().info("Executing " + commands.size() + " commands");
        }

        // Play sound effect
        playSoundEffect(player);

        // Show particles
        showParticleEffect(player);

        // Execute commands
        executeCommands(player, commands);
    }

    private boolean isCraftingMenu(String title) {
        if (title == null) return false;

        String cleanTitle = ChatColor.stripColor(title);
        String lowerTitle = cleanTitle.toLowerCase();

        for (String pattern : configManager.getTitlePatterns()) {
            String cleanPattern = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', pattern)).toLowerCase();
            if (lowerTitle.contains(cleanPattern)) {
                return true;
            }
        }

        return false;
    }

    private boolean isOnCooldown(Player player) {
        if (!configManager.isCooldownEnabled()) {
            return false;
        }

        Long cooldownEnd = commandCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) {
            return false;
        }

        boolean onCooldown = System.currentTimeMillis() < cooldownEnd;
        if (!onCooldown) {
            commandCooldowns.remove(player.getUniqueId());
        }

        return onCooldown;
    }

    private void playSoundEffect(Player player) {
        if (!configManager.isSoundEnabled()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(configManager.getClickSound());
            player.playSound(player.getLocation(), sound,
                    configManager.getSoundVolume(),
                    configManager.getSoundPitch());
        } catch (IllegalArgumentException e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Invalid sound: " + configManager.getClickSound());
            }
        }
    }

    private void showParticleEffect(Player player) {
        if (!configManager.areParticlesEnabled()) {
            return;
        }

        try {
            Particle particle = Particle.valueOf(configManager.getClickParticle());
            player.spawnParticle(particle, player.getLocation().add(0, 1, 0),
                    configManager.getParticleCount());
        } catch (IllegalArgumentException e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Invalid particle: " + configManager.getClickParticle());
            }
        }
    }

    private void executeCommands(Player player, List<String> commands) {
        // Limit number of commands
        int maxCommands = configManager.getMaxCommandsPerClick();
        if (commands.size() > maxCommands) {
            commands = commands.subList(0, maxCommands);
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Command list truncated to " + maxCommands + " commands");
            }
        }

        if (configManager.isAsyncExecutionEnabled()) {
            executeCommandsAsync(player, commands);
        } else {
            executeCommandsSync(player, commands);
        }
    }

    private void executeCommandsSync(Player player, List<String> commands) {
        int delay = configManager.getCommandDelay();

        for (int i = 0; i < commands.size(); i++) {
            final String command = commands.get(i);

            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        plugin.getCommandExecutor().executeCommand(player, command);
                    } catch (Exception e) {
                        handleCommandError(player, command, e);
                    }
                }
            }.runTaskLater(plugin, delay * i);
        }
    }

    private void executeCommandsAsync(Player player, List<String> commands) {
        new BukkitRunnable() {
            @Override
            public void run() {
                executeCommandsSync(player, commands);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void handleCommandError(Player player, String command, Exception error) {
        if (configManager.shouldLogErrors()) {
            plugin.getLogger().severe("Error executing command for " + player.getName() + ": " + command);
            plugin.getLogger().severe("Error: " + error.getMessage());
        }

        if (configManager.shouldNotifyPlayerOnError()) {
            String message = ChatColor.translateAlternateColorCodes('&',
                    configManager.getFallbackMessage());
            player.sendMessage(message);
        }
    }

    // Getter for testing purposes
    public Map<UUID, String> getPlayerCraftingMenus() {
        return new HashMap<>(playerCraftingMenus);
    }

    // Clean up cooldowns and cache periodically
    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        commandCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    public void clearRecipeCache() {
        recipeCache.clear();
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Recipe cache cleared");
        }
    }
}