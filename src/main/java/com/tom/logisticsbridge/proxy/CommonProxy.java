package com.tom.logisticsbridge.proxy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public abstract class CommonProxy {
	public void registerRenderers() {}
	public void addRenderer(Item item){}
	public void addRenderer(ItemStack is, String name){}
	public void init(){}
	public void registerTextures() {}
}
