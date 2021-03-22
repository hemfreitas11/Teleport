package me.bkrmt.teleport;

import me.bkrmt.bkcore.BkPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Dictionary;
import java.util.Hashtable;

public class TeleportCore {
    public static Dictionary<String, CancelCause> cancelCause;
    public static Dictionary<String, Boolean> playersInCooldown;
    public static Dictionary<String, BukkitTask> playerTeleport;
    public static Dictionary<String, Object[]> invulnerablePlayers;

    public TeleportCore(BkPlugin plugin) {
        cancelCause = new Hashtable<>();
        playersInCooldown = new Hashtable<>();
        playerTeleport = new Hashtable<>();
        invulnerablePlayers = new Hashtable<>();
        plugin.getServer().getPluginManager().registerEvents(new DamageListener(), plugin);
    }
}
