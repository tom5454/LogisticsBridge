package com.tom.logisticsbridge.tileentity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.google.common.collect.ImmutableSet;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.api.IDynamicPatternDetails;
import com.tom.logisticsbridge.item.VirtualPattern;
import com.tom.logisticsbridge.pipe.BridgePipe.OpResult;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.crafting.CraftingLink;
import appeng.me.GridAccessException;
import appeng.me.storage.MEMonitorIInventory;
import appeng.tile.grid.AENetworkInvTile;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.IMEAdaptor;
import appeng.util.inv.InvOperation;

public class TileEntityBridge extends AENetworkInvTile implements IGridHost, ITickable, IActionSource, ICraftingRequester, ICellContainer, ICraftingProvider, ICraftingCallback, IMEInventoryHandler<IAEItemStack>, IItemHandlerModifiable, IDynamicPatternDetails {
	private Optional<IActionHost> machine = Optional.of(this);
	private Set<ICraftingLink> links = new HashSet<>();
	private Set<ICraftingPatternDetails> craftings = new HashSet<>();
	public Req reqapi;
	private MEMonitorIInventory meInv = new MEMonitorIInventory(new IMEAdaptor(this, this));
	@SuppressWarnings("rawtypes")
	private List<IMEInventoryHandler> cellArray = Collections.singletonList(this);
	private InventoryBasic invBasic = new InventoryBasic("", false, 4);
	private InvWrapper wrapper = new InvWrapper(invBasic);
	private MEMonitorIInventory intInv = new MEMonitorIInventory(new AdaptorItemHandler(wrapper));
	private static final IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
	private IItemList<IAEItemStack> fakeItems = ITEMS.createList();
	private Map<ItemStack, Integer> toCraft = new HashMap<>();
	private final IAEItemStack FAKE_ITEM = ITEMS.createStack(new ItemStack(LogisticsBridge.logisticsFakeItem, 1));
	private long lastInjectTime;
	//private boolean disableLP;

	@Override
	public void update() {
		if(!world.isRemote){
			long wt = world.getTotalWorldTime();
			if(wt % 40 == 0 && this.getProxy().getNode() != null){
				markInvDirty();
				synchronized (toCraft) {
					toCraft.entrySet().forEach(s -> craftStack(s.getKey(), s.getValue(), false));
					toCraft.clear();
				}
			}
			if(lastInjectTime + 200 < wt && this.getProxy().getNode() != null && !invBasic.isEmpty()){
				lastInjectTime = wt - 20;
				for(int i = 0;i<invBasic.getSizeInventory();i++){
					invBasic.setInventorySlotContents(i, insertItem(0, invBasic.getStackInSlot(i), false));
				}
			}
			try {
				ICraftingGrid cg = this.getProxy().getGrid().getCache(ICraftingGrid.class);
				if(cg.isRequesting(FAKE_ITEM)){
					insertItem(0, LogisticsBridge.fakeStack(null, 1), false);
				}
			} catch (GridAccessException e) {
			}
			//IStorageGrid sg = node.getGrid().getCache(IStorageGrid.class);
			/*if(wt % 100 == 0 && node != null){
				IMEInventoryHandler<IAEItemStack> i = sg.getInventory(ITEMS);
				IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
				craftingCache.clear();
				StreamSupport.stream(Spliterators.spliteratorUnknownSize(items.iterator(), Spliterator.ORDERED), false).
				filter(IAEItemStack::isCraftable).forEach(s -> {
					CC c = new CC();
					cg.beginCraftingJob(world, node.getGrid(), this, s, c);
					craftingCache.put(s.asItemStackRepresentation(), c);
				});
			}*/
		}
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation aePartLocation) {
		return AECableType.SMART;
	}

	public List<BridgeStack<ItemStack>> getItems(){
		if(this.getProxy().getNode() == null)return Collections.emptyList();// || disableLP
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
		List<BridgeStack<ItemStack>> list =  StreamSupport.stream(Spliterators.spliteratorUnknownSize(items.iterator(), Spliterator.ORDERED), false).
				map(ae -> new BridgeStack<>(ae.asItemStackRepresentation(), ae.getStackSize(), ae.isCraftable(), ae.getCountRequestable())).
				filter(s -> s.obj.getItem() != LogisticsBridge.logisticsFakeItem).collect(Collectors.toList());
		//System.out.println(list);
		return list;
	}

	public long countItem(ItemStack stack, boolean requestable){
		if(this.getProxy().getNode() == null)return 0;
		int buffered = 0;
		for(int i = 0;i<invBasic.getSizeInventory();i++){
			ItemStack is = invBasic.getStackInSlot(i);
			if(ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)){
				buffered += is.getCount();
			}
		}
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
		IAEItemStack is = items.findPrecise(ITEMS.createStack(stack));
		return is == null ? 0 : requestable ? is.getStackSize()+is.getCountRequestable()+buffered : is.getStackSize()+buffered;
	}

	public ItemStack extractStack(ItemStack stack, int count, boolean simulate){
		if(this.getProxy().getNode() == null)return ItemStack.EMPTY;
		for(int i = 0;i<invBasic.getSizeInventory();i++){
			ItemStack is = invBasic.getStackInSlot(i);
			if(ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)){
				return wrapper.extractItem(i, count, simulate);
			}
		}
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IAEItemStack st = ITEMS.createStack(stack);
		st.setStackSize(count);
		IAEItemStack is = i.extractItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, this);
		return is == null ? ItemStack.EMPTY : is.createItemStack();
	}

	public void craftStack(ItemStack stack, int count, boolean simulate){
		if(this.getProxy().getNode() == null)return;
		/*if(simulate){
			return craftingCache.entrySet().stream().filter(s -> s.getKey().isItemEqual(stack)).findFirst().
					map(c -> c.getValue().items).orElse(null);
		}*/
		ICraftingGrid g = this.getProxy().getNode().getGrid().getCache(ICraftingGrid.class);
		g.beginCraftingJob(world, this.getProxy().getNode().getGrid(), this, ITEMS.createStack(stack), this);
		return;
	}

	@Override
	public Optional<EntityPlayer> player() {
		return Optional.empty();
	}

	@Override
	public Optional<IActionHost> machine() {
		return machine;
	}

	@Override
	public <T> Optional<T> context(Class<T> key) {
		return Optional.empty();
	}

	@Override
	public IGridNode getActionableNode() {
		return this.getProxy().getNode();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
		return channel == ITEMS ? cellArray : Collections.emptyList();
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public void blinkCell(int slot) {
	}

	@Override
	public void calculationComplete(ICraftingJob job) {
		if(this.getProxy().getNode() == null)return;
		if(job.isSimulation())return;
		ICraftingGrid g = this.getProxy().getNode().getGrid().getCache(ICraftingGrid.class);
		ICraftingLink link = g.submitJob(job, this, null, false, this);
		if(link == null){
			synchronized (toCraft) {
				toCraft.put(job.getOutput().asItemStackRepresentation(), (int) job.getOutput().getStackSize());
			}
		}
		if(link != null)links.add(link);
	}

	@Override
	public ImmutableSet<ICraftingLink> getRequestedJobs() {
		return ImmutableSet.copyOf(links);
	}

	@Override
	public IAEItemStack injectCraftedItems(ICraftingLink link, IAEItemStack items, Actionable mode) {
		if(mode == Actionable.MODULATE)lastInjectTime = world.getTotalWorldTime();
		return intInv.injectItems(items, mode, this);
	}

	@Override
	public void jobStateChange(ICraftingLink link) {
		links.remove(link);
	}
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		NBTTagList lst = new NBTTagList();
		compound.setTag("links", lst);
		links.stream().filter(e -> e != null).map(l -> {
			NBTTagCompound t = new NBTTagCompound();
			l.writeToNBT(t);
			return t;
		}).forEach(lst::appendTag);
		NBTTagList lst2 = new NBTTagList();
		compound.setTag("toCraft", lst2);
		toCraft.entrySet().stream().filter(e -> e != null).map(l -> {
			NBTTagCompound t = new NBTTagCompound();
			l.getKey().writeToNBT(t);
			t.setInteger("Count", l.getValue());
			return t;
		}).forEach(lst2::appendTag);
		//compound.setTag("intInv", LogisticsBridge.saveAllItems(invBasic));
		return super.writeToNBT(compound);
	}
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		NBTTagList lst = compound.getTagList("links", 10);
		toCraft.clear();
		links.clear();
		for (int i = 0;i < lst.tagCount();i++) {
			NBTTagCompound tag = lst.getCompoundTagAt(i);
			links.add(new CraftingLink(tag, this));
		}
		lst = compound.getTagList("toCraft", 10);
		for (int i = 0;i < lst.tagCount();i++) {
			NBTTagCompound tag = lst.getCompoundTagAt(i);
			int count = tag.getInteger("Count");
			toCraft.put(new ItemStack(tag), count);
		}
		//LogisticsBridge.loadAllItems(compound.getTagList("intInv", 10), invBasic);
	}

	@Override
	public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
		return input;
	}

	@Override
	public IAEItemStack extractItems(IAEItemStack request, Actionable type, IActionSource src) {
		if(request.createItemStack().getItem() == LogisticsBridge.logisticsFakeItem){
			IAEItemStack req = fakeItems.findPrecise(request);
			if(req == null)return null;
			long min = Math.min(req.getStackSize(), request.getStackSize());
			IAEItemStack ret = req.copy();
			ret.setStackSize(min);
			if(type == Actionable.MODULATE)req.decStackSize(min);
			return ret;
		}
		/*if(true)return null;
		if( reqapi != null )
		{
			if( type == Actionable.SIMULATE )
			{
				OpResult simulation = reqapi.simulateRequest( request.createItemStack(), false, true );
				if( simulation.used.size() == 0 )
				{
					return null;
				}

				return ITEMS.createStack( simulation.used.get( 0 ) );
			}

			OpResult returned = reqapi.performRequest( request.createItemStack(), false );
			if(returned.missing.size() != 0 || returned.used.size() == 0)
			{
				return null;
			}
			IAEItemStack is = ITEMS.createStack( returned.used.get( 0 ) );
			markInvDirty();
			return is;
		}*/

		return null;
	}

	private void markInvDirty() {
		meInv.onTick();
		this.getProxy().getNode().getGrid().postEvent(new MENetworkCellArrayUpdate());
		this.getProxy().getNode().getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.getProxy().getNode()));
	}

	@Override
	public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
		if(reqapi == null)return out;
		craftings.clear();
		fakeItems.resetStatus();
		try {
			if(Loader.instance().getLoaderState() == LoaderState.SERVER_STOPPING)return out;
			List<ItemStack> pi = reqapi.getProvidedItems();
			List<ItemStack> ci = reqapi.getCraftedItems();
			pi.stream().map(ITEMS::createStack).map(s -> {
				s.setCraftable(true);
				s.setCountRequestable(s.getStackSize());
				s.setStackSize(0);
				return s;
			}).forEach(out::add);
			//pi.stream().map(ITEMS::createStack).forEach(out::addCrafting);
			ci.stream().map(ITEMS::createStack).forEach(out::addCrafting);
			TileEntityWrapper wr = new TileEntityWrapper(this);
			pi.stream().map(i -> {
				ItemStack r = i.copy();
				r.setCount(1);
				fakeItems.add(ITEMS.createStack(LogisticsBridge.fakeStack(r, i.getCount())));
				return VirtualPattern.create(r, wr);
			}).forEach(craftings::add);
			ci.stream().map(i -> VirtualPattern.create(i, wr)).forEach(craftings::add);
			fakeItems.forEach(out::addStorage);
		} catch(Exception e){
			e.printStackTrace();
		}
		/*ICraftingGrid g = node.getGrid().getCache(ICraftingGrid.class);
		if(g instanceof ICraftingProviderHelper){
			ICraftingProviderHelper h = (ICraftingProviderHelper) g;
			pi.stream().map(ITEMS::createStack).forEach(h::setEmitable);
			ci.stream().map(ITEMS::createStack).forEach(h::setEmitable);
		}*/
		return out;
	}

	@Override
	public IStorageChannel<IAEItemStack> getChannel() {
		return ITEMS;
	}

	@Override
	public AccessRestriction getAccess() {
		return AccessRestriction.READ_WRITE;
	}

	@Override
	public boolean isPrioritized(IAEItemStack input) {
		return false;
	}

	@Override
	public boolean canAccept(IAEItemStack input) {
		return false;
	}

	@Override
	public int getSlot() {
		return 0;
	}

	@Override
	public boolean validForPass(int i) {
		return false;
	}

	@Override
	public int getSlots() {
		return wrapper.getSlots();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return wrapper.getStackInSlot(slot);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if(this.getProxy().getNode() == null || stack.isEmpty())return stack;
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IAEItemStack st = ITEMS.createStack(stack);
		IAEItemStack r = i.injectItems(st, simulate ? Actionable.SIMULATE : Actionable.MODULATE, this);
		return r == null ? ItemStack.EMPTY : r.createItemStack();
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return wrapper.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return 64;
	}

	@Override
	public void setStackInSlot(int slot, ItemStack stack) {
		wrapper.setStackInSlot(slot, stack);
	}

	@Override
	public void saveChanges(ICellInventory<?> arg0) {
	}

	@Override
	public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
		if(reqapi == null)return false;
		return reqapi.performRequest(patternDetails.getOutputs()[0].asItemStackRepresentation(), true).missing.isEmpty();
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public void provideCrafting(ICraftingProviderHelper craftingTracker) {
		craftings.forEach(c -> craftingTracker.addCraftingOption(this, c));
		//fakeItems.forEach(craftingTracker::setEmitable);
		craftingTracker.setEmitable(FAKE_ITEM);
	}

	@Override
	public IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
		if(reqapi == null)return def;
		try {
			//disableLP = true;
			boolean craftable = reqapi.getCraftedItems().stream().anyMatch(s -> res.isItemEqual(s));
			OpResult r = reqapi.simulateRequest(res, true, true);
			return Stream.concat(r.missing.stream(), Stream.of(LogisticsBridge.fakeStack(craftable ? null : res, 1))).map(ITEMS::createStack).toArray(IAEItemStack[]::new);
		} finally {
			//disableLP = false;
		}
	}
	/*static class CC implements ICraftingCallback {
		private Map<ItemStack, int[]> items = new HashMap<>();
		@Override
		public void calculationComplete(ICraftingJob job) {
			IItemList<IAEItemStack> item = ITEMS.createList();
			job.populatePlan(item);
			synchronized (items) {
				for (IAEItemStack st : item) {
					items.put(st.asItemStackRepresentation(), new int[]{(int) st.getStackSize(), (int) st.getCountRequestable()});
				}
			}
		}
	}*/

	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}

	@Override
	public IItemHandler getInternalInventory() {
		return this;
	}

	@Override
	public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
	}
}
