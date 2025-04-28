package org.dynmap;

import org.bukkit.Location;

/**
 * Generic block location
 */
public class DynmapLocation {
    public double x, y, z;
    public String world;
    
    public DynmapLocation() {}
    
    public DynmapLocation(String w, double x, double y, double z) {
        world = w;
        this.x = x; this.y = y; this.z = z;
    }

    public DynmapLocation(Location l) {
        world = l.getWorld().getName();
        this.x = l.blockX(); this.y = l.blockY(); this.z = l.getBlockZ();
    }
    public String toString() {
    	return String.format("{%s,%f,%f,%f}", world, x, y, z);
    }
}
