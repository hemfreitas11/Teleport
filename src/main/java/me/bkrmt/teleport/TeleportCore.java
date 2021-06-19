package me.bkrmt.teleport;

import me.bkrmt.bkcore.BkPlugin;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Dictionary;
import java.util.Hashtable;

public enum TeleportCore {
    INSTANCE;

    private static Dictionary<String, CancelCause> cancelCause = new Hashtable<>();
    private static Dictionary<String, Boolean> playersInCooldown = new Hashtable<>();
    private static Dictionary<String, BukkitTask> playerTeleport = new Hashtable<>();
    private static Dictionary<String, Object[]> invulnerablePlayers = new Hashtable<>();

    public void start(BkPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerTakeDamage(EntityDamageEvent event) {
                if (event.getEntityType().equals(EntityType.PLAYER)) {
                    Player damagedPlayer = (Player) event.getEntity();

                    if (TeleportCore.INSTANCE.getInvulnerablePlayers().get(damagedPlayer.getName()) != null) {
                        Object[] values = TeleportCore.INSTANCE.getInvulnerablePlayers().get(damagedPlayer.getName());

                        int length = (int) values[0];
                        float count = (float) values[1];

                        float remaining = (length - count);
                        if (remaining > 0) {
                            event.setCancelled(true);
                            return;
                        }
                    }

                    if (TeleportCore.INSTANCE.getPlayersInCooldown().get(damagedPlayer.getName()) != null) {
                        TeleportCore.INSTANCE.getPlayersInCooldown().put(damagedPlayer.getName(), false);
                        TeleportCore.INSTANCE.getCancelCause().put(damagedPlayer.getName(), CancelCause.DealtDamage);
                    }
                }
            }

            @EventHandler
            public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
                if (event.getDamager().getType().equals(EntityType.PLAYER)) {
                    Player damagerPlayer = (Player) event.getDamager();

                    if (TeleportCore.INSTANCE.getInvulnerablePlayers().get(damagerPlayer.getName()) != null) {
                        if (checkInvulnerable(event, damagerPlayer, 0, damagerPlayer.getName())) return;
                    } else if (TeleportCore.INSTANCE.getInvulnerablePlayers().get(event.getEntity().getName()) != null) {
                        if (checkInvulnerable(event, damagerPlayer, 1, event.getEntity().getName())) return;
                    }

                    if (TeleportCore.INSTANCE.getPlayersInCooldown().get(damagerPlayer.getName()) != null) {
                        TeleportCore.INSTANCE.getPlayersInCooldown().put(damagerPlayer.getName(), false);
                        TeleportCore.INSTANCE.getCancelCause().put(damagerPlayer.getName(), CancelCause.TookDamage);
                    }
                }
            }
        }, plugin);
        TeleportCore.INSTANCE.getPlayersInCooldown().put("Core-Started", true);
    }

    public static Dictionary<String, CancelCause> getCancelCause() {
        return cancelCause;
    }

    public static Dictionary<String, Boolean> getPlayersInCooldown() {
        return playersInCooldown;
    }

    public static Dictionary<String, BukkitTask> getPlayerTeleport() {
        return playerTeleport;
    }

    public static Dictionary<String, Object[]> getInvulnerablePlayers() {
        return invulnerablePlayers;
    }

    private boolean checkInvulnerable(EntityDamageByEntityEvent event, Player damagerPlayer, int key, String name) {
        Object[] values = TeleportCore.INSTANCE.getInvulnerablePlayers().get(name);
        if (values != null) {
            int length = (int) values[0];
            float count = (float) values[1];
            String[] messages = (String[]) values[3];

            float remaining = (length - count);
            if (remaining > 0) {
                int tempInt = (int) remaining > 0 ? (int) remaining : 1;
                damagerPlayer.sendMessage(
                        messages[key].replace("{seconds}", String.valueOf(tempInt))
                );
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }
}
