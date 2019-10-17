package com.tom.logisticsbridge.node;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;

import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class NetworkNodeSatellite extends NetworkNode implements IIdPipe {
	public static final String ID = "lb.satellite";
	private List<ItemStack> itemsToInsert = new ArrayList<>();
	public String satelliteId = "";
	public NetworkNodeSatellite(World world, BlockPos pos) {
		super(world, pos);
	}

	@Override
	public int getEnergyUsage() {
		return 1;
	}

	@Override
	public String getId() {
		return ID;
	}
	@Override
	public void update() {
		super.update();
		if(!world.isRemote){
			TileEntity te = getFacingTile();
			if(te != null && te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, getDirection())){
				IItemHandler h = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, getDirection());
				if(h != null && !itemsToInsert.isEmpty()){
					ListIterator<ItemStack> itr = itemsToInsert.listIterator();
					while (itr.hasNext()) {
						ItemStack stack = itr.next();
						if(stack == null){
							itr.remove();
							continue;
						}
						ItemStack result = injectCraftedItems(stack, h);
						if(result != null)itr.set(result);
						else itr.remove();
					}
				}
			}
		}
	}

	private ItemStack injectCraftedItems(ItemStack stack, IItemHandler h) {
		for (int i = 0; i < h.getSlots(); i++) {
			stack = h.insertItem(i, stack, false);
			if(stack.isEmpty())break;
		}
		return stack;
	}

	@Override
	public NBTTagCompound write(NBTTagCompound extra) {
		super.write(extra);
		extra.setString("satName", satelliteId);
		NBTTagList lst = new NBTTagList();
		itemsToInsert.stream().map(s -> {
			NBTTagCompound tag = new NBTTagCompound();
			s.writeToNBT(tag);
			return tag;
		}).forEach(lst::appendTag);
		extra.setTag("itemsToInsert", lst);
		return extra;
	}

	@Override
	public void read(NBTTagCompound extra) {
		super.read(extra);
		satelliteId = extra.getString("satName");
		NBTTagList lst = extra.getTagList("itemsToInsert", 10);
		itemsToInsert.clear();
		for (int i = 0;i < lst.tagCount();i++) {
			itemsToInsert.add(new ItemStack(lst.getCompoundTagAt(i)));
		}
	}

	public void push(ItemStack is) {
		if(!is.isEmpty())
			itemsToInsert.add(is);
	}

	public boolean canAccept(){
		return itemsToInsert.size() < 10;
	}

	@Override
	public String getPipeID(int id) {
		return satelliteId;
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setBlockPos(getPos()).setDimension(getWorld());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setBlockPos(getPos()).setDimension(getWorld());
			MainProxy.sendPacketToPlayer(packet, player);
		}
		satelliteId = pipeID;
	}

	@Override
	public String getName(int id) {
		return "gui.satelliteBus.id";
	}
}
