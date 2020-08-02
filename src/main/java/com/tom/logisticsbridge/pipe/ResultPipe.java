package com.tom.logisticsbridge.pipe;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.items.CapabilityItemHandler;

import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IItemSpaceControl;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.UpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.LogisticsDictPromise;
import logisticspipes.routing.LogisticsExtraDictPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.pathfinder.IPipeInformationProvider.ConnectionPipeType;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import network.rs485.logisticspipes.connection.NeighborTileEntity;
import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

public class ResultPipe extends CoreRoutedPipe implements IIdPipe, IProvideItems, IChangeListener {
	public static TextureType TEXTURE = Textures.empty;
	public ResultPipe(Item item) {
		super(item);
		_orderItemManager = new LogisticsItemOrderManager(this, this);
	}

	@Override
	public ItemSendMode getItemSendMode() {
		return ItemSendMode.Normal;
	}

	@Override
	public TextureType getCenterTexture() {
		return TEXTURE;
	}

	@Override
	public LogisticsModule getLogisticsModule() {
		return null;
	}

	public static Set<ResultPipe> AllResults = Collections.newSetFromMap(new WeakHashMap<>());;

	// called only on server shutdown
	public static void cleanup() {
		AllResults.clear();
	}

	public String id;
	private boolean cachedAreAllOrderesToBuffer;
	private WeakReference<TileEntity> lastAccessedCrafter;
	private List<NeighborTileEntity<TileEntity>> cachedCrafters = null;

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		id = nbttagcompound.getString("resultname");
		if(nbttagcompound.hasKey("resultid")){
			id = Integer.toString(nbttagcompound.getInteger("resultid"));
		}
		ensureAllSatelliteStatus();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		if(id != null)nbttagcompound.setString("resultname", id);
		super.writeToNBT(nbttagcompound);
	}

	@SuppressWarnings("deprecation")
	protected void ensureAllSatelliteStatus() {
		if (MainProxy.isClient()) {
			return;
		}
		if (id.isEmpty() && AllResults.contains(this)) {
			AllResults.remove(this);
		}
		if (!id.isEmpty() || !AllResults.contains(this)) {
			AllResults.add(this);
		}
	}

	@Override
	public void onAllowedRemoval() {
		if (MainProxy.isClient(getWorld())) {
			return;
		}
		if (AllResults.contains(this)) {
			AllResults.remove(this);
		}
		while (_orderItemManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
			_orderItemManager.sendFailed();
		}
	}

	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		// Send the satellite id when opening gui
		final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(id).setId(0).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityplayer);
		entityplayer.openGui(LogisticsBridge.modInstance, GuiIDs.ResultPipe.ordinal(), getWorld(), getX(), getY(), getZ());
	}
	@Override
	public void setPipeID(int fid, String integer, EntityPlayer player) {
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(integer).setId(fid).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToPlayer(packet, player);
		}
		this.id = integer;
		ensureAllSatelliteStatus();
	}
	@Override
	public String getPipeID(int fid) {
		return id;
	}

	@Override
	public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filter) {
		System.out.println("ResultPipe.canProvide()");
	}

	@Override
	public LogisticsItemOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
		if (promise instanceof LogisticsExtraDictPromise) {
			getItemOrderManager().removeExtras(((LogisticsExtraDictPromise) promise).getResource());
		}
		if (promise instanceof LogisticsExtraPromise) {
			getItemOrderManager()
			.removeExtras(new DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null));
		}
		if (promise instanceof LogisticsDictPromise) {
			spawnParticle(Particles.WhiteParticle, 2);
			return getItemOrderManager().addOrder(((LogisticsDictPromise) promise)
					.getResource(), destination, ResourceType.CRAFTING, info);
		}
		spawnParticle(Particles.WhiteParticle, 2);
		return getItemOrderManager()
				.addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
	}

	@Override
	public void enabledUpdateEntity() {
		if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
			if (isNthTick(6)) {
				cacheAreAllOrderesToBuffer();
			}
			if (getItemOrderManager().isFirstOrderWatched()) {
				if(lastAccessedCrafter == null) {
					getItemOrderManager().setMachineProgress((byte) 0);
				} else {
					TileEntity tile = lastAccessedCrafter.get();
					if (tile != null) {
						getItemOrderManager().setMachineProgress(SimpleServiceLocator.machineProgressProvider.getProgressForTile(tile));
					} else {
						getItemOrderManager().setMachineProgress((byte) 0);
					}
				}
			}
		} else {
			cachedAreAllOrderesToBuffer = false;
		}

		if (!isNthTick(6)) {
			return;
		}

		if ((!getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
			return;
		}

		List<NeighborTileEntity<TileEntity>> adjacentCrafters = locateCrafters();
		if (adjacentCrafters.size() < 1) {
			if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
				getItemOrderManager().sendFailed();
			}
			return;
		}

		spawnParticle(Particles.VioletParticle, 2);

		int itemsleft = itemsToExtract();
		int stacksleft = stacksToExtract();
		while (itemsleft > 0 && stacksleft > 0 && (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
			LogisticsItemOrder nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
			int maxtosend = Math.min(itemsleft, nextOrder.getResource().stack.getStackSize());
			maxtosend = Math.min(nextOrder.getResource().getItem().getMaxStackSize(), maxtosend);
			// retrieve the new crafted items
			ItemStack extracted = null;
			NeighborTileEntity<TileEntity> adjacent = null;
			for (NeighborTileEntity<TileEntity> adjacentCrafter : adjacentCrafters) {
				adjacent = adjacentCrafter;
				extracted = extract(adjacent, nextOrder.getResource(), maxtosend);
				if (extracted != null && extracted.getCount() > 0) {
					break;
				}
			}
			if (extracted == null || extracted.getCount() == 0) {
				getItemOrderManager().deferSend();
				break;
			}
			getCacheHolder().trigger(CacheTypes.Inventory);
			lastAccessedCrafter = new WeakReference<>(adjacent.getTileEntity());
			// send the new crafted items to the destination
			ItemIdentifier extractedID = ItemIdentifier.get(extracted);
			while (extracted.getCount() > 0) {
				if (!doesExtractionMatch(nextOrder, extractedID)) {
					LogisticsItemOrder startOrder = nextOrder;
					if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
						do {
							getItemOrderManager().deferSend();
							nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
						} while (!doesExtractionMatch(nextOrder, extractedID) && startOrder != nextOrder);
					}
					if (startOrder == nextOrder) {
						int numtosend = Math.min(extracted.getCount(), extractedID.getMaxStackSize());
						if (numtosend == 0) {
							break;
						}
						stacksleft -= 1;
						itemsleft -= numtosend;
						ItemStack stackToSend = extracted.splitStack(numtosend);
						//Route the unhandled item

						sendStack(stackToSend, -1, ItemSendMode.Normal, null);
						continue;
					}
				}
				int numtosend = Math.min(extracted.getCount(), extractedID.getMaxStackSize());
				numtosend = Math.min(numtosend, nextOrder.getResource().stack.getStackSize());
				if (numtosend == 0) {
					break;
				}
				stacksleft -= 1;
				itemsleft -= numtosend;
				ItemStack stackToSend = extracted.splitStack(numtosend);
				if (nextOrder.getDestination() != null) {
					SinkReply reply = LogisticsManager.canSink(stackToSend, nextOrder.getDestination().getRouter(), null, true, ItemIdentifier.get(stackToSend), null, true, false);
					boolean defersend = false;
					if (reply == null || reply.bufferMode != BufferMode.NONE || reply.maxNumberOfItems < 1) {
						defersend = true;
					}
					IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stackToSend);
					item.setDestination(nextOrder.getDestination().getRouter().getSimpleID());
					item.setTransportMode(TransportMode.Active);
					item.setAdditionalTargetInformation(nextOrder.getInformation());
					queueRoutedItem(item, adjacent.getDirection());
					getItemOrderManager().sendSuccessfull(stackToSend.getCount(), defersend, item);
				} else {
					sendStack(stackToSend, -1, ItemSendMode.Normal, nextOrder.getInformation());
					getItemOrderManager().sendSuccessfull(stackToSend.getCount(), false, null);
				}
				if (getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
					nextOrder = getItemOrderManager().peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA); // fetch but not remove.
				}
			}
		}

	}
	private boolean doesExtractionMatch(LogisticsItemOrder nextOrder, ItemIdentifier extractedID) {
		return nextOrder.getResource().getItem().equals(extractedID) || (this.getUpgradeManager().isFuzzyUpgrade() && nextOrder.getResource().getBitSet().nextSetBit(0) != -1 && nextOrder.getResource().matches(extractedID, IResource.MatchSettings.NORMAL));
	}
	@Override
	public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {

	}

	@Override
	public void listenedChanged() {
	}
	public boolean areAllOrderesToBuffer() {
		return cachedAreAllOrderesToBuffer;
	}

	public void cacheAreAllOrderesToBuffer() {
		boolean result = true;
		for (LogisticsItemOrder order : getItemOrderManager()) {
			if (order.getDestination() instanceof IItemSpaceControl) {
				SinkReply reply = LogisticsManager.canSink(order.getResource().getItemStack().makeNormalStack(), order.getDestination().getRouter(), null, true, order.getResource().getItem(), null, true, false);
				if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
					result = false;
					break;
				}
			} else { // No Space control
				result = false;
				break;
			}
		}
		cachedAreAllOrderesToBuffer = result;
	}
	protected int neededEnergy() {
		return (int) (10 * Math.pow(1.1, getUpgradeManager().getItemExtractionUpgrade()) * Math.pow(1.2, getUpgradeManager().getItemStackExtractionUpgrade()))	;
	}

	protected int itemsToExtract() {
		return (int) Math.pow(2, getUpgradeManager().getItemExtractionUpgrade());
	}

	protected int stacksToExtract() {
		return 1 + getUpgradeManager().getItemStackExtractionUpgrade();
	}
	@Override
	public UpgradeManager getUpgradeManager() {
		return upgradeManager;
	}
	public List<NeighborTileEntity<TileEntity>> locateCrafters() {
		if (cachedCrafters == null) {
			cachedCrafters = new WorldCoordinatesWrapper(getWorld(), getPos())
					.connectedTileEntities(ConnectionPipeType.ITEM)
					.filter(neighbor -> neighbor.isItemHandler() || neighbor.getInventoryUtil() != null)
					.collect(Collectors.toList());
		}
		return cachedCrafters;
	}
	private ItemStack extract(NeighborTileEntity<TileEntity> adjacent, IResource item, int amount) {
		if (adjacent.getTileEntity().hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, adjacent.getDirection().getOpposite())) {
			return extractFromInventory(adjacent.getTileEntity(), item, amount, adjacent.getDirection());
		}
		return null;
	}
	private ItemStack extractFromInventory(TileEntity inv, IResource wanteditem, int count, EnumFacing dir) {
		IInventoryUtil invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inv, dir.getOpposite());
		ItemIdentifier itemToExtract = null;
		if(wanteditem instanceof ItemResource) {
			itemToExtract = ((ItemResource) wanteditem).getItem();
		} else if(wanteditem instanceof DictResource) {
			int max = Integer.MIN_VALUE;
			ItemIdentifier toExtract = null;
			for (Map.Entry<ItemIdentifier, Integer> content : invUtil.getItemsAndCount().entrySet()) {
				if (wanteditem.matches(content.getKey(), IResource.MatchSettings.NORMAL)) {
					if (content.getValue() > max) {
						max = content.getValue();
						toExtract = content.getKey();
					}
				}
			}
			if(toExtract == null) {
				return null;
			}
			itemToExtract = toExtract;
		}
		int available = invUtil.itemCount(itemToExtract);
		if (available == 0) {
			return null;
		}
		if (!useEnergy(neededEnergy() * Math.min(count, available))) {
			return null;
		}
		return invUtil.getMultipleItems(itemToExtract, Math.min(count, available));
	}
	private ItemStack extractFromInventoryFiltered(TileEntity inv, ItemIdentifierInventory filter, boolean isExcluded, int filterInvLimit, EnumFacing dir) {
		IInventoryUtil invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inv, dir.getOpposite());
		ItemIdentifier wanteditem = null;
		for (ItemIdentifier item : invUtil.getItemsAndCount().keySet()) {
			if (isExcluded) {
				boolean found = false;
				for (int i = 0; i < filter.getSizeInventory() && i < filterInvLimit; i++) {
					ItemIdentifierStack identStack = filter.getIDStackInSlot(i);
					if (identStack == null) {
						continue;
					}
					if (identStack.getItem().equalsWithoutNBT(item)) {
						found = true;
						break;
					}
				}
				if (!found) {
					wanteditem = item;
				}
			} else {
				boolean found = false;
				for (int i = 0; i < filter.getSizeInventory() && i < filterInvLimit; i++) {
					ItemIdentifierStack identStack = filter.getIDStackInSlot(i);
					if (identStack == null) {
						continue;
					}
					if (identStack.getItem().equalsWithoutNBT(item)) {
						found = true;
						break;
					}
				}
				if (found) {
					wanteditem = item;
				}
			}
		}
		if (wanteditem == null) {
			return null;
		}
		int available = invUtil.itemCount(wanteditem);
		if (available == 0) {
			return null;
		}
		if (!useEnergy(neededEnergy() * Math.min(64, available))) {
			return null;
		}
		return invUtil.getMultipleItems(wanteditem, Math.min(64, available));
	}
	@Override
	public String getName(int id) {
		return "gui.resultPipe.id";
	}

	public void registerExtras(IPromise promise) {
		if(promise instanceof LogisticsDictPromise) {
			getItemOrderManager().addExtra(((LogisticsDictPromise) promise).getResource());
			return;
		} else {
			ItemIdentifierStack stack = new ItemIdentifierStack(promise.getItemType(), promise.getAmount());
			getItemOrderManager().addExtra(new DictResource(stack, null));
		}
	}

	public String getResultPipeName() {
		return id;
	}

	public void extractCleanup(ItemIdentifierInventory _cleanupInventory, boolean cleanupModeIsExclude, int i) {
		List<NeighborTileEntity<TileEntity>> adjacentCrafters = locateCrafters();
		if (adjacentCrafters.size() > 0) {
			ItemStack extracted = null;
			NeighborTileEntity<TileEntity> adjacent = null;
			for (NeighborTileEntity<TileEntity> adjacentCrafter : adjacentCrafters) {
				adjacent = adjacentCrafter;
				extracted = extractFromInventoryFiltered(adjacent.getTileEntity(), _cleanupInventory, cleanupModeIsExclude, i, adjacent.getDirection());
				if (extracted != null && extracted.getCount() > 0) {
					break;
				}
			}
			if (extracted == null || extracted.getCount() == 0) {
				return;
			}
			queueRoutedItem(SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted), EnumFacing.UP);
			getCacheHolder().trigger(CacheTypes.Inventory);
		}
	}

	public boolean hasRequests() {
		return getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA);
	}
}
