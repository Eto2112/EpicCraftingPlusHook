package com.eto2112.epiccraftingshook.listeners;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnhancedCraftingMenuListener implements Listener {

    private final EpicCraftingsHookPlugin plugin;
    private final ConfigManager configManager;

    // Track which recipe each player has open (using TYPE.MMOITEMID format)
    private final Map<UUID, String> playerCurrentRecipes = new ConcurrentHashMap<>();

    // Command cooldown tracking
    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();

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

        // Check if this is a crafting menu (contains "chế tạo")
        if (isCraftingMenu(title)) {
            // Delay check to allow inventory to fully load
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean hasRecipeIndicator = checkRecipeIndicator(event.getView());
                    if (hasRecipeIndicator) {
                        // Mark this player as having a valid crafting menu open
                        playerCurrentRecipes.put(player.getUniqueId(), "CRAFTING_ACTIVE");

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("CRAFTING ENABLED: Player " + player.getName() + " has valid crafting menu (slot 34 indicator found)");
                        }
                    } else {
                        // Use default_crafting as fallback
                        playerCurrentRecipes.put(player.getUniqueId(), "default_crafting");

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("FALLBACK: Using default_crafting for " + player.getName() + " (no slot 34 indicator)");
                        }
                    }
                }
            }.runTaskLater(plugin, 3L); // Wait 3 ticks for inventory to load
        }
    }

    private boolean checkRecipeIndicator(org.bukkit.inventory.InventoryView view) {
        if (view == null || view.getTopInventory() == null) {
            return false;
        }

        try {
            // Check if there's an item with custom model data 10004 at slot 34 (recipe indicator)
            ItemStack indicatorItem = view.getTopInventory().getItem(34);

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("=== SLOT 34 (Recipe Indicator) CHECK ===");
                if (indicatorItem == null) {
                    plugin.getLogger().info("Slot 34: NULL ITEM");
                } else {
                    plugin.getLogger().info("Slot 34 Material: " + indicatorItem.getType());
                    plugin.getLogger().info("Slot 34 Amount: " + indicatorItem.getAmount());
                    if (indicatorItem.hasItemMeta()) {
                        ItemMeta indicatorMeta = indicatorItem.getItemMeta();
                        plugin.getLogger().info("Slot 34 Has CustomModelData: " + indicatorMeta.hasCustomModelData());
                        if (indicatorMeta.hasCustomModelData()) {
                            plugin.getLogger().info("Slot 34 CustomModelData: " + indicatorMeta.getCustomModelData());
                        }
                        if (indicatorMeta.hasDisplayName()) {
                            plugin.getLogger().info("Slot 34 Display Name: '" + indicatorMeta.getDisplayName() + "'");
                        }
                    } else {
                        plugin.getLogger().info("Slot 34: No ItemMeta");
                    }
                }
            }

            return isValidRecipeIndicator(indicatorItem);

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error checking recipe indicator: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean isValidRecipeIndicator(ItemStack item) {
        if (item == null) {
            return false;
        }

        try {
            if (!item.hasItemMeta()) {
                return false;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            return meta.hasCustomModelData() && meta.getCustomModelData() == 10004;
        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error checking recipe indicator: " + e.getMessage());
            }
            return false;
        }
    }

    private boolean isCraftingMenu(String title) {
        if (title == null) return false;

        String cleanTitle = ChatColor.stripColor(title);
        String lowerTitle = cleanTitle.toLowerCase();

        // Check for "chế tạo" specifically
        return lowerTitle.contains("chế tạo");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            String status = playerCurrentRecipes.remove(player.getUniqueId());

            if (status != null && configManager.isDebugEnabled()) {
                plugin.getLogger().info("Player " + player.getName() + " closed crafting menu (was: " + status + ")");
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

        // IMPORTANT: Check if this menu actually has the slot 34 indicator
        // This prevents fake chests with "chế tạo" name from working
        if (!checkRecipeIndicator(event.getView())) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: Menu has 'chế tạo' in title but no slot 34 indicator - ignoring click");
            }
            return;
        }

        // Check if we have a valid crafting session (should be CRAFTING_ACTIVE if indicator exists)
        String currentStatus = playerCurrentRecipes.get(player.getUniqueId());
        if (currentStatus == null || !currentStatus.equals("CRAFTING_ACTIVE")) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: No valid crafting session for player " + player.getName() + " (status: " + currentStatus + ")");
            }
            return;
        }

        int clickedSlot = event.getSlot();

        // DEBUG: Log all clicks when debug is enabled
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("DEBUG: Player " + player.getName() + " clicked slot " + clickedSlot + " in VALID crafting menu");
        }

        // Check if this slot is configured as a require item slot
        if (!configManager.isRequireItemSlot(clickedSlot)) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: Slot " + clickedSlot + " is not a require item slot");
            }
            return;
        }

        // Detect the recipe from slot 25 when they click
        String recipeKey = detectRecipeFromSlot25(event.getView());
        if (recipeKey == null) {
            recipeKey = "default_crafting";
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Could not detect recipe from slot 25, using default_crafting");
            }
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("DEBUG: Player " + player.getName() + " is on cooldown");
            }
            return;
        }

        // Get commands for this slot in the detected recipe
        List<String> commands = configManager.getCommandsForSlot(recipeKey, clickedSlot);
        if (commands.isEmpty()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("No commands configured for slot " + clickedSlot + " in recipe " + recipeKey);
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
                    clickedSlot + " in recipe " + recipeKey + " (position: " +
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

    private String detectRecipeFromSlot25(org.bukkit.inventory.InventoryView view) {
        if (view == null || view.getTopInventory() == null) {
            return null;
        }

        try {
            // Get the result item from slot 25
            ItemStack resultItem = view.getTopInventory().getItem(25);

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("=== SLOT 25 (Result Item) DEBUG ===");
                if (resultItem == null) {
                    plugin.getLogger().info("Slot 25: NULL ITEM");
                } else {
                    plugin.getLogger().info("Slot 25 Material: " + resultItem.getType());
                    plugin.getLogger().info("Slot 25 Amount: " + resultItem.getAmount());
                    if (resultItem.hasItemMeta()) {
                        plugin.getLogger().info("Slot 25: Has ItemMeta");
                        ItemMeta resultMeta = resultItem.getItemMeta();
                        if (resultMeta.hasDisplayName()) {
                            plugin.getLogger().info("Slot 25 Display Name: '" + ChatColor.stripColor(resultMeta.getDisplayName()) + "'");
                        }
                        if (resultMeta.hasCustomModelData()) {
                            plugin.getLogger().info("Slot 25 CustomModelData: " + resultMeta.getCustomModelData());
                        }
                    } else {
                        plugin.getLogger().info("Slot 25: No ItemMeta");
                    }
                }
            }

            if (resultItem == null) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("No result item found at slot 25");
                }
                return null;
            }

            // Try to get MMOItems data using MMOItems API
            String itemType = null;
            String itemId = null;

            try {
                // Method 1: Try using MMOItems API correctly according to documentation
                NBTItem nbtItem = NBTItem.get(resultItem);

                // Check if this is an MMOItems item
                if (nbtItem.hasType()) {
                    // Get the item type using the official API
                    itemType = nbtItem.getType();

                    // Get the item ID using the official method
                    itemId = nbtItem.getString("MMOITEMS_ITEM_ID");

                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("=== MMOItems API SUCCESS ===");
                        plugin.getLogger().info("MMOItems TYPE: '" + (itemType != null ? itemType : "NULL") + "'");
                        plugin.getLogger().info("MMOItems ID: '" + (itemId != null ? itemId : "NULL") + "'");
                    }
                } else {
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("nbtItem.hasType() returned false - not an MMOItems item");
                    }
                }

            } catch (Exception mmoError) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("MMOItems API access failed: " + mmoError.getMessage());
                    plugin.getLogger().info("Trying fallback methods...");
                }

                // Fallback Method 1: Try reading NBT via NMS
                try {
                    Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".inventory.CraftItemStack");
                    Object nmsItemStack = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, resultItem);
                    Object nbtTag = nmsItemStack.getClass().getMethod("getTag").invoke(nmsItemStack);

                    if (nbtTag != null) {
                        Boolean hasType = (Boolean) nbtTag.getClass().getMethod("hasKey", String.class).invoke(nbtTag, "MMOITEMS_ITEM_TYPE");
                        if (hasType) {
                            itemType = (String) nbtTag.getClass().getMethod("getString", String.class).invoke(nbtTag, "MMOITEMS_ITEM_TYPE");
                        }

                        Boolean hasId = (Boolean) nbtTag.getClass().getMethod("hasKey", String.class).invoke(nbtTag, "MMOITEMS_ITEM_ID");
                        if (hasId) {
                            itemId = (String) nbtTag.getClass().getMethod("getString", String.class).invoke(nbtTag, "MMOITEMS_ITEM_ID");
                        }

                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info("=== NBT FALLBACK SUCCESS ===");
                            plugin.getLogger().info("MMOItems TYPE: " + (itemType != null ? "'" + itemType + "'" : "NULL"));
                            plugin.getLogger().info("MMOItems ID: " + (itemId != null ? "'" + itemId + "'" : "NULL"));
                        }
                    }

                } catch (Exception nbtError) {
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().warning("NBT fallback also failed: " + nbtError.getMessage());
                    }

                    // Fallback Method 2: Try PDC
                    try {
                        if (resultItem.hasItemMeta()) {
                            ItemMeta meta = resultItem.getItemMeta();
                            PersistentDataContainer pdc = meta.getPersistentDataContainer();

                            NamespacedKey[] possibleKeys = {
                                    new NamespacedKey("mmoitems", "mmoitems_item_type"),
                                    new NamespacedKey("mmoitems", "mmoitems_item_id"),
                                    NamespacedKey.fromString("mmoitems:mmoitems_item_type"),
                                    NamespacedKey.fromString("mmoitems:mmoitems_item_id")
                            };

                            for (NamespacedKey key : possibleKeys) {
                                if (key != null && pdc.has(key, PersistentDataType.STRING)) {
                                    String value = pdc.get(key, PersistentDataType.STRING);
                                    if (key.getKey().contains("type")) {
                                        itemType = value;
                                    } else if (key.getKey().contains("id")) {
                                        itemId = value;
                                    }

                                    if (configManager.isDebugEnabled()) {
                                        plugin.getLogger().info("Found PDC key " + key + ": " + value);
                                    }
                                }
                            }
                        }
                    } catch (Exception pdcError) {
                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().warning("PDC fallback also failed: " + pdcError.getMessage());
                        }
                    }
                }
            }

            if (itemId != null && !itemId.trim().isEmpty()) {
                String recipeKey = itemId.trim().toUpperCase();

                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("RECIPE DETECTED: " + recipeKey + " (MMOItems Type: " + itemType + ", ID: " + itemId + ")");
                }

                return recipeKey;
            } else {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("No MMOItems ID found in result item at slot 25");
                    plugin.getLogger().info("This might not be an MMOItems item, or the API is not working correctly");

                    // Debug: Check if MMOItems plugin is properly loaded
                    if (plugin.getServer().getPluginManager().getPlugin("MMOItems") != null) {
                        plugin.getLogger().info("MMOItems plugin is loaded: " + plugin.getServer().getPluginManager().getPlugin("MMOItems").getDescription().getVersion());
                    } else {
                        plugin.getLogger().warning("MMOItems plugin is not loaded!");
                    }
                }
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error detecting recipe from slot 25: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return null;
    }

    private String getServerVersion() {
        String packageName = plugin.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
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
    public Map<UUID, String> getPlayerCurrentRecipes() {
        return new HashMap<>(playerCurrentRecipes);
    }

    // Clean up cooldowns periodically
    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        commandCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    // Method called when config is reloaded
    public void reloadRecipeData() {
        // Clear any cached data if needed
        cleanupCooldowns();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Recipe data reloaded for EnhancedCraftingMenuListener");
        }
    }
}