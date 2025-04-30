package org.dynmap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import org.bukkit.entity.Player;
import org.dynmap.bukkit.DynmapPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ClientUpdateComponent extends Component {

    private final int hideifshadow;
    private final int hideifunder;
    private final boolean hideifsneaking;
    private final boolean hideifspectator;
    private final boolean hideifinvisiblepotion;
    private final boolean is_protected;
    public static boolean usePlayerColors;
    public static boolean hideNames;

    public ClientUpdateComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);

        hideNames = configuration.getBoolean("hidenames", false);
        hideifshadow = configuration.getInteger("hideifshadow", 15);
        hideifunder = configuration.getInteger("hideifundercover", 15);
        hideifsneaking = configuration.getBoolean("hideifsneaking", false);
        hideifspectator = configuration.getBoolean("hideifspectator", false);
        hideifinvisiblepotion = configuration.getBoolean("hide-if-invisiblity-potion", true);
        is_protected = configuration.getBoolean("protected-player-info", false);
        usePlayerColors = configuration.getBoolean("use-name-colors", false);
        if (is_protected) {
            core.player_info_protected = true;
        }

        core.events.addListener("buildclientupdate", (ClientUpdateEvent e) -> {
            buildClientUpdate(e);
        });
    }

    protected void buildClientUpdate(ClientUpdateEvent e) {
        DynmapWorld world = e.world;
        JSONObject u = e.update;
        long since = e.timestamp;
        String worldName = world.dynmapName();
        boolean see_all = true;

        if (is_protected && (!e.include_all_users)) {
            //if(e.user != null)
            //      see_all = core.getServer().checkPlayerPermission(e.user, "playermarkers.seeall");
            //  else
            //      see_all = false;
        }
        if ((e.include_all_users) && is_protected) {
            /* If JSON request AND protected, leave mark for script */
            s(u, "protected", true);
        }

        s(u, "confighash", core.getConfigHashcode());

        s(u, "servertime", world.getTime() % 24000);
        s(u, "hasStorm", world.hasStorm());
        s(u, "isThundering", world.isThundering());

        s(u, "players", new JSONArray());
        //List<Player> players = core.playerList.getVisiblePlayers();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) {
                JSONObject jp = new JSONObject();
                s(jp, "type", "player");
                if (hideNames) {
                    s(jp, "name", "");
                } else if (usePlayerColors) {
                    s(jp, "name", Client.encodeColorInHTML(p.getDisplayName()));
                } else {
                    s(jp, "name", Client.stripColor(p.getDisplayName()));
                }
                s(jp, "account", p.getName());
                s(jp, "world", "-hidden-player-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
                s(jp, "health", 0);
                s(jp, "armor", 0);
                s(jp, "sort", 0);//p.getSortWeight());
                a(u, "players", jp);
            } else {
                boolean hide = false;
                DynmapLocation dynLoc = new DynmapLocation(p.getLocation());
                DynmapWorld pw = DynmapPlugin.dw(dynLoc.dwName);
                if (pw == null) {
                    hide = true;
                }
                JSONObject jp = new JSONObject();

                s(jp, "type", "player");
                if (hideNames) {
                    s(jp, "name", "");
                } else if (usePlayerColors) {
                    s(jp, "name", Client.encodeColorInHTML(p.getDisplayName()));
                } else {
                    s(jp, "name", Client.stripColor(p.getDisplayName()));
                }
                s(jp, "account", p.getName());
                if ((!hide) && (hideifshadow < 15)) {
                    if (pw.getLightLevel((int) dynLoc.x, (int) dynLoc.y, (int) dynLoc.z) <= hideifshadow) {
                        hide = true;
                    }
                }
                if ((!hide) && (hideifunder < 15)) {
                    if (pw.canGetSkyLightLevel()) {
                        /* If we can get real sky level */
                        if (pw.getSkyLightLevel((int) dynLoc.x, (int) dynLoc.y, (int) dynLoc.z) <= hideifunder) {
                            hide = true;
                        }
                    } else if (pw.isNether() == false) {
                        /* Not nether */
                        if (pw.getHighestBlockYAt((int) dynLoc.x, (int) dynLoc.z) > dynLoc.y) {
                            hide = true;
                        }
                    }
                }
                if ((!hide) && hideifsneaking && p.isSneaking()) {
                    hide = true;
                }
                if ((!hide) && hideifspectator && p.getGameMode() == GameMode.SPECTATOR) {
                    hide = true;
                }
                if ((!hide) && is_protected && (!see_all)) {
                    if (e.user != null) {
                        hide = !core.testIfPlayerVisibleToPlayer(e.user, p.getName());
                    } else {
                        hide = true;
                    }
                }
                if ((!hide) && hideifinvisiblepotion && p.isInvisible()) {
                    hide = true;
                }

                /* Don't leak player location for world not visible on maps, or if sendposition disbaled */
                DynmapWorld dw = DynmapPlugin.dw(dynLoc.dwName);//MapManager.mapman.worldsLookup.get(pl.world);
                /* Fix typo on 'sendpositon' to 'sendposition', keep bad one in case someone used it */
                if (configuration.getBoolean("sendposition", true) && configuration.getBoolean("sendpositon", true)
                        && dw != null && dw.sendposition && !hide) {
                    s(jp, "world", dynLoc.dwName);//dynLoc.world);
                    s(jp, "x", dynLoc.x);
                    s(jp, "y", dynLoc.y);
                    s(jp, "z", dynLoc.z);
                } else {
                    s(jp, "world", "-some-other-bogus-world-");
                    s(jp, "x", 0.0);
                    s(jp, "y", 64.0);
                    s(jp, "z", 0.0);
                }
                /* Only send health if enabled AND we're on visible world */
               // if (configuration.getBoolean("sendhealth", false) && (dw != null) && dw.sendhealth && (!hide)) {
               //     s(jp, "health", p.getHealth());
                //    s(jp, "armor", p.getAttribute(Attribute.ARMOR).getValue());//p.getArmorPoints());
                //} else {
                //    s(jp, "health", 0);
                //    s(jp, "armor", 0);
                //}
                //s(jp, "sort", 0);//p.getSortWeight());
                a(u, "players", jp);
            }
        }
        //List<Player> hidden = core.playerList.getHiddenPlayers();
        //if (configuration.getBoolean("includehiddenplayers", false)) {
        /*for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR) continue;
                JSONObject jp = new JSONObject();
                s(jp, "type", "player");
                if (hideNames) {
                    s(jp, "name", "");
                } else if (usePlayerColors) {
                    s(jp, "name", Client.encodeColorInHTML(p.getDisplayName()));
                } else {
                    s(jp, "name", Client.stripColor(p.getDisplayName()));
                }
                s(jp, "account", p.getName());
                s(jp, "world", "-hidden-player-");
                s(jp, "x", 0.0);
                s(jp, "y", 64.0);
                s(jp, "z", 0.0);
                s(jp, "health", 0);
                s(jp, "armor", 0);
                s(jp, "sort", 0);//p.getSortWeight());
                a(u, "players", jp);
            }*/
        s(u, "currentcount", core.getCurrentPlayers());
        //} else {
        //    s(u, "currentcount", core.getCurrentPlayers() - hidden.size());
        // }

        s(u, "updates", new JSONArray());
        for (Object update : DynmapCore.mapManager.getWorldUpdates(worldName, since)) {
            a(u, "updates", (Client.Update) update);
        }
    }

}
