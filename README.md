# ChestSecure

ChestSecure is a Paper plugin that lets players lock chests, trapped chests, barrels, hoppers and shulker boxes with a crafted Chest Key. Locked blocks can only be opened by the owner and trusted players, and only a Secure Hopper bound to the container can move items in or out.

## Requirements

* Paper 1.21.11 (api version 1.21)
* Java 21
* No other plugins. Locks, trust lists, player settings and logs are stored in a local SQLite database.

## Installation

* Put the JAR into the `plugins` folder and start the server.
* ChestSecure creates `plugins/ChestSecure` with `config.yml`, `messages.yml` and the database `chestsecure.db`.
* Set the worlds in `config.yml` under `worlds.enabled`. The defaults are `world`, `world_nether` and `world_the_end`. Players can only lock blocks in listed worlds. An empty list (`enabled: []`) enables all worlds.

## Usage

* Right click a chest, trapped chest, barrel, hopper or shulker box while holding a Chest Key. A confirmation menu opens. Sneak while clicking to lock without the menu.
* Right click your own locked block with the Chest Key to open its management menu. A sneak right click removes the lock directly.
* Quick Lock is a setting in `/cs`. When it is on, a right click with an empty hand locks a block at once if a Chest Key is in your inventory. A right click with the Chest Key then also locks without the confirmation menu.
* Quick Remove is a setting in `/cs`. When it is on, a right click with the Chest Key removes a lock at once.
* The management menu shows the position and trusted players. From there you can ping the storage, transfer ownership, remove the lock and add trusted players, either from online players nearby or by typing a name in chat.
* Trust levels: a Viewer can open the container and look at the contents but cannot move items. A Stocker can take and put items. Only the owner and admins can break a locked block.
* A text display with the owner name appears at each locked block.
* Secure Hopper: place it facing a container you locked. It binds to that container. Normal hoppers and hopper minecarts cannot move items into or out of locked containers. Only the owner can open a Secure Hopper, and admins can open it with the Admin Key. The owner and admins can break it. Breaking it drops the Secure Hopper item.
* Undo: removing a lock, removing all your locks and transferring a lock can be undone in the main menu `/cs` for 20 seconds. Up to 5 steps are kept.
* Locked blocks and Secure Hoppers are not destroyed by explosions, do not burn, cannot be moved by pistons and cannot be changed by entities. Players cannot place a chest next to a locked chest of another player to form a double chest.
* When another player tries to break a locked block, the owner gets a chat notification. Offline owners see it at their next join.
* Admins with `chestsecure.admin` can break any locked block and open locked blocks while holding the Admin Key. The admin panel in `/cs` gives the Admin Key, a log book, a server report, a log browser, an inspect mode and a global unlock for one player.

## Recipes

Chest Key:

```
[N] [D] [N]
[D] [H] [D]
[N] [D] [N]
```

N = Netherite Ingot, D = Diamond, H = Tripwire Hook

Secure Hopper:

```
[ ] [I] [ ]
[H] [K] [H]
[ ] [I] [ ]
```

I = Iron Block, H = Hopper, K = Chest Key, empty brackets mean an empty slot

## Commands

* `/chestsecure` or `/cs` opens the main menu. Players only.
* `/cs reload` reloads `config.yml`, `messages.yml` and the world list. Recipes are only registered at server start.

## Permissions

* `chestsecure.use` (default: true) opens the main menu with `/cs`.
* `chestsecure.admin` (default: op) breaks and opens locked blocks, opens the admin panel and runs `/cs reload`.
* `chestsecure.admin.teleport` (default: op) teleports to log positions in the admin log view. Also needs `admin.logs.teleport_enabled: true`.

## Configuration

* `sounds.enabled: true` and `particles.enabled: true` play feedback on lock actions.
* `signs.enabled: true` shows the owner text display at locked blocks.
* `ui.confirm_timeout_seconds: 15` is the time a confirmation stays valid.
* `ui.undo_seconds: 20` and `ui.max_undo_history: 5` control undo.
* `ui.trust_nearby_radius: 32.0` is the radius for the nearby players list.
* `auto_lock.enabled: false` locks blocks when they are placed. `auto_lock.require_key: true` needs a Chest Key in the inventory, `auto_lock.sneak_only: false` limits it to sneaking players.
* `notifications.enabled: true` and `notifications.max_per_player: 20` control break attempt notifications.
* `limits.max_locks_per_player: 1000` and `limits.max_hoppers_per_player: 50`. A value of 0 means no limit.
* `cooldowns.lock_action_ms: 1000` and `cooldowns.break_notification_ms: 30000`.
* `admin.logs.book_entries: 200` and `admin.logs.teleport_enabled: false`.
* `recipes.chest_key` and `recipes.secure_hopper` set `enabled`, `shape` and `ingredients` for both recipes.

## Language

Menus and chat messages are in German. Most chat messages and the item names and lore are in `messages.yml` and can be changed there. Run `/cs reload` after editing. The menu labels are fixed in the code.

## Building

```
mvn clean package
```

This builds the JAR in the target folder. Use JDK 21. With JDK 25 the Mockito tests fail because the Byte Buddy version in Mockito 5.11 does not support Java 25.

## Author

crimethunder
