package me.bkrmt.teleport.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public class PlayerBkTeleportCountStartEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancel = false;

    private final int countdown;

    public PlayerBkTeleportCountStartEvent(Player who, int countdown) {
        super(who);
        this.countdown = countdown;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }

    public int getCountdown() {
        return countdown;
    }
}
