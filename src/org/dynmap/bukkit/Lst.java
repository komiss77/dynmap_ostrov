package org.dynmap.bukkit;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.dynmap.Client;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.MarkersComponent;
import ru.komiss77.events.LocalDataLoadEvent;


public class Lst implements Listener {
    

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLocalDataLoad(LocalDataLoadEvent e) {
        final Player p = (e.getPlayer());
        Location loc = e.getPlayer().getLocation();
        DynmapCore.mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
           
        // Give other handlers a change to prep player (nicknames and such from Essentials)
        //DynmapPlugin.plugin.getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.plugin, new Runnable() {
        //    @Override
         //   public void run() {
                //core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
                DynmapCore.playerJoined(p);
                if (!DynmapPlugin.core.disable_chat_to_web) {
                    DynmapCore.mapManager.pushUpdate(new Client.PlayerJoinMessage(p.getDisplayName(), p.getName()));
                }
               // Marker m = offlineset.findMarker(p.getName());
               // if (m != null) {
               //     m.deleteMarker();
               //     offline_times.remove(p.getName());
               // }
          //  }
       // }, 2);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent e) {
       // Player dp = (evt.getPlayer());
        //core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
        DynmapCore.playerQuit(e.getPlayer());
       // String pname = p.getName();
       /* Marker m = offlineset.findMarker(pname);
        if (m != null) {
            m.deleteMarker();
            offline_times.remove(p.getName());
        }
        //  if (core.playerList.isVisiblePlayer(pname)) {
        DynmapLocation loc = new DynmapLocation(p.getLocation());
        m = offlineset.createMarker(p.getName(), core.getServer().stripChatColor(p.getDisplayName()), false,
                loc.world, loc.x, loc.y, loc.z, offlineicon, true);
        if (maxofflineage > 0) {
            offline_times.put(p.getName(), System.currentTimeMillis() + maxofflineage);
        }*/
        //  }
    }

    //@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    //public void onWorldLoad(WorldLoadEvent e) {
       // worldLoad(e.getWorld());
    //}
    
   /* public static void worldLoad (final World w) {
        
        DynmapWorld bw = DynmapPlugin.bukkitWorld(w.getName());
        if (bw == null) {
            bw = new DynmapWorld(w);
            DynmapPlugin.world_by_name.put(w.getName(), bw);
            DynmapCore.updateConfigHashcode();
            DynmapCore.mapManager.activateWorld(bw);
        } else {
            DynmapCore.mapManager.loadWorld(bw);
        }
    }*/

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        DynmapWorld w = DynmapPlugin.removeWorld(event.getWorld());//MapManager.getWorld(event.getWorld().getName());
        if (w != null) {
            w.setWorldUnloaded();
            DynmapCore.mapManager.unloadWorld(w);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (DynmapPlugin.dw(e.getBlock().getWorld().getName()) == null) return;
        Location loc = e.getBlock().getLocation();
        String wn = loc.getWorld().getName();
        DynmapPlugin.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        DynmapCore.mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (DynmapPlugin.dw(e.getBlock().getWorld().getName()) == null) return;
        Location loc = e.getBlock().getLocation();
        String wn = loc.getWorld().getName();
        DynmapPlugin.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        DynmapCore.mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent e) {
        DynmapWorld dw = DynmapPlugin.dw(e.getWorld().getName());
        if (dw==null) return;
        Chunk c = e.getChunk();
        /* Touch extreme corners */
        int x = c.getX() << 4;
        int z = c.getZ() << 4;
        int ymin = dw.minY;
        int ymax = dw.worldheight;
        DynmapCore.mapManager.touchVolume(dw.dynmapName(), x, ymin, z, x + 15, ymax, z + 16, "chunkpopulate");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnChange(SpawnChangeEvent e) {
        DynmapWorld dw = DynmapPlugin.dw(e.getWorld().getName());
        if (dw==null) return;
        //core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
        DynmapLocation loc = dw.getSpawnLocation();
        /* Get location of spawn */
        if (loc != null) {
            MarkersComponent.addUpdateWorld(dw, loc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent evt) {
        if (DynmapPlugin.core.disable_chat_to_web) {
            return;
        }
        final Player p = evt.getPlayer();
        final String msg = DynmapPlugin.core.scanAndReplaceLog4JMacro(evt.getMessage());
        DynmapPlugin.plugin.getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.plugin, new Runnable() {
            public void run() {
                // Player dp = null;
                //  if (p != null) {
                //     dp = new BukkitPlayer(p);
                // }
                //core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, p, msg);
                if (DynmapCore.mapManager != null) {
                    DynmapCore.mapManager.pushUpdate(new Client.ChatMessage("player", "", p.getDisplayName(), msg, p.getName()));
                }
            }
        });
    }

}
