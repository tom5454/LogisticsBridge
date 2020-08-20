package com.tom.logisticsbridge.tileentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.IInventoryChangedListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.item.VirtualPatternAE;
import com.tom.logisticsbridge.item.VirtualPatternAE.VirtualPatternHandler;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;
import com.tom.logisticsbridge.part.PartSatelliteBus;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.util.InventoryAdaptor;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class TileEntityCraftingManager extends AENetworkInvTile implements ITickable, ICraftingProvider,
IIdPipe, IInventoryChangedListener, ICraftingManager {
	private static final IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
	private int priority;
	private List<ICraftingPatternDetails> craftingList = null;
	public String supplyID = "";
	public InventoryBasic inv = new InventoryBasic("", false, 27);
	public InvWrapper wr = new InvWrapper(inv);
	private IActionSource as = new MachineSource(this);
	private List<ItemStack> toInsert = new ArrayList<>();
	private BlockingMode blockingMode = BlockingMode.OFF;

	public TileEntityCraftingManager() {
		getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
		inv.addInventoryChangeListener(this);
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation aePartLocation) {
		return AECableType.SMART;
	}

	private boolean checkBlocking() {
		switch (blockingMode) {
		case EMPTY_MAIN_SATELLITE:
		{
			if(supplyID.isEmpty())return false;
			PartSatelliteBus bus = find(supplyID);
			if(bus == null)return false;
			InventoryAdaptor inv = bus.getHandler();
			if (inv != null) {
				if(inv.containsItems())return false;
			}
			return true;
		}

		case REDSTONE_HIGH:
			if(!getWorld().isBlockPowered(getPos()))return false;
			return true;

		case REDSTONE_LOW:
			if(getWorld().isBlockPowered(getPos()))return false;
			return true;

			/*case WAIT_FOR_RESULT://TODO
		{
			IRouter resultR = getResultRouterByID(getResultUUID());
			if(resultR == null)return false;
			if(!(resultR.getPipe() instanceof ResultPipe))return false;
			if(((ResultPipe) resultR.getPipe()).hasRequests())return false;
			return true;
		}*/

		default:
			return true;
		}
	}

	@Override
	public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
		if(!checkBlocking())return false;
		if(patternDetails instanceof VirtualPatternHandler){
			if(toInsert.size() > 10)return false;
			ItemStack stack = patternDetails.getCondensedOutputs()[0].asItemStackRepresentation();
			if(!stack.hasTagCompound())return false;
			String id = stack.getTagCompound().getString("__pkgDest");
			toInsert.add(LogisticsBridge.packageStack(patternDetails.getCondensedInputs()[0].asItemStackRepresentation(), 1, id, true));
			return true;
		}
		if(supplyID.isEmpty())return false;
		PartSatelliteBus bus = find(supplyID);
		if(bus == null)return false;
		for (int i = 0;i < table.getSizeInventory();i++) {
			ItemStack is = table.getStackInSlot(i);
			if(!is.isEmpty() && is.getItem() == LogisticsBridge.packageItem && is.hasTagCompound() && is.getTagCompound().getBoolean("__actStack")){
				ItemStack pkgItem = new ItemStack(is.getTagCompound());
				String id = is.getTagCompound().getString("__pkgDest");
				PartSatelliteBus b = find(id);
				if(b == null)return false;
				pkgItem.setCount(pkgItem.getCount() * is.getCount());
				if(!b.push(pkgItem))return false;
				table.removeStackFromSlot(i);
			}
		}
		return bus.push(table);
	}
	public ItemStack insertItem(ItemStack stack, boolean simulate) {
		if(this.getProxy().getNode() == null || stack.isEmpty())return stack;
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IAEItemStack st = ITEMS.createStack(stack);
		IAEItemStack r = i.injectItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, as);
		return r == null ? ItemStack.EMPTY : r.createItemStack();
	}
	private PartSatelliteBus find(String id){
		for (final IGridNode node : getNode().getGrid().getMachines(PartSatelliteBus.class)) {
			IGridHost h = node.getMachine();
			if(h instanceof PartSatelliteBus){
				PartSatelliteBus satellite = (PartSatelliteBus) h;
				if(satellite.satelliteId.equals(id)){
					return satellite;
				}
			}
		}
		return null;
	}
	@Override
	public boolean isBusy() {
		return supplyID.isEmpty();
	}

	@Override
	public void provideCrafting(ICraftingProviderHelper craftingTracker) {
		if( getNode() != null && this.getNode().isActive() && this.craftingList != null )
		{
			for( final ICraftingPatternDetails details : this.craftingList )
			{
				details.setPriority( this.priority );
				craftingTracker.addCraftingOption( this, details );
			}
		}
	}

	@Override
	public String getPipeID(int id) {
		return id == 0 ? supplyID : Integer.toString(blockingMode.ordinal());
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		if(pipeID == null)pipeID = "";
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setTilePos(getTile());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setTilePos(getTile());
			MainProxy.sendPacketToPlayer(packet, player);
		}
		if(id == 0)supplyID = pipeID;
		else if(id == 1)blockingMode = BlockingMode.VALUES[Math.abs(pipeID.charAt(0) - '0') % BlockingMode.VALUES.length];
	}
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound.setString("supplyName", supplyID);
		compound.setByte("blockingMode", (byte) blockingMode.ordinal());
		return super.writeToNBT(compound);
	}
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		supplyID = compound.getString("supplyName");
		blockingMode = BlockingMode.VALUES[Math.abs(compound.getByte("blockingMode")) % BlockingMode.VALUES.length];
		super.readFromNBT(compound);
		updateCraftingList();
	}
	private void addToCraftingList( final ItemStack is )
	{
		if( is.isEmpty() )
		{
			return;
		}

		if( is.getItem() instanceof ICraftingPatternItem )
		{
			final ICraftingPatternItem cpi = (ICraftingPatternItem) is.getItem();
			final ICraftingPatternDetails details = cpi.getPatternForItem( is, world );

			if( details != null )
			{
				if( this.craftingList == null )
				{
					this.craftingList = new ArrayList<>();
				}

				IAEItemStack[] cin = details.getCondensedInputs();
				IAEItemStack[] in = details.getInputs();

				List<ItemStack> pkgs = new ArrayList<>();
				visitArray(cin, pkgs, false);
				visitArray(in, pkgs, false);
				pkgs.stream().map(p -> VirtualPatternAE.create(new ItemStack(p.getTagCompound()), p)).forEach(craftingList::add);

				this.craftingList.add( details );
			}
		}
	}
	private void visitArray(IAEItemStack[] array, List<ItemStack> pkgs, boolean act) {
		for (int i = 0;i < array.length;i++) {
			IAEItemStack iaeItemStack = array[i];
			if(iaeItemStack != null){
				ItemStack is = iaeItemStack.getDefinition();
				if(is.getItem() == LogisticsBridge.packageItem){
					if(is.hasTagCompound() && is.getTagCompound().getBoolean("__actStack") == act){
						if(!act){
							is = is.copy();
							is.getTagCompound().setBoolean("__actStack", true);
							array[i] = ITEMS.createStack(is);
						}
						pkgs.add(is);
					}
				}
			}
		}
	}

	private void updateCraftingList()
	{
		final Boolean[] accountedFor = new Boolean[inv.getSizeInventory()]; // 9...
		Arrays.fill(accountedFor, false);

		assert ( accountedFor.length == this.inv.getSizeInventory() );

		if( !this.getProxy().isReady() )
		{
			return;
		}

		if( this.craftingList != null )
		{
			Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
			while( i.hasNext() )
			{
				final ICraftingPatternDetails details = i.next();
				boolean found = false;

				for( int x = 0; x < accountedFor.length; x++ )
				{
					final ItemStack is = this.inv.getStackInSlot( x );
					if( details.getPattern() == is )
					{
						accountedFor[x] = found = true;
					}
				}

				if(details instanceof VirtualPatternHandler){
					found = true;
				}

				if( !found )
				{
					i.remove();
				}
			}
			List<ItemStack> pkgs = new ArrayList<>();
			for (ICraftingPatternDetails is : craftingList) {
				visitArray(is.getCondensedInputs(), pkgs, true);
				visitArray(is.getInputs(), pkgs, true);
			}
			i = this.craftingList.iterator();
			while( i.hasNext() )
			{
				final ICraftingPatternDetails details = i.next();
				boolean found = false;

				if(details instanceof VirtualPatternHandler){
					if(!pkgs.isEmpty()){
						IAEItemStack[] in = details.getCondensedOutputs();
						for (int j = 0;j < in.length;j++) {
							IAEItemStack iaeItemStack = in[j];
							ItemStack is = iaeItemStack == null ? ItemStack.EMPTY : iaeItemStack.asItemStackRepresentation();
							if(!is.isEmpty() && is.getItem() == LogisticsBridge.packageItem && pkgs.stream().anyMatch(s -> ItemStack.areItemStackTagsEqual(s, is))){
								found = true;
								break;
							}
						}
					}
				}else found = true;

				if( !found )
				{
					i.remove();
				}
			}
		}

		for( int x = 0; x < accountedFor.length; x++ )
		{
			if( !accountedFor[x] )
			{
				this.addToCraftingList( this.inv.getStackInSlot( x ) );
			}
		}

		this.getNode().getGrid().postEvent( new MENetworkCraftingPatternChange( this, this.getNode() ) );
	}
	@Override
	public void onInventoryChanged(IInventory invBasic) {
		updateCraftingList();
	}
	@Override
	public String getName(int id) {
		return null;
	}
	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}
	@Override
	public IItemHandler getInternalInventory() {
		return wr;
	}
	@Override
	public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
	}
	@Override
	public NBTTagCompound downloadSettings(SettingsFrom from) {
		NBTTagCompound tag = super.downloadSettings(from);
		if(tag == null)tag = new NBTTagCompound();
		if(from == SettingsFrom.DISMANTLE_ITEM){
			tag.setTag("patterns", LogisticsBridge.saveAllItems(inv));
			inv.clear();
		}
		if(!supplyID.isEmpty())tag.setString("satName", supplyID);
		return tag.hasNoTags() ? null : tag;
	}
	@Override
	public void uploadSettings(SettingsFrom from, NBTTagCompound compound) {
		super.uploadSettings(from, compound);
		if(from == SettingsFrom.DISMANTLE_ITEM){
			LogisticsBridge.loadAllItems(compound.getTagList("patterns", 10), inv);
		}
		supplyID = compound.getString("satName");
	}
	private IGridNode getNode(){
		return this.getProxy().getNode();
	}
	@Override
	public void onReady()
	{
		super.onReady();
		updateCraftingList();
	}

	@Override
	public void update() {
		if(!world.isRemote){
			if(!toInsert.isEmpty()){
				ListIterator<ItemStack> itr = toInsert.listIterator();
				while (itr.hasNext()) {
					ItemStack stack = itr.next();
					if(stack.isEmpty()){
						itr.remove();
						continue;
					}
					ItemStack result = insertItem(stack, false);
					if(!result.isEmpty())itr.set(result);
					else itr.remove();
				}
			}
		}
	}
	@Override
	public List<String> list(int id) {
		List<String> ret = new ArrayList<>();
		for (final IGridNode node : getNode().getGrid().getMachines(PartSatelliteBus.class)) {
			IGridHost h = node.getMachine();
			if(h instanceof PartSatelliteBus){
				PartSatelliteBus satellite = (PartSatelliteBus) h;
				ret.add(satellite.satelliteId);
			}
		}

		return ret;
	}
	public void openGui(EntityPlayer playerIn){
		playerIn.openGui(LogisticsBridge.modInstance, GuiIDs.CraftingManager.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
		ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(supplyID).setId(0).setPosX(pos.getX()).setPosY(pos.getY()).setPosZ(pos.getZ());
		MainProxy.sendPacketToPlayer(packet, playerIn);
	}

	@Override
	public ItemStack satelliteDisplayStack() {
		return AE2Plugin.SATELLITE_BUS_SRC.stack(1);
	}

	@Override
	public Slot createGuiSlot(int i, int x, int y) {
		return new SlotEncPattern(i, x, y);
	}

	private class SlotEncPattern extends Slot {
		private final Item pattern = AE2Plugin.INSTANCE.api.definitions().items().encodedPattern().maybeItem().orElse(Items.AIR);
		public SlotEncPattern(int index, int xPosition, int yPosition) {
			super(inv, index, xPosition, yPosition);
		}
		@Override
		public int getSlotStackLimit() {
			return 1;
		}
		@Override
		public boolean isItemValid(ItemStack stack) {
			return stack.getItem() == pattern;
		}
	}

	@Override
	public BlockPos getPosition() {
		return pos;
	}

	@Override
	public BlockingMode getBlockingMode() {
		return blockingMode;
	}
}
