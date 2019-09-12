package com.tom.logisticsbridge;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.raoulvdberge.refinedstorage.RSBlocks;
import com.raoulvdberge.refinedstorage.RSItems;
import com.raoulvdberge.refinedstorage.api.IRSAPI;
import com.raoulvdberge.refinedstorage.api.RSAPIInject;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;
import com.raoulvdberge.refinedstorage.item.ItemProcessor;

import com.tom.logisticsbridge.tileentity.NetworkNodeBridge;

import logisticspipes.LPItems;

public class RSPlugin {
	@RSAPIInject
	public static IRSAPI rsapi;

	public static void init(){
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
}
