package com.tom.logisticsbridge.part;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.core.AELog;
import appeng.core.settings.TickRates;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.parts.PartModel;
import appeng.parts.automation.PartSharedItemBus;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class PartSatelliteBus extends PartSharedItemBus implements IIdPipe {
	public static final ResourceLocation MODEL_BASE = new ResourceLocation( LogisticsBridge.ID, "part/satellite_bus_base" );

	@PartModels
	public static final IPartModel MODELS_OFF = new PartModel( MODEL_BASE, new ResourceLocation( LogisticsBridge.ID, "part/satellite_bus_off" ) );

	@PartModels
	public static final IPartModel MODELS_ON = new PartModel( MODEL_BASE, new ResourceLocation( LogisticsBridge.ID, "part/satellite_bus_on" ) );

	@PartModels
	public static final IPartModel MODELS_HAS_CHANNEL = new PartModel( MODEL_BASE, new ResourceLocation( LogisticsBridge.ID, "part/satellite_bus_has_channel" ) );
	private List<IAEItemStack> itemsToInsert = new ArrayList<>();
	public String satelliteId = "";
	@Reflected
	public PartSatelliteBus(ItemStack is) {
		super(is);
	}

	@Override
	public TickingRequest getTickingRequest( final IGridNode node )
	{
		return new TickingRequest( TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false );
	}

	@Override
	public TickRateModulation tickingRequest( final IGridNode node, final int ticksSinceLastCall )
	{
		return this.doBusWork();
	}

	@Override
	protected TickRateModulation doBusWork() {
		if( !this.getProxy().isActive() || !this.canDoBusWork() )
		{
			return TickRateModulation.IDLE;
		}
		if(!itemsToInsert.isEmpty()){
			ListIterator<IAEItemStack> itr = itemsToInsert.listIterator();
			boolean didSomething = false;
			while (itr.hasNext()) {
				IAEItemStack stack = itr.next();
				if(stack == null){
					itr.remove();
					continue;
				}
				IAEItemStack result = injectCraftedItems(stack, Actionable.MODULATE);
				if(!stack.equals(result))didSomething = true;
				if(result != null)itr.set(result);
				else itr.remove();
			}
			return didSomething ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
		}
		return TickRateModulation.SLOWER;
	}
	@Override
	public void getBoxes( final IPartCollisionHelper bch )
	{
		bch.addBox( 5, 5, 12, 11, 11, 13 );
		bch.addBox( 3, 3, 13, 13, 13, 14 );
		bch.addBox( 2, 2, 14, 14, 14, 16 );
	}

	@Override
	public float getCableConnectionLength( AECableType cable )
	{
		return 1;
	}
	private IAEItemStack injectCraftedItems( final IAEItemStack items, final Actionable mode )
	{
		final InventoryAdaptor d = this.getHandler();

		try
		{
			if( d != null && this.getProxy().isActive() )
			{
				final IEnergyGrid energy = this.getProxy().getEnergy();
				final double power = items.getStackSize();

				if( energy.extractAEPower( power, mode, PowerMultiplier.CONFIG ) > power - 0.01 )
				{
					if( mode == Actionable.MODULATE )
					{
						return AEItemStack.fromItemStack( d.addItems( items.createItemStack() ) );
					}
					return AEItemStack.fromItemStack( d.simulateAdd( items.createItemStack() ) );
				}
			}
		}
		catch( final GridAccessException e )
		{
			AELog.debug( e );
		}

		return items;
	}
	@Override
	public IPartModel getStaticModels()
	{
		if( this.isActive() && this.isPowered() )
		{
			return MODELS_HAS_CHANNEL;
		}
		else if( this.isPowered() )
		{
			return MODELS_ON;
		}
		else
		{
			return MODELS_OFF;
		}
	}
	@Override
	public boolean onPartActivate( final EntityPlayer player, final EnumHand hand, final Vec3d posIn )
	{
		if( Platform.isServer() )
		{
			if(player.getHeldItem(hand).getItem() == LogisticsBridge.packageItem){
				ItemStack is = player.getHeldItem(hand);
				if(!is.hasTagCompound())is.setTagCompound(new NBTTagCompound());
				is.getTagCompound().setString("__pkgDest", satelliteId);
				player.inventoryContainer.detectAndSendChanges();
			}else{
				TileEntity tile = this.getHost().getTile();
				BlockPos pos = tile.getPos();
				player.openGui(LogisticsBridge.modInstance, 100 + getSide().ordinal(), tile.getWorld(), pos.getX(), pos.getY(), pos.getZ());
				final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(satelliteId).setId(0).setSide(getSide().ordinal()).setTilePos(getTile());
				MainProxy.sendPacketToPlayer(packet, player);
			}
		}
		return true;
	}

	@Override
	public String getPipeID(int id) {
		return satelliteId;
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setSide(getSide().ordinal()).setTilePos(getTile());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setSide(getSide().ordinal()).setTilePos(getTile());
			MainProxy.sendPacketToPlayer(packet, player);
		}
		satelliteId = pipeID;
	}

	@Override
	public String getName(int id) {
		return "gui.satelliteBus.id";
	}
	@Override
	public void writeToNBT(NBTTagCompound extra) {
		super.writeToNBT(extra);
		extra.setString("satName", satelliteId);
		NBTTagList lst = new NBTTagList();
		itemsToInsert.stream().map(s -> {
			NBTTagCompound tag = new NBTTagCompound();
			s.writeToNBT(tag);
			return tag;
		}).forEach(lst::appendTag);
		extra.setTag("itemsToInsert", lst);
	}
	@Override
	public void readFromNBT(NBTTagCompound extra) {
		super.readFromNBT(extra);
		satelliteId = extra.getString("satName");
		NBTTagList lst = extra.getTagList("itemsToInsert", 10);
		itemsToInsert.clear();
		IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
		for (int i = 0;i < lst.tagCount();i++) {
			NBTTagCompound tag = lst.getCompoundTagAt(i);
			itemsToInsert.add(ITEMS.createFromNBT(tag));
		}
	}

	public boolean push(IInventory table) {
		if(itemsToInsert.size() > 9)return false;
		IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
		for (int i = 0;i < table.getSizeInventory();i++) {
			ItemStack is = table.removeStackFromSlot(i);
			if(!is.isEmpty())
				itemsToInsert.add(ITEMS.createStack(is));
		}
		return true;
	}
	public boolean push(ItemStack is) {
		if(itemsToInsert.size() > 9)return false;
		IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
		if(!is.isEmpty())
			itemsToInsert.add(ITEMS.createStack(is));
		return true;
	}

	@Override
	public InventoryAdaptor getHandler() {
		return super.getHandler();
	}
}
