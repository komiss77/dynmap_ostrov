package org.dynmap.bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapMapCommands;
import org.dynmap.DynmapWorld;
import org.dynmap.exporter.DynmapExpCommands;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.markers.impl.MarkerAPIImpl;
import ru.komiss77.ApiOstrov;


public class Cmd {
    
    private static DynmapMapCommands dmapcmds = new DynmapMapCommands();
    private static DynmapExpCommands dynmapexpcmds = new DynmapExpCommands();
    
    /* Parse argument strings : handle quoted strings */
    public static String[] parseArgs(String[] args, CommandSender snd) {
        return parseArgs(args, snd, false);
    }

    /* Parse argument strings : handle quoted strings */
    public static String[] parseArgs(String[] args, CommandSender snd, boolean allowUnclosedQuotes) {
        ArrayList<String> rslt = new ArrayList<String>();
        /* Build command line, so we can parse our way - make sure there is trailing space */
        String cmdline = "";
        for(int i = 0; i < args.length; i++) {
            cmdline += args[i] + " ";
        }
        boolean inquote = false;
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < cmdline.length(); i++) {
            char c = cmdline.charAt(i);
            if(inquote) {   /* If in quote, accumulate until end or another quote */
                if(c == '\"') { /* End quote */
                    inquote = false;
                }
                else {
                    sb.append(c);
                }
            }
            else if(c == '\"') {    /* Start of quote? */
                inquote = true;
            }
            else if(c == ' ') { /* Ending space? */
                rslt.add(sb.toString());
                sb.setLength(0);
            }
            else {
                sb.append(c);
            }
        }
        if(inquote) {  // If still in quote
            if(allowUnclosedQuotes) {
                if(sb.length() > 1) { // Add remaining input without trailing space
                    rslt.add(sb.substring(0, sb.length() - 1));
                }
            } else { // Syntax error
                snd.sendMessage("Error: unclosed doublequote");
                return null;
            }
        }
        return rslt.toArray(new String[rslt.size()]);
    }

    private static final Set<String> commands = new HashSet<String>(Arrays.asList(new String[] {
        "render",
        "hide",
        "show",
        "version",
        "fullrender",
        "cancelrender",
        "radiusrender",
        "updaterender",
        "stats",
        "triggerstats",
        "resetstats",
        "sendtoweb",
        "pause",
        "purgequeue",
        "purgemap",
        "purgeworld",
        "quiet",
        "ids-for-ip",
        "ips-for-id",
        "add-id-for-ip",
        "del-id-for-ip",
        "webregister",
        "dumpmemory",
        "url",
        "help"}));

    private static class CommandInfo {
        final String cmd;
        final String subcmd;
        final String args;
        final String helptext;
        public CommandInfo(String cmd, String subcmd, String helptxt) {
            this.cmd = cmd;
            this.subcmd = subcmd;
            this.helptext = helptxt;
            this.args = "";
        }
        public CommandInfo(String cmd, String subcmd, String args, String helptxt) {
            this.cmd = cmd;
            this.subcmd = subcmd;
            this.args = args;
            this.helptext = helptxt;
        }
        public boolean matches(String c, String sc) {
            return (cmd.equals(c) && subcmd.equals(sc));
        }
        public boolean matches(String c) {
            return cmd.equals(c);
        }
    };
    
    private static final CommandInfo[] commandinfo = {
        new CommandInfo("dynmap", "", "Control execution of dynmap."),
        new CommandInfo("dynmap", "hide", "Hides the current player from the map."),
        new CommandInfo("dynmap", "hide", "<player>", "Hides <player> on the map."),
        new CommandInfo("dynmap", "show", "Shows the current player on the map."),
        new CommandInfo("dynmap", "show", "<player>", "Shows <player> on the map."),
        new CommandInfo("dynmap", "render", "Renders the tile at your location."),
        new CommandInfo("dynmap", "fullrender", "Render all maps for entire world from your location."),
        new CommandInfo("dynmap", "fullrender", "<world>", "Render all maps for world <world>."),
        new CommandInfo("dynmap", "fullrender", "<world>:<map>", "Render map <map> of world <world>."),
        new CommandInfo("dynmap", "fullrender", "resume <world>", "Resume render of all maps for world <world>. Skip already rendered tiles."),
        new CommandInfo("dynmap", "fullrender", "resume <world>:<map>", "Resume render of map <map> of world <world>. Skip already rendered tiles."),
        new CommandInfo("dynmap", "radiusrender", "<radius>", "Render at least <radius> block radius from your location on all maps."),
        new CommandInfo("dynmap", "radiusrender", "<radius> <mapname>", "Render at least <radius> block radius from your location on map <mapname>."),
        new CommandInfo("dynmap", "radiusrender", "<world> <x> <z> <radius>", "Render at least <radius> block radius from location <x>,<z> on world <world>."),
        new CommandInfo("dynmap", "radiusrender", "<world> <x> <z> <radius> <map>", "Render at least <radius> block radius from location <x>,<z> on world <world> on map <map>."),
        new CommandInfo("dynmap", "updaterender", "Render updates starting at your location on all maps."),
        new CommandInfo("dynmap", "updaterender", "<map>", "Render updates starting at your location on map <map>."),
        new CommandInfo("dynmap", "updaterender", "<world> <x> <z> <map>", "Render updates starting at location <x>,<z> on world <world> for map <map>."),
        new CommandInfo("dynmap", "cancelrender", "Cancels any active renders on current world."),
        new CommandInfo("dynmap", "cancelrender", "<world>", "Cancels any active renders of world <world>."),
        new CommandInfo("dynmap", "stats", "Show render statistics."),
        new CommandInfo("dynmap", "triggerstats", "Show render update trigger statistics."),
        new CommandInfo("dynmap", "resetstats", "Reset render statistics."),
        new CommandInfo("dynmap", "sendtoweb", "<msg>", "Send message <msg> to web users."),
        new CommandInfo("dynmap", "purgequeue", "Empty all pending tile updates from update queue."),
        new CommandInfo("dynmap", "purgequeue", "<world>", "Empty all pending tile updates from update queue for world <world>."),
        new CommandInfo("dynmap", "purgemap", "<world> <map>", "Delete all existing tiles for map <map> on world <world>."),
        new CommandInfo("dynmap", "purgeworld", "<world>", "Delete all existing directories for world <world>."),
        new CommandInfo("dynmap", "pause", "Show render pause state."),
        new CommandInfo("dynmap", "pause", "<all|none|full|update>", "Set render pause state."),
        new CommandInfo("dynmap", "quiet", "Stop output from active jobs."),
        new CommandInfo("dynmap", "ids-for-ip", "<ipaddress>", "Show player IDs that have logged in from address <ipaddress>."),
        new CommandInfo("dynmap", "ips-for-id", "<player>", "Show IP addresses that have been used for player <player>."),
        new CommandInfo("dynmap", "add-id-for-ip", "<player> <ipaddress>", "Associate player <player> with IP address <ipaddress>."),
        new CommandInfo("dynmap", "del-id-for-ip", "<player> <ipaddress>", "Disassociate player <player> from IP address <ipaddress>."),
        new CommandInfo("dynmap", "webregister", "Start registration process for creating web login account"),
        new CommandInfo("dynmap", "webregister", "<player>", "Start registration process for creating web login account for player <player>"),
        new CommandInfo("dynmap", "version", "Return version information"),
        new CommandInfo("dynmap", "dumpmemory", "Return mempry use information"),
        new CommandInfo("dynmap", "url", "Return confgured URL for Dynmap web"),
        new CommandInfo("dmarker", "", "Manipulate map markers."),
        new CommandInfo("dmarker", "add", "<label>", "Add new marker with label <label> at current location (use double-quotes if spaces needed)."),
        new CommandInfo("dmarker", "add", "id:<id> <label>", "Add new marker with ID <id> at current location (use double-quotes if spaces needed)."),
        new CommandInfo("dmarker", "movehere", "<label>", "Move marker with label <label> to current location."),
        new CommandInfo("dmarker", "movehere", "id:<id>", "Move marker with ID <id> to current location."),
        new CommandInfo("dmarker", "update", "<label> icon:<icon> newlabel:<newlabel>", "Update marker with ID <id> with new label <newlabel> and new icon <icon>."),
        new CommandInfo("dmarker", "delete", "<label>", "Delete marker with label of <label>."),
        new CommandInfo("dmarker", "delete", "id:<id>", "Delete marker with ID of <id>."),
        new CommandInfo("dmarker", "list", "List details of all markers."),
        new CommandInfo("dmarker", "icons", "List details of all icons."),
        new CommandInfo("dmarker", "addset", "<label>", "Add marker set with label <label>."),
        new CommandInfo("dmarker", "addset", "id:<id> <label>", "Add marker set with ID <id> and label <label>."),
        new CommandInfo("dmarker", "updateset", "id:<id> newlabel:<newlabel>", "Update marker set with ID <id> with new label <newlabel>."),
        new CommandInfo("dmarker", "updateset", "<label> newlabel:<newlabel>", "Update marker set with label <label> to new label <newlabel>."),
        new CommandInfo("dmarker", "deleteset", "<label>", "Delete marker set with label <label>."),
        new CommandInfo("dmarker", "deleteset", "id:<id>", "Delete marker set with ID of <id>."),
        new CommandInfo("dmarker", "listsets", "List all marker sets."),
        new CommandInfo("dmarker", "addicon", "id:<id> <label> file:<filename>", "Install new icon with ID <id> using image file <filename>"),
        new CommandInfo("dmarker", "updateicon", "id:<id> newlabel:<newlabel> file:<filename>", "Update existing icon with ID of <id> with new label or file."),
        new CommandInfo("dmarker", "updateicon", "<label> newlabel:<newlabel> file:<filename>", "Update existing icon with label of <label> with new label or file."),
        new CommandInfo("dmarker", "deleteicon", "id:<id>", "Remove icon with ID of <id>."),
        new CommandInfo("dmarker", "deleteicon", "<label>", "Remove icon with label of <label>."),
        new CommandInfo("dmarker", "addcorner", "Add corner to corner list using current location."),
        new CommandInfo("dmarker", "addcorner", "<x> <y> <z> <world>", "Add corner with coordinate <x>, <y>, <z> on world <world> to corner list."),
        new CommandInfo("dmarker", "clearcorners", "Clear corner list."),
        new CommandInfo("dmarker", "addarea", "<label>", "Add new area marker with label of <label> using current corner list."),
        new CommandInfo("dmarker", "addarea", "id:<id> <label>", "Add new area marker with ID of <id> using current corner list."),
        new CommandInfo("dmarker", "deletearea", "<label>", "Delete area marker with label of <label>."),
        new CommandInfo("dmarker", "deletearea", "id:<id> <label>", "Delete area marker with ID of <id>."),
        new CommandInfo("dmarker", "listareas", "List details of all area markers."),
        new CommandInfo("dmarker", "updatearea", "<label> <arg>:<value> ...", "Update attributes of area marker with label of <label>."),
        new CommandInfo("dmarker", "updatearea", "id:<id> <arg>:<value> ...", "Update attributes of area marker with ID of <id>."),
        new CommandInfo("dmarker", "addline", "<label>", "Add new poly-line marker with label of <label> using current corner list."),
        new CommandInfo("dmarker", "addline", "id:<id> <label>", "Add new poly-line marker with ID of <id> using current corner list."),
        new CommandInfo("dmarker", "deleteline", "<label>", "Delete poly-line marker with label of <label>."),
        new CommandInfo("dmarker", "deleteline", "id:<id>", "Delete poly-line marker with ID of <id>."),
        new CommandInfo("dmarker", "listlines", "List details of all poly-line markers."),
        new CommandInfo("dmarker", "updateline", "<label> <arg>:<value> ...", "Update attributes of poly-line marker with label of <label>."),
        new CommandInfo("dmarker", "updateline", "id:<id> <arg>:<value> ...", "Update attributes of poly-line marker with ID of <id>."),
        new CommandInfo("dmarker", "addcircle", "<label> radius:<rad>", "Add new circle centered at current location with radius of <radius> and label of <label>."),
        new CommandInfo("dmarker", "addcircle", "id:<id> <label> radius:<rad>", "Add new circle centered at current location with radius of <radius> and ID of <id>."),
        new CommandInfo("dmarker", "addcircle", "<label> radius:<rad> x:<x> y:<y> z:<z> world:<world>", "Add new circle centered at <x>,<y>,<z> on world <world> with radius of <rad> and label of <label>."),
        new CommandInfo("dmarker", "deletecircle", "<label>", "Delete circle with label of <label>."),
        new CommandInfo("dmarker", "deletecircle", "id:<id>", "Delete circle with ID of <id>."),
        new CommandInfo("dmarker", "listcircles", "List details of all circles."),
        new CommandInfo("dmarker", "updatecircle", "<label> <arg>:<value> ...", "Update attributes of circle with label of <label>."),
        new CommandInfo("dmarker", "updatecircle", "id:<id> <arg>:<value> ...", "Update attributes of circle with ID of <id>."),
        new CommandInfo("dmap", "", "List and modify dynmap configuration."),
        new CommandInfo("dmap", "worldlist", "List all worlds configured (enabled or disabled)."),
        new CommandInfo("dmap", "worldset", "<world> enabled:<true|false>", "Enable or disable world named <world>."),
        new CommandInfo("dmap", "worldset", "<world> center:<x/y/z|here|default>", "Set map center for world <world> to ccoordinates <x>,<y>,<z>."),
        new CommandInfo("dmap", "worldset", "<world> extrazoomout:<N>", "Set extra zoom out levels for world <world>."),
        new CommandInfo("dmap", "maplist", "<world>", "List all maps for world <world>"),
        new CommandInfo("dmap", "mapdelete", "<world>:<map>", "Delete map <map> from world <world>."),
        new CommandInfo("dmap", "mapadd", "<world>:<map> <attrib>:<value> <attrib>:<value>", "Create map for world <world> with name <map> using provided attributes."),
        new CommandInfo("dmap", "mapset", "<world>:<map> <attrib>:<value> <attrib>:<value>", "Update map <map> of world <world> with new attribute values."),
        new CommandInfo("dmap", "worldreset", "<world>", "Reset world <world> to default template for world type"),
        new CommandInfo("dmap", "worldreset", "<world> <templatename>", "Reset world <world> to template <templatename>."),
        new CommandInfo("dmap", "worldgetlimits", "<world>", "List visibity and hidden limits for world"),
        new CommandInfo("dmap", "worldaddlimit", "<world> corner1:<x>/<z> corner2:<x>/<z>", "Add rectangular visibilty limit"),
        new CommandInfo("dmap", "worldaddlimit", "<world> type:round center:<x>/<z> radius:<radius>", "Add round visibilty limit"),
        new CommandInfo("dmap", "worldaddlimit", "<world> limittype:hidden corner1:<x>/<z> corner2:<x>/<z>", "Add rectangular hidden limit"),
        new CommandInfo("dmap", "worldaddlimit", "<world> limittype:hidden hitype:round center:<x>/<z> radius:<radius>", "Add round hidden limit"),
        new CommandInfo("dmap", "worldremovelimit", "<world> <limit-index>", "Remove world limit with index limit-index"),        
        new CommandInfo("dynmapexp", "", "Set and execute exports in OBJ format."),
        new CommandInfo("dynmapexp", "set", "<attrib> <value> ...", "Set bounds attributes for OBJ export."),
        new CommandInfo("dynmapexp", "reset", "Reset all bounds for OBJ export."),
        new CommandInfo("dynmapexp", "pos0", "Set first corner of bounds to player's position."),
        new CommandInfo("dynmapexp", "pos1", "Set second corner of bounds to player's position."),
        new CommandInfo("dynmapexp", "radius", "<radius>", "Set bounds to radius <radius> around player's position."),
        new CommandInfo("dynmapexp", "export", "<name>", "Export map data to <name>.zip in export path."),
        new CommandInfo("dynmapexp", "purge", "<name>", "Purge exported map data <name>.zip from export path.")
    };
    
    public static void printCommandHelp(CommandSender sender, String cmd, String subcmd) {
        boolean matched = false;
        if((subcmd != null) && (!subcmd.equals(""))) {
            for(CommandInfo ci : commandinfo) {
                if(ci.matches(cmd, subcmd)) {
                    sender.sendMessage(String.format("/%s %s %s - %s", ci.cmd, ci.subcmd, ci.args, ci.helptext));
                    matched = true;
                }
            }
            if(!matched) {
                sender.sendMessage("Invalid subcommand: " + subcmd);
            }
            else {
                return;
            }
        }
        for(CommandInfo ci : commandinfo) {
            if(ci.matches(cmd, "")) {
                sender.sendMessage(String.format("/%s - %s", ci.cmd, ci.helptext));
            }
        }
        String subcmdlist = " Valid subcommands:";
        TreeSet<String> ts = new TreeSet<String>();
        for(CommandInfo ci : commandinfo) {
            if(ci.matches(cmd)) {
                ts.add(ci.subcmd);
            }
        }
        for(String sc : ts) {
            subcmdlist += " " + sc;
        }
        sender.sendMessage(subcmdlist);
    }

    /**
     * Returns tab completion suggestions for subcommands
     *
     * @param sender - The command sender requesting the tab completion suggestions
     * @param cmd    - The top level command to suggest for
     * @param arg    - Optional partial subcommand name to filter by
     * @return List of tab completion suggestions
     */
    public static List<String> getSubcommandSuggestions(CommandSender sender, String cmd, String arg) {
        List<String> suggestions = new ArrayList<>();

        for (CommandInfo ci : commandinfo) {
            //TODO: Permission checks
            if (ci.matches(cmd) && ci.subcmd.startsWith(arg) && !suggestions.contains(ci.subcmd)) {
                suggestions.add(ci.subcmd);
            }
        }

        return suggestions;
    }

    /**
     * Returns tab completion suggestions for world names
     *
     * @param arg - Partial world name to filter by
     * @return List of tab completion suggestions
     */
    public static List<String> getWorldSuggestions(String arg) {
        return DynmapPlugin.mapManager.getWorlds().stream()
                .map(DynmapWorld::getName)
                .filter(name -> name.startsWith(arg))
                .collect(Collectors.toList());
    }

    /**
     * Returns tab completion suggestions for map names of a specific world
     *
     * @param worldName      - Name of the world
     * @param mapArg         - Partial map name to filter by
     * @param colonSeparated - Whether to return suggestions in world:map format
     * @return List of tab completion suggestions
     */
    public static List<String> getMapSuggestions(String worldName, String mapArg, boolean colonSeparated) {
        DynmapWorld world = DynmapPlugin.mapManager.getWorld(worldName);

        if (world != null) {
            //Don't suggest anything if the argument contains a space as the client doesn't handle this well
            if(mapArg.contains(" ")) {
                return Collections.emptyList();
            }

            return world.maps.stream()
                    .filter(map -> map.getName().startsWith(mapArg))
                    .map(map -> {
                        if (map.getName().contains(" ")) { //Quote if map name contains a space
                            return "\"" + (colonSeparated ? worldName + ":" + map.getName() : map.getName()) + "\"";
                        } else {
                            return colonSeparated ? worldName + ":" + map.getName() : map.getName();
                        }
                    })
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * Returns tab completion suggestions for map names without a world name, in world:map format
     *
     * @param arg - Partial world:map name to filter by
     * @return List of tab completion suggestions
     */
    public static List<String> getMapSuggestions(String arg) {
        int colon = arg.indexOf(":");
        final String worldName = (colon >= 0) ? arg.substring(0, colon) : arg;
        String mapArg = (colon >= 0) ? arg.substring(colon + 1) : null;

        //Don't suggest anything if the argument contains a space as the client doesn't handle this well
        if(arg.contains(" ")) {
            return Collections.emptyList();
        }

        if (mapArg != null) {
            return getMapSuggestions(worldName, mapArg, true);
        }

        List<String> suggestions = new ArrayList<>();

        DynmapPlugin.mapManager.getWorlds().stream()
                .filter(world -> world.getName().startsWith(worldName))
                .forEach(world -> {
                    List<String> maps = world.maps.stream()
                            .map(map -> {
                                if (map.getName().contains(" ")) { //Quote if map name contains a space
                                    return "\"" + world.getName() + ":" + map.getName() + "\"";
                                } else {
                                    return world.getName() + ":" + map.getName();
                                }
                            })
                            .collect(Collectors.toList());
                    suggestions.addAll(maps);
                });

        return suggestions;
    }

    /**
     * Returns tab completion suggestions for field:value args based on the provided arguments
     * If the last provided argument contains a ":", values for the field will be suggested if present
     * Otherwise fields will be suggested if they do not already exist with a value in the provided arguments
     *
     * @param args - Array of already provided command arguments
     * @param fields - Map of possible field names and suppliers for values
     * @return List of tab completion suggestions
     */
    public static List<String> getFieldValueSuggestions(String[] args, Map<String, Supplier<String[]>> fields) {
        if (args.length == 0 || fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>(fields.keySet());
        String[] lastArgument = args[args.length - 1].split(":", 2);

        //If last argument is an incomplete field value, suggest matching values for that field.
        if (lastArgument.length == 2) {
            if (fields.containsKey(lastArgument[0])) {
                //Don't suggest anything if the value contains a space as the client doesn't handle this well
                if(lastArgument[1].contains(" ")) {
                    return Collections.emptyList();
                }

                return Arrays.stream(fields.get(lastArgument[0]).get())
                        .filter(value -> value.startsWith(lastArgument[1]))
                        //Format suggestions as field:value, quoting the value if it contains a space
                        .map(value -> lastArgument[0] + ":" + (value.contains(" ") ? "\"" + value + "\"" : value))
                        .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        }

        //Remove fields with values in previous args from suggestions
        for (String arg : args) {
            String[] value = arg.split(":");

            if (suggestions.contains(value[0]) && value.length == 2) {
                suggestions.remove(value[0]);
            }
        }

        //Suggest remaining fields
        return suggestions.stream().
                filter(field -> field.startsWith(args[args.length - 1]))
                .map(field -> field + ":")
                .collect(Collectors.toList());
    }

    public static boolean processCommand(CommandSender sender, String cmd, String commandLabel, String[] args) {
        if (!ApiOstrov.canBeBuilder(sender)) { // Initialization faulure
            sender.sendMessage("builder");
            return true;
        }
        if (DynmapPlugin.mapManager == null) { // Initialization faulure
            sender.sendMessage("Dynmap failed to initialize properly: commands not available");
            return true;
        }
        if(cmd.equalsIgnoreCase("dmarker")) {
            if(!MarkerAPIImpl.onCommand(DynmapPlugin.core, sender, cmd, commandLabel, args)) {
                printCommandHelp(sender, cmd, (args.length > 0)?args[0]:"");
            }
            return true;
        }
        if (cmd.equalsIgnoreCase("dmap")) {
            if(!dmapcmds.processCommand(sender, cmd, commandLabel, args, DynmapPlugin.core)) {
                printCommandHelp(sender, cmd, (args.length > 0)?args[0]:"");
            }
            return true;
        }
        if (cmd.equalsIgnoreCase("dynmapexp")) {
            if(!dynmapexpcmds.processCommand(sender, cmd, commandLabel, args, DynmapPlugin.core)) {
                printCommandHelp(sender, cmd, (args.length > 0)?args[0]:"");
            }
            return true;
        }
        if (!cmd.equalsIgnoreCase("dynmap"))
            return false;
        Player player = null;
        if (sender instanceof Player)
            player = (Player) sender;
        /* Re-parse args - handle doublequotes */
        args = parseArgs(args, sender);
        
        if(args == null) {
            printCommandHelp(sender, cmd, "");
            return true;
        }

        if (args.length > 0) {
            String c = args[0];
            if (!commands.contains(c)) {
                printCommandHelp(sender, cmd, "");
                return true;
            }
            
            if (c.equals("render") ){// && checkPlayerPermission(sender,"render")) {
                if (player != null) {
                    DynmapLocation loc = new DynmapLocation(player.getLocation());
                    if (loc != null) {
                        DynmapPlugin.mapManager.touch(loc.world, (int)loc.x, (int)loc.y, (int)loc.z, "render");
                        sender.sendMessage("Tile render queued.");
                    }
                }
                else {
                    sender.sendMessage("Command can only be issued by player.");
                }
            }
            else if(c.equals("radiusrender") ){//&& checkPlayerPermission(sender,"radiusrender")) {
                int radius = 0;
                String mapname = null;
                DynmapLocation loc = null;
                if((args.length == 2) || (args.length == 3)) {  /* Just radius, or <radius> <map> */
                    try {
                        radius = Integer.parseInt(args[1]); /* Parse radius */
                    } catch (NumberFormatException nfe) {
                        sender.sendMessage("Invalid radius: " + args[1]);
                        return true;
                    }
                    if(radius < 0)
                        radius = 0;
                    if(args.length > 2)
                        mapname = args[2];
                    if (player != null)
                        loc = new DynmapLocation(player.getLocation());
                    else
                        sender.sendMessage("Command require <world> <x> <z> <radius> if issued from console.");
                }
                else if(args.length > 3) {  /* <world> <x> <z> */
                    DynmapWorld w = DynmapPlugin.mapManager.worldsLookup.get(args[1]);   /* Look up world */
                    if(w == null) {
                        sender.sendMessage("World '" + args[1] + "' not defined/loaded");
                    }
                    int x = 0, z = 0;
                    try {
                        x = Integer.parseInt(args[2]);
                    } catch (NumberFormatException nfe) {
                        sender.sendMessage("Invalid x coord: " + args[2]);
                        return true;
                    }
                    try {
                        z = Integer.parseInt(args[3]);
                    } catch (NumberFormatException nfe) {
                        sender.sendMessage("Invalid z coord: " + args[3]);
                        return true;
                    }
                    if(args.length > 4) {
                        try {
                            radius = Integer.parseInt(args[4]);
                        } catch (NumberFormatException nfe) {
                            sender.sendMessage("Invalid radius: " + args[4]);
                            return true;
                        }
                    }
                    if(args.length > 5)
                        mapname = args[5];
                    if(w != null)
                        loc = new DynmapLocation(w.getName(), x, 64, z);
                }
                if(loc != null)
                    DynmapPlugin.mapManager.renderWorldRadius(loc, sender, mapname, radius);
            } else if(c.equals("updaterender") ){//&& checkPlayerPermission(sender,"updaterender")) {
                String mapname = null;
                DynmapLocation loc = null;
                if(args.length <= 3) {  /* Just command, or command plus map */
                    if(args.length > 1)
                        mapname = args[1];
                    if (player != null)
                        loc = new DynmapLocation(player.getLocation());
                    else
                        sender.sendMessage("Command require <world> <x> <z> <mapname> if issued from console.");
                }
                else {  /* <world> <x> <z> */
                    DynmapWorld w = DynmapPlugin.mapManager.worldsLookup.get(args[1]);   /* Look up world */
                    if(w == null) {
                        sender.sendMessage("World '" + args[1] + "' not defined/loaded");
                    }
                    int x = 0, z = 0;
                    try {
                        x = Integer.parseInt(args[2]);
                    } catch (NumberFormatException nfe) {
                        sender.sendMessage("Invalid x coord: " + args[2]);
                        return true;
                    }
                    try {
                        z = Integer.parseInt(args[3]);
                    } catch (NumberFormatException nfe) {
                        sender.sendMessage("Invalid z coord: " + args[3]);
                        return true;
                    }
                    if(args.length > 4)
                        mapname = args[4];
                    if(w != null)
                        loc = new DynmapLocation(w.getName(), x, 64, z);
                }
                if(loc != null)
                    DynmapPlugin.mapManager.renderFullWorld(loc, sender, mapname, true, false);
            } /*else if (c.equals("hide")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"hide.self")) {
                        playerList.setVisible(player.getName(),false);
                        sender.sendMessage("You are now hidden on Dynmap.");
                    }
                } else if (checkPlayerPermission(sender,"hide.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],false);
                        sender.sendMessage(args[i] + " is now hidden on Dynmap.");
                    }
                }
            } else if (c.equals("show")) {
                if (args.length == 1) {
                    if(player != null && checkPlayerPermission(sender,"show.self")) {
                        playerList.setVisible(player.getName(),true);
                        sender.sendMessage("You are now visible on Dynmap.");
                    }
                } else if (checkPlayerPermission(sender,"show.others")) {
                    for (int i = 1; i < args.length; i++) {
                        playerList.setVisible(args[i],true);
                        sender.sendMessage(args[i] + " is now visible on Dynmap.");
                    }
                }
            }
            */else if (c.equals("fullrender") ){//&& checkPlayerPermission(sender,"fullrender")) {
                String map = null;
                if (args.length > 1) {
                    boolean resume = false;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equalsIgnoreCase("resume")) {
                             resume = true;
                             continue;
                        }
                        int dot = args[i].indexOf(":");
                        DynmapWorld w;
                        String wname = args[i];
                        if(dot >= 0) {
                            wname = args[i].substring(0, dot);
                            map = args[i].substring(dot+1);
                        }
                        w = DynmapPlugin.mapManager.getWorld(wname);
                        if(w != null) {
                            DynmapLocation loc;
                            if(w.center != null)
                                loc = w.center;
                            else
                                loc = w.getSpawnLocation();
                            DynmapPlugin.mapManager.renderFullWorld(loc,sender, map, false, resume);
                        }
                        else
                            sender.sendMessage("World '" + wname + "' not defined/loaded");
                    }
                } else if (player != null) {
                    DynmapLocation loc = new DynmapLocation(player.getLocation());
                    if(args.length > 1)
                        map = args[1];
                    if(loc != null)
                        DynmapPlugin.mapManager.renderFullWorld(loc, sender, map, false, false);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("cancelrender") ){//&& checkPlayerPermission(sender,"cancelrender")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        DynmapWorld w = DynmapPlugin.mapManager.getWorld(args[i]);
                        if(w != null)
                            DynmapPlugin.mapManager.cancelRender(w.getName(), sender);
                        else
                            sender.sendMessage("World '" + args[i] + "' not defined/loaded");
                    }
                } else if (player != null) {
                    DynmapLocation loc = new DynmapLocation(player.getLocation());
                    if(loc != null)
                        DynmapPlugin.mapManager.cancelRender(loc.world, sender);
                } else {
                    sender.sendMessage("World name is required");
                }
            } else if (c.equals("purgequeue") ){//&& checkPlayerPermission(sender, "purgequeue")) {
                if (args.length > 1) {
                    for (int i = 1; i < args.length; i++) {
                        DynmapPlugin.mapManager.purgeQueue(sender, args[i]);
                    }
                }
                else {
                    DynmapPlugin.mapManager.purgeQueue(sender, null);
                }
            } else if (c.equals("purgemap") ){//&& checkPlayerPermission(sender,"purgemap")) {
                if (args.length > 2) {
                    DynmapPlugin.mapManager.purgeMap(sender, args[1], args[2]);
                } else {
                    sender.sendMessage("World name and map name values are required");
                }
            } else if (c.equals("purgeworld") ){//&& checkPlayerPermission(sender,"purgeworld")) {
                if (args.length > 1) {
                    DynmapPlugin.mapManager.purgeWorld(sender, args[1]);
                } else {
                    sender.sendMessage("World name is required");
                }
            } /*else if (c.equals("reload") && checkPlayerPermission(sender, "reload")) {
                sender.sendMessage("Reloading Dynmap...");
                getServer().reload();
                sender.sendMessage("Dynmap reloaded");
            } */else if (c.equals("stats") ){//&& checkPlayerPermission(sender, "stats")) {
                if(args.length == 1)
                    DynmapPlugin.mapManager.printStats(sender, null);
                else
                    DynmapPlugin.mapManager.printStats(sender, args[1]);
            } else if (c.equals("triggerstats") ){//&& checkPlayerPermission(sender, "stats")) {
                DynmapPlugin.mapManager.printTriggerStats(sender);
            } else if (c.equals("pause") ){//&& checkPlayerPermission(sender, "pause")) {
                if(args.length == 1) {
                }
                else if(args[1].equals("full")) {
                    DynmapPlugin.core.setPauseFullRadiusRenders(true);
                    DynmapPlugin.core.setPauseUpdateRenders(false);
                }
                else if(args[1].equals("update")) {
                    DynmapPlugin.core.setPauseFullRadiusRenders(false);
                    DynmapPlugin.core.setPauseUpdateRenders(true);
                }
                else if(args[1].equals("all")) {
                    DynmapPlugin.core.setPauseFullRadiusRenders(true);
                    DynmapPlugin.core.setPauseUpdateRenders(true);
                }
                else {
                    DynmapPlugin.core.setPauseFullRadiusRenders(false);
                    DynmapPlugin.core.setPauseUpdateRenders(false);
                }
                if(DynmapPlugin.core.getPauseFullRadiusRenders())
                    sender.sendMessage("Full/Radius renders are PAUSED");
                else if (DynmapPlugin.mapManager.getTPSFullRenderPause())
                    sender.sendMessage("Full/Radius renders are TPS PAUSED");
                else
                    sender.sendMessage("Full/Radius renders are ACTIVE");
                if(DynmapPlugin.core.getPauseUpdateRenders())
                    sender.sendMessage("Update renders are PAUSED");
                else if (DynmapPlugin.mapManager.getTPSUpdateRenderPause())
                    sender.sendMessage("Update renders are TPS PAUSED");
                else
                    sender.sendMessage("Update renders are ACTIVE");
                if (DynmapPlugin.mapManager.getTPSZoomOutPause())
                    sender.sendMessage("Zoom out processing is TPS PAUSED");
                else
                    sender.sendMessage("Zoom out processing is ACTIVE");
            } else if (c.equals("resetstats") ){//&& checkPlayerPermission(sender, "resetstats")) {
                if(args.length == 1)
                    DynmapPlugin.mapManager.resetStats(sender, null);
                else
                    DynmapPlugin.mapManager.resetStats(sender, args[1]);
            } else if (c.equals("sendtoweb") ){//&& checkPlayerPermission(sender, "sendtoweb")) {
                String msg = "";
                for(int i = 1; i < args.length; i++) {
                    msg += args[i] + " ";
                }
                DynmapPlugin.core.sendBroadcastToWeb("dynmap", msg);
            }  /*else if(c.equals("ids-for-ip") ){//&& checkPlayerPermission(sender, "ids-for-ip")) {
                if(args.length > 1) {
                    List<String> ids = getIDsForIP(args[1]);
                    sender.sendMessage("IDs logged in from address " + args[1] + " (most recent to least):");
                    if(ids != null) {
                        for(String id : ids)
                            sender.sendMessage("  " + id);
                    }
                }
                else {
                    sender.sendMessage("IP address required as parameter");
                }
            }else if(c.equals("ips-for-id") ){//&& checkPlayerPermission(sender, "ips-for-id")) {
                if(args.length > 1) {
                    sender.sendMessage("IP addresses logged for player " + args[1] + ":");
                    for(String ip: ids_by_ip.keySet()) {
                        LinkedList<String> ids = ids_by_ip.get(ip);
                        if((ids != null) && ids.contains(args[1])) {
                            sender.sendMessage("  " + ip);
                        }
                    }
                }
                else {
                    sender.sendMessage("Player ID required as parameter");
                }
            } else if ( c.equals("add-id-for-ip") ||//&& checkPlayerPermission(sender, "add-id-for-ip")) ||
                    c.equals("del-id-for-ip") ){//&& checkPlayerPermission(sender, "del-id-for-ip"))) {
                if(args.length > 2) {
                    String ipaddr = "";
                    try {
                        InetAddress ip = InetAddress.getByName(args[2]);
                        ipaddr = ip.getHostAddress();
                    } catch (UnknownHostException uhx) {
                        sender.sendMessage("Invalid address : " + args[2]);
                        return true;
                    }
                    LinkedList<String> ids = ids_by_ip.get(ipaddr);
                    if(ids == null) {
                        ids = new LinkedList<String>();
                        ids_by_ip.put(ipaddr, ids);
                    }
                    ids.remove(args[1]); /* Remove existing, if any *
                    if(c.equals("add-id-for-ip")) {
                        ids.addFirst(args[1]);  /* And add us first *
                        sender.sendMessage("Added player ID '" + args[1] + "' to address '" + ipaddr + "'");
                    }
                    else {
                        sender.sendMessage("Removed player ID '" + args[1] + "' from address '" + ipaddr + "'");
                    }
                    saveIDsByIP();
                }
                else {
                    sender.sendMessage("Needs player ID and IP address");
                }
            } */else if(c.equals("webregister") ){//&& checkPlayerPermission(sender, "webregister")) {
                if(DynmapPlugin.core.authmgr != null)
                    return DynmapPlugin.core.authmgr.processWebRegisterCommand(DynmapPlugin.core, sender, player, args);
                else
                    sender.sendMessage("Login support is not enabled");
            }
            else if (c.equals("quiet") ){//&& checkPlayerPermission(sender, "quiet")) {
                DynmapPlugin.mapManager.setJobsQuiet(sender);
            }
            else if(c.equals("help")) {
                printCommandHelp(sender, cmd, (args.length > 1)?args[1]:"");
            }
            else if(c.equals("dumpmemory") ){//&& checkPlayerPermission(sender, "dumpmemory")) {
            	TexturePack.tallyMemory(sender);
            }
            else if(c.equals("version")) {
                sender.sendMessage("Dynmap version: core=" + DynmapPlugin.core.getDynmapCoreVersion() + ", plugin=" + DynmapPlugin.core.getDynmapPluginVersion());
            }
            else if (c.equals("url")) {
            	if (DynmapPlugin.core.publicURL.length() > 0) {
            		sender.sendMessage("Dynmap URL for this server is: " + DynmapPlugin.core.publicURL);
            	}
            	else {
            		sender.sendMessage("URL of Dynmap not configured");
            	}
            }
            return true;
        }
        printCommandHelp(sender, cmd, (args.length > 0)?args[0]:"");

        return true;
    }

    /**
     * Returns a list of tab completion suggestions for the given sender, command and command arguments.
     *
     * @param sender - The sender of the tab completion, used for permission checks
     * @param cmd - The top level command being tab completed
     * @param args - Array of extra command arguments
     * @return List of tab completion suggestions
     */
    public static List<String> getTabCompletions(CommandSender sender, String cmd, String[] args) {
        if (DynmapPlugin.mapManager == null || args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return getSubcommandSuggestions(sender, cmd, args[0]);
        }

        if (cmd.equalsIgnoreCase("dmap")) {
            return dmapcmds.getTabCompletions(sender, args, DynmapPlugin.core);
        }

        if (cmd.equalsIgnoreCase("dmarker")) {
            return DynmapPlugin.core.markerapi.getTabCompletions(sender, args, DynmapPlugin.core);
        }

        if (cmd.equalsIgnoreCase("dynmapexp")) {
            return dynmapexpcmds.getTabCompletions(sender, args, DynmapPlugin.core);
        }

        if (!cmd.equalsIgnoreCase("dynmap")) {
            return Collections.emptyList();
        }

        /* Re-parse args - handle double quotes */
        args = parseArgs(args, sender, true);

        if (args == null || args.length <= 1) {
            return Collections.emptyList();
        }

        String subcommand = args[0];
        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (subcommand.equals("radiusrender") ){//&& checkPlayerPermission(sender, "radiusrender")) {
            if(args.length == 2) { // /dynmap radiusrender *<world>* <x> <z> <radius> <map>
                return getWorldSuggestions(args[1]);
            } if(args.length == 3 && player != null) { // /dynmap radiusrender <radius> *<mapname>*
            	try (Scanner sc = new Scanner(args[1])) {
            		if(sc.hasNextInt(10)) { //Only show map suggestions if a number was entered before
            			return getMapSuggestions(player.getLocation().getWorld().getName(), args[2], false);
            		}
            	}
            } else if(args.length == 6) { // /dynmap radiusrender <world> <x> <z> <radius> *<map>*
                return getMapSuggestions(args[1], args[5], false);
            }
        } else if (subcommand.equals("updaterender")){//&& checkPlayerPermission(sender, "updaterender")) {
            if(args.length == 2) { // /dynmap updaterender *<world>* <x> <z> <map>/*<map>*
                List<String> suggestions = getWorldSuggestions(args[1]);

                if(player != null) {
                    suggestions.addAll(getMapSuggestions(player.getLocation().getWorld().getName(), args[1], false));
                }

                return suggestions;
            } else if(args.length == 5) { // /dynmap updaterender <world> <x> <z> *<map>*
                return getMapSuggestions(args[1], args[4], false);
            }
        } /*else if (subcommand.equals("hide") ){//&& checkPlayerPermission(sender, "hide.others")) {
            if(args.length == 2) { // /dynmap hide *<player>*
                final String arg = args[1];
                return playerList.getVisiblePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.startsWith(arg))
                        .collect(Collectors.toList());
            }
        } else if (subcommand.equals("show") ){//&& checkPlayerPermission(sender, "show.others")) {
            if(args.length == 2) { // /dynmap show *<player>*
                final String arg = args[1];
                return playerList.getHiddenPlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.startsWith(arg))
                        .collect(Collectors.toList());
            }
        }*/ else if (subcommand.equals("fullrender") ){//&& checkPlayerPermission(sender, "fullrender")) {
            List<String> suggestions = getWorldSuggestions(args[args.length - 1]); //World suggestions
            suggestions.addAll(getMapSuggestions(args[args.length - 1])); //world:map suggestions

            //Remove suggestions present in other arguments
            for (String arg : args) {
                suggestions.remove(arg.contains(" ") ? "\"" + arg + "\"" : arg);
            }

            //Add resume if previous argument wasn't resume
            if ("resume".startsWith(args[args.length - 1])
                    && (args.length == 2 || !args[args.length - 2].equals("resume"))) {
                suggestions.add("resume");
            }

            return suggestions;
        } else if ( subcommand.equals("cancelrender") //&& checkPlayerPermission(sender, "cancelrender"))
                || subcommand.equals("purgequeue") ){//&& checkPlayerPermission(sender, "purgequeue"))) {
            List<String> suggestions = getWorldSuggestions(args[args.length - 1]);
            suggestions.removeAll(Arrays.asList(args)); //Remove worlds present in other arguments

            return suggestions;
        } else if (subcommand.equals("purgemap") ){//&& checkPlayerPermission(sender, "purgemap")) {
            if (args.length == 2) { // /dynmap purgemap *<world>* <map>
                return getWorldSuggestions(args[1]);
            } else if (args.length == 3) { // /dynmap purgemap <world> *<map>*
                return getMapSuggestions(args[1], args[2], false);
            }
        } else if ( subcommand.equals("purgeworld") //&& checkPlayerPermission(sender, "purgeworld"))
                || subcommand.equals("stats")// && checkPlayerPermission(sender, "stats"))
                || subcommand.equals("resetstats") ){// && checkPlayerPermission(sender, "resetstats"))) {
            if (args.length == 2) {
                return getWorldSuggestions(args[1]);
            }
        } else if (subcommand.equals("pause") ){//&& checkPlayerPermission(sender, "pause")) {
            List<String> suggestions = Arrays.asList("full", "update", "all", "none");

            if (args.length == 2) {
                final String arg = args[1];
                return suggestions.stream().filter(suggestion -> suggestion.startsWith(arg))
                        .collect(Collectors.toList());
            }
        } else if ( subcommand.equals("ips-for-id") //&& checkPlayerPermission(sender, "ips-for-id"))
                || subcommand.equals("add-id-for-ip") //&& checkPlayerPermission(sender, "add-id-for-ip"))
                || subcommand.equals("del-id-for-ip") //&& checkPlayerPermission(sender, "del-id-for-ip"))
                || subcommand.equals("webregister") ){//&& checkPlayerPermission(sender, "webregister.other"))) {
            if(args.length == 2) {
                final String arg = args[1];
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.startsWith(arg))
                        .collect(Collectors.toList());
            }
        } else if(subcommand.equals("help")) {
            if(args.length == 2) {
                return getSubcommandSuggestions(sender, "dynmap", args[1]);
            }
        }

        return Collections.emptyList();
    }

    
}
