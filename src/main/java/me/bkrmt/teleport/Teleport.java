package me.bkrmt.teleport;

import me.bkrmt.bkcore.BkPlugin;
import me.bkrmt.bkcore.Utils;
import me.bkrmt.bkcore.config.Configuration;
import me.bkrmt.teleport.events.PlayerBkTeleportCountStartEvent;
import me.bkrmt.teleport.events.PlayerBkTeleportEvent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

public class Teleport {
    private int duration;
    private int startingDuration;
    private final CommandSender sender;
    private final TeleportType type;
    private String warpName;
    private String title;
    private String subtitle;
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
        hasMoveListener = true;
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
        title = "";
        subtitle = "";
        warpName = null;
        location = null;

        startEvent = new PlayerBkTeleportCountStartEvent((Player) this.sender, this.duration);
        this.startingDuration = duration;
    }

    private void startMoveListener(BkPlugin bkPlugin) {
        moveListener = bkPlugin.getConfigManager().getConfig().getBoolean("teleport-countdown.cancel-on-move") ? new Listener() {
            @EventHandler
            public void onMove(PlayerMoveEvent event) {
                if ((int) event.getFrom().getX() != (int) event.getTo().getX() || (int) event.getFrom().getZ() != (int) event.getTo().getZ()) {
                    Player player = event.getPlayer();
                    if (TeleportCore.INSTANCE.getPlayersInCooldown().get(player.getName()) != null) {
                        TeleportCore.INSTANCE.getPlayersInCooldown().put(player.getName(), false);
                        TeleportCore.INSTANCE.getCancelCause().put(player.getName(), CancelCause.Moved);
                        HandlerList.unregisterAll(moveListener);
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
        this.startingDuration = duration;
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

            boolean useSound = bkPlugin.getConfigManager().getConfig().getBoolean("teleport-countdown.use-sound");
            boolean useTitle = bkPlugin.getConfigManager().getConfig().getBoolean("teleport-countdown.use-title");
            boolean useAction = bkPlugin.getConfigManager().getConfig().getBoolean("teleport-countdown.use-actionbar");

            if (duration == 0) {
                teleport(useSound);
                return;
            }
            bkPlugin.getServer().getPluginManager().callEvent(startEvent);

            if (!isCanceled(useSound) && hasMoveListener) startMoveListener(bkPlugin);

            BukkitTask teleport = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isCanceled(useSound)) cancel();

                    else {
                        if (duration > 0) sendCountdown(useTitle, useAction, useSound);
                    }

                    if (duration <= 0) {
                        teleport(useSound);
                        cancel();
                    }
                }
            }.runTaskTimer(bkPlugin, 0, 20);
            TeleportCore.INSTANCE.getPlayerTeleport().put(sender.getName(), teleport);
        } else {
            sender.sendMessage(bkPlugin.getLangFile().get("error.already-waiting"));
        }
    }

    private void pling(boolean useSound, int volume, float pitch) {
        if (useSound) {
            ((Player) sender).playSound(((Player) sender).getLocation(), bkPlugin.getHandler().getSoundManager().getPling(), volume, pitch);
        }
    }

    private void sendCountdown(boolean useTitle, boolean useActionBar, boolean useSound) {
        if (useTitle) {
            bkPlugin.sendTitle((Player) sender,
                    5, 10, 5,
                    bkPlugin.getLangFile().get("info.time-remaining").replace("{seconds}", String.valueOf(duration)), "");
        }
        if (useActionBar) {
            bkPlugin.sendActionBar((Player) sender, buildBar());
        }
        duration--;
        if (useSound) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    pling(true, 15, 1);
                }
            }.runTaskLater(bkPlugin, 5);
        }
    }

    private void teleport(boolean useSound) {
        if (startingDuration > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkTeleport(useSound);
                }
            }.runTaskLater(bkPlugin, 25);
        } else checkTeleport(useSound);
    }

    public Teleport setTitle(String title) {
        this.title = title;
        return this;
    }

    public Teleport setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    private void checkTeleport(boolean useSound) {
        if (isCanceled(useSound)) return;

        TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
        TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
        try {
            Player player = (Player) sender;
            PlayerBkTeleportEvent tpEvent = new PlayerBkTeleportEvent(player, getWarpingLocation());
            bkPlugin.getServer().getPluginManager().callEvent(tpEvent);
            if (!tpEvent.isCancelled()) {
                movePlayer();
                pling(useSound, 15, 2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isCanceled(boolean useSound) {
        if (!isCancelable) return false;
        if (startEvent.isCancelled()) {
            TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
            TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
            if (finishRunnable != null)
                Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, true), 3);
            return true;
        } else if (!TeleportCore.INSTANCE.getPlayersInCooldown().get(sender.getName())) {
            String subtitle = "";
            if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.DealtDamage))
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.took-damage");
            else if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.TookDamage)) {
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.dealt-damage");
            } else if (TeleportCore.INSTANCE.getCancelCause().get(sender.getName()).equals(CancelCause.Moved)) {
                subtitle = bkPlugin.getLangFile().get("error.warp-canceled-cause.moved");
            }
            bkPlugin.sendTitle((Player) sender, 5, 30, 5, bkPlugin.getLangFile().get("error.warp-canceled-title"), subtitle);
            pling(useSound, 15, 0.5f);
            TeleportCore.INSTANCE.getPlayerTeleport().remove(sender.getName());
            TeleportCore.INSTANCE.getPlayersInCooldown().remove(sender.getName());
            TeleportCore.INSTANCE.getCancelCause().remove(sender.getName());
            if (finishRunnable != null)
                Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, true), 3);
            return true;
        } else return false;
    }

    private void movePlayer() {
        bkPlugin.sendActionBar((Player) sender, " ");
        int invTime = bkPlugin.getConfigManager().getConfig().getInt("invulnerable-time");
        if (!((Player) sender).getGameMode().equals(GameMode.CREATIVE) && invTime > 0) {
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
                            } else sendActionBar = true;
                            TeleportCore.INSTANCE.getInvulnerablePlayers().get(sender.getName())[1] = count + 0.5f;
                        } else {
                            bkPlugin.sendActionBar(player, "");
                            cancel();
                            TeleportCore.INSTANCE.getInvulnerablePlayers().remove(player.getName());
                        }
                    }
                }
            }.runTaskTimer(bkPlugin, 10, 10);
            values[3] = new String[]{bkPlugin.getLangFile().get("error.cant-attack-now.self"), bkPlugin.getLangFile().get("error.cant-attack-now.others")};

            TeleportCore.INSTANCE.getInvulnerablePlayers().put(sender.getName(), values);
        }

        try {
            saveLastLocation((Player) sender);
        } catch (IOException e) {
            new IOException("Could not save last location of player " + sender.getName(), e).printStackTrace();
        }

        ((Player) sender).teleport(getWarpingLocation());

        if (type == null) {
            if (title.isEmpty())
                title = bkPlugin.getLangFile().get("info.warped.title");
            if (subtitle.isEmpty())
                subtitle = bkPlugin.getLangFile().get("info.warped.subtitle")
                    .replace("{location-name}", warpName)
                    .replace("{player}", warpName)
                    .replace("{target}", warpName)
                    .replace("{sender}", warpName)
                    .replace("{warpname}", warpName)
                    .replace("{warp-name}", warpName);
        } else {
            if (type.equals(TeleportType.Tpa) || type.equals(TeleportType.Shop)) {
                if (Utils.getPlayer(warpName) == null) {
                    OfflinePlayer offlineTarget = Bukkit.getServer().getOfflinePlayer(warpName);
                    if (offlineTarget != null) warpName = offlineTarget.getName();
                } else warpName = Utils.getPlayer(warpName).getName();
            }
            if (type.equals(TeleportType.Shop)) {
                Configuration configFile = bkPlugin.getConfigManager().getConfig("shops", warpName.toLowerCase() + ".yml");
                String customColor = "7";
                title = bkPlugin.getLangFile().get("info.warped.title").replace("{player}", warpName);
                if (configFile.getString("shop.color") != null) customColor = configFile.getString("shop.color");
                if (configFile.getString("shop.message") != null)
                    subtitle = "&" + customColor + configFile.getString("shop.message");
                title = "&" + customColor + title;
            } else {
                title = bkPlugin.getLangFile().get("info.warped.title");
                subtitle = bkPlugin.getLangFile().get("info.warped.subtitle").replace("{player}", warpName);
            }
        }
        if (finishRunnable != null)
            Bukkit.getScheduler().scheduleSyncDelayedTask(bkPlugin, () -> finishRunnable.run((Player) sender, location, false), 3);
        if (type != null)
            bkPlugin.sendTitle((Player) sender, 5, 45, 10, Utils.translateColor(title), Utils.translateColor(subtitle));
        else {
            if (!title.isEmpty()) {
                bkPlugin.sendTitle((Player) sender, 5, 45, 10, Utils.translateColor(title), Utils.translateColor(subtitle));
            }
        }
    }

    private void saveLastLocation(Player player) throws IOException {
        Plugin bkTeleport = Bukkit.getPluginManager().getPlugin("BkTeleport");
        if (bkTeleport != null && bkTeleport.isEnabled()) {
            findProvider(bkTeleport, player);
        } else {
            Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess != null && ess.isEnabled()) {
                findProvider(ess, player);
            }
        }
    }

    private void findProvider(Plugin lastLocationProvider, Player player) throws IOException {
        String filePath = lastLocationProvider.getDataFolder() + File.separator + "userdata" + File.separator + player.getUniqueId().toString() + ".yml";
        File userDataFile = new File(filePath);
        if (userDataFile.exists()) {
            writeLastLocation(userDataFile, player);
        } else {
            if (userDataFile.createNewFile()) {
                writeLastLocation(userDataFile, player);
            } else {
                throw new IOException("Return value of createNewFile() is false");
            }
        }
    }

    public void writeLastLocation(File userDataFile, Player player) throws IOException {
        FileConfiguration userDataConfig = YamlConfiguration.loadConfiguration(userDataFile);
        Location lastLocation = player.getLocation();
        userDataConfig.set("lastAccountName", player.getName());
        userDataConfig.set("timestampts.lastteleport", System.currentTimeMillis());
        userDataConfig.set("lastlocation.world", lastLocation.getWorld().getName());
        userDataConfig.set("lastlocation.x", lastLocation.getX());
        userDataConfig.set("lastlocation.y", lastLocation.getY());
        userDataConfig.set("lastlocation.z", lastLocation.getZ());
        userDataConfig.set("lastlocation.yaw", lastLocation.getYaw());
        userDataConfig.set("lastlocation.pitch", lastLocation.getPitch());
        userDataConfig.save(userDataFile);
    }

    private String buildBar() {
        int barAmount = duration;
        StringBuilder barBuilder = new StringBuilder();
        barBuilder.append("§7[");
        int count = 0;
        for (int i = 0; i < startingDuration; i++) {
            if (count > 0) barBuilder.append(" ");
            if (barAmount > 0) {
                barBuilder.append(getBarColor(count)).append("⬛");
                barAmount--;
            } else {
                barBuilder.append("§7⬛");
            }
            count++;
        }
        barBuilder.append("§7]");
        return barBuilder.toString();
    }

    private String getBarColor(int count) {
        int percentage = (int) (((double) count / (double) startingDuration) * 100d);
        if (percentage < 33) {
            return "§c";
        } else if (percentage < 66) {
            return "§e";
        } else {
            return "§a";
        }
    }

    private Location getWarpingLocation() {
        if (location == null) {
            String fileName = "";
            String filePath = bkPlugin.getDataFolder().getPath();
            String configKey = "";
            String tempWarpName = warpName + ".";
            if (type.equals(TeleportType.Home) || type.equals(TeleportType.Shop)) {
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
        if (type.equals(TeleportType.Shop)) permission = "bkshop";
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
