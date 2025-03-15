# BattleLock Plugin
**BattleLock** is a Minecraft Paper 1.21.4 plugin that prevents combat logging and command use during PvP. When players engage in PvP combat, they are "tagged" and cannot use most commands until the tag expires. If a player logs out during combat, an NPC is created in their place that can be killed by other players - potentially causing the combat logger to lose all their items when they return.

## Features
- Prevents combat logging by creating killable NPCs when players log out during PvP
- Automatically tags players when they engage in PvP combat
- Blocks command usage during combat (except for configurable whitelist)
- Combat tags automatically expire after a configurable duration
- NPCs hold the player's inventory and drop items if killed
- Clear player messaging with combat timers
- Works with direct attacks and projectiles (arrows, etc.)
- Combat loggers will lose their items no matter when they return - even after server restarts

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/battlelock/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Configuration
In `config.yml`, you can configure:
```yaml
# Combat tag duration in seconds
# How long players remain in combat after receiving/dealing damage
combat-tag-duration: 15

# Combat log NPC despawn time in seconds
# How long a combat log NPC remains before disappearing
combat-log-despawn-time: 30

# List of commands allowed during combat
# Players can use these commands even while in combat
allowed-commands:
  - "tell"
  - "msg"
  - "r"
  - "w"
  - "me"
```

## How It Works
1. When a player engages in PvP combat (attacking or being attacked by another player), both players are "tagged"
2. Tagged players cannot use most commands (except those in the allowed list)
3. If a tagged player logs out, a villager NPC is spawned in their place with:
   - The player's name
   - The player's health
   - The player's inventory (stored internally)
4. Other players can kill this NPC, causing it to drop the combat logger's items
5. If the NPC survives until the player returns, they get their inventory back
6. If the NPC is killed, the player will lose all their items when they return

## Important Notes
- Only player-vs-player combat triggers tagging (mobs attacking players doesn't count)
- If a player dies during combat, they are automatically untagged
- The NPC will automatically despawn after the configured time if no one kills it
- Combat loggers are notified what happened to their NPC when they return
- Punishment records never expire - even if a player waits months to return or the server restarts multiple times

## Support Me
Donations will help me with the development of this project.

One-off donation: https://ko-fi.com/lolwhatyesme

Patreon: https://www.patreon.com/lolwhatyesme 