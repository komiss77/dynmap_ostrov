package org.dynmap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.dynmap.MapType.ImageEncoding;
import org.bukkit.entity.Player;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.debug.Debug;
import org.dynmap.debug.Debugger;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDBlockStateTextureMap;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.javax.servlet.Filter;
import org.dynmap.javax.servlet.http.HttpServlet;
import org.dynmap.jetty.server.Connector;
import org.dynmap.jetty.server.NetworkTrafficServerConnector;
import org.dynmap.jetty.server.Server;
import org.dynmap.jetty.server.handler.AllowSymLinkAliasChecker;
import org.dynmap.jetty.server.handler.ContextHandler;
import org.dynmap.jetty.server.handler.HandlerList;
import org.dynmap.jetty.server.session.DefaultSessionIdManager;
import org.dynmap.jetty.server.session.SessionHandler;
import org.dynmap.jetty.servlet.ServletHolder;
import org.dynmap.jetty.util.thread.ExecutorThreadPool;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.impl.MarkerAPIImpl;
import org.dynmap.modsupport.ModSupportImpl;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.servlet.*;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.FileTreeMapStorage;
import org.dynmap.storage.MySQLMapStorage;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.ImageIOManager;
import org.dynmap.web.BanIPFilter;
import org.dynmap.web.CustomHeaderFilter;
import org.dynmap.web.FileNameFilter;
import org.dynmap.web.FilterHandler;
import org.dynmap.web.HandlerRouter;
import org.yaml.snakeyaml.Yaml;

public class DynmapCore implements DynmapCommonAPI {

    public String getDynmapPluginPlatformVersion() {
        return platformVersion;
    }

    /**
     * Callbacks for core initialization - subclassed by platform plugins
     */
    public static abstract class EnableCoreCallbacks {
        public abstract void configurationLoaded();
    }
    
    private File jarfile;
    private DynmapServerInterface server;
    private String version;
    //private String platform = null;
    public String platformVersion = "";
    private Server webServer = null;
    private String webhostname = null;
    private int webport = 0;
    private HandlerRouter router = null;
    public static MapManager mapManager = null;
    // public PlayerList playerList;
    public ConfigurationNode configuration;
    public ConfigurationNode world_config;
    public ComponentManager componentManager = new ComponentManager();
    // public DynmapListenerManager listenerManager = new DynmapListenerManager(this);
    //public PlayerFaces playerfacemgr;
    public SkinUrlProvider skinUrlProvider;
    public Events events = new Events();
    public String deftemplatesuffix = "";

    boolean bettergrass = false;
    boolean smoothlighting = false;
    private boolean ctmsupport = false;
    private boolean customcolorssupport = false;
    private String def_image_format = "png";
    private HashSet<String> enabledTriggers = new HashSet<>();
    public boolean disable_chat_to_web = false;
    public WebAuthManager authmgr;
    public boolean player_info_protected;
    private boolean transparentLeaves = true;
    //private List<String> sortPermissionNodes;
    private int perTickLimit = 50;   // 50 ms
    private boolean dumpMissing = false;
    private static boolean migrate_chunks = false;
    public boolean isInternalWebServerDisabled = false;

    private static int config_hashcode;
    /* Used to signal need to reload web configuration (world changes, config update, etc) */
    private static int fullrenderplayerlimit;
    /* Number of online players that will cause fullrender processing to pause */
    private static int updateplayerlimit;
    /* Number of online players that will cause update processing to pause */
    public String publicURL;	// If set, public HRL for accessing dynmap (declared by administrator)
    private static boolean didfullpause;
    private static boolean didupdatepause;
    //private Map<String, LinkedList<String>> ids_by_ip = new HashMap<>();
    //private boolean persist_ids_by_ip = false;
    private int snapshotcachesize;
    private boolean snapshotsoftref;
    private List<String> biomenames = List.of();//new String[0];
    private Map<String, Integer> blockmap = null;
    private Map<String, Integer> itemmap = null;

    private boolean loginRequired;

    private String hackAttemptSub = "(IaM5uchA1337Haxr-Ban Me!)";
    // WEBP support
    private String cwebpPath;
    private String dwebpPath;
    private boolean did_cwebpPath_warn = false;
    private boolean did_dwebpPath_warn = false;

    /* Flag to let code know that we're doing reload - make sure we don't double-register event handlers */
    public boolean is_reload = false;
    public static boolean ignore_chunk_loads = false;
    /* Flag keep us from processing our own chunk loads */

    public MarkerAPIImpl markerapi;

    private File dataDirectory;
    private File tilesDirectory;
    private File exportDirectory;
    private File importDirectory;
    //private String plugin_ver;
    private MapStorage defaultStorage;

    // Read web path
    private String webpath;
    // And whether to disable web file update
    private boolean updatewebpathfiles = true;

    private String[] deftriggers = {};

    private Boolean webserverCompConfigWarn = false;
    private final String CompConfigWiki = "https://github.com/webbukkit/dynmap/wiki/Component-Configuration";

    private final String[] defaultTemplates = {"vlowres", "lowres", "medres", "hires", "low_boost_hi",
        "hi_boost_vhi", "hi_boost_xhi"};

    /* Constructor for core */
    public DynmapCore() {
    }

    public void setSkinUrlProvider(SkinUrlProvider skinUrlProvider) {
        this.skinUrlProvider = skinUrlProvider;
    }

    /* Cleanup method */
    public void cleanup() {
        server = null;
        markerapi = null;
    }

    public void restartMarkerSaveJob() {
        this.markerapi.scheduleWriteJob();
    }

    // Set plugin jar file
    public void setPluginJarFile(File f) {
        jarfile = f;
    }

    // Get plugin jar file
    public File getPluginJarFile() {
        return jarfile;
    }

    public void setDataFolder(File dir) {
        dataDirectory = dir;
    }

    public final File getDataFolder() {
        return dataDirectory;
    }

    public final File getTilesFolder() {
        return tilesDirectory;
    }

    public final File getExportFolder() {
        return exportDirectory;
    }

    public final File getImportFolder() {
        return importDirectory;
    }

    public void setServer(DynmapServerInterface srv) {
        server = srv;
    }

    public final DynmapServerInterface getServer() {
        return server;
    }

    public final void setBiomeNames(List<String> names) {
        biomenames = names;
    }

    public static final boolean migrateChunks() {
        return migrate_chunks;
    }

    public String getCWEBPPath() {
        if ((cwebpPath == null) && (!did_cwebpPath_warn)) {
            Log.severe("ERROR: trying to use WEBP without cwebp tool installed or cwebpPath set properly");
            did_cwebpPath_warn = true;
        }
        return cwebpPath;
    }

    public String getDWEBPPath() {
        if ((dwebpPath == null) && (!did_dwebpPath_warn)) {
            Log.severe("ERROR: trying to use WEBP without dwebp tool installed or dwebpPath set properly");
            did_dwebpPath_warn = true;
        }
        return dwebpPath;
    }

    /*public final String getBiomeName(int biomeid) {
        String n = null;
        if ((biomeid >= 0) && (biomeid < biomenames.length)) {
            n = biomenames[biomeid];
        }
        if(n == null) n = "biome" + biomeid;
        return n;
    }*/
    public final List<String> getBiomeNames() {
        return biomenames;
    }

    public final MapManager getMapManager() {
        return mapManager;
    }

    public final void setTriggerDefault(String[] triggers) {
        deftriggers = triggers;
    }

    public final void setLeafTransparency(boolean trans) {
        transparentLeaves = trans;
    }

    public final boolean getLeafTransparency() {
        return transparentLeaves;
    }

    /* Add/Replace branches in configuration tree with contribution from a separate file */
    private void mergeConfigurationBranch(ConfigurationNode cfgnode, String branch, boolean replace_existing, boolean islist) {
        Object srcbranch = cfgnode.getObject(branch);
        if (srcbranch == null) {
            return;
        }
        /* See if top branch is in configuration - if not, just add whole thing */
        Object destbranch = configuration.getObject(branch);
        if (destbranch == null) {
            /* Not found */
            configuration.put(branch, srcbranch);
            /* Add new tree to configuration */
            return;
        }
        /* If list, merge by "name" attribute */
        if (islist) {
            List<ConfigurationNode> dest = configuration.getNodes(branch);
            List<ConfigurationNode> src = cfgnode.getNodes(branch);
            /* Go through new records : see what to do with each */
            for (ConfigurationNode node : src) {
                String name = node.getString("name", null);
                if (name == null) {
                    continue;
                }
                /* Walk destination - see if match */
                boolean matched = false;
                for (ConfigurationNode dnode : dest) {
                    String dname = dnode.getString("name", null);
                    if (dname == null) {
                        continue;
                    }
                    if (dname.equals(name)) {
                        /* Match? */
                        if (replace_existing) {
                            dnode.clear();
                            dnode.putAll(node);
                        }
                        matched = true;
                        break;
                    }
                }
                /* If no match, add to end */
                if (!matched) {
                    dest.add(node);
                }
            }
            configuration.put(branch, dest);
        } /* If configuration node, merge by key */ else {
            ConfigurationNode src = cfgnode.getNode(branch);
            ConfigurationNode dest = configuration.getNode(branch);
            for (String key : src.keySet()) {
                /* Check each contribution */
                if (dest.containsKey(key)) {
                    /* Exists? */
                    if (replace_existing) {
                        /* If replacing, do so */
                        dest.put(key, src.getObject(key));
                    }
                } else {
                    /* Else, always add if not there */
                    dest.put(key, src.getObject(key));
                }
            }
        }
    }
    /* Table of default templates - all are resources in dynmap.jar unnder templates/, and go in templates directory when needed */
    private static final String[] stdtemplates = {"normal.txt", "nether.txt", "normal-lowres.txt",
        "nether-lowres.txt", "normal-hires.txt", "nether-hires.txt",
        "normal-vlowres.txt", "nether-vlowres.txt", "the_end.txt", "the_end-vlowres.txt",
        "the_end-lowres.txt", "the_end-hires.txt",
        "normal-low_boost_hi.txt", "normal-hi_boost_vhi.txt", "normal-hi_boost_xhi.txt",
        "nether-low_boost_hi.txt", "nether-hi_boost_vhi.txt", "nether-hi_boost_xhi.txt",
        "the_end-low_boost_hi.txt", "the_end-hi_boost_vhi.txt", "the_end-hi_boost_xhi.txt"
    };

    private static final String CUSTOM_PREFIX = "custom-";

    /* Load templates from template folder */
    private void loadTemplates() {
        File templatedir = new File(dataDirectory, "templates");
        templatedir.mkdirs();
        /* First, prime the templates directory with default standard templates, if needed */
        for (String stdtemplate : stdtemplates) {
            File f = new File(templatedir, stdtemplate);
            updateVersionUsingDefaultResource("/templates/" + stdtemplate, f);
        }
        /* Now process files */
        String[] templates = templatedir.list();
        /* Go through list - process all ones not starting with 'custom' first */
        for (String tname : templates) {
            /* If matches naming convention */
            if (tname.endsWith(".txt") && (!tname.startsWith(CUSTOM_PREFIX))) {
                File tf = new File(templatedir, tname);
                ConfigurationNode cn = new ConfigurationNode(tf);
                cn.load();
                /* Supplement existing values (don't replace), since configuration.txt is more custom than these */
                mergeConfigurationBranch(cn, "templates", false, false);
            }
        }
        /* Go through list again - this time do custom- ones */
        for (String tname : templates) {
            /* If matches naming convention */
            if (tname.endsWith(".txt") && tname.startsWith(CUSTOM_PREFIX)) {
                File tf = new File(templatedir, tname);
                ConfigurationNode cn = new ConfigurationNode(tf);
                cn.load();
                /* This are overrides - replace even configuration.txt content */
                mergeConfigurationBranch(cn, "templates", true, false);
            }
        }
    }

    public boolean enableCore() {
        boolean rslt = initConfiguration(null);
        if (rslt) {
            rslt = enableCore(null);
        }
        return rslt;
    }

    public boolean initConfiguration(EnableCoreCallbacks cb) {
        /* Start with clean events */
        events = new Events();
        /* Default to being unprotected - set to protected by update components */
        player_info_protected = false;

        /* Load plugin version info */
        loadVersion();

        /* Initialize confguration.txt if needed */
        File f = new File(dataDirectory, "configuration.txt");
        if (!createDefaultFileFromResource("/configuration.txt", f)) {
            return false;
        }

        /* Load configuration.txt */
        configuration = new ConfigurationNode(f);
        configuration.load();

        // Read web path
        webpath = configuration.getString("webpath", "web");
        // And whether to disable web file update
        updatewebpathfiles = configuration.getBoolean("update-webpath-files", true);

        // Check if we are disabling the internal web server (implies external)
        isInternalWebServerDisabled = configuration.getBoolean("disable-webserver", false);

        /* Prime the tiles directory */
        tilesDirectory = getFile(configuration.getString("tilespath", "web/tiles"));
        if (!tilesDirectory.isDirectory() && !tilesDirectory.mkdirs()) {
            Log.warning("Could not create directory for tiles ('" + tilesDirectory + "').");
        }
        // Prime the exports directory
        exportDirectory = getFile(configuration.getString("exportpath", "export"));
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
            Log.warning("Could not create directory for exports ('" + exportDirectory + "').");
        }
        // Prime the imports directory
        importDirectory = getFile(configuration.getString("importpath", "import"));
        if (!importDirectory.isDirectory() && !importDirectory.mkdirs()) {
            Log.warning("Could not create directory for imports ('" + importDirectory + "').");
        }
        // Create default storage handler
        String storetype = configuration.getString("storage/type", "filetree");
        if (storetype.equals("filetree")) {
            defaultStorage = new FileTreeMapStorage();
        } // else if (storetype.equals("sqlite")) {
        //      defaultStorage = new SQLiteMapStorage();
        //  }
        else if (storetype.equals("mysql")) {
            defaultStorage = new MySQLMapStorage();
        } // else if (storetype.equals("mariadb")) {
        //      defaultStorage = new MariaDBMapStorage();
        //  }
        //  else if (storetype.equals("postgres") || storetype.equals("postgresql")) {
        //      defaultStorage = new PostgreSQLMapStorage();
        //  }
        //else if (storetype.equals("aws_s3")) {
        //    defaultStorage = new AWSS3MapStorage();
        //}
        //    else if (storetype.equals("microsoftsql")) {
        //       defaultStorage = new MicrosoftSQLMapStorage();
        //  }
        else {
            Log.severe("Invalid storage type for map data: " + storetype);
            return false;
        }
        if (!defaultStorage.init(this)) {
            Log.severe("Map storage initialization failure");
            return false;
        }

        /* Register API with plugin, if needed */
        if (!markerAPIInitialized()) {
            MarkerAPIImpl api = MarkerAPIImpl.initializeMarkerAPI(this);
            this.registerMarkerAPI(api);
        }
        /* Call back to plugin to report that configuration is available */
        if (cb != null) {
            cb.configurationLoaded();
        }
        return true;
    }

    private String findExecutableOnPath(String fname) {
        String path = System.getenv("PATH");
        // Fast-fail if path is null.
        if (path == null) {
            return null;
        }

        for (String dirname : path.split(File.pathSeparator)) {
            File file = new File(dirname, fname);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
            file = new File(dirname, fname + ".exe");
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    public boolean enableCore(EnableCoreCallbacks cb) {
        /* Update extracted files, if needed */
        updateExtractedFiles();
        /* Initialize authorization manager */
        if (configuration.getBoolean("login-enabled", false)) {
            authmgr = new WebAuthManager(this);
            defaultStorage.setLoginEnabled(this);
        }
        // If storage serves web files, extract and publsh them
        if (defaultStorage.needsStaticWebFiles()) {
            updateStaticWebToStorage();
        }
        /* Load control for leaf transparency (spout lighting bug workaround) */
        transparentLeaves = configuration.getBoolean("transparent-leaves", true);

        // Inject core instance
        ImageIOManager.core = this;
        // Check for webp support
        cwebpPath = configuration.getString("cwebpPath", null);
        dwebpPath = configuration.getString("dwebpPath", null);
        if (cwebpPath == null) {
            cwebpPath = findExecutableOnPath("cwebp");
        }
        if (dwebpPath == null) {
            dwebpPath = findExecutableOnPath("dwebp");
        }
        if (cwebpPath != null) {
            File file = new File(cwebpPath);
            if (!file.isFile() || !file.canExecute()) {
                cwebpPath = null;
            }
        }
        if (dwebpPath != null) {
            File file = new File(dwebpPath);
            if (!file.isFile() || !file.canExecute()) {
                dwebpPath = null;
            }
        }
        if ((cwebpPath != null) && (dwebpPath != null)) {
            Log.info("Found cwebp at " + cwebpPath + " and dwebp at " + dwebpPath + ": webp format enabled");
        } else {
            cwebpPath = dwebpPath = null;
        }
        /* Get default image format */
        def_image_format = configuration.getString("image-format", "png");
        MapType.ImageFormat fmt = MapType.ImageFormat.fromID(def_image_format);
        if ((fmt == null) || ((fmt.enc == ImageEncoding.WEBP) && (cwebpPath == null))) {
            Log.severe("Invalid image-format: " + def_image_format);
            def_image_format = "png";
            fmt = MapType.ImageFormat.fromID(def_image_format);
        }

        DynmapWorld.doInitialScan(configuration.getBoolean("initial-zoomout-validate", true));

        smoothlighting = configuration.getBoolean("smooth-lighting", false);
        ctmsupport = configuration.getBoolean("ctm-support", true);
        customcolorssupport = configuration.getBoolean("custom-colors-support", true);
        Log.verbose = configuration.getBoolean("verbose", true);
        deftemplatesuffix = configuration.getString("deftemplatesuffix", "");
        /* Get snapshot cache size */
        snapshotcachesize = configuration.getInteger("snapshotcachesize", 500);
        /* Get soft ref flag for cache (weak=false, soft=true) */
        snapshotsoftref = configuration.getBoolean("soft-ref-cache", true);
        /* Default better-grass */
        bettergrass = configuration.getBoolean("better-grass", false);
        /* Load full render processing player limit */
        fullrenderplayerlimit = configuration.getInteger("fullrenderplayerlimit", 0);
        /* Load update render processing player limit */
        updateplayerlimit = configuration.getInteger("updateplayerlimit", 0);
        /* Load sort permission nodes */
        //   sortPermissionNodes = configuration.getStrings("player-sort-permission-nodes", null);

        perTickLimit = configuration.getInteger("per-tick-time-limit", 50);
        if (perTickLimit < 5) {
            perTickLimit = 5;
        }

        dumpMissing = configuration.getBoolean("dump-missing-blocks", false);

        migrate_chunks = configuration.getBoolean("migrate-chunks", false);
        if (migrate_chunks) {
            Log.info("EXPERIMENTAL: chunk migration enabled");
        }

        publicURL = configuration.getString("publicURL", "");

        /* Send this message if the player does not have permission to use the command */
        configuration.getString("noPermissionMsg", "You don't have permission to use this command!");

        /* Load preupdate/postupdate commands */
        ImageIOManager.preUpdateCommand = configuration.getString("custom-commands/image-updates/preupdatecommand", "");
        ImageIOManager.postUpdateCommand = configuration.getString("custom-commands/image-updates/postupdatecommand", "");

        /* Get block and item maps */
        blockmap = server.getBlockUniqueIDMap();
        itemmap = server.getItemUniqueIDMap();

        /* Process mod support */
        ModSupportImpl.complete(this.dataDirectory);
        // Finalize block state
        DynmapBlockState.finalizeBlockStates();
        /* Load block models */
        Log.verboseinfo("Loading models...");
        HDBlockModels.loadModels(this, configuration);
        /* Load texture mappings */
        Log.verboseinfo("Loading texture mappings...");
        TexturePack.loadTextureMapping(this, configuration);

        /* Now, process worlds.txt - merge it in as an override of existing values (since it is only user supplied values) */
        File f = new File(dataDirectory, "worlds.txt");
        if (!createDefaultFileFromResource("/worlds.txt", f)) {
            return false;
        }
        world_config = new ConfigurationNode(f);
        world_config.load();

        /* Now, process templates */
        Log.verboseinfo("Loading templates...");
        loadTemplates();

        /* If we're persisting ids-by-ip, load it */
        // persist_ids_by_ip = configuration.getBoolean("persist-ids-by-ip", true);
        //  if(persist_ids_by_ip) {
        //     Log.verboseinfo("Loading userid-by-IP data...");
        //     loadIDsByIP();
        // }
        loadDebuggers();

        // playerList = new PlayerList(getServer(), getFile("hiddenplayers.txt"), configuration);
        //playerList.load();
        mapManager = new MapManager(this, configuration);
        mapManager.startRendering();

        if (markerapi != null) {
            MarkerAPIImpl.completeInitializeMarkerAPI(markerapi);
        }

        //playerfacemgr = new PlayerFaces(this);
        updateConfigHashcode();
        /* Initialize/update config hashcode */

        loginRequired = configuration.getBoolean("login-required", false);
        hackAttemptSub = configuration.getString("hackAttemptBlurb", "(IaM5uchA1337Haxr-Ban Me!)");

        // If not disabled, load and initialize the internal web server
        if (!isInternalWebServerDisabled) {
            loadWebserver();
        }

        enabledTriggers.clear();
        List<String> triggers = configuration.getStrings("render-triggers", new ArrayList<String>());
        if ((triggers != null) && (triggers.size() > 0)) {
            for (Object trigger : triggers) {
                enabledTriggers.add((String) trigger);
            }
        } else {
            for (String def : deftriggers) {
                enabledTriggers.add(def);
            }
        }

        // Load components.
        for (Component component : configuration.<Component>createInstances("components", new Class<?>[]{DynmapCore.class}, new Object[]{this})) {
            componentManager.add(component);
        }
        Log.verboseinfo("Loaded " + componentManager.components.size() + " components.");

        if (!isInternalWebServerDisabled) {	// If internal not disabled, we should be using it and not external
            startWebserver();
            if (!componentManager.isLoaded(InternalClientUpdateComponent.class)) {
                Log.warning("Using internal server, but " + InternalClientUpdateComponent.class.toString() + " is DISABLED!");
                webserverCompConfigWarn = true;
            }
            if (componentManager.isLoaded(JsonFileClientUpdateComponent.class)) {
                Log.warning("Using internal server, but " + JsonFileClientUpdateComponent.class.toString() + " is ENABLED!");
            }
        } else {
            if (componentManager.isLoaded(InternalClientUpdateComponent.class)) {
                Log.warning("Using external server, but " + InternalClientUpdateComponent.class.toString() + " is ENABLED!");
            }
            if (!componentManager.isLoaded(JsonFileClientUpdateComponent.class)) {
                Log.warning("Using external server, but " + JsonFileClientUpdateComponent.class.toString() + " is DISABLED!");
                webserverCompConfigWarn = true;
            }
        }
        if (webserverCompConfigWarn) {
            Log.warning("If the website is missing files or not loading/updating, this might be why.");
            Log.warning("For more info, read this: " + CompConfigWiki);
            webserverCompConfigWarn = false;
        }


        /* Print version info */
        Log.info("For support, visit our Discord at https://discord.gg/s3rd5qn");
        Log.info("For news, visit https://reddit.com/r/Dynmap or follow https://universeodon.com/@dynmap");
        Log.info("To report or track bugs, visit https://github.com/webbukkit/dynmap/issues");
        Log.info("If you'd like to donate, please visit https://www.patreon.com/dynmap or https://ko-fi.com/michaelprimm");

        events.<Object>trigger("initialized", null);

        if (configuration.getBoolean("dumpColorMaps", false)) {
            dumpColorMap("standard.txt", "standard");
            dumpColorMap("default.txt", "standard");
            dumpColorMap("dokudark.txt", "dokudark.zip");
            dumpColorMap("dokulight.txt", "dokulight.zip");
            dumpColorMap("dokuhigh.txt", "dokuhigh.zip");
            dumpColorMap("misa.txt", "misa.zip");
            dumpColorMap("sphax.txt", "sphax.zip");
            dumpColorMap("ovocean.txt", "ovocean.zip");
            dumpColorMap("flames.txt", "standard");	// No TP around for this
            dumpColorMap("sk89q.txt", "standard");	// No TP around for this
            dumpColorMap("amidst.txt", "standard");	// No TP around for this
        }

        return true;
    }

    void dumpColorMap(String id, String name) {
        int[] sides = new int[]{BlockStep.Y_MINUS.ordinal(), BlockStep.X_PLUS.ordinal(), BlockStep.Z_PLUS.ordinal(),
            BlockStep.Y_PLUS.ordinal(), BlockStep.X_MINUS.ordinal(), BlockStep.Z_MINUS.ordinal()};
        FileWriter fw = null;
        try {
            fw = new FileWriter(new File(new File(getDataFolder(), "colorschemes"), id));
            TexturePack tp = TexturePack.getTexturePack(this, name);
            if (tp == null) {
                return;
            }
            tp = tp.resampleTexturePack(1);
            if (tp == null) {
                return;
            }
            Color c = new Color();
            for (int gidx = 0; gidx < DynmapBlockState.getGlobalIndexMax(); gidx++) {
                DynmapBlockState blk = DynmapBlockState.getStateByGlobalIndex(gidx);
                if (blk.isAir()) {
                    continue;
                }
                int meta0color = 0;
                HDBlockStateTextureMap map = HDBlockStateTextureMap.getByBlockState(blk);
                boolean done = false;
                for (int i = 0; (!done) && (i < sides.length); i++) {
                    int idx = map.getIndexForFace(sides[i]);
                    if (idx < 0) {
                        continue;
                    }
                    int rgb[] = tp.getTileARGB(idx % 1000000);
                    if (rgb == null) {
                        continue;
                    }
                    if (rgb[0] == 0) {
                        continue;
                    }
                    c.setARGB(rgb[0]);
                    idx = (idx / 1000000);
                    switch (idx) {
                        case 1, 18 -> // grass
                        {
                            // grass
                            Log.verboseinfo("Used grass for " + blk);
                            c.blendColor(tp.getTrivialGrassMultiplier() | 0xFF000000);
                        }
                        case 2, 19, 22 -> {
                            // foliage
                            Log.verboseinfo("Used foliage for " + blk);
                            c.blendColor(tp.getTrivialFoliageMultiplier() | 0xFF000000);
                        }
                        case 13 -> // foliage
                            // pine
                            c.blendColor(0x619961 | 0xFF000000);
                        case 14 -> // foliage
                            // birch
                            c.blendColor(0x80a755 | 0xFF000000);
                        case 15 -> // lily
                            c.blendColor(0x208030 | 0xFF000000);
                        case 3, 20 -> {
                            // water
                            Log.verboseinfo("Used water for " + blk);
                            c.blendColor(tp.getTrivialWaterMultiplier() | 0xFF000000);
                        }
                        case 12 -> {
                            // clear inside
                            if (blk.isWater()) { // special case for water
                                Log.verboseinfo("Used water for " + blk);
                                c.blendColor(tp.getTrivialWaterMultiplier() | 0xFF000000);
                            }
                        }
                    }
                    // grass
                    // foliage
                    // foliage
                    // water
                    int custmult = tp.getCustomBlockMultiplier(blk);
                    if (custmult != 0xFFFFFF) {
                        Log.info(String.format("Custom color: %06x for %s", custmult, blk));
                        if ((custmult & 0xFF000000) == 0) {
                            custmult |= 0xFF000000;
                        }
                        c.blendColor(custmult);
                    }
                    String ln = "";
                    if (blk.stateIndex == 0) {
                        meta0color = c.getARGB();
                        ln = blk.blockName + " ";
                    } else {
                        ln = blk + " ";
                    }
                    if ((blk.stateIndex == 0) || (meta0color != c.getARGB())) {
                        ln += c.getRed() + " " + c.getGreen() + " " + c.getBlue() + " " + c.getAlpha();
                        ln += " " + (c.getRed() * 4 / 5) + " " + (c.getGreen() * 4 / 5) + " " + (c.getBlue() * 4 / 5) + " " + c.getAlpha();
                        ln += " " + (c.getRed() / 2) + " " + (c.getGreen() / 2) + " " + (c.getBlue() / 2) + " " + c.getAlpha();
                        ln += " " + (c.getRed() * 2 / 5) + " " + (c.getGreen() * 2 / 5) + " " + (c.getBlue() * 2 / 5) + " " + c.getAlpha() + "\n";
                        fw.write(ln);
                    }
                    done = true;
                }
            }
        } catch (IOException iox) {
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException x) {
                }
            }
            Log.info("Dumped RP=" + name + " to " + id);
        }
    }

    public static void playerJoined(Player p) {
        //   playerList.updateOnlinePlayers(null);
        if ((fullrenderplayerlimit > 0) || (updateplayerlimit > 0)) {
            int pcnt = Bukkit.getOnlinePlayers().size();//getServer().getOnlinePlayers().length;

            if ((fullrenderplayerlimit > 0) && (pcnt == fullrenderplayerlimit)) {
                if (mapManager.getPauseFullRadiusRenders() == false) {
                    /* If not paused, pause it */
                    mapManager.setPauseFullRadiusRenders(true);
                    Log.info("Pause full/radius renders - player limit reached");
                    didfullpause = true;
                } else {
                    didfullpause = false;
                }
            }
            if ((updateplayerlimit > 0) && (pcnt == updateplayerlimit)) {
                if (mapManager.getPauseUpdateRenders() == false) {
                    /* If not paused, pause it */
                    mapManager.setPauseUpdateRenders(true);
                    Log.info("Pause tile update renders - player limit reached");
                    didupdatepause = true;
                } else {
                    didupdatepause = false;
                }
            }
        }
        /* Add player info to IP-to-ID table */
 /*InetSocketAddress addr = p.getAddress();
        if(addr != null) {
            String ip = addr.getAddress().getHostAddress();
            LinkedList<String> ids = ids_by_ip.get(ip);
            if(ids == null) {
                ids = new LinkedList<String>();
                ids_by_ip.put(ip, ids);
            }
            String pid = p.getName();
            if(ids.indexOf(pid) != 0) {
                ids.remove(pid);    /* Remove from list *
                ids.addFirst(pid);  /* Put us first on list *
            }
        }*/
 /* Check sort weight permissions list */
 /*  if ((sortPermissionNodes != null) && (sortPermissionNodes.size() > 0)) {
            int ord;
            for (ord = 0; ord < sortPermissionNodes.size(); ord++) {
                if (p.hasPermissionNode(sortPermissionNodes.get(ord))) {
                    break;
                }
            }
            p.setSortWeight(ord);
        }
        else {
            p.setSortWeight(0); // Initialize to zero
        }*/
 /* And re-attach to active jobs */
        if (mapManager != null) {
            mapManager.connectTasksToPlayer(p);
        }
    }

    /* Called by plugin each time a player quits the server */
    public static void playerQuit(Player p) {
        //  playerList.updateOnlinePlayers(p.getName());
        if ((fullrenderplayerlimit > 0) || (updateplayerlimit > 0)) {
            /* Quitting player is still online at this moment, so discount count by 1 */
            int pcnt = Bukkit.getOnlinePlayers().size();//getServer().getOnlinePlayers().length - 1;
            if ((fullrenderplayerlimit > 0) && (pcnt == (fullrenderplayerlimit - 1))) {
                if (didfullpause && mapManager.getPauseUpdateRenders()) {
                    /* Only unpause if we did the pause */
                    mapManager.setPauseFullRadiusRenders(false);
                    Log.info("Resume full/radius renders - below player limit");
                }
                didfullpause = false;
            }
            if ((updateplayerlimit > 0) && (pcnt == (updateplayerlimit - 1))) {
                if (didupdatepause && mapManager.getPauseUpdateRenders()) {
                    /* Only unpause if we did the pause */
                    mapManager.setPauseUpdateRenders(false);
                    Log.info("Resume tile update renders - below player limit");
                }
                didupdatepause = false;
            }
        }
    }

    public static void updateConfigHashcode() {
        config_hashcode = (int) System.currentTimeMillis();
    }

    public int getConfigHashcode() {
        return config_hashcode;
    }

    @SuppressWarnings("deprecation")
    private org.dynmap.jetty.util.resource.FileResource createFileResource(String path) {
        try {
            File f = new File(path);
            URI uri = f.toURI();
            URL url = uri.toURL();
            return new org.dynmap.jetty.util.resource.FileResource(url);
        } catch (Exception e) {
            Log.info("Could not create file resource");
            return null;
        }
    }

    public void loadWebserver() {
        org.dynmap.jetty.util.log.Log.setLog(new JettyNullLogger());
        String ip = server.getServerIP();
        if ((ip == null) || (ip.trim().length() == 0)) {
            ip = "0.0.0.0";
        }
        webhostname = configuration.getString("webserver-bindaddress", ip);
        webport = configuration.getInteger("webserver-port", 8123);

        int maxconnections = configuration.getInteger("max-sessions", 30);
        if (maxconnections < 2) {
            maxconnections = 2;
        }
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(maxconnections);
        ExecutorThreadPool pool = new ExecutorThreadPool(maxconnections, 2, queue);

        webServer = new Server(pool);
        webServer.setSessionIdManager(new DefaultSessionIdManager(webServer));

        NetworkTrafficServerConnector connector = new NetworkTrafficServerConnector(webServer);
        connector.setIdleTimeout(5000);
        connector.setAcceptQueueSize(50);
        if (webhostname.equals("0.0.0.0") == false) {
            connector.setHost(webhostname);
        }
        connector.setPort(webport);
        webServer.setConnectors(new Connector[]{connector});

        webServer.setStopAtShutdown(true);
        //webServer.setGracefulShutdown(1000);
        final boolean allow_symlinks = configuration.getBoolean("allow-symlinks", false);
        router = new HandlerRouter() {
            {
                FileResourceHandler fileResourceHandler = new FileResourceHandler() {
                    {
                        this.setWelcomeFiles(new String[]{"index.html"});
                        this.setRedirectWelcome(false);
                        this.setDirectoriesListed(true);
                        this.setBaseResource(createFileResource(getFile(getWebPath()).getAbsolutePath()));
                    }
                };
                try {
                    fileResourceHandler.doStart();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.severe("Failed to start resource handler: " + ex.getMessage());
                }
                ContextHandler fileResourceContext = new ContextHandler();
                fileResourceContext.setHandler(fileResourceHandler);
                fileResourceContext.clearAliasChecks();
                if (allow_symlinks) {
                    fileResourceContext.addAliasCheck(new ContextHandler.ApproveAliases());
                    fileResourceContext.addAliasCheck(new ContextHandler.ApproveNonExistentDirectoryAliases());
                    fileResourceContext.addAliasCheck(new AllowSymLinkAliasChecker());
                }
                try {
                    Class<?> handlerClass = fileResourceHandler.getClass().getSuperclass().getSuperclass();
                    Field field = handlerClass.getDeclaredField("_context");
                    field.setAccessible(true);
                    field.set(fileResourceHandler, fileResourceContext);
                } catch (Exception e) {
                    Log.severe("Failed to initialize resource handler: " + e.getMessage());
                }
                this.addHandler("/", fileResourceHandler);
                this.addHandler("/tiles/*", new MapStorageResourceHandler() {
                    {
                        this.setCore(DynmapCore.this);
                    }
                });
            }
        };

        if (allow_symlinks) {
            Log.verboseinfo("Web server is permitting symbolic links");
        } else {
            Log.verboseinfo("Web server is not permitting symbolic links");
        }

        List<Filter> filters = new LinkedList<Filter>();

        /* Check for banned IPs */
        boolean checkbannedips = configuration.getBoolean("check-banned-ips", true);
        if (checkbannedips) {
            filters.add(new BanIPFilter(this));
        }
        filters.add(new FileNameFilter(this));

//        filters.add(new LoginFilter(this));
        /* Load customized response headers, if any */
        filters.add(new CustomHeaderFilter(configuration.getNode("http-response-headers")));

        FilterHandler fh = new FilterHandler(router, filters);
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setContextPath("/");
        contextHandler.setHandler(fh);
        HandlerList hlist = new HandlerList();
        hlist.setHandlers(new org.dynmap.jetty.server.Handler[]{new SessionHandler(), contextHandler});
        webServer.setHandler(hlist);

        addServlet("/up/configuration", new ClientConfigurationServlet(this));
        addServlet("/standalone/config.js", new ConfigJSServlet(this));
        if (authmgr != null) {
            LoginServlet login = new LoginServlet(this);
            addServlet("/up/login", login);
            addServlet("/up/register", login);
        }
    }

    public boolean isLoginSupportEnabled() {
        return (authmgr != null);
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }

    public boolean isCTMSupportEnabled() {
        return ctmsupport;
    }

    public boolean isCustomColorsSupportEnabled() {
        return customcolorssupport;
    }

    public void addServlet(String path, HttpServlet servlet) {
        new ServletHolder(servlet);
        router.addServlet(path, servlet);
    }

    public void startWebserver() {
        try {
            if (webServer != null) {
                webServer.start();
                Log.info("Web server started on address " + webhostname + ":" + webport);
            }
        } catch (Exception e) {
            Log.severe("Failed to start WebServer on address " + webhostname + ":" + webport + " : " + e.getMessage());
        }
    }

    public void disableCore() {
        //  if(persist_ids_by_ip)
        //      saveIDsByIP();

        if (webServer != null) {
            try {
                webServer.stop();
                for (int i = 0; i < 100; i++) {
                    /* Limit wait to 10 seconds */
                    if (webServer.isStopping()) {
                        Thread.sleep(100);
                    }
                }
                if (webServer.isStopping()) {
                    Log.warning("Graceful shutdown timed out - continuing to terminate");
                }
            } catch (Exception e) {
                Log.severe("Failed to stop WebServer!", e);
            }
            webServer = null;
        }

        if (componentManager != null) {
            int componentCount = componentManager.components.size();
            for (Component component : componentManager.components) {
                component.dispose();
            }
            componentManager.clear();
            Log.info("Unloaded " + componentCount + " components.");
        }

        if (mapManager != null) {
            mapManager.stopRendering();
            mapManager = null;
        }
        if (defaultStorage != null) {
            defaultStorage.shutdownStorage();
        }
        //  playerfacemgr = null;
        /* Clean up registered listeners */
        // listenerManager.cleanup();

        /* Don't clean up markerAPI - other plugins may still be accessing it */
        authmgr = null;

        Debug.clearDebuggers();
    }

    private static File combinePaths(File parent, String path) {
        return combinePaths(parent, new File(path));
    }

    private static File combinePaths(File parent, File path) {
        if (path.isAbsolute()) {
            return path;
        }
        return new File(parent, path.getPath());
    }

    public File getFile(String path) {
        return combinePaths(getDataFolder(), path);
    }

    protected void loadDebuggers() {
        List<ConfigurationNode> debuggersConfiguration = configuration.getNodes("debuggers");
        Debug.clearDebuggers();
        for (ConfigurationNode debuggerConfiguration : debuggersConfiguration) {
            try {
                Class<?> debuggerClass = Class.forName((String) debuggerConfiguration.getString("class"));
                Constructor<?> constructor = debuggerClass.getConstructor(DynmapCore.class, ConfigurationNode.class);
                Debugger debugger = (Debugger) constructor.newInstance(this, debuggerConfiguration);
                Debug.addDebugger(debugger);
            } catch (Exception e) {
                Log.severe("Error loading debugger: " + e);
                e.printStackTrace();
                continue;
            }
        }
    }

    /*  public boolean checkPlayerPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player) || sender.isOp()) {
            return true;
        } else if (!sender.hasPrivilege(permission.toLowerCase())) {
            sender.sendMessage(noPermissionMsg);
            return false;
        }
        return true;
    }*/
    // public boolean checkPermission(String player, String permission) {
    //      return getServer().checkPlayerPermission(player, permission);
    //  }
    public ConfigurationNode getWorldConfiguration(DynmapWorld world) {
        String wname = world.getName();
        ConfigurationNode finalConfiguration = new ConfigurationNode();
        finalConfiguration.put("name", wname);
        finalConfiguration.put("title", world.getTitle());

        ConfigurationNode worldConfiguration = getWorldConfigurationNode(wname);

        // Get the template.
        ConfigurationNode templateConfiguration = null;
        if (worldConfiguration != null) {
            String templateName = worldConfiguration.getString("template");
            if (templateName != null) {
                templateConfiguration = getTemplateConfigurationNode(templateName);
            }
        }

        // Template not found, using default template.
        if (templateConfiguration == null) {
            templateConfiguration = getDefaultTemplateConfigurationNode(world);
        }

        // Merge the finalConfiguration, templateConfiguration and worldConfiguration.
        finalConfiguration.extend(templateConfiguration);
        finalConfiguration.extend(worldConfiguration);

        Log.verboseinfo("Configuration of world " + world.getName());
        for (Map.Entry<String, Object> e : finalConfiguration.entrySet()) {
            Log.verboseinfo(e.getKey() + ": " + e.getValue());
        }
        /* Update world_config with final */
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        if (worlds == null) {
            worlds = new ArrayList<Map<String, Object>>();
            world_config.put("worlds", worlds);
        }
        boolean did_upd = false;
        for (int idx = 0; idx < worlds.size(); idx++) {
            Map<String, Object> m = worlds.get(idx);
            if (wname.equals(m.get("name"))) {
                worlds.set(idx, finalConfiguration);
                did_upd = true;
                break;
            }
        }
        if (!did_upd) {
            worlds.add(finalConfiguration);
        }

        return finalConfiguration;
    }

    ConfigurationNode getDefaultTemplateConfigurationNode(DynmapWorld world) {
        String environmentName = world.getEnvironment();
        if (deftemplatesuffix.length() > 0) {
            if (!Arrays.asList(defaultTemplates).contains(deftemplatesuffix)) {
                Log.warning("Not using a default defined template, worlds might not be accessible.");
            }
            environmentName += "-" + deftemplatesuffix;
        }
        Log.verboseinfo("Using environment as template: " + environmentName);
        return getTemplateConfigurationNode(environmentName);
    }

    private ConfigurationNode getWorldConfigurationNode(String worldName) {
        worldName = DynmapWorld.normalizeWorldName(worldName);
        for (ConfigurationNode worldNode : world_config.getNodes("worlds")) {
            if (worldName.equals(worldNode.getString("name"))) {
                return worldNode;
            }
        }
        return new ConfigurationNode();
    }

    ConfigurationNode getTemplateConfigurationNode(String templateName) {
        ConfigurationNode templatesNode = configuration.getNode("templates");
        if (templatesNode != null) {
            return templatesNode.getNode(templateName);
        }
        return null;
    }

    public String getWebPath() {
        return webpath;
    }

    public static void setIgnoreChunkLoads(boolean ignore) {
        ignore_chunk_loads = ignore;
    }

    /* Uses resource to create default file, if file does not yet exist */
    public boolean createDefaultFileFromResource(String resourcename, File deffile) {
        if (deffile.canRead()) {
            return true;
        }
        Debug.debug(deffile.getPath() + " not found - creating default");
        InputStream in = getClass().getResourceAsStream(resourcename);
        if (in == null) {
            Log.severe("Unable to find default resource - " + resourcename);
            return false;
        } else {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(deffile);
                byte[] buf = new byte[512];
                int len;
                while ((len = in.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } catch (IOException iox) {
                Log.severe("ERROR creatomg default for " + deffile.getPath());
                return false;
            } finally {
                if (fos != null)
                    try {
                    fos.close();
                } catch (IOException iox) {
                }
                if (in != null)
                    try {
                    in.close();
                } catch (IOException iox) {
                }
            }
            return true;
        }
    }

    /*
     * Update if new version
     */
    public boolean updateVersionUsingDefaultResource(String resourcename, File deffile) {
        InputStream in = getClass().getResourceAsStream(resourcename);
        if (in == null) {
            Log.severe("Unable to find resource - " + resourcename);
            return false;
        }
        if (deffile.canRead() == false) {
            /* Doesn't exist? */
            return createDefaultFileFromResource(resourcename, deffile);
        }
        /* Load default from resource */
        ConfigurationNode def_fc = new ConfigurationNode(in);
        /* Load existing from file */
        ConfigurationNode fc = new ConfigurationNode(deffile);
        fc.load();
        if (fc.getString("version", "").equals(def_fc.getString("version", ""))) {
            return true;
        }
        deffile.delete();
        return createDefaultFileFromResource(resourcename, deffile);
    }

    /*
     * Add in any missing sections to existing file, using resource
     */
    public boolean updateUsingDefaultResource(String resourcename, File deffile, String basenode) {
        InputStream in = getClass().getResourceAsStream(resourcename);
        if (in == null) {
            Log.severe("Unable to find resource - " + resourcename);
            return false;
        }
        if (deffile.canRead() == false) {
            /* Doesn't exist? */
            return createDefaultFileFromResource(resourcename, deffile);
        }
        /* Load default from resource */
        ConfigurationNode def_fc = new ConfigurationNode(in);
        /* Load existing from file */
        ConfigurationNode fc = new ConfigurationNode(deffile);
        fc.load();
        /* Now, get the list associated with the base node default */
        List<Map<String, Object>> existing = fc.getMapList(basenode);
        Set<String> existing_names = new HashSet<String>();
        /* Make map, indexed by 'name' in map */
        if (existing != null) {
            for (Map<String, Object> m : existing) {
                Object name = m.get("name");
                if (name instanceof String) {
                    existing_names.add((String) name);
                }
            }
        }
        boolean did_update = false;
        /* Now, loop through defaults, and see if any are missing */
        List<Map<String, Object>> defmaps = def_fc.getMapList(basenode);
        if (defmaps != null) {
            for (Map<String, Object> m : defmaps) {
                Object name = m.get("name");
                if (name instanceof String) {
                    /* If not an existing one, need to add it */
                    if (existing_names.contains((String) name) == false) {
                        existing.add(m);
                        did_update = true;
                    }
                }
            }
        }
        /* If we did update, save existing */
        if (did_update) {
            fc.put(basenode, existing);
            fc.save(deffile);
            Log.info("Updated file " + deffile.getPath());
        }
        return true;
    }

    /**
     * ** This is the public API for other plugins to use for accessing the
     * Marker API ** This method can return null if the 'markers' component has
     * not been configured - a warning message will be issued to the server.log
     * in this event.
     *
     * @return MarkerAPI, or null if not configured
     */
    public MarkerAPI getMarkerAPI() {
        if (markerapi == null) {
            Log.warning("Marker API has been requested, but is not enabled.  Uncomment or add 'markers' component to configuration.txt.");
        }
        return markerapi;
    }

    public boolean markerAPIInitialized() {
        return (markerapi != null);
    }

    /**
     * Send generic message to all web users
     *
     * @param sender - label for sender of message ("[&lt;sender&gt;] message")
     * - if null, no from notice
     * @param msg - message to be sent
     * @return true if successful
     */
    public boolean sendBroadcastToWeb(String sender, String msg) {
        if (mapManager != null) {
            mapManager.pushUpdate(new Client.ChatMessage("plugin", sender, "", msg, ""));
            return true;
        }
        return false;
    }

    /**
     * Register markers API - used by component to supply marker API to plugin
     *
     * @param api - marker API
     */
    public void registerMarkerAPI(MarkerAPIImpl api) {
        markerapi = api;
    }

    /*
     * Pause full/radius render processing
     * @param dopause - true to pause, false to unpause
     */
    @Override
    public void setPauseFullRadiusRenders(boolean dopause) {
        mapManager.setPauseFullRadiusRenders(dopause);
    }

    /*
     * Test if full renders are paused
     * @return true if paused
     */
    @Override
    public boolean getPauseFullRadiusRenders() {
        return mapManager.getPauseFullRadiusRenders();
    }

    /*
     * Pause update render processing
     * @param dopause - true to pause, false to unpause
     */
    @Override
    public void setPauseUpdateRenders(boolean dopause) {
        mapManager.setPauseUpdateRenders(dopause);
    }

    /*
     * Test if update renders are paused
     * @return true if paused
     */
    @Override
    public boolean getPauseUpdateRenders() {
        return mapManager.getPauseUpdateRenders();
    }

    @Override
    public void postPlayerMessageToWeb(String playerid, String playerdisplay, String message) {
        if (playerdisplay == null) {
            playerdisplay = playerid;
        }
        if (mapManager != null) {
            mapManager.pushUpdate(new Client.ChatMessage("player", "", playerdisplay, message, playerid));
        }
    }

    @Override
    public void postPlayerJoinQuitToWeb(String playerid, String playerdisplay, boolean isjoin) {
        if (playerdisplay == null) {
            playerdisplay = playerid;
        }
        if ((mapManager != null)) {// && (playerList != null) && (playerList.isVisiblePlayer(playerid))) {
            if (isjoin) {
                mapManager.pushUpdate(new Client.PlayerJoinMessage(playerdisplay, playerid));
            } else {
                mapManager.pushUpdate(new Client.PlayerQuitMessage(playerdisplay, playerid));
            }
        }
    }

    @Override
    public String getDynmapCoreVersion() {
        return version;
    }


    @Override
    public int triggerRenderOfBlock(String wid, int x, int y, int z) {
        if (mapManager != null) {
            mapManager.touch(wid, x, y, z, "api");
        }
        return 0;
    }

    @Override
    public int triggerRenderOfVolume(String wid, int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        if (mapManager != null) {
            if ((minx == maxx) && (miny == maxy) && (minz == maxz)) {
                mapManager.touch(wid, minx, miny, minz, "api");
            } else {
                mapManager.touchVolume(wid, minx, miny, minz, maxx, maxy, maxz, "api");
            }
        }
        return 0;
    }

    public boolean isTrigger(String s) {
        return enabledTriggers.contains(s);
    }

    /*public DynmapWorld getWorld(String wid) {
        if (mapManager != null) {
            return mapManager.getWorld(wid);
        }
        return null;
    }*/

    /* Called by plugin when world loaded */
  /*  public boolean processWorldLoad(DynmapWorld w) {
        boolean activated = true;
        if (mapManager.getWorld(w.getName()) == null) {
            updateConfigHashcode();
            activated = mapManager.activateWorld(w);
        } else {
            mapManager.loadWorld(w);
        }
        return activated;
    }

    /* Called by plugin when world unloaded *
    public boolean processWorldUnload(DynmapWorld w) {
        boolean done = false;
        if (mapManager.getWorld(w.getName()) != null) {
            mapManager.unloadWorld(w);
            done = true;
        }
        return done;
    }*/

    /* Enable/disable world */
    public boolean setWorldEnable(String wname, boolean isenab) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        for (Map<String, Object> m : worlds) {
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                m.put("enabled", isenab);
                return true;
            }
        }
        /* If not found, and disable, add disable node */
        if (isenab == false) {
            Map<String, Object> newworld = new LinkedHashMap<String, Object>();
            newworld.put("name", wname);
            newworld.put("enabled", isenab);
        }
        return true;
    }

    public boolean setWorldZoomOut(String wname, int xzoomout) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        for (Map<String, Object> m : worlds) {
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                m.put("extrazoomout", xzoomout);
                return true;
            }
        }
        return false;
    }

    public boolean setWorldTileUpdateDelay(String wname, int tud) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        for (Map<String, Object> m : worlds) {
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                if (tud > 0) {
                    m.put("tileupdatedelay", tud);
                } else {
                    m.remove("tileupdatedelay");
                }
                return true;
            }
        }
        return false;
    }

    public boolean setWorldCenter(String wname, DynmapLocation loc) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        for (Map<String, Object> m : worlds) {
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                if (loc != null) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("x", loc.x);
                    c.put("y", loc.y);
                    c.put("z", loc.z);
                    m.put("center", c);
                } else {
                    m.remove("center");
                }
                return true;
            }
        }
        return false;
    }

    public boolean setWorldOrder(String wname, int order) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        ArrayList<Map<String, Object>> newworlds = new ArrayList<>(worlds);

        Map<String, Object> w = null;
        for (Map<String, Object> m : worlds) {
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                w = m;
                newworlds.remove(m);
                /* Remove from list */
                break;
            }
        }
        if (w != null) {
            /* If found it, add back at right pount */
            if (order >= newworlds.size()) {
                /* At end? */
                newworlds.add(w);
            } else {
                newworlds.add(order, w);
            }
            world_config.put("worlds", newworlds);
            return true;
        }
        return false;
    }

    public boolean updateWorldConfig(DynmapWorld w) {
        ConfigurationNode cn = w.saveConfiguration();
        return replaceWorldConfig(w.getName(), cn);
    }

    public boolean replaceWorldConfig(String wname, ConfigurationNode cn) {
        wname = DynmapWorld.normalizeWorldName(wname);
        List<Map<String, Object>> worlds = world_config.getMapList("worlds");
        if (worlds == null) {
            worlds = new ArrayList<>();
            world_config.put("worlds", worlds);
        }
        for (int i = 0; i < worlds.size(); i++) {
            Map<String, Object> m = worlds.get(i);
            String wn = (String) m.get("name");
            if ((wn != null) && (wn.equals(wname))) {
                worlds.set(i, cn.entries);
                /* Replace */
                return true;
            }
        }
        return false;
    }

    public boolean saveWorldConfig() {
        boolean rslt = world_config.save();
        /* Save world config */
        updateConfigHashcode();
        /* Update config hashcode */
        return rslt;
    }

    /* Refresh world config */
    public boolean refreshWorld(String wname) {
        wname = DynmapWorld.normalizeWorldName(wname);
        saveWorldConfig();
        if (mapManager != null) {
            mapManager.deactivateWorld(wname);
            /* Clean it up */
            DynmapWorld w = getServer().getWorldByName(wname);
            /* Get new instance */
            if (w != null) {
                mapManager.activateWorld(w);    /* And activate it again */
            }
        }
        return true;
    }

    /* Load core version */
    private void loadVersion() {
        InputStream in = getClass().getResourceAsStream("/core.yml");
        if (in == null) {
            return;
        }
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> val = (Map<String, Object>) yaml.load(in);
        if (val != null) {
            version = (String) val.get("version");
        }
    }

    public int getSnapShotCacheSize() {
        return snapshotcachesize;
    }

    public boolean useSoftRefInSnapShotCache() {
        return snapshotsoftref;
    }

    public String getDefImageFormat() {
        return def_image_format;
    }

    public String scanAndReplaceLog4JMacro(String msg) {
        int nestcnt = 0;
        int off = 0;
        int firsthit = -1;
        boolean done = false;
        while (!done) {
            int idx = msg.indexOf("${", off);	// Look for next ${
            if (idx >= 0) {	// Hit
                if (nestcnt == 0) {
                    firsthit = idx;	// Record start of hit
                }
                nestcnt++;
                off = idx + 2;
            } else {
                idx = msg.indexOf("}", off);	// Next }
                if (idx >= 0) {
                    if (nestcnt > 0) {
                        nestcnt--;
                        if ((nestcnt == 0) && (firsthit >= 0)) {	// If back to zero, time to strip it
                            String newmsg = msg.substring(0, firsthit) + hackAttemptSub + msg.substring(idx + 1);
                            msg = newmsg;	// Switch to new version, and restart
                            off = 0;
                            firsthit = -1;	// And restart scan
                        }
                    }
                    off = idx + 1;
                } else {	// At end without a close
                    if (firsthit >= 0) {	// Open strip?
                        String newmsg = msg.substring(0, firsthit) + hackAttemptSub;	// Replace rest
                        msg = newmsg;	// Switch to new version, and restart    					
                    }
                    done = true;
                }
            }
        }
        return msg;
    }

    public void webChat(final String name, final String message) {
        if (mapManager == null) {
            return;
        }
        // Check for folks trying to exploit Log4J
        final String cleanname = scanAndReplaceLog4JMacro(name);
        final String cleanmsg = scanAndReplaceLog4JMacro(message);
        if (!cleanname.equals(name)) {
            Log.severe("Possible hack attempt blocked: name contains Log4J macro - " + name.replaceAll("\\$", "_"));
        }
        if (!cleanmsg.equals(message)) {
            Log.severe("Possible hack attempt blocked: message contains Log4J macro (from " + cleanname + ") - " + message.replaceAll("\\$", "_"));
        }
        Runnable c = new Runnable() {
            @Override
            public void run() {
                ChatEvent event = new ChatEvent("web", cleanname, cleanmsg);
                events.trigger("webchat", event);
            }
        };
        getServer().scheduleServerTask(c, 1);
    }

    /**
     * Disable chat message processing (used by mods that will handle sending
     * chat to the web themselves, via sendBroadcastToWeb()
     *
     * @param disable - if true, suppress internal chat-to-web messages
     */
    @Override
    public boolean setDisableChatToWebProcessing(boolean disable) {
        boolean prev = disable_chat_to_web;
        disable_chat_to_web = disable;
        return prev;
    }

    public boolean getLoginRequired() {
        return loginRequired;
    }

    public boolean registerLogin(String uid, String pwd, String passcode) {
        if (authmgr != null) {
            return authmgr.registerLogin(uid, pwd, passcode);
        }
        return false;
    }

    public boolean checkLogin(String uid, String pwd) {
        if (authmgr != null) {
            return authmgr.checkLogin(uid, pwd);
        }
        return false;
    }

    String getLoginPHP(boolean wrap) {
        if (authmgr != null) {
            return authmgr.getLoginPHP(wrap);
        } else {
            return null;
        }
    }

    String getAccessPHP(boolean wrap) {
        if (authmgr != null) {
            return authmgr.getAccessPHP(wrap);
        } else {
            return WebAuthManager.getDisabledAccessPHP(this, wrap);
        }
    }

    boolean pendingRegisters() {
        if (authmgr != null) {
            return authmgr.pendingRegisters();
        }
        return false;
    }

    boolean processCompletedRegister(String uid, String pc, String hash) {
        if (authmgr != null) {
            return authmgr.processCompletedRegister(uid, pc, hash);
        }
        return false;
    }

    public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see) {
        player = player.toLowerCase();
        player_to_see = player_to_see.toLowerCase();
        /* Can always see self */
        if (player.equals(player_to_see)) {
            return true;
        }
        /* If player is hidden, that is dominant */
        //  if(getPlayerVisbility(player_to_see) == false) return false;
        /* Check if player has see-all permission */
        //if(checkPermission(player, "playermarkers.seeall")) return true;
        if (markerapi != null) {
            return markerapi.testIfPlayerVisible(player, player_to_see);
        }
        return false;
    }

    public Set<String> getPlayersVisibleToPlayer(String player) {
        if (markerapi != null) {
            return markerapi.getPlayersVisibleToPlayer(player);
        } else {
            return Collections.singleton(player.toLowerCase());
        }
    }

    /**
     * Test if player position/information is protected on map view
     *
     * @return true if protected, false if visible to guests and all players
     */
    @Override
    public boolean testIfPlayerInfoProtected() {
        return player_info_protected;
    }

    public int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    public int getCurrentPlayers() {
        return server.getCurrentPlayers();
    }


    public static boolean deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    continue;
                }
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else if (f.isFile()) {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }

    private void updateStaticWebToStorage() {
        if (jarfile == null) {
            return;
        }
        // If doing update and web path update is disabled, send warning
        if (!this.updatewebpathfiles) {
            return;
        }
        Log.info("Publishing web files to storage");
        /* Open JAR as ZIP */
        ZipFile zf = null;
        InputStream ins = null;
        byte[] buf = new byte[2048];
        String n = null;
        try {
            zf = new ZipFile(jarfile);
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                n = ze.getName();
                if (!n.startsWith("extracted/web/")) {
                    continue;
                }
                n = n.substring("extracted/web/".length());
                // If file is going to web path, redirect it to the configured web
                if (ze.isDirectory()) {
                    continue;
                }
                try {
                    ins = zf.getInputStream(ze);
                    BufferOutputStream buffer = new BufferOutputStream();
                    int len;
                    while ((len = ins.read(buf)) >= 0) {
                        buffer.write(buf, 0, len);
                    }
                    defaultStorage.setStaticWebFile(n, buffer);
                } catch (IOException io) {
                    Log.severe("Error updating file in storage - " + n, io);
                } finally {
                    if (ins != null) {
                        ins.close();
                        ins = null;
                    }
                }
            }
        } catch (IOException iox) {
            Log.severe("Error extracting file - " + n);
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException iox) {
                }
                ins = null;
            }
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException iox) {
                }
                zf = null;
            }
        }
    }

    private void updateExtractedFiles() {
        if (jarfile == null) {
            return;
        }
        File df = this.getDataFolder();
        if (df.exists() == false) {
            df.mkdirs();
        }
        File ver = new File(df, "version.txt");
        File wpath = this.getFile(this.getWebPath());
        File webver = new File(wpath, "version.txt");
        String prevver = "1.6";
        String prevwebver = "1.6";
        if (ver.exists()) {
            Reader ir = null;
            try {
                ir = new FileReader(ver);
                prevver = "";
                int c;
                while ((c = ir.read()) >= 0) {
                    prevver += (char) c;
                }
            } catch (IOException iox) {
            } finally {
                if (ir != null) {
                    try {
                        ir.close();
                    } catch (IOException iox) {
                    }
                }
            }
        }
        if (webver.exists()) {
            Reader ir = null;
            try {
                ir = new FileReader(webver);
                prevwebver = "";
                int c;
                while ((c = ir.read()) >= 0) {
                    prevwebver += (char) c;
                }
            } catch (IOException iox) {
            } finally {
                if (ir != null) {
                    try {
                        ir.close();
                    } catch (IOException iox) {
                    }
                }
            }
        }
        String curver = this.getDynmapCoreVersion();
        /* If matched, we're good */
        if (prevver.equals(curver) && prevwebver.equals(curver) && (!curver.endsWith(("-Dev")))) {
            return;
        }
        // If doing update and web path update is disabled, send warning
        if (!this.updatewebpathfiles) {
            Log.warning("Update of web interface is disabled, and update is available - UI may not function without updates");
        }
        /* Get deleted file list */
        InputStream in = getClass().getResourceAsStream("/deleted.txt");
        if (in != null) {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.length() == 0) {
                        continue;
                    }
                    if (line.startsWith("#")) {
                        continue;
                    }
                    File newfile = new File(df, line);
                    newfile.delete();
                }
            } catch (IOException iox) {
                Log.warning("Exception while processing deleted files - " + iox.getMessage());
            } finally {
                try {
                    in.close();
                } catch (IOException x) {
                }
            }
        }
        /* Open JAR as ZIP */
        ZipFile zf = null;
        FileOutputStream fos = null;
        InputStream ins = null;
        byte[] buf = new byte[2048];
        String n = null;
        try {
            File f;
            zf = new ZipFile(jarfile);
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                n = ze.getName();
                if (!n.startsWith("extracted/")) {
                    continue;
                }
                n = n.substring("extracted/".length());
                // If file is going to web path, redirect it to the configured web
                if (n.startsWith("web/")) {
                    // Don't update unless we are allowed to
                    if (!updatewebpathfiles) {
                        continue;
                    }
                    f = new File(wpath, n.substring("web/".length()));
                } else {
                    f = new File(df, n);
                }
                if (ze.isDirectory()) {
                    f.mkdirs();
                } else {
                    try {
                        f.getParentFile().mkdirs();
                        fos = new FileOutputStream(f);
                        ins = zf.getInputStream(ze);
                        int len;
                        while ((len = ins.read(buf)) >= 0) {
                            fos.write(buf, 0, len);
                        }
                    } catch (IOException io) {
                        Log.severe("Error updating file - " + f.getPath(), io);
                    } finally {
                        if (ins != null) {
                            ins.close();
                            ins = null;
                        }
                        if (fos != null) {
                            fos.close();
                            fos = null;
                        }
                    }
                }
            }
        } catch (IOException iox) {
            Log.severe("Error extracting file - " + n);
        } finally {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException iox) {
                }
                ins = null;
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException iox) {
                }
                fos = null;
            }
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException iox) {
                }
                zf = null;
            }
        }

        /* Finally, write new version cookie to both data folder and web folder*/
        Writer out = null;
        try {
            out = new FileWriter(ver);
            out.write(this.getDynmapCoreVersion());
        } catch (IOException iox) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException iox) {
                }
            }
        }
        if (this.updatewebpathfiles) {
            try {
                out = new FileWriter(webver);
                out.write(this.getDynmapCoreVersion());
            } catch (IOException iox) {
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException iox) {
                    }
                }
            }
            Log.info("Extracted files upgraded");
        } else {
            Log.info("Extracted files upgraded (excluding webpath files)");
        }
    }

    // Server thread tick : nominally, once per 20 Hz tick
    public void serverTick(double tps) {
        if (this.mapManager != null) {
            this.mapManager.updateTPS(tps);
        }
    }

    public int getMaxTickUseMS() {
        return perTickLimit;
    }

    public boolean dumpMissingBlocks() {
        return dumpMissing;
    }

    // Notice that server has finished starting (needed for forge, which starts dynmap before full server is running)
    public void serverStarted() {
        events.<Object>trigger("server-started", null);
    }

    // Normalize ID (strip out submods)
    public String getNormalizedModID(String mod) {
        int idx = mod.indexOf('|');
        if (idx > 0) {
            mod = mod.substring(0, idx);
        }
        return mod;
    }

    // Add mod block IDs to value map
    public void addModBlockItemIDs(String mod, Map<String, Integer> modvals) {
        mod = getNormalizedModID(mod);
        for (String k : blockmap.keySet()) {
            String[] ks = k.split(":", 2);
            if (ks.length != 2) {
                continue;
            }
            int id = blockmap.get(k);
            ks[0] = getNormalizedModID(ks[0]);
            if (mod.equals(ks[0])) {
                modvals.put("%" + ks[1], id);
            }
        }
        for (String k : itemmap.keySet()) {
            String[] ks = k.split(":", 2);
            if (ks.length != 2) {
                continue;
            }
            int id = itemmap.get(k);
            ks[0] = getNormalizedModID(ks[0]);
            if (mod.equals(ks[0])) {
                modvals.put("&" + ks[1], id);
            }
        }
    }

    @Override
    public void processSignChange(String material, String world, int x, int y, int z,
            String[] lines, String playerid) {
        //Player dp = server.getPlayer(playerid);
        //  listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, material, world, x, y, z, lines, Bukkit.getPlayerExact(playerid));
    }

    public MapStorage getDefaultMapStorage() {
        return defaultStorage;
    }
}
