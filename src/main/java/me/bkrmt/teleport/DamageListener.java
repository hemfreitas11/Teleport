package me.bkrmt.teleport;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageListener implements Listener {
    @EventHandler
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (event.getEntityType().equals(EntityType.PLAYER)) {
            Player damagedPlayer = (Player) event.getEntity();

            if (TeleportCore.invulnerablePlayers.get(damagedPlayer.getName()) != null) {
                Object[] values = TeleportCore.invulnerablePlayers.get(damagedPlayer.getName());

                int length = (int) values[0];
                float count = (float) values[1];

                float remaining = (length - count);
                if (remaining > 0) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (TeleportCore.playersInCooldown.get(damagedPlayer.getName()) != null) {
                TeleportCore.playersInCooldown.put(damagedPlayer.getName(), false);
                TeleportCore.cancelCause.put(damagedPlayer.getName(), CancelCause.DealtDamage);
            }
        }
    }

    @EventHandler
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType().equals(EntityType.PLAYER)) {
            Player damagerPlayer = (Player) event.getDamager();

            if (TeleportCore.invulnerablePlayers.get(damagerPlayer.getName()) != null) {
                if (checkInvulnerable(event, damagerPlayer, 0, damagerPlayer.getName())) return;
            } else if (TeleportCore.invulnerablePlayers.get(event.getEntity().getName()) != null) {
                if (checkInvulnerable(event, damagerPlayer, 1, event.getEntity().getName())) return;
            }

            if (TeleportCore.playersInCooldown.get(damagerPlayer.getName()) != null) {
                TeleportCore.playersInCooldown.put(damagerPlayer.getName(), false);
                TeleportCore.cancelCause.put(damagerPlayer.getName(), CancelCause.TookDamage);
            }
        }
    }

    private boolean checkInvulnerable(EntityDamageByEntityEvent event, Player damagerPlayer, int key, String name) {
        Object[] values = TeleportCore.invulnerablePlayers.get(name);
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
