package com.tom.logisticsbridge.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.raoulvdberge.refinedstorage.block.BlockCable;
import com.raoulvdberge.refinedstorage.block.info.BlockDirection;
import com.raoulvdberge.refinedstorage.block.info.BlockInfoBuilder;
import com.raoulvdberge.refinedstorage.render.IModelRegistration;
import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsCable;
import com.raoulvdberge.refinedstorage.render.constants.ConstantsExternalStorage;
import com.raoulvdberge.refinedstorage.render.model.baked.BakedModelCableCover;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.tileentity.TileEntitySatelliteBus;

public class BlockSatelliteBus extends BlockCable {
	public BlockSatelliteBus() {
		super(BlockInfoBuilder.forMod(LogisticsBridge.modInstance, LogisticsBridge.ID,
				"lb.satellite_rs").material(Material.GLASS).soundType(SoundType.GLASS).
				hardness(0.35F).tileEntity(TileEntitySatelliteBus::new).create());
	}

	@Override
	@Nullable
	public BlockDirection getDirection() {
		return BlockDirection.ANY;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerModels(IModelRegistration modelRegistration) {
		modelRegistration.setModel(this, 0, new ModelResourceLocation(info.getId(), "direction=north,down=false,east=true,north=false,south=false,up=false,west=true"));
		modelRegistration.addBakedModelOverride(info.getId(), BakedModelCableCover::new);
	}

	@Override
	public List<CollisionGroup> getCollisions(TileEntity tile, IBlockState state) {
		List<CollisionGroup> groups = super.getCollisions(tile, state);

		switch (state.getValue(getDirection().getProperty())) {
		case NORTH:
			groups.add(ConstantsCable.HOLDER_NORTH);
			groups.add(ConstantsExternalStorage.HEAD_NORTH);
			break;
		case EAST:
			groups.add(ConstantsCable.HOLDER_EAST);
			groups.add(ConstantsExternalStorage.HEAD_EAST);
			break;
		case SOUTH:
			groups.add(ConstantsCable.HOLDER_SOUTH);
			groups.add(ConstantsExternalStorage.HEAD_SOUTH);
			break;
		case WEST:
			groups.add(ConstantsCable.HOLDER_WEST);
			groups.add(ConstantsExternalStorage.HEAD_WEST);
			break;
		case UP:
			groups.add(ConstantsCable.HOLDER_UP);
			groups.add(ConstantsExternalStorage.HEAD_UP);
			break;
		case DOWN:
			groups.add(ConstantsCable.HOLDER_DOWN);
			groups.add(ConstantsExternalStorage.HEAD_DOWN);
			break;
		}

		return groups;
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
		if (!canAccessGui(state, world, pos, hitX, hitY, hitZ)) {
			return false;
		}

		return openNetworkGui(player, world, pos, side, () -> {
			TileEntity te = world.getTileEntity(pos);
			if(te instanceof TileEntitySatelliteBus) {
				((TileEntitySatelliteBus)te).openGui(player, hand);
			}
		});
	}

	@Override
	public String getUnlocalizedName() {
		return "tile.lb.satellite_rs";
	}
}
