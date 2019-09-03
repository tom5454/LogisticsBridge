package com.tom.logisticsbridge.block;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.Optional;

import com.tom.logisticsbridge.tileentity.TileEntityBridge;

import appeng.block.AEBaseTileBlock;

public class BlockBridge extends AEBaseTileBlock {

	public BlockBridge() {
		super(Material.IRON);
		setHardness(2f);
		setResistance(4f);
		setTileEntity(TileEntityBridge.class);
	}

	@Override
	@Optional.Method(modid = "")
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TileEntityBridge();
	}
}
