package org.dynmap;

import org.bukkit.Location;

public class DynmapLocation {
    public int x, y, z;
    public String dwName;
    
    public DynmapLocation() {}
    
    public DynmapLocation(String w, double x, double y, double z) {
        dwName = DynmapWorld.normalizeWorldName(w);
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
    }

    public DynmapLocation(Location l) {
        dwName = DynmapWorld.normalizeWorldName(l.getWorld().getName());
        x = l.blockX(); y = l.blockY(); z = l.getBlockZ();
    }
    
    @Override
    public String toString() {
    	return String.format("{%s,%f,%f,%f}", dwName, x, y, z);
    }

    //public static String dynmapWorldName(final String w) {
    //    return DynmapWorld.normalizeWorldName(w);
    //}
}
