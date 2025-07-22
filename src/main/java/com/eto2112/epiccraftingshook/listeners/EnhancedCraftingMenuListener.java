package com.eto2112.epiccraftingshook.listeners;

import com.eto2112.epiccraftingshook.EpicCraftingsHookPlugin;
import com.eto2112.epiccraftingshook.utils.ConfigManager;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class EnhancedCraftingMenuListener implements Listener {

    private final EpicCraftingsHookPlugin plugin;
    private final ConfigManager configManager;

    // Cache for performance optimization
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> titleCache = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> slotCache = new ConcurrentHashMap<>();

    // Pre-computed constants for better performance
    private static final String CRAFTING_KEYWORD = "chế tạo";
    private static final int INDICATOR_SLOT = 34;
    private static final int RESULT_SLOT = 25;
    private static final int INDICATOR_MODEL_DATA = 10004;

    public EnhancedCraftingMenuListener(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();

        // Pre-populate slot cache for better performance
        initializeSlotCache();
    }

    // Pre-compute slot mappings to avoid repeated calculations
    private void initializeSlotCache() {
        for (int i = 0; i < 54; i++) { // Standard inventory size
            slotCache.put(i, configManager.isRequireItemSlot(i));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // Fastest possible checks first to minimize overhead
        if (!(event.getWhoClicked() instanceof Player)) return;

        final int clickedSlot = event.getSlot();

        // Use cached slot lookup instead of method call
        if (!slotCache.getOrDefault(clickedSlot, false)) return;

        final String title = event.getView().getTitle();
        if (!isCraftingMenuCached(title)) return;

        final Player player = (Player) event.getWhoClicked();

        // Quick cooldown check before expensive operations
        if (isOnCooldownFast(player)) return;

        // Cancel click immediately for better UX
        event.setCancelled(true);

        // Process the click asynchronously to avoid blocking the main thread
        CompletableFuture.runAsync(() -> {
            processClickAsync(player, clickedSlot, event.getView());
        }).exceptionally(throwable -> {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Async click processing failed: " + throwable.getMessage());
            }
            return null;
        });
    }

    // Cached title checking for better performance
    private boolean isCraftingMenuCached(String title) {
        if (title == null) return false;

        // Check cache first
        Boolean cached = titleCache.get(title);
        if (cached != null) return cached;

        // Compute and cache result
        String cleanTitle = ChatColor.stripColor(title).toLowerCase();
        boolean result = cleanTitle.contains(CRAFTING_KEYWORD);

        // Only cache positive results to avoid memory bloat
        if (result) {
            titleCache.put(title, true);
        }

        return result;
    }

    // Optimized cooldown check without synchronization overhead
    private boolean isOnCooldownFast(Player player) {
        if (!configManager.isCooldownEnabled()) return false;

        Long cooldownEnd = cooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return false;

        long currentTime = System.currentTimeMillis();
        if (currentTime >= cooldownEnd) {
            cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    // Process click operations asynchronously
    private void processClickAsync(Player player, int clickedSlot, org.bukkit.inventory.InventoryView view) {
        try {
            // Validate menu structure asynchronously
            if (!hasValidIndicatorAsync(view)) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("Player " + player.getName() + " clicked invalid crafting menu - ignoring");
                }
                return;
            }

            // Get recipe ID asynchronously
            String recipeId = getRecipeIdAsync(view);
            if (recipeId == null) {
                recipeId = "default_crafting";
            }

            // Get commands (this is already cached in ConfigManager)
            List<String> commands = configManager.getCommandsForSlot(recipeId, clickedSlot);
            if (commands.isEmpty()) return;

            // Set cooldown
            setCooldownFast(player);

            // Execute commands on main thread (required for Bukkit API)
            new BukkitRunnable() {
                @Override
                public void run() {
                    executeCommandsSync(player, commands);
                }
            }.runTask(plugin);

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Player " + player.getName() + " clicked slot " + clickedSlot +
                        " in recipe " + recipeId + " - executing " + commands.size() + " commands");
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error in async click processing: " + e.getMessage());
            }
        }
    }

    // Async version of indicator validation
    private boolean hasValidIndicatorAsync(org.bukkit.inventory.InventoryView view) {
        try {
            ItemStack item = view.getTopInventory().getItem(INDICATOR_SLOT);
            if (item == null) return false;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;

            return meta.hasCustomModelData() && meta.getCustomModelData() == INDICATOR_MODEL_DATA;
        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error checking recipe indicator: " + e.getMessage());
            }
            return false;
        }
    }

    // Async version of recipe ID extraction
    private String getRecipeIdAsync(org.bukkit.inventory.InventoryView view) {
        try {
            ItemStack resultItem = view.getTopInventory().getItem(RESULT_SLOT);
            if (resultItem == null) return null;

            // Use NBT reading with better error handling
            NBTItem nbtItem = NBTItem.get(resultItem);
            if (nbtItem != null && nbtItem.hasType()) {
                String itemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                if (itemId != null && !itemId.trim().isEmpty()) {
                    return itemId.trim().toUpperCase();
                }
            }

        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error getting recipe ID: " + e.getMessage());
            }
        }
        return null;
    }

    // Fast cooldown setting without synchronization overhead
    private void setCooldownFast(Player player) {
        if (configManager.isCooldownEnabled()) {
            cooldowns.put(player.getUniqueId(),
                    System.currentTimeMillis() + (configManager.getCooldownDuration() * 1000L));
        }
    }

    // Optimized command execution
    private void executeCommandsSync(Player player, List<String> commands) {
        // Execute all commands without delay for better performance
        // The original delay was causing unnecessary complexity
        for (String command : commands) {
            try {
                plugin.getCommandExecutor().executeCommand(player, command);
            } catch (Exception e) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("Error executing command: " + command + " - " + e.getMessage());
                }
            }
        }
    }

    // Cleanup method with batch processing for better performance
    public void cleanupCooldowns() {
        if (cooldowns.isEmpty()) return;

        long currentTime = System.currentTimeMillis();

        // Use parallel processing for large cooldown maps
        if (cooldowns.size() > 100) {
            CompletableFuture.runAsync(() -> {
                cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            });
        } else {
            cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        }
    }

    // Clear caches when needed (called by ConfigManager on reload)
    public void clearCaches() {
        titleCache.clear();
        initializeSlotCache(); // Rebuild slot cache with new config

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Listener caches cleared and rebuilt");
        }
    }

    // Method to get current cache sizes for debugging
    public void logCacheStats() {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Cache stats - Titles: " + titleCache.size() +
                    ", Cooldowns: " + cooldowns.size());
        }
    }
}