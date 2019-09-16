package com.tom.logisticsbridge;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.IRSAPI;
import com.raoulvdberge.refinedstorage.api.RSAPIInject;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.gui.grid.GuiGrid;
import com.raoulvdberge.refinedstorage.gui.grid.stack.IGridStack;
import com.raoulvdberge.refinedstorage.gui.grid.view.GridViewItem;
import com.raoulvdberge.refinedstorage.gui.grid.view.IGridView;
import com.raoulvdberge.refinedstorage.item.ItemProcessor;

import com.tom.logisticsbridge.block.BlockBridgeRS;
import com.tom.logisticsbridge.item.VirtualPatternRS;
import com.tom.logisticsbridge.tileentity.NetworkNodeBridge;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeRS;

import logisticspipes.LPItems;

public class RSPlugin {
	@RSAPIInject
	public static IRSAPI rsapi;

	public static VirtualPatternRS virtualPattern;

	public static void preInit(){
		virtualPattern = new VirtualPatternRS();
		LogisticsBridge.bridgeRS = new BlockBridgeRS().setUnlocalizedName("lb.bridge.rs");
		LogisticsBridge.registerBlock(LogisticsBridge.bridgeRS);
		LogisticsBridge.registerItem(virtualPattern, true);
		GameRegistry.registerTileEntity(TileEntityBridgeRS.class, new ResourceLocation(LogisticsBridge.ID, "bridge_rs"));
		API.instance().getNetworkNodeRegistry().add(NetworkNodeBridge.ID, (tag, world, pos) -> {
			NetworkNode node = new NetworkNodeBridge(world, pos);
			node.read(tag);
			return node;
		});
	}

	public static void loadRecipes(ResourceLocation group){
		ForgeRegistries.RECIPES.register(new ShapedOreRecipe(group, new ItemStack(LogisticsBridge.bridgeRS), "iei", "bIb", "ici",
				'i', "ingotIron",
				'b', LPItems.pipeBasic,
				'I', RSBlocks.INTERFACE,
				'c', new ItemStack(RSItems.PROCESSOR, 1, ItemProcessor.TYPE_IMPROVED),
				'e', new ItemStack(RSItems.PROCESSOR, 1, ItemProcessor.TYPE_ADVANCED)).
				setRegistryName(new ResourceLocation(LogisticsBridge.ID, "recipes/bridge_rs")));
	}

	@SideOnly(Side.CLIENT)
	public static void hideFakeItems(GuiScreenEvent.BackgroundDrawnEvent event){
		Minecraft mc = Minecraft.getMinecraft();
		if(mc.currentScreen instanceof GuiGrid && !GuiScreen.isCtrlKeyDown()){
			IGridView view = ((GuiGrid)mc.currentScreen).getView();
			if(view instanceof GridViewItem){
				List<IGridStack> stacks = view.getStacks();
				stacks.removeIf(s -> {
					Object ing = s.getIngredient();
					if(ing instanceof ItemStack){
						ItemStack is = (ItemStack) ing;
						return is.getItem() == LogisticsBridge.logisticsFakeItem || (is.getItem() == LogisticsBridge.packageItem && s.getQuantity() == 0);
					}
					return false;
				});
			}
		}
	}
}
