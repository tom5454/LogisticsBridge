package com.tom.logisticsbridge.block;

import appeng.block.AEBaseTileBlock;
import com.tom.logisticsbridge.tileentity.TileEntityBridgeAE;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


public class BlockBridgeAE extends AEBaseTileBlock{

	public BlockBridgeAE() {
		super(Material.IRON);
		setHardness(2f);
		setResistance(4f);
		setTileEntity(TileEntityBridgeAE.class);
	}

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
			EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if(!worldIn.isRemote){
			TileEntityBridgeAE b = (TileEntityBridgeAE) worldIn.getTileEntity(pos);
			b.blockClicked(playerIn);
		}
		return true;
	}
}
