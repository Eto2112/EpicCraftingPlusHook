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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EnhancedCraftingMenuListener implements Listener {

    private final EpicCraftingsHookPlugin plugin;
    private final ConfigManager configManager;

    // Simple cooldown tracking
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public EnhancedCraftingMenuListener(EpicCraftingsHookPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // Quick checks first
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isRequireItemSlot(event.getSlot())) return;
        if (!isCraftingMenu(event.getView().getTitle())) return;

        Player player = (Player) event.getWhoClicked();

        // Check cooldown
        if (isOnCooldown(player)) return;

        // Verify this is a valid EpicCraftingsPlus menu
        if (!hasValidIndicator(event.getView())) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("Player " + player.getName() + " clicked fake crafting menu - ignoring");
            }
            return;
        }

        // Get recipe from result item
        String recipeId = getRecipeId(event.getView());
        if (recipeId == null) {
            recipeId = "default_crafting";
        }

        // Get commands for this slot
        List<String> commands = configManager.getCommandsForSlot(recipeId, event.getSlot());
        if (commands.isEmpty()) return;

        // Cancel click and execute commands
        event.setCancelled(true);
        setCooldown(player);
        executeCommands(player, commands);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("Player " + player.getName() + " clicked slot " + event.getSlot() +
                    " in recipe " + recipeId + " - executing " + commands.size() + " commands");
        }
    }

    // Quick check if this is a crafting menu
    private boolean isCraftingMenu(String title) {
        return title != null && ChatColor.stripColor(title).toLowerCase().contains("chế tạo");
    }

    // Quick check if this is a require item slot
    private boolean isRequireItemSlot(int slot) {
        return configManager.isRequireItemSlot(slot);
    }

    // Check if menu has valid indicator at slot 34
    private boolean hasValidIndicator(org.bukkit.inventory.InventoryView view) {
        try {
            ItemStack item = view.getTopInventory().getItem(34);
            if (item == null) return false;

            // Safe ItemMeta access
            ItemMeta meta;
            try {
                if (!item.hasItemMeta()) return false;
                meta = item.getItemMeta();
                if (meta == null) return false;
            } catch (Exception e) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("Error accessing ItemMeta for indicator: " + e.getMessage());
                }
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

    // Get recipe ID from result item at slot 25
    private String getRecipeId(org.bukkit.inventory.InventoryView view) {
        try {
            ItemStack resultItem = view.getTopInventory().getItem(25);
            if (resultItem == null) return null;

            // Safe NBT access with multiple try-catch blocks
            try {
                NBTItem nbtItem = NBTItem.get(resultItem);
                if (nbtItem != null && nbtItem.hasType()) {
                    String itemId = nbtItem.getString("MMOITEMS_ITEM_ID");
                    return itemId != null && !itemId.trim().isEmpty() ? itemId.trim().toUpperCase() : null;
                }
            } catch (Exception nbtError) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().warning("NBT access failed for result item: " + nbtError.getMessage());
                }

                // Fallback: Try basic ItemMeta check to see if this looks like an MMOItems item
                try {
                    if (resultItem.hasItemMeta()) {
                        ItemMeta meta = resultItem.getItemMeta();
                        if (meta != null && meta.hasDisplayName()) {
                            String displayName = meta.getDisplayName();
                            // If it has a colored name, it's likely an MMOItems item but we can't read the ID
                            if (displayName.contains("§") && configManager.isDebugEnabled()) {
                                plugin.getLogger().info("Item appears to be MMOItems but NBT is corrupted");
                            }
                        }
                    }
                } catch (Exception metaError) {
                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().warning("ItemMeta access also failed: " + metaError.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().warning("Error getting recipe ID: " + e.getMessage());
            }
        }
        return null;
    }

    // Simple cooldown check
    private boolean isOnCooldown(Player player) {
        if (!configManager.isCooldownEnabled()) return false;

        Long cooldownEnd = cooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return false;

        if (System.currentTimeMillis() >= cooldownEnd) {
            cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    // Set cooldown for player
    private void setCooldown(Player player) {
        if (configManager.isCooldownEnabled()) {
            cooldowns.put(player.getUniqueId(),
                    System.currentTimeMillis() + (configManager.getCooldownDuration() * 1000L));
        }
    }

    // Execute commands for player
    private void executeCommands(Player player, List<String> commands) {
        for (String command : commands) {
            plugin.getCommandExecutor().executeCommand(player, command);
        }
    }

    // Cleanup expired cooldowns
    public void cleanupCooldowns() {
        long currentTime = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}