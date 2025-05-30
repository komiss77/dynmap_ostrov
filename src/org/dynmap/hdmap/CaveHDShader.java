package org.dynmap.hdmap;

import static org.dynmap.JSONUtils.s;
import java.io.IOException;
import java.util.BitSet;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.MapManager;
import org.dynmap.common.chunk.GenericMapChunkCache.OurMapIterator;
import org.dynmap.exporter.OBJExport;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DynLongHashMap;
import org.dynmap.utils.MapChunkCache;
import org.json.simple.JSONObject;


public class CaveHDShader implements HDShader {
    private String name;
    private boolean iflit;
    private Color startColor;
    private Color endColor;
    private BitSet hiddenids = new BitSet();

    private void setHidden(DynmapBlockState blk) {
        hiddenids.set(blk.globalStateIndex);
    }
    private void setHidden(String blkname) {
        DynmapBlockState bbs = DynmapBlockState.getBaseStateByName(blkname);
        if (bbs.isNotAir()) {
            for (int i = 0; i < bbs.getStateCount(); i++) {
                setHidden(bbs.getState(i));
            }
        }
    }
    private boolean isHidden(DynmapBlockState blk) {
        return hiddenids.get(blk.globalStateIndex);
    }
    public CaveHDShader(DynmapCore core, ConfigurationNode configuration) {
        name = (String) configuration.get("name");
        iflit = configuration.getBoolean("onlyiflit", false);
        startColor = configuration.getColor("startColor", null);
        endColor = configuration.getColor("endColor", null);
        for (int i = 0; i < DynmapBlockState.getGlobalIndexMax(); i++) {
        	DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(i);
        	if (bs.isAir() || bs.isWater()) {
        		setHidden(bs);
        	}
        }

        List<Object> hidden = configuration.getList("hiddennames");
        if(hidden != null) {
            for(Object o : hidden) {
                if(o instanceof String) {
                    setHidden((String) o);
                }
            }
        }
        else {
            setHidden(DynmapBlockState.LOG_BLOCK);
            setHidden(DynmapBlockState.LEAVES_BLOCK);
            setHidden(DynmapBlockState.GLASS_BLOCK);
            setHidden(DynmapBlockState.WOODEN_DOOR_BLOCK);
            setHidden(DynmapBlockState.IRON_DOOR_BLOCK);
            setHidden(DynmapBlockState.SNOW_BLOCK);
            setHidden(DynmapBlockState.ICE_BLOCK);
            setHidden(DynmapBlockState.SNOW_LAYER_BLOCK);
            for (int i = 0; i < DynmapBlockState.getGlobalIndexMax(); i++) {
            	DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(i);
            	if (bs.isLeaves() || bs.isSnow() || bs.isLog()) {
            		setHidden(bs);
            	}
            }
        }
    }
    
    @Override
    public boolean isBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isRawBiomeDataNeeded() { 
        return false; 
    }
    
    @Override
    public boolean isHightestBlockYDataNeeded() {
        return false;
    }

    @Override
    public boolean isBlockTypeDataNeeded() {
        return true;
    }

    @Override
    public boolean isSkyLightLevelNeeded() {
        return false;
    }

    @Override
    public boolean isEmittedLightLevelNeeded() {
        return iflit;
    }

    @Override
    public String getName() {
        return name;
    }
    
    private class OurShaderState implements HDShaderState {
        private Color color;
        protected OurMapIterator mapiter;
        protected HDMap map;
        private boolean air;
        private final int sealevel;
        private final int ymax, ymin;
        final int[] lightingTable;

        private OurShaderState(OurMapIterator mapiter, HDMap map, MapChunkCache cache) {
            this.mapiter = mapiter;
            this.map = map;
            this.color = new Color();
            this.ymax = mapiter.getWorldHeight() - 1;
            this.ymin = mapiter.getWorldYMin();
            this.sealevel = mapiter.getWorldSeaLevel();
            if (MapManager.mapman.useBrightnessTable()) {
                lightingTable = cache.getWorld().getBrightnessTable();
            }
            else {
                lightingTable = null;
            }
        }
        /**
         * Get our shader
         */
        @Override
        public HDShader getShader() {
            return CaveHDShader.this;
        }

        /**
         * Get our map
         */
        @Override
        public HDMap getMap() {
            return map;
        }
        
        /**
         * Get our lighting
         */
        @Override
        public HDLighting getLighting() {
            return map.getLighting();
        }
        
        /**
         * Reset renderer state for new ray
         */
        @Override
        public void reset(HDPerspectiveState ps) {
            color.setTransparent();
            air = true;
        }
        
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        @Override
        public boolean processBlock(HDPerspectiveState ps) {
            DynmapBlockState blocktype = ps.getBlockState();
            if (isHidden(blocktype)) {
                blocktype = DynmapBlockState.AIR;
            }
            else if (blocktype.isNotAir()) {
                air = false;
                return false;
            }
            if (blocktype.isAir() && !air) {
            	if(iflit && (ps.getMapIterator().getBlockEmittedLight() == 0)) {
            		return false;
            	}
                int cr, cg, cb;
                int mult;

                int y = mapiter.getY();
                if((startColor != null) && (endColor != null))
                {
                	double interp = ((double)(y - this.ymin)) / (this.ymax - this.ymin);
                	cr = (int)(((1.0 - interp) * startColor.getRed()) + (interp * endColor.getRed()));
                	cg = (int)(((1.0 - interp) * startColor.getGreen()) + (interp * endColor.getGreen()));
                	cb = (int)(((1.0 - interp) * startColor.getBlue()) + (interp * endColor.getBlue()));
                }
                else
                {
                    if (y < this.sealevel) {
                        cr = 0;
                        cg = 64 + ((192 * (y - this.ymin)) / (this.sealevel - this.ymin));
                        cb = 255 - (255 * (y - this.ymin)) / (this.sealevel - this.ymin);
                    } else {
                        cr = (255 * (y - this.sealevel)) / (this.ymax - this.sealevel);
                        cg = 255;
                        cb = 0;
                    }
                }
                /* Figure out which color to use */
                switch(ps.getLastBlockStep()) {
                    case X_PLUS:
                    case X_MINUS:
                        mult = 224;
                        break;
                    case Z_PLUS:
                    case Z_MINUS:
                        mult = 256;
                        break;
                    default:
                        mult = 160;
                        break;
                }
                cr = cr * mult / 256;
                cg = cg * mult / 256;
                cb = cb * mult / 256;

                color.setRGBA(cr, cg, cb, 255);
                return true;
            }
            return false;
        }        
        /**
         * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
         */
        @Override
        public void rayFinished(HDPerspectiveState ps) {
        }
        /**
         * Get result color - get resulting color for ray
         * @param c - object to store color value in
         * @param index - index of color to request (renderer specific - 0=default, 1=day for night/day renderer
         */
        @Override
        public void getRayColor(Color c, int index) {
            c.setColor(color);
        }
        /**
         * Clean up state object - called after last ray completed
         */
        @Override
        public void cleanup() {
        }
        @Override
        public DynLongHashMap getCTMTextureCache() {
            return null;
        }
        @Override
        public int[] getLightingTable() {
            return lightingTable;
        }
        @Override
        public void setLastBlockState(DynmapBlockState new_lastbs) {
        }
    }

    /**
     *  Get renderer state object for use rendering a tile
     * @param map - map being rendered
     * @param cache - chunk cache containing data for tile to be rendered
     * @param mapiter - iterator used when traversing rays in tile
     * @param scale - scale of perspective
     * @return state object to use for all rays in tile
     */
    @Override
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, OurMapIterator mapiter, int scale) {
        return new OurShaderState(mapiter, map, cache);
    }
    
    /* Add shader's contributions to JSON for map object */
    public void addClientConfiguration(JSONObject mapObject) {
        s(mapObject, "shader", name);
    }
    @Override
    public void exportAsMaterialLibrary(CommandSender sender, OBJExport out) throws IOException {
        throw new IOException("Export unsupported");
    }
    private static final String[] nulllist = new String[0];
    @Override
    public String[] getCurrentBlockMaterials(DynmapBlockState blk, OurMapIterator mapiter, int[] txtidx, BlockStep[] steps) {
        return nulllist;
    }
}
