package com.tom.logisticsbridge.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
			EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if(!worldIn.isRemote){
			TileEntityBridgeRS b = (TileEntityBridgeRS) worldIn.getTileEntity(pos);
			b.blockClicked(playerIn);
		}
		return true;
	}
}
