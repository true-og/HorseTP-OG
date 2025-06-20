package net.trueog.horsetpog.Listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.trueog.horsetpog.HorseTpOG;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public class OnPlayerTeleport implements Listener {

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {

        // run all of this 1 tick after the teleport so that the players location gets updated
        // and the dismount event is guaranteed to have been called
        Bukkit.getScheduler()
                .runTaskLater(
                        HorseTpOG.getPlugin(),
                        () -> {

                            // if the player did not have a vehicle prior to the teleport
                            // ignore this handler
                            if (HorseTpOG.getVehicleCache().get(event.getPlayer()) == null) {
                                return;
                            }

                            // this checks to see if the teleport was a result of a dismount, or a /tp
                            // TeleportCause.UNKNOWN is a dismount
                            if (!event.getCause().equals(TeleportCause.UNKNOWN)) {

                                // I have no idea why this works, but it does
                                // I reset the player's pitch/yaw and then teleport them back
                                event.getPlayer()
                                        .teleport(new Location(
                                                event.getPlayer().getWorld(),
                                                event.getPlayer().getLocation().getX(),
                                                event.getPlayer().getLocation().getY(),
                                                event.getPlayer().getLocation().getZ()));
                                event.getPlayer().teleport(event.getTo());

                                Player player = event.getPlayer();
                                // get the vehicle from the vehicle cache
                                Entity vehicle = HorseTpOG.getVehicleCache().get(player);
                                // remove the player and vehicle from the cache
                                // this is necessary to prevent the WorldGuard teleport
                                // from triggering this event again
                                HorseTpOG.getVehicleCache().remove(player);

                                // check if the area that the player teleported to is a banned area
                                RegionContainer container =
                                        WorldGuard.getInstance().getPlatform().getRegionContainer();
                                com.sk89q.worldedit.util.Location worldGuardPlayerLocation =
                                        BukkitAdapter.adapt(event.getTo());

                                RegionQuery query = container.createQuery();
                                ApplicableRegionSet set = query.getApplicableRegions(worldGuardPlayerLocation);

                                // check if the horse-can-tp flag is set to DENY
                                if (!set.testState(null, HorseTpOG.getHorseTpFlag())) {
                                    // only stop the tp if the player is in a minecart or boat
                                    // Otherwise, a protected area could be flooded with boats and minecarts
                                    // that can't be destroyed by non-admins
                                    if (vehicle instanceof Boat || vehicle instanceof Minecart) {
                                        player.sendMessage(Component.text("[HorseTp]")
                                                .color(TextColor.color(0, 255, 0))
                                                .append(Component.text(" You can't teleport boats here")
                                                        .color(TextColor.color(255, 0, 0))));
                                        return;
                                    }
                                }
                                // get a list of all the passengers the vehicle had
                                // use try-catch to see if the vehicle still exists
                                // as it may have been removed by another plugin
                                List<Entity> passengerList = null;
                                try {
                                    passengerList = vehicle.getPassengers();
                                } catch (Exception e) {
                                    return;
                                }
                                // remove the player from the passenger list
                                passengerList.remove(player);
                                // eject all of the passengers
                                vehicle.eject();

                                // load the chunk that the vehicle is in
                                World vehicleWorld = vehicle.getWorld();
                                Chunk vehicleChunk = vehicle.getChunk();
                                vehicleWorld.loadChunk(vehicleChunk);

                                // wait 1 tick before teleporting the entity
                                List<Entity> finalPassengerList =
                                        passengerList; // apparently this is required for the compiler to be happy ?
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                HorseTpOG.getPlugin(),
                                                () -> {
                                                    // teleport the vehicle and all of the passengers
                                                    // use try-catch to see if the vehicle still exists
                                                    try {
                                                        vehicle.teleport(player.getLocation());
                                                    } catch (Exception e) {
                                                        return;
                                                    }

                                                    finalPassengerList.forEach(entity -> {
                                                        entity.teleport(player.getLocation());
                                                        vehicle.addPassenger(entity);
                                                    });

                                                    // simply hide and show the vehicle
                                                    // so that the vehicle is not invisible
                                                    player.hideEntity(HorseTpOG.getPlugin(), vehicle);
                                                    player.showEntity(HorseTpOG.getPlugin(), vehicle);

                                                    // add the player back to the vehicle
                                                    vehicle.addPassenger(player);

                                                    // unload the chunk that the vehicle was in one tick later
                                                    // also add the player back to the vehicle
                                                    Bukkit.getScheduler()
                                                            .runTaskLater(
                                                                    HorseTpOG.getPlugin(),
                                                                    () -> {
                                                                        vehicleWorld.unloadChunk(vehicleChunk);
                                                                    },
                                                                    1);
                                                },
                                                1);
                            }
                        },
                        1);
    }

    // this method is used for teleporting players back when the region
    // that they teleported to is restricted
    // this is necessary because if someone is laggy, we need to load the
    // chunk that they are in
    // This is mostly a problem for 1.8 players

    // It just recursively calls itself, loading the chunk until
    // the player is teleported back
    public void loadChunksUntilTeleportComplete(World world, Location teleportFrom, Player player) {
        if (!Arrays.stream(world.getChunkAt(teleportFrom).getEntities())
                .toList()
                .contains(player)) {
            loadChunksUntilTeleportComplete(world, teleportFrom, player);
        } else {
            for (int i = 0; i < (int) Math.ceil(((double) player.getPing()) / 50) + 10; i++) {
                Bukkit.getScheduler()
                        .runTaskLater(
                                HorseTpOG.getPlugin(),
                                () -> {
                                    world.getChunkAt(teleportFrom).load();
                                    player.sendBlockChange(
                                            player.getLocation(), world.getBlockData(player.getLocation()));
                                },
                                i);
            }
        }
        world.getChunkAt(teleportFrom).load();
    }
}
