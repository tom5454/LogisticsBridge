package com.tom.logisticsbridge.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.Optional;

import com.tom.logisticsbridge.tileentity.TileEntityCraftingManager;

import appeng.block.AEBaseTileBlock;

public class BlockCraftingManager extends AEBaseTileBlock {

	public BlockCraftingManager() {
		super(Material.IRON);
		setHardness(2f);
		setResistance(4f);
		setTileEntity(TileEntityCraftingManager.class);
	}

	@Override
	@Optional.Method(modid = "")
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TileEntityCraftingManager();
	}

	@Override
	public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if(super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ))return true;
		if(!worldIn.isRemote){
			TileEntity te = worldIn.getTileEntity(pos);
			if(te instanceof TileEntityCraftingManager){
				((TileEntityCraftingManager)te).openGui(playerIn);
			}
		}
		return true;
	}
}
