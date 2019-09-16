package com.tom.logisticsbridge.api;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.FMLCommonHandler;

import appeng.api.storage.data.IAEItemStack;

public interface IDynamicPatternDetailsAE {
	public static Map<String, Function<NBTTagCompound, IDynamicPatternDetailsAE>> FACTORIES = new HashMap<>();
	public static WeakHashMap<NBTTagCompound, IDynamicPatternDetailsAE> CACHE = new WeakHashMap<>();
	public static final String ID_TAG = "_id";
	public static IDynamicPatternDetailsAE load(NBTTagCompound tag){
		return CACHE.computeIfAbsent(tag, t -> {
			String id = t.getString(ID_TAG);
			Function<NBTTagCompound, IDynamicPatternDetailsAE> fac = FACTORIES.get(id);
			return fac.apply(t);
		});
	}
	public static NBTTagCompound save(IDynamicPatternDetailsAE det){
		String id = det.getId();
		NBTTagCompound tag = new NBTTagCompound();
		det.storeToNBT(tag);
		tag.setString(ID_TAG, id);
		return tag;
	}
	default String getId(){
		throw new AbstractMethodError("Missing impl: " + getClass());
	}
	default void storeToNBT(NBTTagCompound tag){
		throw new AbstractMethodError("Missing impl: " + getClass());
	}
	IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed);
	public class TileEntityWrapper implements IDynamicPatternDetailsAE {
		private int dim;
		private BlockPos pos;
		private IDynamicPatternDetailsAE tile;

		public TileEntityWrapper(int dim, BlockPos pos) {
			this.dim = dim;
			this.pos = pos;
		}

		public TileEntityWrapper(TileEntity te) {
			this(te.getWorld().provider.getDimension(), te.getPos());
			tile = (IDynamicPatternDetailsAE) te;
		}

		@Override
		public String getId() {
			return "te";
		}

		@Override
		public void storeToNBT(NBTTagCompound tag) {
			tag.setInteger("dim", dim);
			tag.setInteger("x", pos.getX());
			tag.setInteger("y", pos.getY());
			tag.setInteger("z", pos.getZ());
		}
		public static TileEntityWrapper create(NBTTagCompound tag){
			return new TileEntityWrapper(tag.getInteger("dim"), new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
		}
		public void load(){
			if(tile == null){
				World w = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(dim);
				if(w != null){
					TileEntity te = w.getTileEntity(pos);
					if(te instanceof IDynamicPatternDetailsAE)this.tile = (IDynamicPatternDetailsAE) te;
				}
			}
		}

		@Override
		public IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
			load();
			return tile != null ? tile.getInputs(res, def, condensed) : def;
		}

	}
}
