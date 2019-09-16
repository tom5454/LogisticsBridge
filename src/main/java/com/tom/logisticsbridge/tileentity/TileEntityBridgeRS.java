package com.tom.logisticsbridge.tileentity;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import com.raoulvdberge.refinedstorage.tile.TileNode;

import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.api.IDynamicPatternDetailsRS;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;

public class TileEntityBridgeRS extends TileNode<NetworkNodeBridge> implements IBridge, IDynamicPatternDetailsRS {

	@Override
	public NetworkNodeBridge createNode(World world, BlockPos pos) {
		return new NetworkNodeBridge(world, pos);
	}

	@Override
	public String getNodeId() {
		return NetworkNodeBridge.ID;
	}

	@Override
	public long countItem(ItemStack stack, boolean requestable) {
		return getNode().countItem(stack, requestable);
	}

	@Override
	public void craftStack(ItemStack stack, int count, boolean simulate) {
		getNode().craftStack(stack, count, simulate);
	}

	@Override
	public List<BridgeStack<ItemStack>> getItems() {
		return getNode().getItems();
	}

	@Override
	public ItemStack extractStack(ItemStack stack, int count, boolean simulate) {
		return getNode().extractStack(stack, count, simulate);
	}

	@Override
	public void setReqAPI(Req reqapi) {
		getNode().setReqAPI(reqapi);
	}
	@Override
	public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
		return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getNode());
		}

		return super.getCapability(capability, facing);
	}

	@Override
	public NonNullList<ItemStack> getInputs(ItemStack res, NonNullList<ItemStack> def) {
		return getNode().getInputs(res, def);
	}

	public void infoMessage(EntityPlayer playerIn) {
		getNode().infoMessage(playerIn);
	}
}
