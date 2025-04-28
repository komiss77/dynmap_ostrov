package org.dynmap.bukkit;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import org.bukkit.*;
import ru.komiss77.Ostrov;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.entity.Player;
import org.dynmap.DynmapChunk;
import org.dynmap.Log;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.Polygon;
import ru.komiss77.hook.MapChunkCacheNms;

public class BukkitHelper// extends BukkitVersionHelper 
{

    public static GenericChunkCache gencache;

    public boolean isUnsafeAsync() {
        return false;
    }

    public static IdentityHashMap<BlockType, DynmapBlockState> dataToState;


    /**
     * Create chunk cache for given chunks of given world
     *
     * @param dw - world
     * @param chunks - chunk list
     * @return cache
     */
    // @Override
    public MapChunkCache getChunkCache(BukkitWorld dw, List<DynmapChunk> chunks) {
        MapChunkCacheNms c = new MapChunkCacheNms(gencache);
        c.setChunks(dw, chunks);
        return c;
    }

    public Polygon getWorldBorder(World world) {
        Polygon p = null;
        WorldBorder wb = world.getWorldBorder();
        Location c = wb.getCenter();
        double size = wb.getSize();
        if ((size > 1) && (size < 1E7)) {
            size = size / 2;
            p = new Polygon();
            p.addVertex(c.getX() - size, c.getZ() - size);
            p.addVertex(c.getX() + size, c.getZ() - size);
            p.addVertex(c.getX() + size, c.getZ() + size);
            p.addVertex(c.getX() - size, c.getZ() + size);
        }
        return p;
    }


    private List<String> biomenames;//String[] biomenames;

    public List<String> getBiomeNames() {
        if (biomenames == null) {
            biomenames = new ArrayList<>();
            for (Biome b : Ostrov.registries.BIOMES) {
                biomenames.add(b.getKey().value());
            }
        }
        return biomenames;
    }

    public String getStateStringByCombinedId(int blkid, int meta) {
        Log.severe("getStateStringByCombinedId not implemented");
        return null;
    }

    public Object getUnloadQueue(World world) {
        //Log.warning("getUnloadQueue not implemented yet");
        // TODO Auto-generated method stub
        return null;
    }

   /* public Player[] getOnlinePlayers() {
        Collection<? extends Player> p = Bukkit.getServer().getOnlinePlayers();
        return p.toArray(Player[]::new);
    }*/

    public double getHealth(Player p) {
        return p.getHealth();
    }


    public String getSkinURL(Player player) {
        String url = player.getPlayerProfile().getTextures().getSkin().toString();//null;
        /*CraftPlayer cp = (CraftPlayer)player;
    	GameProfile profile = cp.getProfile();
    	if (profile != null) {
    		PropertyMap pm = profile.getProperties();
    		if (pm != null) {
    			Collection<Property> txt = pm.get("textures");
    	        Property textureProperty = Iterables.getFirst(pm.get("textures"), null);
    	        if (textureProperty != null) {
    				String val = textureProperty.value();
    				if (val != null) {
    					TexturesPayload result = null;
    					try {
                            String json = new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8);
    						result = gson.fromJson(json, TexturesPayload.class);
    					} catch (JsonParseException e) {
    					} catch (IllegalArgumentException x) {
    						Log.warning("Malformed response from skin URL check: " + val);
    					}
    					if ((result != null) && (result.textures != null) && (result.textures.containsKey("SKIN"))) {
    						url = result.textures.get("SKIN").url;
    					}
    				}
    			}
    		}
    	}  */
        return url;
    }

    public int getWorldMinY(World w) {
        return w.getMinHeight();//cw.getMinHeight();
    }

    public boolean useGenericCache() {
        return true;
    }

}




/**
 * Get block short name list
 */
// // @Override
/*  public String[] getBlockNames() {
    	RegistryBlockID<IBlockData> bsids = Block.q;
        Block baseb = null;
    	Iterator<IBlockData> iter = bsids.iterator();
    	ArrayList<String> names = new ArrayList<String>();
		while (iter.hasNext()) {
			IBlockData bs = iter.next();
            Block b = bs.b();
    		// If this is new block vs last, it's the base block state
    		if (b != baseb) {
                baseb = b;
                continue;
    		}
        	MinecraftKey id = BuiltInRegistries.e.b(b);
    		String bn = id.toString();
            if (bn != null) {
            	names.add(bn);
            	Log.info("block=" + bn);
            }
		}
        return names.toArray(new String[0]);
    }*/
//  private static IRegistry<BiomeBase> reg = null;
//  private static IRegistry<BiomeBase> getBiomeReg() {
//  	if (reg == null) {
//  		reg = MinecraftServer.getServer().ba().e(Registries.aI); // MinecraftServer.registryAccess().lookupOrThrow(Registries.BIOME)
// 	}
// 	return reg;
// }
//  private Object[] biomelist;
/**
 * Get list of defined biomebase objects
 */
// @Override
/* public Object[] getBiomeBaseList() {
    	if (biomelist == null) {
        	biomelist = new BiomeBase[256];
        	Iterator<BiomeBase> iter = getBiomeReg().iterator();
        	while (iter.hasNext()) {
                BiomeBase b = iter.next();
                int bidx = getBiomeReg().a(b); // iRegistry.getId
        		if (bidx >= biomelist.length) {
        			biomelist = Arrays.copyOf(biomelist, bidx + biomelist.length);
        		}
        		biomelist[bidx] = b;
        	}
        }
        return biomelist;
    }*/
/**
 * Get ID from biomebase
 */
// @Override
// public int getBiomeBaseID(Object bb) {
//	return getBiomeReg().a((BiomeBase)bb);
// }
/**
 * Get biome base water multiplier
 */
// @Override
//public int getBiomeBaseWaterMult(Object bb) {
//BiomeBase biome = (BiomeBase) bb;
//return biome.i();	// waterColor
//}
/**
 * Get temperature from biomebase
 */
// @Override
// public float getBiomeBaseTemperature(Object bb) {
// 	return ((BiomeBase)bb).g();
// }
/**
 * Get humidity from biomebase
 */
// @Override
/*public float getBiomeBaseHumidity(Object bb) {
    	String vals = ((BiomeBase)bb).i.toString();	// Sleazy
    	float humidity = 0.5F;
    	int idx = vals.indexOf("downfall=");
    	if (idx >= 0) {
        	humidity = Float.parseFloat(vals.substring(idx+9, vals.indexOf(']', idx)));
    	}
    	return humidity;
    }*/
 /*
	// @Override
	public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
		Log.warning("isInUnloadQueue not implemented yet");
		// TODO Auto-generated method stub
		return false;
	}

	// @Override
	public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
		Log.warning("getBiomeBaseFromSnapshot not implemented yet");
		// TODO Auto-generated method stub
		return new Object[256];
	}*/
// @Override
//public long getInhabitedTicks(Chunk c) {
//	return ((CraftChunk)c).getHandle(ChunkStatus.n).w(); // IChunkAccess.getInhabitedTime
//}
// @Override
//public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
//	return ((CraftChunk)c).getHandle(ChunkStatus.n).k; // IChunkAccess.blockEntities
//}
// @Override
/*public int getTileEntityX(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.aA_().u(); // getBlockPos
	}

	// @Override
	public int getTileEntityY(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.aA_().v(); // getBlockPos
	}

	// @Override
	public int getTileEntityZ(Object te) {
		TileEntity tileent = (TileEntity) te;
		return tileent.aA_().w(); // getBlockPos
	}

	// @Override
	public Object readTileEntityNBT(Object te, org.bukkit.World w) {
		TileEntity tileent = (TileEntity) te;
		CraftWorld cw = (CraftWorld) w;
		NBTTagCompound nbt = tileent.e(cw.getHandle().K_()); // TileEntity.saveCustomOnly(world.registryAccess())
        return nbt;
	}*/
// @Override
	/*public Object getFieldValue(Object nbt, String field) {
		NBTTagCompound rec = (NBTTagCompound) nbt;
		NBTBase val = rec.c(field);
        if(val == null) return null;
        if(val instanceof NBTTagByte) {
            return ((NBTTagByte)val).i(); // getAsByte
        }
        else if(val instanceof NBTTagShort) {
            return ((NBTTagShort)val).h(); // getAsShort
        }
        else if(val instanceof NBTTagInt) {
            return ((NBTTagInt)val).g(); // getAsInt
        }
        else if(val instanceof NBTTagLong) {
            return ((NBTTagLong)val).f(); // getAsLong
        }
        else if(val instanceof NBTTagFloat) {
            return ((NBTTagFloat)val).k(); // getAsFloat
        }
        else if(val instanceof NBTTagDouble) {
            return ((NBTTagDouble)val).j(); // getAsDouble
        }
        else if(val instanceof NBTTagByteArray) {
            return ((NBTTagByteArray)val).e(); // getAsByteArray
        }
        else if(val instanceof NBTTagString) {
            return ((NBTTagString)val).u_(); // getAsString
        }
        else if(val instanceof NBTTagIntArray) {
            return ((NBTTagIntArray)val).g(); // getAsIntArray
        }
        return null;
	}*/
