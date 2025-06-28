# EpicCraftingsRequireItemHook

A lightweight Minecraft plugin that enhances EpicCraftingsPlus by allowing players to click on require items in crafting recipes to open their respective crafting menus.

## 📋 Requirements

- **Minecraft**: 1.20.1+
- **Server**: Paper/Spigot
- **Java**: 17+
- **Dependencies**:
  - EpicCraftingsPlus (required)
  - MMOItems (optional, for enhanced NBT reading)


## ⚙️ How It Works

### Click Handling
When a player clicks on a require item slot (10-13, 19-22, 28-31):
- Plugin detects the current recipe from the result item
- Looks up configured commands for that recipe and slot position
- Executes the commands to open the appropriate crafting menu

## 🔧 Configuration

### Basic Setup

```yaml
# config.yml
version: 1.1

items-command:
  # MMOItems ID (UPPERCASE)
  BICHNHA:
    1: '[console] ecraft opencraft %player_name% phongan'
    2: '[console] ecraft opencraft %player_name% trai_tim_linh_bien'
  
  BANGTHANKIEM:
    1: '[console] ecraft opencraft %player_name% bang_tinh_loc'
    2: '[console] ecraft opencraft %player_name% bang_than_kiem1'

# Slot mapping (position -> inventory slot)
slot-mapping:
  positions:
    1: 10    # First require item
    2: 11    # Second require item
    3: 12    # Third require item
    4: 13    # Fourth require item
    # ... up to position 12
```
### Command Types

- `[console]` - Execute as console
- `[player]` - Execute as player
- `[message]` - Send message to player
- `[op]` - Execute as player with temporary OP

## Command & Permission

#### Commands
- `/echook reload` - Reload configuration
- `/echook info` - Show plugin information
- `/echook list` - List all configured recipes
- `/echook test <item_id>` - Test configuration for specific item
- `/echook debug` - Show debug information

#### Permissions
- `echook.admin` - Access to all admin commands (default: OP)

## 🐛 Troubleshooting

### Common Issues

**Plugin doesn't work:**
- Ensure EpicCraftingsPlus is installed and loaded first
- Check that your recipes have the correct MMOItems IDs in config
- Enable debug mode to see detailed information

**Commands not executing:**
- Verify the MMOItems ID matches exactly (case-sensitive)
- Check the slot mapping is correct
- Use `/echook test <item_id>` to verify configuration

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ for the Minecraft community*
