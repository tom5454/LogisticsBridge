package com.tom.logisticsbridge.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.raoulvdberge.refinedstorage.tile.TileNode;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.node.NetworkNodeSatellite;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class TileEntitySatelliteBus extends TileNode<NetworkNodeSatellite> implements IIdPipe {

	@Override
	public NetworkNodeSatellite createNode(World world, BlockPos pos) {
		return new NetworkNodeSatellite(world, pos);
	}

	@Override
	public String getNodeId() {
		return NetworkNodeSatellite.ID;
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
		return getNode().getName(id);
	}

	public void openGui(EntityPlayer playerIn, EnumHand hand) {
		if(playerIn.getHeldItem(hand).getItem() == LogisticsBridge.packageItem){
			ItemStack is = playerIn.getHeldItem(hand);
			if(!is.hasTagCompound())is.setTagCompound(new NBTTagCompound());
			is.getTagCompound().setString("__pkgDest", getNode().satelliteId);
			playerIn.inventoryContainer.detectAndSendChanges();
		}else{
			playerIn.openGui(LogisticsBridge.modInstance, 5, world, pos.getX(), pos.getY(), pos.getZ());
			ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(getNode().satelliteId).setId(0).setPosX(pos.getX()).setPosY(pos.getY()).setPosZ(pos.getZ());
			MainProxy.sendPacketToPlayer(packet, playerIn);
		}
	}

}
