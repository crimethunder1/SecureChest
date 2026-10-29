# ChestSecure

ChestSecure is a Paper plugin that lets players lock chests, trapped chests, copper chests in all oxidation states (waxed or not), barrels, hoppers, shulker boxes, furnaces, blast furnaces, smokers, droppers, dispensers, brewing stands and crafters with a crafted Chest Key. Locked blocks can only be opened by the owner and trusted players, and only a Secure Hopper, a hopper the owner bound to the container with the Chest Key, can move items in or out.

## Requirements

* Paper 26.2 (api version 26.2)
* Java 25
* Built and tested for Paper 26.2. Other server forks may work but are not supported.
* For Minecraft 1.21 use version 1.6.0.
* The Secure Hopper item and recipe were removed in 1.7.0. Existing Secure Hoppers keep working, crafted Secure Hopper items can still be placed. To make a new one, right click a hopper with your Chest Key that faces a container you locked.
* No other plugins. Locks, trust lists, player settings and logs are stored in a local SQLite database.

## Installation

* Put the JAR into the `plugins` folder and start the server.
* ChestSecure creates `plugins/ChestSecure` with `config.yml`, the folder `lang` with `en.yml` and `de.yml`, and the database `chestsecure.db`.
* Set the worlds in `config.yml` under `worlds.enabled`. The defaults are `world`, `world_nether` and `world_the_end`. Players can only lock blocks in listed worlds. An empty list (`enabled: []`) enables all worlds.
* When needed, ChestSecure also creates `areas.yml`, `recipes.yml` and `settings.yml` in the plugin folder. This only happens after a change in the admin panel.
* Apart from creating it on the first start, ChestSecure never writes `config.yml`. Changes from the admin panel are stored in `areas.yml`, `recipes.yml` and `settings.yml`.

## Usage

* Right click a chest, trapped chest, copper chest in any oxidation state (waxed or not), barrel, hopper, shulker box, furnace, blast furnace, smoker, dropper, dispenser, brewing stand or crafter while holding a Chest Key. A confirmation menu opens. Sneak while clicking to lock without the menu.
* Right click your own locked block with the Chest Key to open its management menu. A sneak right click removes the lock directly.
* Quick Lock is a setting in `/cs`. When it is on, a right click with an empty hand locks a block at once if a Chest Key is in your inventory. A right click with the Chest Key then also locks without the confirmation menu.
* Quick Remove is a setting in `/cs`. When it is on, a right click with the Chest Key removes a lock at once.
* Chest Key consumption: admins can turn on the switch "Consume Chest Keys" in the admin panel. Then every new lock costs one Chest Key, also with Quick Lock, Auto-Lock and when a hopper becomes a Secure Hopper. The key is taken from the stack in your hand, otherwise from the first Chest Key in your inventory, and only when the lock succeeds. If the Chest Key is no longer in your inventory when you confirm the menu, nothing is locked and a message tells you why. Removing, transferring and managing a lock never cost a key, and removing a lock gives no key back. Nothing is consumed in creative mode. The switch is off by default.
* The management menu shows the position and trusted players. From there you can ping the storage, transfer ownership, remove the lock and add trusted players, either from online players nearby or by typing a name in chat.
* Ping: the action bar shows a direction arrow relative to your view and the distance in blocks. If the storage is at least 2 blocks higher or lower, a height hint is added. While the ping runs, the locked container glows for you only. The ping ends a short while after you arrive, after a time limit, or when you click the button again, which then reads "Stop ping".
* Trust levels: a Viewer can open the container and look at the contents but cannot move items. A Stocker can take and put items. Only the owner and admins can break a locked block.
* A text display with the owner name appears at each locked block. Connected locked blocks of one owner on the same level share one name, shown in the middle above the group. Admins can turn the owner name display on and off for all locks in the admin panel.
* An owner name is hidden while a solid block, for example stone, a chest or a slab, occupies a space the text touches, and it comes back when the block is gone. Above a double chest this is the space above either half. Air, water, grass, torches and other blocks you can walk through do not hide it. Admins can turn this off in the admin panel with the switch "Hide covered names".
* Every player can turn the name above their own locks off and on again with the switch "Name above my locks" in the settings in `/cs`. This also applies while the player is offline. When admins turn the owner name display off, no names are shown at all.
* Secure Hopper: right click a hopper that faces a container you locked with your Chest Key to turn it into a Secure Hopper bound to that container. The confirmation menu, sneaking and Quick Lock work as for locking. Sneak and right click your Secure Hopper with your Chest Key to turn it back into a normal hopper. Normal hoppers and hopper minecarts cannot move items into or out of locked containers. Only the owner can open a Secure Hopper, and admins can open it with the Admin Key. The owner and admins can break it. Breaking it drops a normal hopper. Secure Hopper items that were crafted earlier can still be placed facing a container you locked.
* Undo: removing a lock, removing all your locks and transferring a lock can be undone in the main menu `/cs` for 20 seconds. Up to 5 steps are kept.
* Locked blocks and Secure Hoppers are not destroyed by explosions, do not burn, cannot be moved by pistons and cannot be changed by entities. Players cannot place a chest next to a locked chest of another player to form a double chest.
* When another player tries to break a locked block, the owner gets a chat notification. Offline owners see it at their next join.
* Admins with `chestsecure.admin` can break any locked block and open locked blocks while holding the Admin Key. The Chest Key only works for the owner of a lock, also for admins, who use the Admin Key and the admin panel instead. Admins hold the Admin Key and sneak and right click a locked container to open an admin menu where they can remove a lock, transfer it or change its trusted players, the owner is notified when a lock is removed or transferred. The admin panel in `/cs` gives the Admin Key, a log book, a server report, a log browser, an inspect mode, a global unlock for one player, the Protection area (area limits for locking, see the section Protection area), the Recipe editor, a switch for the owner name display and a button that gives you Chest Keys, the amount is typed in chat, 1 to 64.

## Limitations

* Locked machines keep running. Redstone can still make a locked dropper, dispenser or crafter hand out its items, so these blocks are protected against players but not against redstone.
* Locked droppers and crafters do not pass items into containers, only Secure Hoppers can move items out of locked blocks.
* Copper golems cannot take items from or put items into locked containers.

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

One craft gives one Chest Key. Admins can set 1 to 64 in the recipe editor or with `recipes.chest_key.amount` in `config.yml`.

## Recipe editor

* Admins edit the recipe of the Chest Key in the admin panel in `/cs` with the button "Recipes". The overview shows the Chest Key with its status and whether the recipe comes from `config.yml` or `recipes.yml`. A click opens the editor.
* The editor shows the recipe as a 3x3 grid. To choose a material, click an item in your own inventory. Only the material counts, the item is not changed.
* A left click on a grid slot inserts the chosen material, a right click clears the slot.
* The switch "Recipe active" turns the recipe on or off.
* The button "Amount" below the result sets how many Chest Keys one craft gives. A left click adds 1, a right click removes 1, with shift it is 8. The amount stays between 1 and 64, and the result slot shows it.
* "Save" applies the recipe at once for all players and stores it in `recipes.yml`. It stays after a restart. Closing the editor without saving discards the changes.
* "Reset to default" removes the entry from `recipes.yml` after a confirmation. Then the recipe from `config.yml` applies again.
* An entry in `recipes.yml` takes priority over the recipe in `config.yml`. If the entry is invalid, ChestSecure writes a warning to the log and uses the recipe from `config.yml`.
* Invalid recipes, for example with an unknown material or with rows of different length, are not registered. The plugin still starts and writes a warning to the log.
* Recipes are only read from the files at server start. `/cs reload` does not reload them, so the editor is the way to change recipes while the server is running.
* `recipes.yml` has the same structure as the section `recipes` in `config.yml`, without the key `recipes`. The editor uses the letters `A` to `I` for materials, for example:

```
chest_key:
  enabled: true
  amount: 1
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
* `/cs reload` reloads `config.yml` with the language setting, the language files in the folder `lang` and the world list. Recipes, `recipes.yml`, `areas.yml` and `settings.yml` are not reloaded.

## Permissions

* `chestsecure.use` (default: true) opens the main menu with `/cs`.
* `chestsecure.admin` (default: op) breaks and opens locked blocks, opens the admin panel and runs `/cs reload`. In the admin panel it also gives access to the protection area and the recipe editor.
* `chestsecure.admin.teleport` (default: op) teleports to log positions in the admin log view. Also needs `admin.logs.teleport_enabled: true`.

## Configuration

* `language: en` sets the language for the whole server. The values are `en` and `de`, the default is `en`.
* `sounds.enabled: true` and `particles.enabled: true` play feedback on lock actions.
* `signs.enabled: true` shows the owner text display at locked blocks. Admins can turn the owner name display on and off for all locks in the admin panel. The setting is stored in `settings.yml` and takes priority over `signs.enabled` in `config.yml`. `/cs reload` does not reload `settings.yml`.
* `signs.hide_when_covered: true` hides an owner name while a solid block occupies a space the text touches. Admins can turn it on and off in the admin panel. The setting is stored in `settings.yml` and takes priority over `signs.hide_when_covered` in `config.yml`.
* `ui.confirm_timeout_seconds: 15` is the time a confirmation stays valid.
* `ui.undo_seconds: 20` and `ui.max_undo_history: 5` control undo.
* `ui.trust_nearby_radius: 32.0` is the radius for the nearby players list.
* `auto_lock.enabled: false` locks blocks when they are placed. `auto_lock.require_key: true` needs a Chest Key in the inventory, `auto_lock.sneak_only: false` limits it to sneaking players.
* `locking.consume_chest_key: false` makes every new lock cost one Chest Key when set to `true`. Auto-Lock only costs a key with `auto_lock.require_key: true`. Admins can turn it on and off in the admin panel. The setting is stored in `settings.yml` and takes priority over `locking.consume_chest_key` in `config.yml`.
* `notifications.enabled: true` and `notifications.max_per_player: 20` control break attempt notifications.
* `ping.timeout_seconds: 300` ends a ping after this time, 0 means no limit. `ping.arrived_seconds: 15` keeps the ping running for this time after you arrive, 0 ends it at once. `ping.highlight_enabled: true` lets the pinged container glow for the pinging player, `false` shows only the action bar.
* `limits.max_locks_per_player: 1000` and `limits.max_hoppers_per_player: 50`. A value of 0 means no limit.
* `cooldowns.lock_action_ms: 1000` and `cooldowns.break_notification_ms: 30000`.
* `admin.logs.book_entries: 200` and `admin.logs.teleport_enabled: false`.
* `recipes.chest_key` sets `enabled`, `shape` and `ingredients` for the recipe of the Chest Key. An entry in `recipes.yml` from the recipe editor takes priority.
* `recipes.chest_key.amount: 1` is the number of Chest Keys one craft gives, from 1 to 64. Without this entry the amount is 1. Another value makes the recipe invalid.
* A section `recipes.secure_hopper` in `config.yml` or an entry `secure_hopper` in `recipes.yml` from an earlier version is no longer used. ChestSecure writes a note to the log at the start, and the next save in the recipe editor leaves the entry out of `recipes.yml`.
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
