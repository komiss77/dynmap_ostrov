package org.dynmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.bukkit.DynmapPlugin;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.DynmapBufferedImage;
import org.dynmap.utils.ImageIOManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.RectangleVisibilityLimit;
import org.dynmap.utils.RoundVisibilityLimit;
import org.dynmap.utils.TileFlags;
import org.dynmap.utils.VisibilityLimit;
import org.dynmap.utils.Polygon;
import ru.komiss77.hook.MapChunkCacheNms;


public final class DynmapWorld {

    private final String name;
    private WeakReference<World> world;
    private final int hashcode;

    public List<MapType> maps = new ArrayList<>();
    public List<MapTypeState> mapstate = new ArrayList<>();
    public UpdateQueue updates = new UpdateQueue();
    public DynmapLocation center;
    public List<DynmapLocation> seedloc;
    /* All seed location - both direct and based on visibility limits */
    private List<DynmapLocation> seedloccfg;
    /* Configured full render seeds only */
    public List<VisibilityLimit> visibility_limits;
    public List<VisibilityLimit> hidden_limits;
    public MapChunkCache.HiddenChunkStyle hiddenchunkstyle;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean showborder;
    private int extrazoomoutlevels;
    /* Number of additional zoom out levels to generate */
    private boolean cancelled;
    //private final String raw_wname;
    private String title;
    public int tileupdatedelay;
    private boolean is_enabled;
    boolean is_protected;
    /* If true, user needs 'dynmap.world.<worldid>' privilege to see world */
    protected int[] brightnessTable = new int[16];  // 0-256 scaled brightness table

    private MapStorage storage; // Storage handler for this world's maps

    /* World height data */
    public int worldheight;	// really maxY+1
    public int minY;
    public int sealevel;
    private World.Environment env;
    private boolean skylight;
    private DynmapLocation spawnloc = new DynmapLocation();


    public DynmapWorld(final World w) {
        world = new WeakReference<>(w);
        this.name = w.getName();//normalizeWorldName(wname);
        this.hashcode = name.hashCode();
        this.title = w.getName();
        this.worldheight = w.getMaxHeight();
        this.minY = w.getMinHeight();
        this.sealevel = w.getSeaLevel();
        /* Generate default brightness table for surface world */
        env = w.getEnvironment();
        skylight = (env == World.Environment.NORMAL);
        // Generate non-default environment lighting table
        switch (env) {
            case NETHER -> {
                float f = 0.1F;
                for (int i = 0; i <= 15; ++i) {
                    float f1 = 1.0F - (float) i / 15.0F;
                    setBrightnessTableEntry(i, (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - f) + f);
                }
            }
            default -> {
                for (int i = 0; i <= 15; ++i) {
                    float f1 = 1.0F - (float) i / 15.0F;
                    setBrightnessTableEntry(i, ((1.0F - f1) / (f1 * 3.0F + 1.0F)));
                }

            }
        }
    }

    public World world () {
        return world.get();
    }
    
    protected void setBrightnessTableEntry(int level, float value) {
        if ((level < 0) || (level > 15)) {
            return;
        }
        this.brightnessTable[level] = (int) (256.0 * value);
        if (this.brightnessTable[level] > 256) {
            this.brightnessTable[level] = 256;
        }
        if (this.brightnessTable[level] < 0) {
            this.brightnessTable[level] = 0;
        }
    }


    public int[] getBrightnessTable() {
        return brightnessTable;
    }

    public void setExtraZoomOutLevels(int lvl) {
        extrazoomoutlevels = lvl;
    }

    public int getExtraZoomOutLevels() {
        return extrazoomoutlevels;
    }

    public void enqueueZoomOutUpdate(MapStorageTile tile) {
        MapTypeState mts = getMapState(tile.map);
        if (mts != null) {
            mts.setZoomOutInv(tile.x, tile.y, tile.zoom);
        }
    }

    public void freshenZoomOutFiles() {
        MapTypeState.ZoomOutCoord c = new MapTypeState.ZoomOutCoord();
        for (MapTypeState mts : mapstate) {
            if (cancelled) {
                return;
            }
            MapType mt = mts.type;
            MapType.ImageVariant var[] = mt.getVariants();
            mts.startZoomOutIter(); // Start iterator
            while (mts.nextZoomOutInv(c)) {
                if (cancelled) {
                    return;
                }
                for (int varIdx = 0; varIdx < var.length; varIdx++) {
                    MapStorageTile tile = storage.getTile(this, mt, c.x, c.y, c.zoomlevel, var[varIdx]);
                    processZoomFile(mts, tile, varIdx == 0);
                }
            }
        }
    }

    public void cancelZoomOutFreshen() {
        cancelled = true;
    }

    public void activateZoomOutFreshen() {
        cancelled = false;
    }

    private static final int[] stepseq = {3, 1, 2, 0};

    private void processZoomFile(MapTypeState mts, MapStorageTile tile, boolean firstVariant) {
        long mostRecentTimestamp = 0;
        int step = 1 << tile.zoom;
        MapStorageTile ztile = tile.getZoomOutTile();
        int width = mts.tileSize, height = mts.tileSize;
        BufferedImage zIm;
        DynmapBufferedImage kzIm;
        boolean blank = true;
        int[] argb = new int[width * height];
        int tx = ztile.x;
        int ty = ztile.y;
        ty = ty - step;
        /* Adjust for negative step */

 /* create image buffer */
        kzIm = DynmapBufferedImage.allocateBufferedImage(width, height);
        zIm = kzIm.buf_img;
        for (int i = 0; i < 4; i++) {
            boolean doblit = true;
            int tx1 = tx + step * (1 & stepseq[i]);
            int ty1 = ty + step * (stepseq[i] >> 1);
            MapStorageTile tile1 = storage.getTile(this, tile.map, tx1, ty1, tile.zoom, tile.var);
            if (tile1 == null) {
                continue;
            }
            tile1.getReadLock();
            if (firstVariant) { // We're handling this one - but only clear on first variant (so that we don't miss updates later)
                mts.clearZoomOutInv(tile1.x, tile1.y, tile1.zoom);
            }
            try {
                MapStorageTile.TileRead tr = tile1.read();
                if (tr != null) {
                    BufferedImage im = null;
                    try {
                        im = ImageIOManager.imageIODecode(tr);
                        // Only consider the timestamp when the tile exists and isn't broken
                        mostRecentTimestamp = Math.max(mostRecentTimestamp, tr.lastModified);
                    } catch (IOException iox) {
                        // Broken file - zap it
                        tile1.delete();
                    }
                    if ((im != null) && (im.getWidth() >= width) && (im.getHeight() >= height)) {
                        int iwidth = im.getWidth();
                        int iheight = im.getHeight();
                        if (iwidth > iheight) {
                            iwidth = iheight;
                        }

                        if ((iwidth == width) && (iheight == height)) {
                            im.getRGB(0, 0, width, height, argb, 0, width);
                            /* Read data */
                            im.flush();
                            /* Do binlinear scale to width/2 x height/2 */
                            int off;
                            for (int y = 0; y < height; y += 2) {
                                off = y * width;
                                for (int x = 0; x < width; x += 2, off += 2) {
                                    int p0 = argb[off];
                                    int p1 = argb[off + 1];
                                    int p2 = argb[off + width];
                                    int p3 = argb[off + width + 1];
                                    int alpha = ((p0 >> 24) & 0xFF) + ((p1 >> 24) & 0xFF) + ((p2 >> 24) & 0xFF) + ((p3 >> 24) & 0xFF);
                                    int red = ((p0 >> 16) & 0xFF) + ((p1 >> 16) & 0xFF) + ((p2 >> 16) & 0xFF) + ((p3 >> 16) & 0xFF);
                                    int green = ((p0 >> 8) & 0xFF) + ((p1 >> 8) & 0xFF) + ((p2 >> 8) & 0xFF) + ((p3 >> 8) & 0xFF);
                                    int blue = (p0 & 0xFF) + (p1 & 0xFF) + (p2 & 0xFF) + (p3 & 0xFF);
                                    argb[off >> 1] = (((alpha >> 2) & 0xFF) << 24) | (((red >> 2) & 0xFF) << 16) | (((green >> 2) & 0xFF) << 8) | ((blue >> 2) & 0xFF);
                                }
                            }
                        } else {
                            int[] buf = new int[iwidth * iwidth];
                            im.getRGB(0, 0, iwidth, iwidth, buf, 0, iwidth);
                            im.flush();
                            TexturePack.scaleTerrainPNGSubImage(iwidth, width / 2, buf, argb);
                            /* blit scaled rendered tile onto zoom-out tile */
                            zIm.setRGB(((i >> 1) != 0) ? 0 : width / 2, (i & 1) * height / 2, width / 2, height / 2, argb, 0, width / 2);
                            doblit = false;
                        }
                        blank = false;
                    } else {
                        if (tile1.map.getImageFormat().getEncoding() == ImageEncoding.JPG) {
                            Arrays.fill(argb, tile1.map.getBackgroundARGB(tile1.var));
                        } else {
                            Arrays.fill(argb, 0);
                        }
                        tile1.delete();    // Delete unusable tile
                    }
                } else {
                    if (tile1.map.getImageFormat().getEncoding() == ImageEncoding.JPG) {
                        Arrays.fill(argb, tile1.map.getBackgroundARGB(tile1.var));
                    } else {
                        Arrays.fill(argb, 0);
                    }
                }
            } finally {
                tile1.releaseReadLock();
            }
            /* blit scaled rendered tile onto zoom-out tile */
            if (doblit) {
                zIm.setRGB(((i >> 1) != 0) ? 0 : width / 2, (i & 1) * height / 2, width / 2, height / 2, argb, 0, width);
            }
        }
        ztile.getWriteLock();
        try {
            MapManager mm = MapManager.mapman;
            if (mm == null) {
                return;
            }
            long crc = MapStorage.calculateImageHashCode(kzIm.argb_buf, 0, kzIm.argb_buf.length);
            /* Get hash of tile */
            if (blank) {
                if (ztile.exists()) {
                    ztile.delete();
                    MapManager.mapman.pushUpdate(this, new Client.Tile(ztile.getURI()));
                    enqueueZoomOutUpdate(ztile);
                }
            } else /* if (!ztile.matchesHashCode(crc)) */ {
                ztile.write(crc, zIm, (mostRecentTimestamp == 0) ? System.currentTimeMillis() : mostRecentTimestamp);
                MapManager.mapman.pushUpdate(this, new Client.Tile(ztile.getURI()));
                enqueueZoomOutUpdate(ztile);
            }
        } finally {
            ztile.releaseWriteLock();
            DynmapBufferedImage.freeBufferedImage(kzIm);
        }
    }

    public String getName() {
        return name;
    }

    public boolean isNether() {
        return env == World.Environment.NETHER;
    }

    public DynmapLocation getSpawnLocation() {
        if (world.get() != null) {
            Location sloc = world.get().getSpawnLocation();
            spawnloc.x = sloc.getBlockX();
            spawnloc.y = sloc.getBlockY();
            spawnloc.z = sloc.getBlockZ();
            spawnloc.world = normalizeWorldName(sloc.getWorld().getName());
        }
        return spawnloc;
    }

    public long getTime() {
        if (world.get() != null) {
            return world.get().getTime();
        } else {
            return -1;
        }
    }

    public boolean hasStorm() {
        if (world.get() != null) {
            return world.get().hasStorm();
        } else {
            return false;
        }
    }

    public boolean isThundering() {
        if (world.get() != null) {
            return world.get().isThundering();
        } else {
            return false;
        }
    }

    public boolean isLoaded() {
        return world.get() != null && Bukkit.getWorld(name) != null;
    }

    public void setWorldUnloaded() {
        getSpawnLocation();
        /* Remember spawn location before unload */
        world = new WeakReference<>(null);
    }

    public int getLightLevel(int x, int y, int z) {
        if (world.get() != null) {
            if ((y >= minY) && (y < worldheight)) {
                return world.get().getBlockAt(x, y, z).getLightLevel();
            }
            return 0;
        } else {
            return -1;
        }
    }

    public int getHighestBlockYAt(int x, int z) {
        if (world.get() != null) {
            return world.get().getHighestBlockYAt(x, z);
        } else {
            return -1;
        }
    }

    public boolean canGetSkyLightLevel() {
        return skylight && (world.get() != null);
    }

    public int getSkyLightLevel(int x, int y, int z) {
        if (world.get() != null) {
            if ((y >= minY) && (y < worldheight)) {
                return world.get().getBlockAt(x, y, z).getLightFromSky();
            } else {
                return 15;
            }
        } else {
            return -1;
        }
    }

    public String getEnvironment() {
        return env.name().toLowerCase();
    }

    public MapChunkCache getChunkCache(List<DynmapChunk> chunks) {
        if (isLoaded()) {
            //return DynmapPlugin.helper.getChunkCache(this, chunks);
            //public MapChunkCache getChunkCache(DynmapWorld dw, List<DynmapChunk> chunks) {
            MapChunkCacheNms cache = new MapChunkCacheNms(DynmapPlugin.gencache);
            cache.setChunks(this, chunks);
            return cache;
        } else {
            return null;
        }
    }

    public int getChunkMap(TileFlags map) {
        map.clear();
        if (world.get() == null) {
            return -1;
        }
        int cnt = 0;
        // Mark loaded chunks
        for (Chunk c : world.get().getLoadedChunks()) {
            map.setFlag(c.getX(), c.getZ(), true);
            cnt++;
        }
        File f = world.get().getWorldFolder();
        File regiondir = new File(f, "region");
        File[] lst = regiondir.listFiles();
        if (lst != null) {
            byte[] hdr = new byte[4096];
            for (File rf : lst) {
                if (!rf.getName().endsWith(".mca")) {
                    continue;
                }
                String[] parts = rf.getName().split("\\.");
                if ((!parts[0].equals("r")) && (parts.length != 4)) {
                    continue;
                }

                RandomAccessFile rfile = null;
                int x = 0, z = 0;
                try {
                    x = Integer.parseInt(parts[1]);
                    z = Integer.parseInt(parts[2]);
                    rfile = new RandomAccessFile(rf, "r");
                    rfile.read(hdr, 0, hdr.length);
                } catch (IOException iox) {
                    Arrays.fill(hdr, (byte) 0);
                } catch (NumberFormatException nfx) {
                    Arrays.fill(hdr, (byte) 0);
                } finally {
                    if (rfile != null) {
                        try {
                            rfile.close();
                        } catch (IOException iox) {
                        }
                    }
                }
                for (int i = 0; i < 1024; i++) {
                    int v = hdr[4 * i] | hdr[4 * i + 1] | hdr[4 * i + 2] | hdr[4 * i + 3];
                    if (v == 0) {
                        continue;
                    }
                    int xx = (x << 5) | (i & 0x1F);
                    int zz = (z << 5) | ((i >> 5) & 0x1F);
                    if (!map.setFlag(xx, zz, true)) {
                        cnt++;
                    }
                }
            }
        }
        return cnt;
    }

    private Polygon lastBorder;

    public Polygon getWorldBorder() {
        if (world.get() != null) {
            //lastBorder = DynmapPlugin.helper.getWorldBorder(world.get());
            WorldBorder wb = world.get().getWorldBorder();
            Location c = wb.getCenter();
            double size = wb.getSize();
            if ((size > 1) && (size < 1E7)) {
                size = size / 2;
                lastBorder = new Polygon();
                lastBorder.addVertex(c.getX() - size, c.getZ() - size);
                lastBorder.addVertex(c.getX() + size, c.getZ() - size);
                lastBorder.addVertex(c.getX() + size, c.getZ() + size);
                lastBorder.addVertex(c.getX() - size, c.getZ() + size);
            }
        }
        return lastBorder;
    }

    public String getTitle() {
        return title;
    }

    public DynmapLocation getCenterLocation() {
        if (center != null) {
            return center;
        } else {
            return getSpawnLocation();
        }
    }

    /* Load world configuration from configuration node */
    public boolean loadConfiguration(DynmapCore core, ConfigurationNode worldconfig) {
        is_enabled = worldconfig.getBoolean("enabled", false);
        if (!is_enabled) {
            return false;
        }
        title = worldconfig.getString("title", title);
        ConfigurationNode ctr = worldconfig.getNode("center");
        int mid_y = (worldheight + minY) / 2;
        if (ctr != null) {
            center = new DynmapLocation(name, ctr.getDouble("x", 0.0), ctr.getDouble("y", mid_y), ctr.getDouble("z", 0));
        } else {
            center = null;
        }
        List<ConfigurationNode> loclist = worldconfig.getNodes("fullrenderlocations");
        seedloc = new ArrayList<>();
        seedloccfg = new ArrayList<>();
        servertime = (int) (getTime() % 24000);
        sendposition = worldconfig.getBoolean("sendposition", true);
        sendhealth = worldconfig.getBoolean("sendhealth", true);
        showborder = worldconfig.getBoolean("showborder", true);
        is_protected = worldconfig.getBoolean("protected", false);
        setExtraZoomOutLevels(worldconfig.getInteger("extrazoomout", 0));
        setTileUpdateDelay(worldconfig.getInteger("tileupdatedelay", -1));
        storage = core.getDefaultMapStorage();
        if (loclist != null) {
            for (ConfigurationNode loc : loclist) {
                DynmapLocation lx = new DynmapLocation(name, loc.getDouble("x", 0), loc.getDouble("y", mid_y), loc.getDouble("z", 0));
                seedloc.add(lx);
                /* Add to both combined and configured seed list */
                seedloccfg.add(lx);
            }
        }
        /* Build maps */
        maps.clear();
        Log.verboseinfo("Loading maps of world '" + name + "'...");
        for (MapType map : worldconfig.<MapType>createInstances("maps", new Class<?>[]{DynmapCore.class}, new Object[]{core})) {
            if (map.getName() != null) {
                maps.add(map);
            }
        }
        /* Rebuild map state list - match on indexes */
        mapstate.clear();
        for (MapType map : maps) {
            MapTypeState ms = new MapTypeState(this, map);
            ms.setInvalidatePeriod(map.getTileUpdateDelay(this));
            mapstate.add(ms);
        }
        Log.info("Loaded " + maps.size() + " maps of world '" + name + "'.");
        /* Load visibility limits, if any are defined */
        List<ConfigurationNode> vislimits = worldconfig.getNodes("visibilitylimits");
        if (vislimits != null) {
            visibility_limits = new ArrayList<>();
            for (ConfigurationNode vis : vislimits) {
                VisibilityLimit lim;
                if (vis.containsKey("r")) {
                    /* It is round visibility limit */
                    int x_center = vis.getInteger("x", 0);
                    int z_center = vis.getInteger("z", 0);
                    int radius = vis.getInteger("r", 0);
                    lim = new RoundVisibilityLimit(x_center, z_center, radius);
                } else {
                    /* Rectangle visibility limit */
                    int x0 = vis.getInteger("x0", 0);
                    int x1 = vis.getInteger("x1", 0);
                    int z0 = vis.getInteger("z0", 0);
                    int z1 = vis.getInteger("z1", 0);
                    lim = new RectangleVisibilityLimit(x0, z0, x1, z1);
                }
                visibility_limits.add(lim);
                /* Also, add a seed location for the middle of each visible area */
                seedloc.add(new DynmapLocation(name, lim.xCenter(), 64, lim.zCenter()));
            }
        }
        /* Load hidden limits, if any are defined */
        List<ConfigurationNode> hidelimits = worldconfig.getNodes("hiddenlimits");
        if (hidelimits != null) {
            hidden_limits = new ArrayList<>();
            for (ConfigurationNode vis : hidelimits) {
                VisibilityLimit lim;
                if (vis.containsKey("r")) {
                    /* It is round visibility limit */
                    int x_center = vis.getInteger("x", 0);
                    int z_center = vis.getInteger("z", 0);
                    int radius = vis.getInteger("r", 0);
                    lim = new RoundVisibilityLimit(x_center, z_center, radius);
                } else {
                    /* Rectangle visibility limit */
                    int x0 = vis.getInteger("x0", 0);
                    int x1 = vis.getInteger("x1", 0);
                    int z0 = vis.getInteger("z0", 0);
                    int z1 = vis.getInteger("z1", 0);
                    lim = new RectangleVisibilityLimit(x0, z0, x1, z1);
                }
                hidden_limits.add(lim);
            }
        }
        String s = worldconfig.getString("hidestyle", "stone");
        this.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.fromValue(s);
        if (this.hiddenchunkstyle == null) {
            this.hiddenchunkstyle = MapChunkCache.HiddenChunkStyle.FILL_STONE_PLAIN;
        }

        return true;
    }

    /*
     * Make configuration node for saving world
     */
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode node = new ConfigurationNode();
        /* Add name and title */
        node.put("name", name);
        node.put("title", getTitle());
        node.put("enabled", is_enabled);
        node.put("protected", is_protected);
        node.put("showborder", showborder);
        if (tileupdatedelay > 0) {
            node.put("tileupdatedelay", tileupdatedelay);
        }
        /* Add center */
        if (center != null) {
            ConfigurationNode c = new ConfigurationNode();
            c.put("x", center.x);
            c.put("y", center.y);
            c.put("z", center.z);
            node.put("center", c.entries);
        }
        /* Add seed locations, if any */
        if (!seedloccfg.isEmpty()) {
            ArrayList<Map<String, Object>> locs = new ArrayList<>();
            for (int i = 0; i < seedloccfg.size(); i++) {
                DynmapLocation dl = seedloccfg.get(i);
                ConfigurationNode ll = new ConfigurationNode();
                ll.put("x", dl.x);
                ll.put("y", dl.y);
                ll.put("z", dl.z);
                locs.add(ll.entries);
            }
            node.put("fullrenderlocations", locs);
        }
        /* Add flags */
        node.put("sendposition", sendposition);
        node.put("sendhealth", sendhealth);
        node.put("extrazoomout", extrazoomoutlevels);
        /* Save visibility limits, if defined */
        if (visibility_limits != null) {
            ArrayList<Map<String, Object>> lims = new ArrayList<>();
            for (int i = 0; i < visibility_limits.size(); i++) {
                VisibilityLimit lim = visibility_limits.get(i);
                LinkedHashMap<String, Object> lv = new LinkedHashMap<>();
                if (lim instanceof RectangleVisibilityLimit rect_lim) {
                    lv.put("x0", rect_lim.x_min);
                    lv.put("z0", rect_lim.z_min);
                    lv.put("x1", rect_lim.x_max);
                    lv.put("z1", rect_lim.z_max);
                } else {
                    RoundVisibilityLimit round_lim = (RoundVisibilityLimit) lim;
                    lv.put("x", round_lim.x_center);
                    lv.put("z", round_lim.z_center);
                    lv.put("r", round_lim.radius);
                }
                lims.add(lv);
            }
            node.put("visibilitylimits", lims);
        }
        /* Save hidden limits, if defined */
        if (hidden_limits != null) {
            ArrayList<Map<String, Object>> lims = new ArrayList<>();
            for (int i = 0; i < hidden_limits.size(); i++) {
                VisibilityLimit lim = hidden_limits.get(i);
                LinkedHashMap<String, Object> lv = new LinkedHashMap<>();
                if (lim instanceof RectangleVisibilityLimit) {
                    RectangleVisibilityLimit rect_lim = (RectangleVisibilityLimit) lim;
                    lv.put("x0", rect_lim.x_min);
                    lv.put("z0", rect_lim.z_min);
                    lv.put("x1", rect_lim.x_max);
                    lv.put("z1", rect_lim.z_max);
                } else {
                    RoundVisibilityLimit round_lim = (RoundVisibilityLimit) lim;
                    lv.put("x", round_lim.x_center);
                    lv.put("z", round_lim.z_center);
                    lv.put("r", round_lim.radius);
                }
                lims.add(lv);
            }
            node.put("hiddenlimits", lims);
        }
        /* Handle hide style */
        node.put("hidestyle", hiddenchunkstyle.getValue());
        /* Handle map settings */
        ArrayList<Map<String, Object>> mapinfo = new ArrayList<>();
        for (MapType mt : maps) {
            ConfigurationNode mnode = mt.saveConfiguration();
            mapinfo.add(mnode);
        }
        node.put("maps", mapinfo);

        return node;
    }

    public boolean isEnabled() {
        return is_enabled;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public static String normalizeWorldName(String n) {
        return (n != null) ? n.replace('/', '-').replace('[', '_').replace(']', '_') : null;
    }

    //public String getRawName() {
    //    return raw_wname;
    //}
    public boolean isProtected() {
        return is_protected;
    }

    public int getTileUpdateDelay() {
        if (tileupdatedelay > 0) {
            return tileupdatedelay;
        } else {
            return MapManager.mapman.getDefTileUpdateDelay();
        }
    }

    public void setTileUpdateDelay(int time_sec) {
        tileupdatedelay = time_sec;
    }

    public static void doInitialScan(boolean doscan) {
    }


    // Get map state for given map
    public MapTypeState getMapState(MapType m) {
        for (int i = 0; i < this.maps.size(); i++) {
            MapType mt = this.maps.get(i);
            if (mt == m) {
                return this.mapstate.get(i);
            }
        }
        return null;
    }

    public void purgeTree() {
        storage.purgeMapTiles(this, null);
    }

    public void purgeMap(MapType mt) {
        storage.purgeMapTiles(this, mt);
    }

    public MapStorage getMapStorage() {
        return storage;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    @Override
    public int hashCode() {
        return this.hashcode;
    }



}
