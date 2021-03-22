package me.bkrmt.teleport;

import org.bukkit.Location;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface TeleportRunnable {
    void run(Player player, Location location, boolean isCanceled);
}
