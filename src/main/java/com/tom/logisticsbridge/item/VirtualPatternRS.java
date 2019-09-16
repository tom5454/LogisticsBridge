package com.tom.logisticsbridge.item;

import java.util.List;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternProvider;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.registry.CraftingTaskFactory;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.RSPlugin;
import com.tom.logisticsbridge.api.IDynamicPatternDetailsRS;

public class VirtualPatternRS extends Item implements ICraftingPatternProvider {
	private static final WeakHashMap<NBTTagCompound, ICraftingPattern> CACHE = new WeakHashMap<>();
	private static final String DYNAMIC_PATTERN_ID = "__dyPatternDetails";
	static {
		IDynamicPatternDetailsRS.FACTORIES.put("te", IDynamicPatternDetailsRS.TileEntityWrapper::create);
	}
	public VirtualPatternRS() {
		setUnlocalizedName("lb.virtPatternRS");
	}
	@Override
	public ICraftingPattern create(World world, ItemStack stack, ICraftingPatternContainer container) {
		return getPatternForItem(stack, container);
	}

	@Override
	public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		tooltip.add(I18n.format("tooltip.logisticsbridge.techItem"));
	}

	private static ICraftingPattern getPatternForItem(ItemStack is, ICraftingPatternContainer container) {
		try {
			return new VirtualPatternHandler(is, container);
		} catch ( final Throwable t ) {
			return null;
		}
	}
	public static ICraftingPattern create(ItemStack output, ICraftingPatternContainer container){
		NBTTagCompound ot = output.writeToNBT(new NBTTagCompound());
		return CACHE.computeIfAbsent(ot, n -> VirtualPatternRS.create(n, container));
	}
	public static ICraftingPattern create(ItemStack input, ItemStack output, ICraftingPatternContainer container){
		NBTTagCompound ot = new NBTTagCompound();
		ot.setTag("in", input.writeToNBT(new NBTTagCompound()));
		ot.setTag("out", output.writeToNBT(new NBTTagCompound()));
		return CACHE.computeIfAbsent(ot, n -> VirtualPatternRS.create(n, container));
	}
	public static ICraftingPattern create(ItemStack output, IDynamicPatternDetailsRS handler, ICraftingPatternContainer container){
		NBTTagCompound ot = output.writeToNBT(new NBTTagCompound());
		ot.setTag(DYNAMIC_PATTERN_ID, IDynamicPatternDetailsRS.save(handler));
		return CACHE.computeIfAbsent(ot, n -> VirtualPatternRS.create(n, container));
	}
	private static ICraftingPattern create(NBTTagCompound ot, ICraftingPatternContainer container){
		ItemStack is = new ItemStack(RSPlugin.virtualPattern);
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
		if(!in.hasNoTags() && Item.getByNameOrId(in.getString("id")) == LogisticsBridge.logisticsFakeItem)tag.setBoolean("writer", true);
		if(in.hasNoTags())
			list.appendTag(LogisticsBridge.fakeStack(ot, 1).writeToNBT(new NBTTagCompound()));
		else
			list.appendTag(in);
		if(!dyTag.hasNoTags())tag.setTag("dynamic", dyTag);
		return getPatternForItem(is, container);
	}
	public static class VirtualPatternHandler implements ICraftingPattern {
		private static final NonNullList<ItemStack> EMPTY_IS = NonNullList.create();
		private static final NonNullList<FluidStack> EMPTY_FS = NonNullList.create();
		private final ICraftingPatternContainer container;
		private ItemStack stack, result;
		private final NonNullList<ItemStack> in;
		private final NonNullList<ItemStack> out;
		private final IDynamicPatternDetailsRS dynamic;
		private final boolean writingPattern;
		public VirtualPatternHandler(ItemStack is, ICraftingPatternContainer container) {
			this.container = container;
			this.stack = is;
			final NBTTagCompound tag = is.getTagCompound();
			if( tag == null )
			{
				throw new IllegalArgumentException( "No pattern here!" );
			}
			if(tag.hasKey("out", NBT.TAG_COMPOUND)){
				result = new ItemStack(tag.getCompoundTag("out"));
			}else{
			}
			in = NonNullList.create();
			out = NonNullList.create();
			out.add(result);
			writingPattern = tag.getBoolean("writer");
			final NBTTagList inTag = tag.getTagList( "in", 10 );
			for( int x = 0; x < inTag.tagCount(); x++ )
			{
				NBTTagCompound ingredient = inTag.getCompoundTagAt( x );
				final ItemStack gs = new ItemStack( ingredient );

				if( !ingredient.hasNoTags() && gs.isEmpty() )
				{
					throw new IllegalArgumentException( "No pattern here!" );
				}

				in.add(gs);
			}
			if(tag.hasKey("dynamic", NBT.TAG_COMPOUND)){
				dynamic = IDynamicPatternDetailsRS.load(tag.getCompoundTag("dynamic"));
			}else{
				dynamic = null;
			}
		}

		@Override
		public ICraftingPatternContainer getContainer() {
			return container;
		}

		@Override
		public ItemStack getStack() {
			return stack;
		}

		@Override
		public boolean isValid() {
			return true;
		}

		@Override
		public boolean isProcessing() {
			return !writingPattern;
		}

		@Override
		public boolean isOredict() {
			return false;
		}

		@Override
		public List<NonNullList<ItemStack>> getInputs() {
			return (dynamic != null ? dynamic.getInputs(result, in) : in).stream().
					map(i -> NonNullList.from(ItemStack.EMPTY, i)).collect(Collectors.toList());
		}

		@Override
		public NonNullList<ItemStack> getOutputs() {
			return out;
		}

		@Override
		public ItemStack getOutput(NonNullList<ItemStack> took) {
			if(writingPattern){
				return result.copy();
			}else
				throw new IllegalStateException("Cannot get crafting output from processing pattern");
		}

		@Override
		public NonNullList<ItemStack> getByproducts() {
			if(writingPattern)return EMPTY_IS;
			throw new IllegalStateException("Cannot get byproduct outputs from processing pattern");
		}

		@Override
		public NonNullList<ItemStack> getByproducts(NonNullList<ItemStack> took) {
			if(writingPattern)return EMPTY_IS;
			throw new IllegalStateException("Cannot get byproduct outputs from processing pattern");
		}

		@Override
		public NonNullList<FluidStack> getFluidInputs() {
			return EMPTY_FS;
		}

		@Override
		public NonNullList<FluidStack> getFluidOutputs() {
			return EMPTY_FS;
		}

		@Override
		public String getId() {
			return CraftingTaskFactory.ID;
		}

		@Override
		public boolean canBeInChainWith(ICraftingPattern other) {
			return other == this;
		}

		@Override
		public int getChainHashCode() {
			int result = 0;

			result = 31 * result + 1;
			result = 31 * result;

			for (ItemStack input : in) {
				result = 31 * result + API.instance().getItemStackHashCode(input);
			}


			for (ItemStack output : this.out) {
				result = 31 * result + API.instance().getItemStackHashCode(output);
			}

			result = 31 * result + 69;

			return result;
		}

	}
}
