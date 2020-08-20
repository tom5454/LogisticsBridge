package com.tom.logisticsbridge.tileentity;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.items.SlotItemHandler;

import com.raoulvdberge.refinedstorage.tile.TileNode;

import com.tom.logisticsbridge.RSPlugin;
import com.tom.logisticsbridge.node.NetworkNodeCraftingManager;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;

public class TileEntityCraftingManagerRS extends TileNode<NetworkNodeCraftingManager> implements ICraftingManager {

	@Override
	public NetworkNodeCraftingManager createNode(World world, BlockPos pos) {
		return new NetworkNodeCraftingManager(world, pos);
	}

	@Override
	public String getNodeId() {
		return NetworkNodeCraftingManager.ID;
	}

	@Override
	public String getPipeID(int id) {
		return getNode().getPipeID(id);
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		getNode().setPipeID(id, pipeID, player);
	}

	@Override
	public String getName(int id) {
		return getNode().getName();
	}

	@Override
	public ItemStack satelliteDisplayStack() {
		return new ItemStack(RSPlugin.satelliteBus);
	}

	@Override
	public Slot createGuiSlot(int i, int x, int y) {
		return new SlotItemHandler(getNode().getPatternInventory(), i, x, y);
	}

	@Override
	public BlockPos getPosition() {
		return pos;
	}

	@Override
	public List<String> list(int id) {
		return getNode().list(id);
	}

	@Override
	public BlockingMode getBlockingMode() {
		return getNode().getBlockingMode();
	}
}
