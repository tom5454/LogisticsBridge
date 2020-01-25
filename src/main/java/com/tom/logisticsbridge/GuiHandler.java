package com.tom.logisticsbridge;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.network.IGuiHandler;

import com.tom.logisticsbridge.gui.GuiCraftingManager;
import com.tom.logisticsbridge.gui.GuiCraftingManagerU;
import com.tom.logisticsbridge.gui.GuiPackage;
import com.tom.logisticsbridge.gui.GuiResultPipe;
import com.tom.logisticsbridge.inventory.ContainerCraftingManager;
import com.tom.logisticsbridge.inventory.ContainerCraftingManagerU;
import com.tom.logisticsbridge.inventory.ContainerPackage;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.tileentity.ICraftingManager;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;

public class GuiHandler implements IGuiHandler {
	public static enum GuiIDs {
		ResultPipe,
		CraftingManager,
		TemplatePkg,

		;
		public static final GuiIDs[] VALUES = values();
	}
	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		if(ID >= 100 && ID < 120){
			AEPartLocation side = AEPartLocation.fromOrdinal( ID - 100 );
			final TileEntity TE = world.getTileEntity( new BlockPos( x, y, z ) );
			if( TE instanceof IPartHost ) {
				final IPart part = ( (IPartHost) TE ).getPart( side );
				if(part instanceof PartSatelliteBus){
					return new DummyContainer(player.inventory, null);
				}
			}
			return null;
		}
		if(ID == 5){
			final TileEntity TE = world.getTileEntity( new BlockPos( x, y, z ) );
			if (TE != null && TE instanceof IIdPipe) {
				return new DummyContainer(player.inventory, null);
			}
			return null;
		}
		switch (GuiIDs.VALUES[ID]) {
		case ResultPipe:
		{
			CoreUnroutedPipe pipe = getPipe(world, x, y, z);
			if (pipe != null && pipe instanceof IIdPipe) {
				return new DummyContainer(player.inventory, null);
			}
		}
		break;
		case CraftingManager:
		{
			CoreUnroutedPipe pipe = getPipe(world, x, y, z);
			if (pipe != null && pipe instanceof CraftingManager) {
				return new ContainerCraftingManager(player, (CraftingManager) pipe, true);
			}else{
				TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
				if(tile instanceof ICraftingManager){
					return new ContainerCraftingManagerU(player, (ICraftingManager) tile);
				}
			}
		}
		break;
		case TemplatePkg:
		{
			EnumHand hand = EnumHand.values()[x];
			ItemStack is = player.getHeldItem(hand);
			if(is.getItem() == LogisticsBridge.packageItem)
				return new ContainerPackage(player, hand);
		}
		break;
		default:
			break;
		}
		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		if(ID >= 100 && ID < 120){
			AEPartLocation side = AEPartLocation.fromOrdinal( ID - 100 );
			final TileEntity TE = world.getTileEntity( new BlockPos( x, y, z ) );
			if( TE instanceof IPartHost ) {
				final IPart part = ( (IPartHost) TE ).getPart( side );
				if(part instanceof PartSatelliteBus){
					return new GuiResultPipe((IIdPipe) part, player, 0);
				}
			}
			return null;
		}
		if(ID == 5){
			final TileEntity TE = world.getTileEntity( new BlockPos( x, y, z ) );
			if (TE != null && TE instanceof IIdPipe) {
				return new GuiResultPipe((IIdPipe) TE, player, 0);
			}
			return null;
		}
		switch (GuiIDs.VALUES[ID]) {
		case ResultPipe:
		{
			CoreUnroutedPipe pipe = getPipe(world, x, y, z);
			if (pipe != null && pipe instanceof IIdPipe) {
				return new GuiResultPipe((IIdPipe) pipe, player, 0);
			}
		}
		break;
		case CraftingManager:
		{
			CoreUnroutedPipe pipe = getPipe(world, x, y, z);
			if (pipe != null && pipe instanceof CraftingManager) {
				return new GuiCraftingManager(player, (CraftingManager) pipe);
			}else{
				TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
				if(tile instanceof ICraftingManager){
					return new GuiCraftingManagerU(player, (ICraftingManager) tile);
				}
			}
		}
		break;
		case TemplatePkg:
		{
			EnumHand hand = EnumHand.values()[x];
			ItemStack is = player.getHeldItem(hand);
			if(is.getItem() == LogisticsBridge.packageItem)
				return new GuiPackage(player, hand);
		}
		break;
		default:
			break;
		}
		return null;
	}
	private static CoreUnroutedPipe getPipe(World world, int x, int y, int z){
		TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
		LogisticsTileGenericPipe pipe = null;
		if (tile instanceof LogisticsTileGenericPipe) {
			pipe = (LogisticsTileGenericPipe) tile;
		}
		if (pipe != null) {
			return pipe.pipe;
		}
		return null;
	}
}
