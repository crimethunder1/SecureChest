# Changelog

## 1.7.0

Requires Paper 26.2 and Java 25. Minecraft 1.21 is no longer supported, please stay on version 1.6.0 there. ChestSecure is built and tested for Paper 26.2. Other server forks may work but are not supported.

### Added

* Protection area. Admins define rectangular areas (X and Z, several per world) where locking is possible. While the switch is on, new locks are only possible inside an area. Turning the switch on, shrinking an area or deleting it removes the locks that end up outside, and their owners get a message. It is managed in the admin panel and stored in `areas.yml`.
* Recipe editor. The Chest Key recipe can be changed live in the admin panel. Changes apply at once and survive a restart (`recipes.yml`).
* Secure Hopper through the Chest Key. Right click a hopper that faces a container you locked to turn it into a Secure Hopper. Sneak and right click it with the Chest Key to turn it back. Breaking it drops a normal hopper.
* A button in the admin panel that gives you Chest Keys. The amount is typed in chat.
* A switch in the admin panel that shows or hides the owner name above all locks (`settings.yml`).
* Owner names are hidden while a solid block occupies the space where the text would stand. Admins can turn this off in the admin panel (`signs.hide_when_covered`).
* Every player can switch the name display for their own locks on and off in their settings.
* A better chest locator. The action bar shows an arrow relative to your view and a height hint, and the locked container glows for you while the ping runs. The ping ends after you arrive, after a time limit or when you click the button again (`ping.timeout_seconds`, `ping.arrived_seconds`, `ping.highlight_enabled`).
* Every action in the log browser has its own icon and label.
* An admin menu for single locks. Sneak and right click a locked container with the Admin Key to remove the lock, transfer it to another player or change its trusted players. Every change is logged with its own action, and the owner is notified when a lock is removed or transferred, also while offline and also when `notifications.enabled` is off.
* A switch in the admin panel that makes every new lock cost one Chest Key (off by default, `locking.consume_chest_key`).
* The number of Chest Keys a recipe produces can be set from 1 to 64 in the recipe editor and in `config.yml` (`recipes.chest_key.amount`).
* A short message when a lock click is dropped because of the cooldown.
* Furnaces, blast furnaces, smokers, droppers, dispensers, brewing stands and crafters can be locked now, with the same rules as chests. Viewers can look but not take, stockers can put items in and take them out. Machines keep running: smelting and redstone are not stopped.
* Copper chests can be locked now in all oxidation states, waxed or not, with the same rules as chests.
* Copper golems cannot take items from or put items into locked containers.

### Changed

* The Chest Key only works for the owner of a lock, also for operators. Operators no longer take over or manage other players' locks with it, they use the Admin Key and the admin panel.
* Chunk loading no longer scans every lock. The plugin keeps an index from chunks to locks, which keeps the owner names fast to restore on servers with many locks.
* Owner names are shown once per group of connected locked blocks instead of once per block, centered above the group, which also centers the name above double chests and includes Secure Hoppers next to locked blocks.
* The log browser shows player names instead of UUIDs in the notes of its entries, and its action filter also offers locks removed by the protection area and transfers and trust changes by admins.

### Removed

* The Secure Hopper item and its recipe. Existing Secure Hoppers keep working, and crafted Secure Hopper items can still be placed. `recipes.secure_hopper` in `config.yml` is no longer used, and the log says so once.

### Fixed

* Double chests. A lock could be lost when a second chest was placed next to a locked one, and the check against placing next to someone else's lock did not work in every case.
* The plugin did not start with an invalid recipe in `config.yml`. Invalid recipes now log a warning and are skipped.
* Unlocked chests and hoppers no longer cause database lookups and log warnings on the main thread.
* Owner names reappear above chests after a chunk was unloaded and loaded again.
* Owner names above locks and player names in the menu lists show the player name instead of a shortened UUID after a restart or while the player is offline. This bug also affected older versions.
* Undoing a lock transfer now restores the previous owner in the database as well and no longer overwrites a newer lock at the same position.
* The owner dialogs for trusted players and transfers ask again after a typing mistake.
* An owner name text is hidden when a solid block is on any of the fields it touches, for a double chest this is the field above either half.
* Chest Keys and Admin Keys can no longer be placed as tripwire hooks.

### Known limitations

* Redstone can still make a locked dropper, dispenser or crafter hand out its items. Locks protect against players, not against redstone.
* Locked droppers and crafters do not pass items into containers. Only Secure Hoppers move items out of locked blocks.

### Update notes

* Back up `plugins/ChestSecure` before updating.
* `config.yml` is never written by the plugin. The admin panel stores its changes in `areas.yml`, `recipes.yml` and `settings.yml`.
* An existing `config.yml` without `locking.consume_chest_key` and `recipes.chest_key.amount` keeps working as before, with Chest Key consumption off and one Chest Key per craft.
* The plugin adds a new table `player_names` to `chestsecure.db` that keeps the last known name of each player.
* The plugin adds the column `show_owner_name` to the table `player_settings` in `chestsecure.db`.
* Older versions could store a lock on the neighbouring chest when double chests stood directly next to each other in a row. After the update such a lock belongs to the chest it is stored on, so please check chests in dense chest rows.
* Admins who used the Chest Key to open the menu of other players' locks, unlock them, change their trust or transfer them can no longer do that. They sneak and right click the lock with the Admin Key to open the admin menu for that lock instead, and they still open locks with the Admin Key and use the admin panel for everything else.
* Copper chests change their block type when they oxidize or are waxed, a lock stays valid when that happens.
