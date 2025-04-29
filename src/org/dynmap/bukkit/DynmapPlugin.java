package org.dynmap.bukkit;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.common.BiomeMap;
import org.bukkit.entity.Player;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.modsupport.ModSupportImpl;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;
import ru.komiss77.Ostrov;
import ru.komiss77.hook.DynmapNms;
import ru.komiss77.objects.CaseInsensitiveMap;

public class DynmapPlugin extends JavaPlugin implements DynmapAPI {

    public static DynmapCore core;
    public static MapManager mapManager;
    public static DynmapPlugin plugin;
    public PluginManager pm;
    private BukkitEnableCoreCallback enabCoreCB = new BukkitEnableCoreCallback();
    private Method ismodloaded;
    private Method instance;
    private Method getindexedmodlist;
    private Method getversion;
    public static CaseInsensitiveMap<DynmapWorld> world_by_name = new CaseInsensitiveMap<>();
    private HashSet<String> modsused = new HashSet<>();
    // TPS calculator
    private double tps;
    private long lasttick;
    private long perTickLimit;
    private long cur_tick_starttime;
    private long avgticklen = 50000000;

    private int chunks_in_cur_tick = 0;
    private long cur_tick;
    private long prev_tick;

    private static DynmapWorld last_bworld;

    public static GenericChunkCache gencache;
    private List<String> biomenames;

    @Override
    public void onEnable() {
        if (core != null) {
            if (core.getMarkerAPI() != null) {
                getLogger().info("Starting Scheduled Write Job (markerAPI).");
                core.restartMarkerSaveJob();
            }
        }

        DynmapNms.initializeBlockStates();//helper.initializeBlockStates();

        File dataDirectory = this.getDataFolder();
        if (dataDirectory.exists() == false) {
            dataDirectory.mkdirs();
        }

        if (core == null) {
            core = new DynmapCore();
        }
        
        String bukkitver = getServer().getVersion();
        String mcver = "1.0.0";
        int idx = bukkitver.indexOf("(MC: ");
        if(idx > 0) {
            mcver = bukkitver.substring(idx+5);
            idx = mcver.indexOf(")");
            if(idx > 0) mcver = mcver.substring(0, idx);
        }
        core.platformVersion = mcver;
        
        /* Inject dependencies */
        core.setPluginJarFile(this.getFile());
        core.setDataFolder(dataDirectory);
        core.setServer(new BukkitServer());

        if (biomenames == null) {
            biomenames = new ArrayList<>();
            for (Biome b : Ostrov.registries.BIOMES) {
                biomenames.add(b.getKey().value());
            }
        }
        core.setBiomeNames(biomenames);

        /* Load configuration */
        if (!core.initConfiguration(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }

        if (!readyToEnable()) {
            Listener pl = new Listener() {
                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onPluginEnabled(PluginEnableEvent evt) {
                    if (!readyToEnable()) {
                        if (readyToEnable()) {
                            /* If we;re ready now, finish enable */
                            doEnable();
                            /* Finish enable */
                        }
                    }
                }
            };
            pm.registerEvents(pl, this);
        } else {
            doEnable();
        }
        // Start tps calculation
        lasttick = System.nanoTime();
        tps = 20.0;
        perTickLimit = core.getMaxTickUseMS() * 1000000;

        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            processTick();
        }, 1, 1);
    }

    private boolean readyToEnable() {
        return true;
    }

    private void doEnable() {
        if (!core.enableCore(enabCoreCB)) {
            this.setEnabled(false);
            return;
        }
        gencache = new GenericChunkCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());

        mapManager = core.getMapManager();

        //for (World world : getServer().getWorlds()) {
        //    Lst.worldLoad(world);
        //}

        pm.registerEvents(new Lst(), this);

        DynmapCommonAPIListener.apiInitialized(this);

        Log.info("Enabled");
    }

    @Override
    public void onDisable() {
        DynmapCommonAPIListener.apiTerminated();
        core.disableCore();
        if (gencache != null) {
            gencache.cleanup();
            gencache = null;
        }
        Log.info("Disabled");
    }

    public static final DynmapWorld bukkitWorld(String name) {
        if (last_bworld != null && last_bworld.getName().equals(name)) {
            return last_bworld;
        }
        DynmapWorld bw = world_by_name.get(name);
        last_bworld = bw;
        return bw;
    }

    public static DynmapWorld removeWorld(World w) {
        DynmapWorld bw = world_by_name.remove(w.getName());
        last_bworld = null;
        return bw;
    }

    private class BukkitEnableCoreCallback extends DynmapCore.EnableCoreCallbacks {

        @Override
        public void configurationLoaded() {
            File st = new File(core.getDataFolder(), "renderdata/spout-texture.txt");
            if (st.exists()) {
                st.delete();
            }
        }
    }

    public DynmapPlugin() {
        plugin = this;
        try {
            Class<?> c = Class.forName("cpw.mods.fml.common.Loader");
            ismodloaded = c.getMethod("isModLoaded", String.class);
            instance = c.getMethod("instance");
            getindexedmodlist = c.getMethod("getIndexedModList");
            c = Class.forName("cpw.mods.fml.common.ModContainer");
            getversion = c.getMethod("getVersion");
        } catch (NoSuchMethodException nsmx) {
        } catch (ClassNotFoundException e) {
        }
    }

    /**
     * Server access abstraction class
     */
    public class BukkitServer extends DynmapServerInterface {

        @Override
        public int isSignAt(String wname, int x, int y, int z) {
            World w = getServer().getWorld(wname);
            if ((w != null) && w.isChunkLoaded(x >> 4, z >> 4)) {
                Block b = w.getBlockAt(x, y, z);
                BlockState s = b.getState();

                if (s instanceof Sign) {
                    return 1;
                } else {
                    return 0;
                }
            }
            return -1;
        }

        @Override
        public void scheduleServerTask(Runnable run, long delay) {
            getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, run, delay);
        }

        @Override
        public void reload() {
            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.disablePlugin(DynmapPlugin.this);
            pluginManager.enablePlugin(DynmapPlugin.this);
        }

        @Override
        public <T> Future<T> callSyncMethod(Callable<T> task) {
            if (DynmapPlugin.this.isEnabled()) {
                return getServer().getScheduler().callSyncMethod(DynmapPlugin.this, task);
            } else {
                return null;
            }
        }

        @Override
        public String getServerName() {
            return getServer().getMotd();
        }

        @Override
        public boolean isServerThread() {
            return Bukkit.getServer().isPrimaryThread();
        }

        @Override
        public String stripChatColor(String s) {
            return ChatColor.stripColor(s);
        }
        //  private final Set<EventType> registered = new HashSet<>();

        /*  @Override
        public boolean requestEventNotification(EventType type) {
            if (registered.contains(type)) {
                return true;
            }
            switch (type) {
                case WORLD_LOAD, WORLD_UNLOAD -> {
                }
                case WORLD_SPAWN_CHANGE -> pm.registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void onSpawnChange(SpawnChangeEvent evt) {
                        BukkitWorld w = getWorld(evt.getWorld());
                        core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                    }
                }, DynmapPlugin.this);
                case PLAYER_JOIN, PLAYER_QUIT -> {
                }
                case PLAYER_BED_LEAVE -> pm.registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                        Player p = (evt.getPlayer());
                        core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                    }
                }, DynmapPlugin.this);
                case PLAYER_CHAT -> pm.registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void onPlayerChat(AsyncPlayerChatEvent evt) {
                        final Player p = evt.getPlayer();
                        final String msg = evt.getMessage();
                        getServer().getScheduler().scheduleSyncDelayedTask(DynmapPlugin.this, new Runnable() {
                            public void run() {
                                // Player dp = null;
                                //  if (p != null) {
                                //     dp = new BukkitPlayer(p);
                                // }
                                core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, p, msg);
                            }
                        });
                    }
                }, DynmapPlugin.this);
                case BLOCK_BREAK -> pm.registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void onBlockBreak(BlockBreakEvent evt) {
                        Block b = evt.getBlock();
                        Location l = b.getLocation();
                        core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().name(),
                                getWorld(l.getWorld()).getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                    }
                }, DynmapPlugin.this);

                default -> {
                    Log.severe("Unhandled event type: " + type);
                    return false;
                }
            }
            /* Already called for normal world activation/deactivation */
 /* Already handled *
            registered.add(type);
            return true;
        }*/
        @Override
        public boolean sendWebChatEvent(String source, String name, String msg) {
            DynmapWebChatEvent evt = new DynmapWebChatEvent(source, name, msg);
            getServer().getPluginManager().callEvent(evt);
            return ((evt.isCancelled() == false) && (evt.isProcessed() == false));
        }

        @Override
        public void broadcastMessage(String msg) {
            getServer().broadcastMessage(msg);
        }

        @Override
        public String[] getBiomeIDs() {
            BiomeMap[] b = BiomeMap.values();
            String[] bname = new String[b.length];
            for (int i = 0; i < bname.length; i++) {
                bname[i] = b[i].toString();
            }
            return bname;
        }

        @Override
        public double getCacheHitRate() {
            return gencache.getHitRate();//helper.useGenericCache() ? helper.gencache.getHitRate() : SnapshotCache.sscache.getHitRate();
        }

        @Override
        public void resetCacheStats() {
            gencache.resetStats();
        }

        @Override
        public DynmapWorld getWorldByName(String wname) {
            return bukkitWorld(wname);
        }

        /**
         * Render processor helper - used by code running on render threads to
         * request chunk snapshot cache from server/sync thread
         */
        @Override
        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
                boolean blockdata, boolean highesty, boolean biome, boolean rawbiome) {
            MapChunkCache c = w.getChunkCache(chunks);
            if (c == null) {
                /* Can fail if not currently loaded */
                return null;
            }
            if (w.visibility_limits != null) {
                for (VisibilityLimit limit : w.visibility_limits) {
                    c.setVisibleRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if (w.hidden_limits != null) {
                for (VisibilityLimit limit : w.hidden_limits) {
                    c.setHiddenRange(limit);
                }
                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }
            if (c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false) {
                Log.severe("CraftBukkit build does not support biome APIs");
            }
            if (chunks.size() == 0) {
                /* No chunks to get? */
                c.loadChunks(0);
                return c;
            }

            final MapChunkCache cc = c;

            while (!cc.isDoneLoading()) {
                if (1 == 2) { //helper.isUnsafeAsync() = false
                    Future<Boolean> f = core.getServer().callSyncMethod(new Callable<>() {
                        public Boolean call() throws Exception {
                            boolean exhausted = true;

                            if (prev_tick != cur_tick) {
                                prev_tick = cur_tick;
                                cur_tick_starttime = System.nanoTime();
                            }
                            if (chunks_in_cur_tick > 0) {
                                boolean done = false;
                                while (!done) {
                                    int cnt = chunks_in_cur_tick;
                                    if (cnt > 5) {
                                        cnt = 5;
                                    }
                                    chunks_in_cur_tick -= cc.loadChunks(cnt);
                                    exhausted = (chunks_in_cur_tick == 0) || ((System.nanoTime() - cur_tick_starttime) > perTickLimit);
                                    done = exhausted || cc.isDoneLoading();
                                }
                            }
                            return exhausted;
                        }
                    });
                    if (f == null) {
                        return null;
                    }
                    Boolean delay;
                    try {
                        delay = f.get();
                    } catch (CancellationException cx) {
                        return null;
                    } catch (InterruptedException cx) {
                        return null;
                    } catch (ExecutionException ex) {
                        Log.severe("Exception while fetching chunks: ", ex.getCause());
                        return null;
                    } catch (Exception ix) {
                        Log.severe(ix);
                        return null;
                    }

                    if ((delay != null) && delay.booleanValue()) {
                        try {
                            Thread.sleep(25);
                        } catch (InterruptedException ix) {
                        }
                    }
                } else {
                    if (prev_tick != cur_tick) {
                        prev_tick = cur_tick;
                        cur_tick_starttime = System.nanoTime();
                    }
                    if (cc instanceof GenericMapChunkCache) {
                        ((GenericMapChunkCache) cc).loadChunksAsync();
                    } else {
                        cc.loadChunks(Integer.MAX_VALUE);
                    }
                }
            }
            /* If cancelled due to world unload return nothing */
            if (w.isLoaded() == false) {
                return null;
            }
            return c;
        }

        @Override
        public int getMaxPlayers() {
            return getServer().getMaxPlayers();
        }

        @Override
        public int getCurrentPlayers() {
            return Bukkit.getOnlinePlayers().size();
        }

        @Override
        public boolean isModLoaded(String name) {
            if (ismodloaded != null) {
                try {
                    Object rslt = ismodloaded.invoke(null, name);
                    if (rslt instanceof Boolean) {
                        if (((Boolean) rslt).booleanValue()) {
                            modsused.add(name);
                            return true;
                        }
                    }
                } catch (IllegalArgumentException iax) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
            }
            return false;
        }

        @Override
        public String getModVersion(String name) {
            if ((instance != null) && (getindexedmodlist != null) && (getversion != null)) {
                try {
                    Object inst = instance.invoke(null);
                    Map<?, ?> modmap = (Map<?, ?>) getindexedmodlist.invoke(inst);
                    Object mod = modmap.get(name);
                    if (mod != null) {
                        return (String) getversion.invoke(mod);
                    }
                } catch (IllegalArgumentException iax) {
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
            }
            return null;
        }

        @Override
        public double getServerTPS() {
            return tps;
        }

        @Override
        public String getServerIP() {
            return Bukkit.getServer().getIp();
        }
    }

    @Override
    public void onLoad() {
        Log.setLogger(this.getLogger(), "");
        pm = this.getServer().getPluginManager();
        ModSupportImpl.init();
    }

    private void processTick() {
        long now = System.nanoTime();
        long elapsed = now - lasttick;
        lasttick = now;
        avgticklen = ((avgticklen * 99) / 100) + (elapsed / 100);
        tps = (double) 1E9 / (double) avgticklen;
        if (mapManager != null) {
            chunks_in_cur_tick = mapManager.getMaxChunkLoadsPerTick();
        }
        cur_tick++;

        // Tick core
        if (core != null) {
            core.serverTick(tps);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (core != null) {
            return Cmd.processCommand(sender, cmd.getName(), commandLabel, args);
        } else {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (core != null) {
            return Cmd.getTabCompletions(sender, cmd.getName(), args);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public final MarkerAPI getMarkerAPI() {
        return core.getMarkerAPI();
    }

    @Override
    public final boolean markerAPIInitialized() {
        return core.markerAPIInitialized();
    }

    @Override
    public final boolean sendBroadcastToWeb(String sender, String msg) {
        return core.sendBroadcastToWeb(sender, msg);
    }

    @Override
    public final int triggerRenderOfVolume(String wid, int minx, int miny, int minz,
            int maxx, int maxy, int maxz) {
        invalidateSnapshot(wid, minx, miny, minz, maxx, maxy, maxz);
        return core.triggerRenderOfVolume(wid, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public final int triggerRenderOfBlock(String wid, int x, int y, int z) {
        invalidateSnapshot(wid, x, y, z);
        return core.triggerRenderOfBlock(wid, x, y, z);
    }

    @Override
    public final void setPauseFullRadiusRenders(boolean dopause) {
        core.setPauseFullRadiusRenders(dopause);
    }

    @Override
    public final boolean getPauseFullRadiusRenders() {
        return core.getPauseFullRadiusRenders();
    }

    @Override
    public final void setPauseUpdateRenders(boolean dopause) {
        core.setPauseUpdateRenders(dopause);
    }

    @Override
    public final boolean getPauseUpdateRenders() {
        return core.getPauseUpdateRenders();
    }

    @Override
    public final void postPlayerMessageToWeb(String playerid, String playerdisplay,
            String message) {
        core.postPlayerMessageToWeb(playerid, playerdisplay, message);
    }

    @Override
    public final void postPlayerJoinQuitToWeb(String playerid, String playerdisplay,
            boolean isjoin) {
        core.postPlayerJoinQuitToWeb(playerid, playerdisplay, isjoin);
    }

    @Override
    public final String getDynmapCoreVersion() {
        return core.getDynmapCoreVersion();
    }

    @Override
    public final int triggerRenderOfVolume(Location l0, Location l1) {
        int x0 = l0.getBlockX(), y0 = l0.getBlockY(), z0 = l0.getBlockZ();
        int x1 = l1.getBlockX(), y1 = l1.getBlockY(), z1 = l1.getBlockZ();

        return core.triggerRenderOfVolume(bukkitWorld(l0.getWorld().getName()).getName(), Math.min(x0, x1), Math.min(y0, y1),
                Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1));
    }

    @Override
    public final void postPlayerMessageToWeb(Player player, String message) {
        core.postPlayerMessageToWeb(player.getName(), player.getDisplayName(), message);
    }

    @Override
    public void postPlayerJoinQuitToWeb(Player player, boolean isjoin) {
        core.postPlayerJoinQuitToWeb(player.getName(), player.getDisplayName(), isjoin);
    }

    public static void invalidateSnapshot(String wn, int x, int y, int z) {
        gencache.invalidateSnapshot(wn, x, y, z);
    }

    public static void invalidateSnapshot(String wname, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        gencache.invalidateSnapshot(wname, minx, miny, minz, maxx, maxy, maxz);
    }

    @Override
    public boolean setDisableChatToWebProcessing(boolean disable) {
        return core.setDisableChatToWebProcessing(disable);
    }

    @Override
    public boolean testIfPlayerInfoProtected() {
        return core.testIfPlayerInfoProtected();
    }

    @Override
    public void processSignChange(String material, String world, int x, int y, int z,
            String[] lines, String playerid) {
        core.processSignChange(material, world, x, y, z, lines, playerid);
    }

    public static boolean migrateChunks() {
        if ((plugin != null) && (plugin.core != null)) {
            return plugin.core.migrateChunks();
        }
        return false;
    }
}
