package org.dynmap.bukkit;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.dynmap.Client;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.MarkersComponent;
import ru.komiss77.enums.Game;
import ru.komiss77.modules.games.GM;


public class Lst implements Listener {
    
    public static final NamespacedKey MAP = new NamespacedKey(DynmapPlugin.plugin, "dynmap");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        final Player p = (e.getPlayer());
        Location loc = e.getPlayer().getLocation();
        DynmapCore.mapManager.touch(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "playerjoin");
           
        // Give other handlers a change to prep player (nicknames and such from Essentials)
        DynmapPlugin.plugin.getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.plugin, new Runnable() {
            @Override
            public void run() {
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
            }
        }, 2);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent evt) {
        Player dp = (evt.getPlayer());
        //core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
        DynmapCore.playerQuit(dp);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent e) {
        worldLoad(e.getWorld());
        //DynmapWorld w = MapManager.getWorld(event.getWorld().getName());
        //if (core.processWorldLoad(w)) /* Have core process load first - fire event listeners if good load after */ {
           // core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
       // }
    }
    
    public static void worldLoad (final World w) {
        
        if (GM.GAME == Game.AR || GM.GAME == Game.SB || GM.GAME == Game.OB) {
            if (w.getPersistentDataContainer().has(MAP)) {
                boolean load = w.getPersistentDataContainer().get(MAP, PersistentDataType.BOOLEAN);
                if (!load) {
                    //удалить карту
                    //удалить ключ
                    return;
                }
            }
        }
        
        BukkitWorld bw = DynmapPlugin.bukkitWorld(w.getName());
        if (bw == null) {
            bw = new BukkitWorld(w);
            DynmapPlugin.world_by_name.put(w.getName(), bw);
            //bw = DynmapPlugin.getOrCreateWorld(w);
            DynmapCore.updateConfigHashcode();
            DynmapCore.mapManager.activateWorld(bw);
        } else {
        //core.processWorldLoad(dw);
        //boolean activated = true;
        //if (DynmapCore.mapManager.getWorld(w.getName()) == null) {
        //    DynmapCore.updateConfigHashcode();
         //   activated = mapManager.activateWorld(w);
       // } else {
            DynmapCore.mapManager.loadWorld(bw);
        }
        //return activated;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        DynmapWorld w = DynmapPlugin.removeWorld(event.getWorld());//MapManager.getWorld(event.getWorld().getName());
        if (w != null) {
            //core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, w);
            w.setWorldUnloaded();
            //core.processWorldUnload(w);
            //if (DynmapCore.mapManager.getWorld(w.getName()) != null) {
                DynmapCore.mapManager.unloadWorld(w);
                //done = true;
            //}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        String wn = loc.getWorld().getName();
        DynmapPlugin.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        DynmapCore.mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockplace");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Location loc = b.getLocation();
        String wn = loc.getWorld().getName();
        DynmapPlugin.invalidateSnapshot(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        DynmapCore.mapManager.touch(wn, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), "blockbreak");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent e) {
        DynmapWorld w = DynmapPlugin.bukkitWorld(e.getWorld().getName());
        Chunk c = e.getChunk();
        /* Touch extreme corners */
        int x = c.getX() << 4;
        int z = c.getZ() << 4;
        int ymin = w.minY;
        int ymax = w.worldheight;
        DynmapCore.mapManager.touchVolume(w.getName(), x, ymin, z, x + 15, ymax, z + 16, "chunkpopulate");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnChange(SpawnChangeEvent e) {
        DynmapWorld w = DynmapPlugin.bukkitWorld(e.getWorld().getName());
        //core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
        DynmapLocation loc = w.getSpawnLocation();
        /* Get location of spawn */
        if (loc != null) {
            MarkersComponent.addUpdateWorld(w, loc);
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
