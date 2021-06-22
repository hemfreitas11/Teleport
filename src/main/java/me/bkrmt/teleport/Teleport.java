package me.bkrmt.teleport;

import me.bkrmt.bkcore.BkPlugin;
import me.bkrmt.bkcore.Utils;
import me.bkrmt.bkcore.config.Configuration;
import me.bkrmt.teleport.events.PlayerBkTeleportCountStartEvent;
import me.bkrmt.teleport.events.PlayerBkTeleportEvent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;


public class Teleport {
    private int duration;
    private final int startingDuration;
    private final CommandSender sender;
    private final TeleportType type;
    private String warpName;
    private final BkPlugin bkPlugin;
    private final PlayerBkTeleportCountStartEvent startEvent;
    private Location location = null;
    private boolean hasMoveListener;
    private boolean isCancelable = true;
    private TeleportRunnable finishRunnable;
    private Listener moveListener;

    @Deprecated
    public Teleport(BkPlugin bkPlugin, CommandSender sender, String warpName, TeleportType type) {
        this.type = type;
        this.bkPlugin = bkPlugin;
        if (type.equals(TeleportType.Tpa)) {
            this.sender = Utils.getPlayer(warpName);
            this.warpName = sender.getName();
        } else {
            this.sender = sender;
            this.warpName = warpName;
        }
        this.duration = getDuration();
        startEvent = new PlayerBkTeleportCountStartEvent((Player) sender, duration);
        startingDuration = duration;
        finishRunnable = null;

        startTeleport();
    }

    public Teleport(BkPlugin bkPlugin, Player player, boolean hasMoveListener) {
        this.bkPlugin = bkPlugin;
        this.sender = player;
        this.hasMoveListener = hasMoveListener;
        type = null;
        duration = 5;
        finishRunnable = null;
        warpName = null;
        location = null;

        startEvent = new PlayerBkTeleportCountStartEvent((Player) this.sender, this.duration);
        this.startingDuration = duration;
    }

    private void startMoveListener(BkPlugin bkPlugin) {
        moveListener = bkPlugin.getConfigManager().getConfig().getBoolean("cancel-on-move") ? new Listener() {
            @EventHandler
            public void onMove(PlayerMoveEvent event) {
                if (bkPlugin.getConfigManager().getConfig().getBoolean("cancel-on-move")) {
                    if ((int) event.getFrom().getX() != (int) event.getTo().getX() || (int) event.getFrom().getZ() != (int) event.getTo().getZ()) {
                        Player player = event.getPlayer();
                        if (TeleportCore.INSTANCE.getPlayersInCooldown().get(player.getName()) != null) {
                            TeleportCore.INSTANCE.getPlayersInCooldown().put(player.getName(), false);
                            TeleportCore.INSTANCE.getCancelCause().put(player.getName(), CancelCause.Moved);
                            HandlerList.unregisterAll(moveListener);
                        }
                    }
                }
            }
        } : null;

        if (moveListener != null) bkPlugin.getServer().getPluginManager().registerEvents(moveListener, bkPlugin);
    }

    public Teleport setRunnable(TeleportRunnable runnable) {
        this.finishRunnable = runnable;
        return this;
    }

    public Teleport setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public Teleport setLocation(String name, Location location) {
        this.location = location;
        this.warpName = name;
        return this;
    }

    public Teleport setIsCancellable(boolean isCancellable) {
        this.isCancelable = isCancellable;
        return this;
    }

    public void startTeleport() {
        if (TeleportCore.INSTANCE.getPlayersInCooldown().get(sender.getName()) == null) {
            TeleportCore.INSTANCE.getPlayersInCooldown().put(sender.getName(), true);
            if (TeleportCore.INSTANCE.getPlayerTeleport().get(sender.getName()) != null) {
                TeleportCore.INSTANCE.getPlayerTeleport().get(sender.getName()).cancel();
            }
            if (duration == 0) {
                teleport();
                return;
            }
            bkPlugin.getServer().getPluginManager().callEvent(startEvent);

            if (hasMoveListener && !isCanceled()) startMoveListener(bkPlugin);

            BukkitTask teleport = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isCanceled()) cancel();

                    else sendCountdown();

                    if (duration <= 0) {
                        teleport();
                        cancel();
                    }
                }
            }.runTaskTimer(bkPlugin, 0, 20);
            TeleportCore.INSTANCE.getPlayerTeleport().put(sender.getName(), teleport);
        } else {
            sender.sendMessage(bkPlugin.getLangFile().get("error.already-waiting"));
        }
    }

    private void pling(int volume, float pitch) {
        ((Player) sender).playSound(((Player) sender).getLocation(), bkPlugin.getHandler().getSoundManager().getPling(), volume, pitch);
    }

    private void sendCountdown() {
        bkPlugin.sendTitle((Player) sender,
                5, 10, 5,
                bkPlugin.getLangFile().get("info.time-remaining").replace("{seconds}", String.valueOf(duration)), "");
        duration--;
        new BukkitRunnable() {
            @Override
            public void run() {
                pling(15, 1);
            }
        }.runTaskLater(bkPlugin, 5);
    }

    private void teleport() {
        if (startingDuration != 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkTeleport();
                }
            }.runTaskLater(bkPlugin, 25);
        }
        else checkTeleport();
    }

    private void checkTeleport() {
        if (isCanceled()) return;

        TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
        TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
        try {
            Player player = (Player) sender;
            PlayerBkTeleportEvent tpEvent = new PlayerBkTeleportEvent(player, getWarpingLocation());
            bkPlugin.getServer().getPluginManager().callEvent(tpEvent);
            if (!tpEvent.isCancelled()) {
                movePlayer();
                pling(15, 2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isCanceled() {
        if (!isCancelable) return false;
        if (startEvent.isCancelled()) {
            TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
            TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
            if (finishRunnable != null) Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, true), 3);
            return true;
        }else if (!TeleportCore.INSTANCE.getPlayersInCooldown().get(sender.getName())) {
            String subtitle = "";
            if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.DealtDamage))
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.took-damage");
            else if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.TookDamage)) {
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.dealt-damage");
            } else if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.Moved)) {
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.moved");
            }
            bkPlugin.sendTitle((Player) sender, 5, 30, 5, bkPlugin.getLangFile().get("error.warp-canceled-title"), subtitle);
            pling(15, 0.5f);
            TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
            TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
            TeleportCore.INSTANCE.getCancelCause().remove(sender.getName());
            if (finishRunnable != null) Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, true), 3);
            return true;
        } else return false;
    }

    private void movePlayer() {
        int invTime = bkPlugin.getConfigManager().getConfig().getInt("invulnerable-time");
        if (!((Player)sender).getGameMode().equals(GameMode.CREATIVE) && invTime > 0) {
            if (TeleportCore.INSTANCE.getInvulnerablePlayers().get(sender.getName()) != null) {
                ((BukkitTask) TeleportCore.INSTANCE.getInvulnerablePlayers().get(sender.getName())[2]).cancel();
                TeleportCore.INSTANCE.getInvulnerablePlayers().remove(sender.getName());
            }

            String actionMessage = bkPlugin.getLangFile().get("info.invulnerable-remaining");

            bkPlugin.sendActionBar((Player) sender, actionMessage.replace("{seconds}", String.valueOf(invTime)));

            Object[] values = new Object[4];
            values[0] = invTime;
            values[1] = 0f;
            values[2] = new BukkitRunnable() {
                boolean sendActionBar = false;

                @Override
                public void run() {
                    Player player = (Player) sender;
                    if (!player.isOnline()) {
                        cancel();
                        TeleportCore.INSTANCE.getInvulnerablePlayers().remove(sender.getName());
                    } else {
                        Object[] values = TeleportCore.INSTANCE.getInvulnerablePlayers().get(sender.getName());

                        int length = (int) values[0];
                        float count = (float) values[1];
                        if ((int) (length - count) > 0) {
                            if (sendActionBar) {
                                bkPlugin.sendActionBar(player, actionMessage.replace("{seconds}", String.valueOf((int) (length - count))));
                                sendActionBar = false;
                            }
                            else sendActionBar = true;
                            TeleportCore.INSTANCE.getInvulnerablePlayers().get(sender.getName())[1] = count + 0.5f;
                        } else {
                            bkPlugin.sendActionBar(player, "");
                            cancel();
                            TeleportCore.INSTANCE.getInvulnerablePlayers().remove(player.getName());
                        }
                    }
                }
            }.runTaskTimer(bkPlugin, 10, 10);
            values[3] = new String[] {bkPlugin.getLangFile().get("error.cant-attack-now.self"), bkPlugin.getLangFile().get("error.cant-attack-now.others")};

            TeleportCore.INSTANCE.getInvulnerablePlayers().put(sender.getName(), values);
        }

        ((Player) sender).teleport(getWarpingLocation());
        String title;
        String subtitle = "";

        if (type == null) {
            title = bkPlugin.getLangFile().get("info.warped.title");
            subtitle = bkPlugin.getLangFile().get("info.warped.subtitle").replace("{location-name}", warpName);
        } else {
            if (type.equals(TeleportType.Tpa) || type.equals(TeleportType.Loja)) {
                if (Utils.getPlayer(warpName) == null) {
                    OfflinePlayer offlineTarget = Bukkit.getServer().getOfflinePlayer(warpName);
                    if (offlineTarget != null) warpName = offlineTarget.getName();
                } else warpName = Utils.getPlayer(warpName).getName();
            }
            if (type.equals(TeleportType.Loja)) {
                Configuration configFile = bkPlugin.getConfigManager().getConfig("shops", warpName.toLowerCase() + ".yml");
                String customColor = "7";
                title = bkPlugin.getLangFile().get("info.warped.title").replace("{player}", warpName);
                title.replaceAll("&", "");
                if (configFile.getString("shop.color") != null) customColor = configFile.getString("shop.color");
                if (configFile.getString("shop.message") != null)
                    subtitle = "&" + customColor + configFile.getString("shop.message");
                title = "&" + customColor + title;
            } else {
                title = bkPlugin.getLangFile().get("info.warped.title");
                subtitle = bkPlugin.getLangFile().get("info.warped.subtitle").replace("{player}", warpName);
            }
        }
        if (finishRunnable != null) Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, false), 3);
        bkPlugin.sendTitle((Player) sender, 5, 45, 10, ChatColor.translateAlternateColorCodes('&', title), ChatColor.translateAlternateColorCodes('&', subtitle));
    }

    private Location getWarpingLocation() {
        if (location == null) {
            String fileName = "";
            String filePath = bkPlugin.getDataFolder().getPath();
            String configKey = "";
            String tempWarpName = warpName + ".";
            if (type.equals(TeleportType.Home) || type.equals(TeleportType.Loja)) {
                if (type.equals(TeleportType.Home)) {
                    filePath = "userdata";
                    configKey = "homes.";
                    fileName = ((Player) sender).getUniqueId().toString() + ".yml";
                } else {
                    fileName = warpName.toLowerCase() + ".yml";
                    filePath = "shops";
                    tempWarpName = ".";
                    configKey = "shop";
                }
            } else if (type.equals(TeleportType.Warp)) {
                filePath = "warps";
                tempWarpName = "";
                configKey = "";
                fileName = warpName.toLowerCase() + ".yml";
            }

            Location warpingLocation;
            if (type.equals(TeleportType.Tpa)) {
                warpingLocation = Utils.getPlayer(warpName).getLocation();
            } else if (type.equals(TeleportType.TpaHere)) {
                warpingLocation = ((Player) sender).getLocation();
            } else {
                Configuration config = bkPlugin.getConfigManager().getConfig(filePath, fileName);
                World world = bkPlugin.getServer().getWorld(config.getString(configKey + tempWarpName + "world"));
                double x = config.getDouble(configKey + tempWarpName + "x");
                double y = config.getDouble(configKey + tempWarpName + "y");
                double z = config.getDouble(configKey + tempWarpName + "z");
                float yaw = (float) config.getDouble(configKey + tempWarpName + "yaw");
                float pitch = (float) config.getDouble(configKey + tempWarpName + "pitch");
                warpingLocation = new Location(world, x, y, z, yaw, pitch);
            }

            return warpingLocation;
        } else {
            return location;
        }
    }

    private int getDuration() {
        Player player = ((Player) sender);
        String permission = "";
        if (type.equals(TeleportType.Loja)) permission = "bkshop";
        else permission = permission + "bkteleport";
        int returnDuration = 5;
        for (int count = 0; count <= 99; count++) {
            if (player.hasPermission(permission + ".countdown." + count)) {
                returnDuration = count;
                break;
            }
        }
        return returnDuration;
    }
}
