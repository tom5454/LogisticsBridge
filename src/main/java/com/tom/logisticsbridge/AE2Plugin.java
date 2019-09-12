package com.tom.logisticsbridge;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.tom.logisticsbridge.block.BlockBridgeAE;
import com.tom.logisticsbridge.block.BlockCraftingManager;
import com.tom.logisticsbridge.item.VirtualPattern;
import com.tom.logisticsbridge.network.RequestIDListPacket;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeAE;
import com.tom.logisticsbridge.tileentity.TileEntityCraftingManager;

import appeng.api.AEPlugin;
import appeng.api.IAppEngApi;
import appeng.api.config.FuzzyMode;
import appeng.api.definitions.IMaterials;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.block.AEBaseItemBlock;
import appeng.core.Api;
import appeng.core.CreativeTab;
import appeng.core.features.AEFeature;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.core.features.ItemStackSrc;
import appeng.integration.IntegrationType;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import appeng.tile.AEBaseTile;
import appeng.util.ItemSorters;
import appeng.util.prioritylist.MergedPriorityList;
import io.netty.buffer.ByteBuf;
import logisticspipes.LPItems;

@AEPlugin
public class AE2Plugin {
	static class StackSize implements IAEItemStack {
		private long stackSize;
		@Override
		public long getStackSize() {
			return stackSize;
		}

		@Override
		public IAEItemStack setStackSize(long stackSize) {
			this.stackSize = stackSize;
			return this;
		}

		@Override
		public long getCountRequestable() {
			return 0;
		}

		@Override
		public IAEItemStack setCountRequestable(long countRequestable) {
			return this;
		}

		@Override
		public boolean isCraftable() {
			return false;
		}

		@Override
		public IAEItemStack setCraftable(boolean isCraftable) {
			return this;
		}

		@Override
		public IAEItemStack reset() {
			return this;
		}

		@Override
		public boolean isMeaningful() {
			return false;
		}

		@Override
		public void incStackSize(long i) {
			stackSize += i;
		}

		@Override
		public void decStackSize(long i) {
			stackSize -= i;
		}

		@Override
		public void incCountRequestable(long i) {
		}

		@Override
		public void decCountRequestable(long i) {
		}

		@Override
		public void writeToNBT(NBTTagCompound i) {
		}

		@Override
		public boolean fuzzyComparison(IAEItemStack other, FuzzyMode mode) {
			return false;
		}

		@Override
		public void writeToPacket(ByteBuf data) throws IOException {
		}

		@Override
		public IAEItemStack empty() {
			return this;
		}

		@Override
		public boolean isItem() {
			return false;
		}

		@Override
		public boolean isFluid() {
			return false;
		}

		@Override
		public IStorageChannel<IAEItemStack> getChannel() {
			return null;
		}

		@Override
		public ItemStack asItemStackRepresentation() {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack createItemStack() {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean hasTagCompound() {
			return false;
		}

		@Override
		public void add(IAEItemStack option) {
		}

		@Override
		public IAEItemStack copy() {
			return new StackSize().setStackSize(stackSize);
		}

		@Override
		public Item getItem() {
			return Items.AIR;
		}

		@Override
		public int getItemDamage() {
			return 0;
		}

		@Override
		public boolean sameOre(IAEItemStack is) {
			return false;
		}

		@Override
		public boolean isSameType(IAEItemStack otherStack) {
			return false;
		}

		@Override
		public boolean isSameType(ItemStack stored) {
			return false;
		}

		@Override
		public ItemStack getDefinition() {
			return ItemStack.EMPTY;
		}
	}
	public static AE2Plugin INSTANCE;
	public final IAppEngApi api;
	public static VirtualPattern virtualPattern;
	public static HideFakeItem HIDE_FAKE_ITEM;
	public static Field MergedPriorityList_negative;
	public static PartType SATELLITE_BUS;
	public static ItemStackSrc SATELLITE_BUS_SRC;
	public AE2Plugin(IAppEngApi api) {
		this.api = api;
		INSTANCE = this;
	}
	public static void registerBlock(Block block){
		block.setCreativeTab(CreativeTab.instance);
		LogisticsBridge.registerBlock(block, () -> new AEBaseItemBlock(block));
	}
	public static void preInit(){
		virtualPattern = new VirtualPattern();
		LogisticsBridge.bridgeAE = new BlockBridgeAE().setUnlocalizedName("lb.bridge");
		LogisticsBridge.craftingManager = new BlockCraftingManager().setUnlocalizedName("lb.crafting_managerAE");
		AE2Plugin.registerBlock(LogisticsBridge.bridgeAE);
		AE2Plugin.registerBlock(LogisticsBridge.craftingManager);
		LogisticsBridge.registerItem(virtualPattern, true);
		try {
			AE2Plugin.MergedPriorityList_negative = MergedPriorityList.class.getDeclaredField("negative");
			AE2Plugin.MergedPriorityList_negative.setAccessible(true);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		AE2Plugin.SATELLITE_BUS = EnumHelper.addEnum(PartType.class, "SATELLITE_BUS", new Class[]{int.class, String.class, Set.class, Set.class, Class.class},
				1024, "satellite_bus", EnumSet.of( AEFeature.CRAFTING_CPU ), EnumSet.noneOf( IntegrationType.class ), PartSatelliteBus.class);
		Api.INSTANCE.getPartModels().registerModels(AE2Plugin.SATELLITE_BUS.getModels());
		AE2Plugin.SATELLITE_BUS_SRC = ItemPart.instance.createPart(AE2Plugin.SATELLITE_BUS);

		GameRegistry.registerTileEntity(TileEntityBridgeAE.class, new ResourceLocation(LogisticsBridge.ID, "bridge"));
		AEBaseTile.registerTileItem(TileEntityBridgeAE.class, new BlockStackSrc(LogisticsBridge.bridgeAE, 0, ActivityState.Enabled));
		GameRegistry.registerTileEntity(TileEntityCraftingManager.class, new ResourceLocation(LogisticsBridge.ID, "craftingManagerAE"));
		AEBaseTile.registerTileItem(TileEntityCraftingManager.class, new BlockStackSrc(LogisticsBridge.craftingManager, 0, ActivityState.Enabled));
	}
	@SuppressWarnings("unchecked")
	public static void patchSorter(){
		try {
			Field sorterBySize = ItemSorters.class.getDeclaredField("CONFIG_BASED_SORT_BY_SIZE");
			sorterBySize.setAccessible(true);
			Field mod = Field.class.getDeclaredField("modifiers");
			mod.setAccessible(true);
			mod.set(sorterBySize, sorterBySize.getModifiers() & ~Modifier.FINAL);
			Comparator<IAEItemStack> old = (Comparator<IAEItemStack>) sorterBySize.get(null);
			IAEItemStack s1 = new AE2Plugin.StackSize().setStackSize(1);
			IAEItemStack s2 = new AE2Plugin.StackSize().setStackSize(2);
			sorterBySize.set(null, new Comparator<IAEItemStack>() {

				@Override
				public int compare(IAEItemStack o1, IAEItemStack o2) {
					final int cmp = Long.compare( o2.getStackSize() + o2.getCountRequestable(), o1.getStackSize() + o1.getCountRequestable() );
					return applyDirection( cmp );
				}

				private int applyDirection( int cmp ) {
					int dir = old.compare(s1, s2);
					return dir*cmp;
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void loadRecipes(ResourceLocation group){
		IMaterials mat = AE2Plugin.INSTANCE.api.definitions().materials();
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(LogisticsBridge.bridgeAE), "iei", "bIb", "ici",
				'i', "ingotIron",
				'b', LPItems.pipeBasic,
				'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(LogisticsBridge.ID, "recipes/bridge")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, AE2Plugin.SATELLITE_BUS_SRC.stack(1), " c ", "ifi", " p ",
				'p', Blocks.PISTON,
				'f', mat.formationCore().maybeStack(1).orElse(ItemStack.EMPTY),
				'i', "ingotIron",
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(LogisticsBridge.ID, "recipes/satellite_bus")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(LogisticsBridge.craftingManager), "IlI", "cec", "ili",
				'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
				'l', mat.logicProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'i', "ingotIron",
				'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(LogisticsBridge.ID, "recipes/crafting_manager_ae")));
	}

	public static IIdPipe processReqIDList(EntityPlayer player, RequestIDListPacket pck) {
		AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
		IPartHost ph = pck.getTile(player.world, IPartHost.class);
		if(ph == null)return null;
		IPart p = ph.getPart(side);
		if(p instanceof IIdPipe){
			return (IIdPipe) p;
		}
		return null;
	}

	public static void processResIDMod(EntityPlayer player, SetIDPacket pck){
		AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
		IPartHost ph = pck.getTile(player.world, IPartHost.class);
		if(ph == null)return;
		IPart p = ph.getPart(side);
		if(p instanceof IIdPipe){
			((IIdPipe) p).setPipeID(pck.id, pck.pid, player);
		}
	}
}
