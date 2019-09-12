package com.tom.logisticsbridge.tileentity;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;

public interface IBridge {

	long countItem(ItemStack stack, boolean requestable);
	void craftStack(ItemStack stack, int count, boolean simulate);
	List<BridgeStack<ItemStack>> getItems();
	ItemStack extractStack(ItemStack stack, int count, boolean simulate);
	void setReqAPI(Req reqapi);

}
