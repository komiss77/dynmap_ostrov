package org.dynmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;
import org.dynmap.bukkit.DynmapPlugin;
import org.json.simple.JSONObject;

public class ClientConfigurationComponent extends Component {
    public ClientConfigurationComponent(final DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        
        core.events.<JSONObject>addListener("buildclientconfiguration", (JSONObject t) -> {
            ConfigurationNode c = core.configuration;
            s(t, "confighash", core.getConfigHashcode());
            s(t, "updaterate", c.getFloat("updaterate", 1.0f));
            s(t, "showplayerfacesinmenu", c.getBoolean("showplayerfacesinmenu", true));
            s(t, "joinmessage", c.getString("joinmessage", "%playername% joined"));
            s(t, "quitmessage", c.getString("quitmessage", "%playername% quit"));
            s(t, "spammessage", c.getString("spammessage", "You may only chat once every %interval% seconds."));
            s(t, "webprefix", unescapeString(c.getString("webprefix", "[WEB] ")));
            s(t, "defaultzoom", c.getInteger("defaultzoom", 0));
            s(t, "sidebaropened", c.getString("sidebaropened", "false"));
            s(t, "dynmapversion", "os");
            s(t, "coreversion", core.getDynmapCoreVersion());
            s(t, "cyrillic", c.getBoolean("cyrillic-support", false));
            s(t, "showlayercontrol", c.getString("showlayercontrol", "true"));
            s(t, "grayplayerswhenhidden", c.getBoolean("grayplayerswhenhidden", true));
            s(t, "login-enabled", core.isLoginSupportEnabled());
            String sn = core.getServer().getServerName();
            if(sn.equals("Unknown Server"))
                sn = "Minecraft Dynamic Map";
            s(t, "title", c.getString("webpage-title", sn));
            s(t, "msg-maptypes", c.getString("msg/maptypes", "Map Types"));
            s(t, "msg-players", c.getString("msg/players", "Players"));
            s(t, "msg-chatrequireslogin", c.getString("msg/chatrequireslogin", "Chat Requires Login"));
            s(t, "msg-chatnotallowed", c.getString("msg/chatnotallowed", "You are not permitted to send chat messages"));
            s(t, "msg-hiddennamejoin", c.getString("msg/hiddennamejoin", "Player joined"));
            s(t, "msg-hiddennamequit", c.getString("msg/hiddennamequit", "Player quit"));
            s(t, "maxcount", core.getMaxPlayers());
            
            DynmapWorld defaultWorld = null;
            String defmap = null;
            a(t, "worlds", null);
            
            for(DynmapWorld dw : DynmapPlugin.worlds()) {
                if (dw.maps.isEmpty()) continue;
                if (defaultWorld == null) defaultWorld = dw;
                JSONObject wo = new JSONObject();
                s(wo, "name", dw.dynmapName());
                s(wo, "title", dw.getTitle());
                s(wo, "protected", dw.isProtected());
                DynmapLocation center = dw.getCenterLocation();
                s(wo, "center/x", center.x);
                s(wo, "center/y", center.y);
                s(wo, "center/z", center.z);
                s(wo, "extrazoomout", dw.getExtraZoomOutLevels());
                s(wo, "sealevel", dw.sealevel);
                s(wo, "worldheight", dw.worldheight);
                a(t, "worlds", wo);
                
                for(MapType mt : dw.maps) {
                    mt.buildClientConfiguration(wo, dw);
                    if(defmap == null) defmap = mt.getName();
                }
            }
            s(t, "defaultworld", c.getString("defaultworld", defaultWorld == null ? "world" : defaultWorld.dynmapName()));
            s(t, "defaultmap", c.getString("defaultmap", defmap == null ? "surface" : defmap));
            if(c.getString("followmap", null) != null)
                s(t, "followmap", c.getString("followmap"));
            if(c.getInteger("followzoom",-1) >= 0)
                s(t, "followzoom", c.getInteger("followzoom", 0));
        });
    }
    
}
