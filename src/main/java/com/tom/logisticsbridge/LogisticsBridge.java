package com.tom.logisticsbridge;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import com.tom.logisticsbridge.block.BlockBridge;
import com.tom.logisticsbridge.block.BlockCraftingManager;
import com.tom.logisticsbridge.inventory.ContainerCraftingManager;
import com.tom.logisticsbridge.item.FakeItem;
import com.tom.logisticsbridge.item.VirtualPattern;
import com.tom.logisticsbridge.network.RequestIDListPacket;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.pipe.BridgePipe;
import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.pipe.ResultPipe;
import com.tom.logisticsbridge.proxy.CommonProxy;
import com.tom.logisticsbridge.tileentity.TileEntityBridge;
import com.tom.logisticsbridge.tileentity.TileEntityCraftingManager;

import appeng.api.config.FuzzyMode;
import appeng.api.definitions.IMaterials;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
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
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsProgramCompilerTileEntity;
import logisticspipes.blocks.LogisticsProgramCompilerTileEntity.ProgrammCategories;
import logisticspipes.items.ItemLogisticsProgrammer;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.recipes.NBTIngredient;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.ModuleSlot;

@Mod(modid = LogisticsBridge.ID, name = LogisticsBridge.NAME, version = LogisticsBridge.VERSION, dependencies = LogisticsBridge.DEPS)
public class LogisticsBridge {
	public static final String ID = "logisticsbridge";
	public static final String NAME = "Logistics Bridge";
	public static final String VERSION = "0.3.1";
	public static final String DEPS = "required-after:appliedenergistics2;required-after:logisticspipes@[0.10.2.203,)";
	public static final Logger log = LogManager.getLogger(NAME);
	public static Method registerTexture, registerPipe;

	private static final String CLIENT_PROXY_CLASS = "com.tom.logisticsbridge.proxy.ClientProxy";
	private static final String SERVER_PROXY_CLASS = "com.tom.logisticsbridge.proxy.ServerProxy";

	@SidedProxy(clientSide = CLIENT_PROXY_CLASS, serverSide = SERVER_PROXY_CLASS)
	public static CommonProxy proxy;

	public static Block bridge, craftingManager;
	public static VirtualPattern virtualPattern;
	public static Item logisticsFakeItem;
	public static Item packageItem;
	public static HideFakeItem HIDE_FAKE_ITEM;
	public static Field MergedPriorityList_negative;
	public static PartType SATELLITE_BUS;
	public static ItemStackSrc SATELLITE_BUS_SRC;

	@ObjectHolder("logisticspipes:pipe_lb.bridgepipe")
	public static Item pipeBridge;

	@ObjectHolder("logisticspipes:pipe_lb.resultpipe")
	public static Item pipeResult;

	@ObjectHolder("logisticspipes:pipe_lb.craftingmanager")
	public static Item pipeCraftingManager;

	@Instance(ID)
	public static LogisticsBridge modInstance;

	@EventHandler
	public static void construction(FMLConstructionEvent evt) {
		log.info("Logistics Bridge version: " + VERSION);
	}

	@EventHandler
	public static void preInit(FMLPreInitializationEvent evt) {
		log.info("Start Pre Initialization");
		long tM = System.currentTimeMillis();
		virtualPattern = new VirtualPattern();
		logisticsFakeItem = new FakeItem(false).setUnlocalizedName("lb.logisticsFakeItem");
		packageItem = new FakeItem(true).setUnlocalizedName("lb.package").setCreativeTab(CreativeTab.instance);

		bridge = new BlockBridge().setUnlocalizedName("lb.bridge").setCreativeTab(CreativeTab.instance);
		craftingManager = new BlockCraftingManager().setUnlocalizedName("lb.crafting_managerAE").setCreativeTab(CreativeTab.instance);
		registerBlock(bridge);
		registerBlock(craftingManager);
		registerItem(virtualPattern, true);
		registerItem(logisticsFakeItem, true);
		registerItem(packageItem, true);

		GameRegistry.registerTileEntity(TileEntityBridge.class, new ResourceLocation(ID, "bridge"));
		AEBaseTile.registerTileItem(TileEntityBridge.class, new BlockStackSrc(bridge, 0, ActivityState.Enabled));
		GameRegistry.registerTileEntity(TileEntityCraftingManager.class, new ResourceLocation(ID, "craftingManagerAE"));
		AEBaseTile.registerTileItem(TileEntityCraftingManager.class, new BlockStackSrc(craftingManager, 0, ActivityState.Enabled));

		try {
			registerTexture = Textures.class.getDeclaredMethod("registerTexture", Object.class, String.class, int.class);
			registerTexture.setAccessible(true);
			registerPipe = LogisticsPipes.class.getDeclaredMethod("registerPipe", IForgeRegistry.class, String.class, Function.class);
			registerPipe.setAccessible(true);
			MergedPriorityList_negative = MergedPriorityList.class.getDeclaredField("negative");
			MergedPriorityList_negative.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException | NoSuchFieldException e) {
			throw new RuntimeException(e);
		}

		SATELLITE_BUS = EnumHelper.addEnum(PartType.class, "SATELLITE_BUS", new Class[]{int.class, String.class, Set.class, Set.class, Class.class},
				1024, "satellite_bus", EnumSet.of( AEFeature.CRAFTING_CPU ), EnumSet.noneOf( IntegrationType.class ), PartSatelliteBus.class);
		Api.INSTANCE.getPartModels().registerModels(SATELLITE_BUS.getModels());
		SATELLITE_BUS_SRC = ItemPart.instance.createPart(SATELLITE_BUS);

		MinecraftForge.EVENT_BUS.register(modInstance);
		proxy.registerRenderers();
		long time = System.currentTimeMillis() - tM;
		log.info("Pre Initialization took in " + time + " milliseconds");
	}
	@SubscribeEvent
	public void initItems(RegistryEvent.Register<Item> event) {
		IForgeRegistry<Item> registry = event.getRegistry();
		registerPipe(registry, "lb.bridgepipe", BridgePipe::new);
		registerPipe(registry, "lb.resultpipe", ResultPipe::new);
		registerPipe(registry, "lb.craftingmanager", CraftingManager::new);
		log.info("Registered Pipes");
	}
	@SubscribeEvent
	public void openGui(PlayerContainerEvent.Open event){
		if(event.getContainer() instanceof DummyContainer && !(event.getContainer() instanceof ContainerCraftingManager)){
			DummyContainer dc = (DummyContainer) event.getContainer();
			dc.inventorySlots.stream().filter(s -> s instanceof ModuleSlot).findFirst().
			map(s -> ((ModuleSlot)s).get_pipe()).filter(p -> p instanceof CraftingManager).ifPresent(cmgr -> {
				((CraftingManager)cmgr).openGui(event.getEntityPlayer());
			});
		}
	}
	@EventHandler
	public void cleanup(FMLServerStoppingEvent event) {
		ResultPipe.cleanup();
	}
	private static void registerPipe(IForgeRegistry<Item> registry, String name, Function<Item, ? extends CoreUnroutedPipe> constructor){
		try {
			registerPipe.invoke(LogisticsPipes.instance, registry, name, constructor);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void textureLoad(TextureStitchEvent.Pre event) {
		if (!event.getMap().getBasePath().equals("textures")) {
			return;
		}
		proxy.registerTextures();
	}

	@SuppressWarnings("unchecked")
	@EventHandler
	public static void init(FMLInitializationEvent evt) {
		log.info("Start Initialization");
		long tM = System.currentTimeMillis();
		if (evt.getSide() == Side.SERVER) {
			registerTextures(null);
		}
		try {
			Field sorterBySize = ItemSorters.class.getDeclaredField("CONFIG_BASED_SORT_BY_SIZE");
			sorterBySize.setAccessible(true);
			Field mod = Field.class.getDeclaredField("modifiers");
			mod.setAccessible(true);
			mod.set(sorterBySize, sorterBySize.getModifiers() & ~Modifier.FINAL);
			Comparator<IAEItemStack> old = (Comparator<IAEItemStack>) sorterBySize.get(null);
			IAEItemStack s1 = new StackSize().setStackSize(1);
			IAEItemStack s2 = new StackSize().setStackSize(2);
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
			Field pipe = LogisticsTileGenericPipe.class.getDeclaredField("pipe");
			RequestIDListPacket.pipe = ASMUtil.getfield(pipe);
		} catch (Exception e) {
			e.printStackTrace();
		}
		NetworkRegistry.INSTANCE.registerGuiHandler(modInstance, new GuiHandler());
		proxy.init();
		long time = System.currentTimeMillis() - tM;
		log.info("Initialization took in " + time + " milliseconds");
	}
	@EventHandler
	public static void postInit(FMLPostInitializationEvent evt) {
		log.info("Start Post Initialization");
		long tM = System.currentTimeMillis();
		ResourceLocation bridgePrg = pipeBridge.delegate.name();
		ResourceLocation resultPrg = pipeResult.delegate.name();
		ResourceLocation craftingMgrPrg = pipeCraftingManager.delegate.name();
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(bridgePrg);
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(resultPrg);
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(craftingMgrPrg);
		ResourceLocation group = new ResourceLocation(ID, "recipes");
		IMaterials mat = AE2Plugin.INSTANCE.api.definitions().materials();
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(bridge), "iei", "bIb", "ici",
				'i', "ingotIron",
				'b', LPItems.pipeBasic,
				'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(ID, "recipes/bridge")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(pipeBridge), " p ", "fbf", "dad",
				'p', getIngredientForProgrammer(bridgePrg),
				'b', LPItems.pipeBasic,
				'f', LPItems.chipFPGA,
				'd', "gemDiamond",
				'a', LPItems.chipAdvanced).
				setRegistryName(new ResourceLocation(ID, "recipes/pipe_bridge")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(pipeResult), " p ", "rar", " s ",
				'p', getIngredientForProgrammer(resultPrg),
				's', LPItems.pipeBasic,
				'a', LPItems.chipFPGA,
				'r', "dustRedstone").
				setRegistryName(new ResourceLocation(ID, "recipes/pipe_result")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(pipeCraftingManager), "gpg", "rsr", "gcg",
				'p', getIngredientForProgrammer(craftingMgrPrg),
				's', LPItems.pipeBasic,
				'g', LPItems.chipFPGA,
				'r', "ingotGold",
				'c', "chest").
				setRegistryName(new ResourceLocation(ID, "recipes/crafting_manager")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, SATELLITE_BUS_SRC.stack(1), " c ", "ifi", " p ",
				'p', Blocks.PISTON,
				'f', mat.formationCore().maybeStack(1).orElse(ItemStack.EMPTY),
				'i', "ingotIron",
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(ID, "recipes/satellite_bus")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(craftingManager), "IlI", "cec", "ili",
				'I', AE2Plugin.INSTANCE.api.definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
				'l', mat.logicProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'i', "ingotIron",
				'e', mat.engProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
				'c', mat.calcProcessor().maybeStack(1).orElse(ItemStack.EMPTY)).
				setRegistryName(new ResourceLocation(ID, "recipes/crafting_manager_ae")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(packageItem), "pw",
				'p', Items.PAPER,
				'w', "plankWood").
				setRegistryName(new ResourceLocation(ID, "recipes/package")));
		long time = System.currentTimeMillis() - tM;
		log.info("Post Initialization took in " + time + " milliseconds");
	}
	private static Ingredient getIngredientForProgrammer(ResourceLocation rl) {
		ItemStack programmerStack = new ItemStack(LPItems.logisticsProgrammer);
		programmerStack.setTagCompound(new NBTTagCompound());
		programmerStack.getTagCompound().setString(ItemLogisticsProgrammer.RECIPE_TARGET, rl.toString());
		return NBTIngredient.fromStacks(programmerStack);
	}
	public static void registerTextures(Object object) {
		BridgePipe.TEXTURE = registerTexture(object, "pipes/lb/bridge");
		ResultPipe.TEXTURE = registerTexture(object, "pipes/lb/result");
		CraftingManager.TEXTURE = registerTexture(object, "pipes/lb/crafting_manager");
	}
	private static TextureType registerTexture(Object par1IIconRegister, String fileName) {
		return registerTexture(par1IIconRegister, fileName, 1);
	}
	private static TextureType registerTexture(Object reg, String fileName, int flag){
		try {
			return (TextureType) registerTexture.invoke(LogisticsPipes.textures, reg, fileName, flag);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	public static void registerItem(Item item, boolean registerRenderer){
		item.setRegistryName(item.getUnlocalizedName().substring(5));
		ForgeRegistries.ITEMS.register(item);
		if(registerRenderer)proxy.addRenderer(item);
	}

	public static void registerBlock(Block block){
		registerOnlyBlock(block);
		registerItem(new ItemBlock(block), true);
	}
	public static void registerOnlyBlock(Block block){
		block.setRegistryName(block.getUnlocalizedName().substring(5));
		ForgeRegistries.BLOCKS.register(block);
	}
	private static class StackSize implements IAEItemStack {
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
	public static ItemStack fakeStack(ItemStack stack, int count){
		ItemStack is = new ItemStack(logisticsFakeItem, count);
		if(stack != null && !stack.isEmpty())is.setTagCompound(stack.writeToNBT(new NBTTagCompound()));
		return is;
	}
	public static ItemStack packageStack(ItemStack stack, int count, String id, boolean actStack){
		ItemStack is = new ItemStack(packageItem, count);
		if(stack != null && !stack.isEmpty())is.setTagCompound(stack.writeToNBT(new NBTTagCompound()));
		if(!is.hasTagCompound())is.setTagCompound(new NBTTagCompound());
		is.getTagCompound().setString("__pkgDest", id);
		is.getTagCompound().setBoolean("__actStack", actStack);
		return is;
	}
	public static NBTTagList saveAllItems(IInventory inv) {
		NBTTagList nbttaglist = new NBTTagList();
		for (int i = 0;i < inv.getSizeInventory();++i) {
			ItemStack itemstack = inv.getStackInSlot(i);

			if (!itemstack.isEmpty()) {
				NBTTagCompound nbttagcompound = new NBTTagCompound();
				nbttagcompound.setByte("Slot", (byte) i);
				itemstack.writeToNBT(nbttagcompound);
				nbttaglist.appendTag(nbttagcompound);
			}
		}
		return nbttaglist;
	}

	public static void loadAllItems(NBTTagList nbttaglist, IInventory inv) {
		inv.clear();
		int invSize = inv.getSizeInventory();
		for (int i = 0;i < nbttaglist.tagCount();++i) {
			NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
			int j = nbttagcompound.getByte("Slot") & 255;

			if (j >= 0 && j < invSize) {
				inv.setInventorySlotContents(j, new ItemStack(nbttagcompound));
			}
		}
	}
	@SuppressWarnings("unchecked")
	public static void processResIDMod(EntityPlayer player, SetIDPacket pck){
		if(pck.side == -1){
			if(player.openContainer instanceof Consumer){
				((Consumer<String>)player.openContainer).accept(pck.pid);
			}
		}else{
			AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
			IPartHost ph = pck.getTile(player.world, IPartHost.class);
			if(ph == null)return;
			IPart p = ph.getPart(side);
			if(p instanceof IIdPipe){
				((IIdPipe) p).setPipeID(pck.id, pck.pid, player);
			}
		}
	}
	/*public static void processResSetID(EntityPlayer player, ResultPipeID pck){
		AEPartLocation side = AEPartLocation.fromOrdinal(pck.side - 1);
		IPartHost ph = pck.getTile(player.world, IPartHost.class);
		if(ph == null)return;
		IPart p = ph.getPart(side);
		if(p instanceof IIdPipe){
			((IIdPipe) p).setPipeID(pck.id, pck.pipeID);
		}
	}*/

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
}