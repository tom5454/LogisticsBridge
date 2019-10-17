package com.tom.logisticsbridge.api;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.FMLCommonHandler;

public interface IDynamicPatternDetailsRS {
	public static Map<String, Function<NBTTagCompound, IDynamicPatternDetailsRS>> FACTORIES = new HashMap<>();
	public static WeakHashMap<NBTTagCompound, IDynamicPatternDetailsRS> CACHE = new WeakHashMap<>();
	public static final String ID_TAG = "_id";
	public static IDynamicPatternDetailsRS load(NBTTagCompound tag){
		return CACHE.computeIfAbsent(tag, t -> {
			String id = t.getString(ID_TAG);
			Function<NBTTagCompound, IDynamicPatternDetailsRS> fac = FACTORIES.get(id);
			return fac.apply(t);
		});
	}
	public static NBTTagCompound save(IDynamicPatternDetailsRS det){
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
	NonNullList<ItemStack> getInputs(ItemStack res, NonNullList<ItemStack> def);
	NonNullList<ItemStack> getOutputs(ItemStack res, NonNullList<ItemStack> def);
	public class TileEntityWrapper implements IDynamicPatternDetailsRS {
		private int dim;
		private BlockPos pos;
		private IDynamicPatternDetailsRS tile;

		public TileEntityWrapper(int dim, BlockPos pos) {
			this.dim = dim;
			this.pos = pos;
		}

		public TileEntityWrapper(World world, BlockPos pos) {
			this(world.provider.getDimension(), pos);
		}

		public TileEntityWrapper(TileEntity te) {
			this(te.getWorld(), te.getPos());
			tile = (IDynamicPatternDetailsRS) te;
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
					if(te instanceof IDynamicPatternDetailsRS)this.tile = (IDynamicPatternDetailsRS) te;
				}
			}
		}

		@Override
		public NonNullList<ItemStack> getInputs(ItemStack res, NonNullList<ItemStack> def) {
			load();
			return tile != null ? tile.getInputs(res, def) : def;
		}

		@Override
		public NonNullList<ItemStack> getOutputs(ItemStack res, NonNullList<ItemStack> def) {
			load();
			return tile != null ? tile.getOutputs(res, def) : def;
		}
	}
}
