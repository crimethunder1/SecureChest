package de.SecureChest.plugin.ping;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

record ActivePingSession(Location targetLocation, BukkitTask navigationTask) {}
