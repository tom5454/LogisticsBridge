package com.tom.logisticsbridge.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import net.minecraftforge.common.util.Constants.NBT;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.IDynamicPatternDetailsAE;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

public class VirtualPatternAE extends Item implements ICraftingPatternItem {
	private static final WeakHashMap<NBTTagCompound, ICraftingPatternDetails> CACHE = new WeakHashMap<>();
	private static final String DYNAMIC_PATTERN_ID = "__dyPatternDetails";
	static {
		IDynamicPatternDetailsAE.FACTORIES.put("te", IDynamicPatternDetailsAE.TileEntityWrapper::create);
	}
	public VirtualPatternAE() {
		setUnlocalizedName("lb.virtPattern");
	}
	@Override
	public ICraftingPatternDetails getPatternForItem(ItemStack is, World w) {
		return getPatternForItem(is);
	}
	private static ICraftingPatternDetails getPatternForItem(ItemStack is) {
		try {
			return new VirtualPatternHandler(is);
		} catch ( final Throwable t ) {
			return null;
		}
	}
	public static ICraftingPatternDetails create(ItemStack output){
		NBTTagCompound ot = output.writeToNBT(new NBTTagCompound());
		return CACHE.computeIfAbsent(ot, VirtualPatternAE::create);
	}
	public static ICraftingPatternDetails create(ItemStack input, ItemStack output){
		NBTTagCompound ot = new NBTTagCompound();
		ot.setTag("in", input.writeToNBT(new NBTTagCompound()));
		ot.setTag("out", output.writeToNBT(new NBTTagCompound()));
		return CACHE.computeIfAbsent(ot, VirtualPatternAE::create);
	}
	public static ICraftingPatternDetails create(ItemStack output, IDynamicPatternDetailsAE handler){
		NBTTagCompound ot = output.writeToNBT(new NBTTagCompound());
		ot.setTag(DYNAMIC_PATTERN_ID, IDynamicPatternDetailsAE.save(handler));
		return CACHE.computeIfAbsent(ot, VirtualPatternAE::create);
	}
	private static ICraftingPatternDetails create(NBTTagCompound ot){
		ItemStack is = new ItemStack(AE2Plugin.virtualPattern);
		NBTTagCompound dyTag = ot.getCompoundTag(DYNAMIC_PATTERN_ID);
		ot.removeTag(DYNAMIC_PATTERN_ID);
		NBTTagCompound in = ot.getCompoundTag("in");
		NBTTagCompound out = ot.getCompoundTag("out");
		if(!out.hasNoTags())ot = out;
		is.setTagCompound(new NBTTagCompound());
		NBTTagCompound tag = is.getTagCompound();
		tag.setTag("out", ot);
		NBTTagList list = new NBTTagList();
		tag.setTag("in", list);
		if(in.hasNoTags())
			list.appendTag(new ItemStack(LogisticsBridge.logisticsFakeItem).writeToNBT(new NBTTagCompound()));
		else
			list.appendTag(in);
		if(!dyTag.hasNoTags())tag.setTag("dynamic", dyTag);
		return getPatternForItem(is);
	}
	@Override
	public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		tooltip.add(I18n.format("tooltip.logisticsbridge.techItem"));
	}
	public static class VirtualPatternHandler implements ICraftingPatternDetails, Comparable<VirtualPatternHandler> {
		private final ItemStack patternItem, result;
		private final IAEItemStack[] condensedInputs;
		private final IAEItemStack[] condensedOutputs;
		private final IAEItemStack[] inputs;
		private final IAEItemStack[] outputs;
		private final IAEItemStack pattern;
		private final IDynamicPatternDetailsAE dynamic;
		private int priority = 0;

		public VirtualPatternHandler(ItemStack is) {
			final NBTTagCompound tag = is.getTagCompound();
			if( tag == null )
			{
				throw new IllegalArgumentException( "No pattern here!" );
			}

			this.patternItem = is;
			this.pattern = AEItemStack.fromItemStack( is );

			final List<IAEItemStack> in = new ArrayList<>();
			final List<IAEItemStack> out = new ArrayList<>();

			IItemStorageChannel ITEMS = AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class );

			final NBTTagList inTag = tag.getTagList( "in", 10 );

			if(tag.hasKey("out", NBT.TAG_COMPOUND)){
				result = new ItemStack(tag.getCompoundTag("out"));
				out.add(ITEMS.createStack(result));
			}else{
				final NBTTagList outTag = tag.getTagList( "out", 10 );

				for( int x = 0; x < outTag.tagCount(); x++ )
				{
					NBTTagCompound resultItemTag = outTag.getCompoundTagAt( x );
					final ItemStack gs = new ItemStack( resultItemTag );

					if( !resultItemTag.hasNoTags() && gs.isEmpty() )
					{
						throw new IllegalArgumentException( "No pattern here!" );
					}

					if( !gs.isEmpty() )
					{
						out.add( ITEMS.createStack( gs ) );
					}
				}
				result = out.get(0).asItemStackRepresentation();
			}

			for( int x = 0; x < inTag.tagCount(); x++ )
			{
				NBTTagCompound ingredient = inTag.getCompoundTagAt( x );
				final ItemStack gs = new ItemStack( ingredient );

				if( !ingredient.hasNoTags() && gs.isEmpty() )
				{
					throw new IllegalArgumentException( "No pattern here!" );
				}

				in.add( ITEMS.createStack( gs ) );
			}

			this.outputs = out.toArray( new IAEItemStack[out.size()] );
			this.inputs = in.toArray( new IAEItemStack[in.size()] );

			final Map<IAEItemStack, IAEItemStack> tmpOutputs = new HashMap<>();

			for( final IAEItemStack io : this.outputs )
			{
				if( io == null )
				{
					continue;
				}

				final IAEItemStack g = tmpOutputs.get( io );

				if( g == null )
				{
					tmpOutputs.put( io, io.copy() );
				}
				else
				{
					g.add( io );
				}
			}

			final Map<IAEItemStack, IAEItemStack> tmpInputs = new HashMap<>();

			for( final IAEItemStack io : this.inputs )
			{
				if( io == null )
				{
					continue;
				}

				final IAEItemStack g = tmpInputs.get( io );

				if( g == null )
				{
					tmpInputs.put( io, io.copy() );
				}
				else
				{
					g.add( io );
				}
			}

			if( tmpOutputs.isEmpty() )
			{
				throw new IllegalStateException( "No pattern here!" );
			}

			this.condensedInputs = new IAEItemStack[tmpInputs.size()];
			int offset = 0;

			for( final IAEItemStack io : tmpInputs.values() )
			{
				this.condensedInputs[offset] = io;
				offset++;
			}

			offset = 0;
			this.condensedOutputs = new IAEItemStack[tmpOutputs.size()];

			for( final IAEItemStack io : tmpOutputs.values() )
			{
				this.condensedOutputs[offset] = io;
				offset++;
			}
			if(tag.hasKey("dynamic", NBT.TAG_COMPOUND)){
				dynamic = IDynamicPatternDetailsAE.load(tag.getCompoundTag("dynamic"));
				dynamic.getOutputs(result, null, false);
			}else{
				dynamic = null;
			}
		}

		@Override
		public ItemStack getPattern() {
			return patternItem;
		}

		@Override
		public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
			throw new IllegalStateException( "Only crafting recipes supported." );
		}

		@Override
		public boolean isCraftable() {
			return false;
		}

		@Override
		public IAEItemStack[] getInputs() {
			return dynamic != null ? dynamic.getInputs(result, inputs, false) : inputs;
		}

		@Override
		public IAEItemStack[] getCondensedInputs() {
			return dynamic != null ? dynamic.getInputs(result, condensedInputs, true) : condensedInputs;
		}

		@Override
		public IAEItemStack[] getCondensedOutputs() {
			return dynamic != null ? dynamic.getOutputs(result, condensedOutputs, true) : condensedOutputs;
		}

		@Override
		public IAEItemStack[] getOutputs() {
			return dynamic != null ? dynamic.getOutputs(result, outputs, true) : outputs;
		}

		@Override
		public boolean canSubstitute() {
			return false;
		}

		@Override
		public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
			throw new IllegalStateException( "Only crafting recipes supported." );
		}

		@Override
		public int getPriority() {
			return this.priority;
		}

		@Override
		public void setPriority( final int priority ) {
			this.priority = priority;
		}
		@Override
		public boolean equals( final Object obj ) {
			if( obj == null )
			{
				return false;
			}
			if( this.getClass() != obj.getClass() )
			{
				return false;
			}

			final VirtualPatternHandler other = (VirtualPatternHandler) obj;

			if( this.pattern != null && other.pattern != null )
			{
				return this.pattern.equals( other.pattern );
			}
			return false;
		}
		@Override
		public int compareTo( final VirtualPatternHandler o )
		{
			return Integer.compare( o.priority, this.priority );
		}

		@Override
		public int hashCode()
		{
			return this.pattern.hashCode();
		}
	}
}
