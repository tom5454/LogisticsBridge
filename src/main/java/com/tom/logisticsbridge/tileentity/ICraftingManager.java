package com.tom.logisticsbridge.tileentity;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;

public interface ICraftingManager extends IIdPipe {
	ItemStack satelliteDisplayStack();
	Slot createGuiSlot(int i, int x, int y);
	BlockPos getPosition();
	BlockingMode getBlockingMode();
}
