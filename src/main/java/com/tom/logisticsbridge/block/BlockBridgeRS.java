package com.tom.logisticsbridge.block;

import com.raoulvdberge.refinedstorage.block.BlockNode;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeRS;

public class BlockBridgeRS extends BlockNode {

	public BlockBridgeRS() {
		super(BlockInfoBuilder.forMod(LogisticsBridge.modInstance, LogisticsBridge.ID, "lb.bridge.rs").tileEntity(TileEntityBridgeRS::new).create());
	}

	@Override
	public boolean hasConnectedState() {
		return true;
	}

	@Override
	public String getUnlocalizedName() {
		return "tile.lb.bridge.rs";
	}
}
