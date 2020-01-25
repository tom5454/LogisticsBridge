package com.tom.logisticsbridge.block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.raoulvdberge.refinedstorage.block.BlockNode;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;

import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.tileentity.TileEntityCraftingManagerRS;

public class BlockCraftingManagerRS extends BlockNode {

	public BlockCraftingManagerRS() {
		super(BlockInfoBuilder.forMod(LogisticsBridge.modInstance, LogisticsBridge.ID, "lb.craftingmanager.rs").
				tileEntity(TileEntityCraftingManagerRS::new).create());
	}

	@Override
	public boolean hasConnectedState() {
		return true;
	}

	@Override
	public String getUnlocalizedName() {
		return "tile.lb.craftingmanager.rs";
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
			EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
		if (!canAccessGui(state, world, pos, hitX, hitY, hitZ)) {
			return false;
		}

		return openNetworkGui(GuiIDs.CraftingManager.ordinal(), player, world, pos, side);
	}
}
