# ChestSecure

ChestSecure is a Paper plugin that lets players lock chests, trapped chests, barrels, hoppers and shulker boxes with a crafted Chest Key. Locked blocks can only be opened by the owner and trusted players, and only a Secure Hopper bound to the container can move items in or out.

## Requirements

* Paper 26.2 (api version 26.2)
* Java 25
* For Minecraft 1.21 use version 1.6.0.
* No other plugins. Locks, trust lists, player settings and logs are stored in a local SQLite database.

## Installation

* Put the JAR into the `plugins` folder and start the server.
* ChestSecure creates `plugins/ChestSecure` with `config.yml`, the folder `lang` with `en.yml` and `de.yml`, and the database `chestsecure.db`.
* Set the worlds in `config.yml` under `worlds.enabled`. The defaults are `world`, `world_nether` and `world_the_end`. Players can only lock blocks in listed worlds. An empty list (`enabled: []`) enables all worlds.
* When needed, ChestSecure also creates `areas.yml` and `recipes.yml` in the plugin folder. This only happens after a change in the admin panel.
* Apart from creating it on the first start, ChestSecure never writes `config.yml`. Changes from the admin panel are stored in `areas.yml` and `recipes.yml`.

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
* Admins with `chestsecure.admin` can break any locked block and open locked blocks while holding the Admin Key. The admin panel in `/cs` gives the Admin Key, a log book, a server report, a log browser, an inspect mode, a global unlock for one player, the Protection area (area limits for locking, see the section Protection area) and the Recipe editor.

## Protection area

* The protection area limits locking to areas that admins define. It is off by default. When it is off, everything works as before and the areas have no effect.
* When it is on, players can only lock blocks inside an area of the same world. A world without an area allows no new locks. The protection area works in addition to `worlds.enabled`, both must allow the block.
* An area is a rectangle from X and Z in one world. Y does not matter and the borders are inclusive. A world can have several areas, and they may overlap.
* Area names have 1 to 32 characters from `A-Z`, `a-z`, `0-9`, `_` and `-`. Each name can only be used once, case is ignored.
* Admins manage it in the admin panel in `/cs` with the button "Protection area". The switch "Protection area active" turns it on and off.
* "New area" asks in chat for the name and then for the corners as four integers `x1 z1 x2 z2`. The area is created in the world the admin is in when sending the corners. Type `cancel` to abort.
* A left click on an area changes its size with new corners in chat. A right click deletes it.
* Turning it on, changing the size and deleting need a confirmation. The dialog shows how many locks and Secure Hoppers are removed and how many owners are notified. Creating an area and turning it off apply at once and never remove locks.
* Turning it on, making an area smaller or deleting an area permanently deletes the locks that are outside afterwards. Secure Hoppers bound to these locks and Secure Hoppers outside the areas are removed as well, the hopper block stays as a normal hopper. There is no undo.
* The owners get a message with the number of removed locks and Secure Hoppers, also when `notifications.enabled` is `false`. Offline owners see it at their next join.
* Locks and Secure Hoppers in worlds that are not loaded at that moment are not checked and stay. ChestSecure writes a warning to the log.
* Outside the areas, a right click with the Chest Key only shows a message that there is no protection area. Quick Lock and Auto-Lock do nothing there, and placing a Secure Hopper is cancelled.
* A double chest can only be locked when both halves are inside an area.
* The switch and the areas are stored in `areas.yml`. `config.yml` stays unchanged. `/cs reload` does not reload `areas.yml`. Manual changes to the file are read at the next start and do not remove locks.

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

## Recipe editor

* Admins edit both recipes in the admin panel in `/cs` with the button "Recipes". The overview shows the Chest Key and the Secure Hopper with their status and whether the recipe comes from `config.yml` or `recipes.yml`. A click opens the editor.
* The editor shows the recipe as a 3x3 grid. To choose a material, click an item in your own inventory. Only the material counts, the item is not changed.
* A left click on a grid slot inserts the chosen material, a right click clears the slot. A shift left click inserts the Chest Key, only in the recipe of the Secure Hopper.
* The switch "Recipe active" turns the recipe on or off.
* "Save" applies the recipe at once for all players and stores it in `recipes.yml`. It stays after a restart. Closing the editor without saving discards the changes.
* "Reset to default" removes the entry from `recipes.yml` after a confirmation. Then the recipe from `config.yml` applies again.
* An entry in `recipes.yml` takes priority over the recipe in `config.yml`. If the entry is invalid, ChestSecure writes a warning to the log and uses the recipe from `config.yml`.
* Invalid recipes, for example with an unknown material or with rows of different length, are not registered. The plugin still starts and writes a warning to the log.
* Recipes are only read from the files at server start. `/cs reload` does not reload them, so the editor is the way to change recipes while the server is running.
* `recipes.yml` has the same structure as the section `recipes` in `config.yml`, without the key `recipes`. The editor uses the letters `A` to `I` for materials and `K` for the Chest Key, for example:

```
chest_key:
  enabled: true
  shape:
  - ABA
  - BCB
  - ABA
  ingredients:
    A: NETHERITE_INGOT
    B: DIAMOND
    C: TRIPWIRE_HOOK
```

## Commands

* `/chestsecure` or `/cs` opens the main menu. Players only.
* `/cs reload` reloads `config.yml` with the language setting, the language files in the folder `lang` and the world list. Recipes, `recipes.yml` and `areas.yml` are not reloaded.

## Permissions

* `chestsecure.use` (default: true) opens the main menu with `/cs`.
* `chestsecure.admin` (default: op) breaks and opens locked blocks, opens the admin panel and runs `/cs reload`. In the admin panel it also gives access to the protection area and the recipe editor.
* `chestsecure.admin.teleport` (default: op) teleports to log positions in the admin log view. Also needs `admin.logs.teleport_enabled: true`.

## Configuration

* `language: en` sets the language for the whole server. The values are `en` and `de`, the default is `en`.
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
* `recipes.chest_key` and `recipes.secure_hopper` set `enabled`, `shape` and `ingredients` for both recipes. An entry in `recipes.yml` from the recipe editor takes priority.
* An invalid recipe is not registered. The plugin still starts and writes a warning to the log.

## Language

ChestSecure ships with English and German.

* Set `language` in `config.yml` to `en` or `de`. The setting applies to the whole server.
* The texts are in `plugins/ChestSecure/lang/en.yml` and `plugins/ChestSecure/lang/de.yml` and can be changed there.
* Color codes are written with `&`, for example `&a` for green. Placeholders like `{0}` are replaced with values such as player names or numbers.
* Run `/cs reload` after changing the setting or a language file.
* Items that were already created keep the name and lore of the language they were created in.

## Building

```
mvn clean package
```

This builds the JAR in the target folder. Use JDK 25.

## Author

crimethunder
