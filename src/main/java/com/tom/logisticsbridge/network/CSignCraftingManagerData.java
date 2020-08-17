package com.tom.logisticsbridge.network;

import net.minecraft.entity.player.EntityPlayer;

import com.tom.logisticsbridge.pipe.CraftingManager;

import logisticspipes.network.abstractpackets.InventoryModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.utils.StaticResolve;

@StaticResolve
public class CSignCraftingManagerData extends InventoryModuleCoordinatesPacket {

	public CSignCraftingManagerData(int id) {
		super(id);
	}

	@Override
	public void processPacket(EntityPlayer player) {
		CoreUnroutedPipe pipe = this.getPipe(player.world, LTGPCompletionCheck.PIPE).pipe;
		if (pipe == null || !(pipe instanceof CraftingManager)) {
			return;
		}
		for (int i = 0; i < getStackList().size(); i++) {
			((CraftingManager)pipe).getClientModuleInventory().setInventorySlotContents(i, getStackList().get(i));
		}
	}

	@Override
	public ModernPacket template() {
		return new CSignCraftingManagerData(getId());
	}
}
