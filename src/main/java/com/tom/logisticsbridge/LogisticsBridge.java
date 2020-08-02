package com.tom.logisticsbridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
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
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.fml.common.Loader;
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
import net.minecraftforge.fml.common.registry.GameRegistry.ObjectHolder;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import com.tom.logisticsbridge.inventory.ContainerCraftingManager;
import com.tom.logisticsbridge.item.FakeItem;
import com.tom.logisticsbridge.module.BufferUpgrade;
import com.tom.logisticsbridge.network.RequestIDListPacket;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.pipe.BridgePipe;
import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.pipe.ResultPipe;
import com.tom.logisticsbridge.proxy.CommonProxy;
import com.tom.logisticsbridge.util.DynamicInventory;

import logisticspipes.LPItems;
import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsProgramCompilerTileEntity;
import logisticspipes.blocks.LogisticsProgramCompilerTileEntity.ProgrammCategories;
import logisticspipes.items.ItemLogisticsProgrammer;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.recipes.NBTIngredient;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.ModuleSlot;

@Mod(modid = LogisticsBridge.ID, name = LogisticsBridge.NAME, version = LogisticsBridge.VERSION,
dependencies = LogisticsBridge.DEPS, updateJSON = LogisticsBridge.UPDATE)
public class LogisticsBridge {
	public static final String ID = "logisticsbridge";
	public static final String NAME = "Logistics Bridge";
	public static final String VERSION = "1.3.9";
	public static final String DEPS = "after:appliedenergistics2;after:refinedstorage@[1.6.15,);required-after:logisticspipes@[0.10.3.39,)";
	public static final String UPDATE = "https://github.com/tom5454/LogisticsBridge/blob/master/version-check.json";
	public static final Logger log = LogManager.getLogger(NAME);
	public static Method registerTexture, registerPipe;

	private static final String CLIENT_PROXY_CLASS = "com.tom.logisticsbridge.proxy.ClientProxy";
	private static final String SERVER_PROXY_CLASS = "com.tom.logisticsbridge.proxy.ServerProxy";

	@SidedProxy(clientSide = CLIENT_PROXY_CLASS, serverSide = SERVER_PROXY_CLASS)
	public static CommonProxy proxy;

	public static Block bridgeAE, bridgeRS;
	public static Block craftingManager;
	public static Item logisticsFakeItem;
	public static Item packageItem;
	public static boolean aeLoaded, rsLoaded;

	@ObjectHolder("logisticspipes:pipe_lb.bridgepipe")
	public static Item pipeBridge;

	@ObjectHolder("logisticspipes:pipe_lb.resultpipe")
	public static Item pipeResult;

	@ObjectHolder("logisticspipes:pipe_lb.craftingmanager")
	public static Item pipeCraftingManager;

	@ObjectHolder("logisticspipes:upgrade_lb.buffer_upgrade")
	public static Item upgradeBuffer;

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
		aeLoaded = Loader.isModLoaded("appliedenergistics2");
		rsLoaded = Loader.isModLoaded("refinedstorage");

		logisticsFakeItem = new FakeItem(false).setUnlocalizedName("lb.logisticsFakeItem");
		packageItem = new FakeItem(true).setUnlocalizedName("lb.package").setCreativeTab(CreativeTabs.MISC);

		if(aeLoaded){
			AE2Plugin.preInit();
		}
		if(rsLoaded){
			RSPlugin.preInit();
		}
		registerItem(logisticsFakeItem, true);
		registerItem(packageItem, true);

		try {
			registerTexture = Textures.class.getDeclaredMethod("registerTexture", Object.class, String.class, int.class);
			registerTexture.setAccessible(true);
			registerPipe = LogisticsPipes.class.getDeclaredMethod("registerPipe", IForgeRegistry.class, String.class, Function.class);
			registerPipe.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
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
		ItemUpgrade.registerUpgrade(registry, "lb.buffer_upgrade", BufferUpgrade::new);
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

	@EventHandler
	public static void init(FMLInitializationEvent evt) {
		log.info("Start Initialization");
		long tM = System.currentTimeMillis();
		if (evt.getSide() == Side.SERVER) {
			registerTextures(null);
		}
		if(aeLoaded){
			AE2Plugin.patchSorter();
		}
		NetworkRegistry.INSTANCE.registerGuiHandler(modInstance, new GuiHandler());
		proxy.init();
		loadRecipes();
		long time = System.currentTimeMillis() - tM;
		log.info("Initialization took in " + time + " milliseconds");
	}

	@EventHandler
	public static void postInit(FMLPostInitializationEvent evt) {
		log.info("Start Post Initialization");
		long tM = System.currentTimeMillis();
		//loadRecipes();
		long time = System.currentTimeMillis() - tM;
		log.info("Post Initialization took in " + time + " milliseconds");
	}

	private static void loadRecipes() {
		ResourceLocation bridgePrg = pipeBridge.delegate.name();
		ResourceLocation resultPrg = pipeResult.delegate.name();
		ResourceLocation craftingMgrPrg = pipeCraftingManager.delegate.name();
		ResourceLocation bufferUpgr = upgradeBuffer.delegate.name();
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(bridgePrg);
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(resultPrg);
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(craftingMgrPrg);
		LogisticsProgramCompilerTileEntity.programByCategory.get(ProgrammCategories.MODDED).add(bufferUpgr);
		ResourceLocation group = new ResourceLocation(ID, "recipes");

		if(aeLoaded){
			AE2Plugin.loadRecipes(group);
		}

		if(rsLoaded){
			RSPlugin.loadRecipes(group);
		}

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
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(packageItem), "pw",
				'p', Items.PAPER,
				'w', "plankWood").
				setRegistryName(new ResourceLocation(ID, "recipes/package")));
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(upgradeBuffer), "rpr", "ici", "PnP",
				'p', getIngredientForProgrammer(bufferUpgr),
				'n', LPItems.chipAdvanced,
				'c', LPItems.chipFPGA,
				'r', "dustRedstone",
				'i', "gemDiamond",
				'P', "paper").
				setRegistryName(new ResourceLocation(ID, "recipes/buffer_upgrade")));
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
		if(item.getRegistryName() == null)item.setRegistryName(item.getUnlocalizedName().substring(5));
		ForgeRegistries.ITEMS.register(item);
		if(registerRenderer)proxy.addRenderer(item);
	}

	public static void registerBlock(Block block){
		registerBlock(block, ItemBlock::new);
	}

	public static <T extends Block> void registerBlock(T block, Function<T, Item> itemBlock){
		registerOnlyBlock(block);
		registerItem(itemBlock.apply(block), true);
	}

	public static void registerOnlyBlock(Block block){
		if(block.getRegistryName() == null)
			block.setRegistryName(block.getUnlocalizedName().substring(5));
		ForgeRegistries.BLOCKS.register(block);
	}

	public static ItemStack fakeStack(int count){
		return new ItemStack(logisticsFakeItem, count);
	}

	public static ItemStack fakeStack(ItemStack stack, int count){
		ItemStack is = new ItemStack(logisticsFakeItem, count);
		if(stack != null && !stack.isEmpty())is.setTagCompound(stack.writeToNBT(new NBTTagCompound()));
		return is;
	}

	public static ItemStack fakeStack(NBTTagCompound stack, int count){
		ItemStack is = new ItemStack(logisticsFakeItem, count);
		if(stack != null && !stack.hasNoTags())is.setTagCompound(stack);
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
		int invSize = inv instanceof DynamicInventory ? Integer.MAX_VALUE : inv.getSizeInventory();
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
		}else if(aeLoaded){
			AE2Plugin.processResIDMod(player, pck);
		}
	}

	public static IIdPipe processReqIDList(EntityPlayer player, RequestIDListPacket pck) {
		if(aeLoaded){
			return AE2Plugin.processReqIDList(player, pck);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> Stream<T> concatStreams(Stream<T>... streams){
		return Arrays.stream(streams).flatMap(UnaryOperator.identity());
	}
}