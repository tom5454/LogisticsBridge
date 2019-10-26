package com.tom.logisticsbridge.tileentity;

import java.util.ArrayList;
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
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.TextComponentString;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.google.common.collect.ImmutableSet;

import com.tom.logisticsbridge.AE2Plugin;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.api.IDynamicPatternDetailsAE;
import com.tom.logisticsbridge.item.VirtualPatternAE;
import com.tom.logisticsbridge.pipe.BridgePipe.OpResult;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;
import com.tom.logisticsbridge.util.DynamicInventory;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
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
import appeng.util.inv.IMEAdaptor;
import appeng.util.inv.InvOperation;

public class TileEntityBridgeAE extends AENetworkInvTile implements IGridHost, ITickable, IActionSource,
ICraftingRequester, ICellContainer, ICraftingProvider, ICraftingCallback, IMEInventoryHandler<IAEItemStack>,
IItemHandlerModifiable, IDynamicPatternDetailsAE, IBridge {
	private Optional<IActionHost> machine = Optional.of(this);
	private Set<ICraftingLink> links = new HashSet<>();
	private Set<ICraftingPatternDetails> craftings = new HashSet<>();
	private Req reqapi;
	private MEMonitorIInventory meInv = new MEMonitorIInventory(new IMEAdaptor(this, this));
	@SuppressWarnings("rawtypes")
	private List<IMEInventoryHandler> cellArray = Collections.singletonList(this);
	private DynamicInventory dynInv = new DynamicInventory();
	private InvWrapper wrapper = new InvWrapper(dynInv);
	//private MEMonitorIInventory intInv = new MEMonitorIInventory(new AdaptorItemHandler(wrapper));
	private static final IItemStorageChannel ITEMS = AE2Plugin.INSTANCE.api.storage().getStorageChannel(IItemStorageChannel.class);
	private IItemList<IAEItemStack> fakeItems = ITEMS.createList();
	private Map<ItemStack, Integer> toCraft = new HashMap<>();
	private List<ItemStack> insertingStacks = new ArrayList<>();
	private final IAEItemStack FAKE_ITEM = ITEMS.createStack(new ItemStack(LogisticsBridge.logisticsFakeItem, 1));
	private long lastInjectTime;
	private OpResult lastPush;
	private long lastPushTime;
	private boolean disableLP;

	public TileEntityBridgeAE() {
		getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
	}

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
				dynInv.removeEmpties();
			}
			if(lastInjectTime + 200 < wt && this.getProxy().getNode() != null && !dynInv.isEmpty()){
				lastInjectTime = wt - 20;
				for(int i = 0;i<dynInv.getSizeInventory();i++){
					dynInv.setInventorySlotContents(i, insertItem(0, dynInv.getStackInSlot(i), false));
				}
				dynInv.removeEmpties();
			}
			try {
				ICraftingGrid cg = this.getProxy().getGrid().getCache(ICraftingGrid.class);
				if(cg.isRequesting(FAKE_ITEM)){
					insertItem(0, LogisticsBridge.fakeStack(1), false);
				}
			} catch (GridAccessException e) {
			}
		}
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation aePartLocation) {
		return AECableType.SMART;
	}

	@Override
	public List<BridgeStack<ItemStack>> getItems(){
		if(this.getProxy().getNode() == null || disableLP)return Collections.emptyList();
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
		List<BridgeStack<ItemStack>> list = Stream.concat(
				StreamSupport.stream(Spliterators.spliteratorUnknownSize(items.iterator(), Spliterator.ORDERED), false).
				map(ae -> new BridgeStack<>(ae.asItemStackRepresentation(), ae.getStackSize(), ae.isCraftable(), ae.getCountRequestable())).
				filter(s -> s.obj.getItem() != LogisticsBridge.logisticsFakeItem && s.obj.getItem() != LogisticsBridge.packageItem),

				Stream.concat(insertingStacks.stream(), dynInv.stream())
				.map(s -> new BridgeStack<>(s, s.getCount(), false, 0))
				).collect(Collectors.toList());
		return list;
	}

	@Override
	public long countItem(ItemStack stack, boolean requestable){
		if(this.getProxy().getNode() == null || disableLP)return 0;
		int buffered = 0;
		for(int i = 0;i<dynInv.getSizeInventory();i++){
			ItemStack is = dynInv.getStackInSlot(i);
			if(ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)){
				buffered += is.getCount();
			}
		}
		for(int i = 0;i<insertingStacks.size();i++){
			ItemStack is = insertingStacks.get(i);
			if(ItemStack.areItemsEqual(stack, is) && ItemStack.areItemStackTagsEqual(stack, is)){
				buffered += is.getCount();
			}
		}
		IStorageGrid g = this.getProxy().getNode().getGrid().getCache(IStorageGrid.class);
		IMEInventoryHandler<IAEItemStack> i = g.getInventory(ITEMS);
		IItemList<IAEItemStack> items = i.getAvailableItems(ITEMS.createList());
		IAEItemStack is = items.findPrecise(ITEMS.createStack(stack));
		long inAE = is == null ? 0 : requestable ? is.getStackSize()+is.getCountRequestable() : is.getStackSize();
		return inAE + buffered;
	}

	@Override
	public ItemStack extractStack(ItemStack stack, int count, boolean simulate){
		if(this.getProxy().getNode() == null)return ItemStack.EMPTY;
		for(int i = 0;i<dynInv.getSizeInventory();i++){
			ItemStack is = dynInv.getStackInSlot(i);
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

	@Override
	public void craftStack(ItemStack stack, int count, boolean simulate){
		if(this.getProxy().getNode() == null)return;
		ICraftingGrid g = this.getProxy().getNode().getGrid().getCache(ICraftingGrid.class);
		IAEItemStack aestack = ITEMS.createStack(stack);
		aestack.setStackSize(count);
		g.beginCraftingJob(world, this.getProxy().getNode().getGrid(), this, aestack, this);
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
		if(mode == Actionable.MODULATE){
			lastInjectTime = world.getTotalWorldTime();
			dynInv.insert(items.createItemStack());
			return null;
		}else{
			return null;
		}
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
		dynInv.removeEmpties();
		compound.setTag("intInventory", LogisticsBridge.saveAllItems(dynInv));
		return super.writeToNBT(compound);
	}
	boolean readingFromNBT;
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		try {
			readingFromNBT = true;
			super.readFromNBT(compound);
		} finally {
			readingFromNBT = false;
		}
		LogisticsBridge.loadAllItems(compound.getTagList("intInventory", 10), dynInv);
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
	}

	@Override
	public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
		if(input.asItemStackRepresentation().getItem() == LogisticsBridge.logisticsFakeItem)return null;
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
			ci.stream().map(ITEMS::createStack).forEach(out::addCrafting);
			TileEntityWrapper wr = new TileEntityWrapper(this);
			pi.stream().map(i -> {
				ItemStack r = i.copy();
				r.setCount(1);
				fakeItems.add(ITEMS.createStack(LogisticsBridge.fakeStack(r, i.getCount())));
				return VirtualPatternAE.create(r, wr);
			}).forEach(craftings::add);
			ci.stream().map(i -> VirtualPatternAE.create(i, wr)).forEach(craftings::add);
			fakeItems.forEach(out::addStorage);
		} catch(Exception e){
			e.printStackTrace();
		}
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
		return input.asItemStackRepresentation().getItem() == LogisticsBridge.logisticsFakeItem;
	}

	@Override
	public boolean canAccept(IAEItemStack input) {
		return input.asItemStackRepresentation().getItem() == LogisticsBridge.logisticsFakeItem;
	}

	@Override
	public int getSlot() {
		return 0;
	}

	@Override
	public boolean validForPass(int i) {
		return i == 1;
	}

	@Override
	public int getSlots() {
		return wrapper.getSlots() + 1;
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
		insertingStacks.clear();
		for(int i = 0;i<table.getSizeInventory();i++){
			if(table.getStackInSlot(i).getItem() != LogisticsBridge.logisticsFakeItem)
				insertingStacks.add(table.getStackInSlot(i));
		}
		OpResult opres = reqapi.performRequest(patternDetails.getOutputs()[0].createItemStack(), true);
		boolean pushed = opres.missing.isEmpty();
		insertingStacks.clear();
		if(pushed){
			lastInjectTime = world.getTotalWorldTime();
			for(int i = 0;i<table.getSizeInventory();i++){
				ItemStack stack = table.getStackInSlot(i);
				if(stack.getItem() != LogisticsBridge.logisticsFakeItem)
					dynInv.insert(stack.copy());
			}
		}else{
			lastPush = opres;
			lastPushTime = System.currentTimeMillis();
		}
		return pushed;
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public void provideCrafting(ICraftingProviderHelper craftingTracker) {
		craftings.forEach(c -> craftingTracker.addCraftingOption(this, c));
		craftingTracker.setEmitable(FAKE_ITEM);
	}

	@Override
	public IAEItemStack[] getInputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
		if(reqapi == null)return def;
		try {
			disableLP = true;
			boolean craftable = reqapi.getCraftedItems().stream().anyMatch(s -> res.isItemEqual(s));
			OpResult r = reqapi.simulateRequest(res, 0b0001, true);
			return Stream.concat(r.missing.stream(), Stream.of(LogisticsBridge.fakeStack(craftable ? null : res, 1))).map(ITEMS::createStack).toArray(IAEItemStack[]::new);
		} finally {
			disableLP = false;
		}
	}

	@Override
	public IAEItemStack[] getOutputs(ItemStack res, IAEItemStack[] def, boolean condensed) {
		if(reqapi == null || !reqapi.isDefaultRoute() || Loader.instance().getLoaderState() == LoaderState.SERVER_STOPPING)return def;
		try {
			disableLP = true;
			OpResult r = reqapi.simulateRequest(res, 0b0110, true);
			List<IAEItemStack> ret = new ArrayList<>();
			IAEItemStack resAE = ITEMS.createStack(res);
			/*if(def != null)
				for (int i = 0; i < def.length; i++) ret.add(def[i].copy());*/
			if(def != null)ret.add(resAE.copy());
			r.extra.forEach(i -> {
				IAEItemStack is = ITEMS.createStack(i);
				boolean added = false;
				for (IAEItemStack e : ret) {
					if(e.equals(is)) {
						e.add(is);
						added = true;
						break;
					}
				}
				if(!added)ret.add(is);
			});
			if(def == null){
				for (IAEItemStack e : ret) {
					if(e.equals(resAE)) {
						res.setCount(((int) e.getStackSize()) + res.getCount());
					}
				}
				return null;
			}
			return ret.toArray(new IAEItemStack[0]);
		} finally {
			disableLP = false;
		}
	}

	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}

	@Override
	public IItemHandler getInternalInventory() {
		return readingFromNBT ? EmptyHandler.INSTANCE : this;
	}

	@Override
	public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
	}

	public String infoString() {
		StringBuilder b = new StringBuilder();
		if(disableLP)b.append("  disableLP flag is stuck\n");
		if(lastPush != null)b.append("  Missing items:\n");
		return b.toString();
	}

	public void infoMessage(EntityPlayer playerIn) {
		String info = infoString();
		if(info.isEmpty())info = "  No problems";
		TextComponentString text = new TextComponentString("AE Bridge\n" + info);
		if(lastPush != null){
			for(ItemStack i : lastPush.missing){
				text.appendText("    ");
				text.appendSibling(i.getTextComponent());
				text.appendText(" * " + i.getCount() + "\n");
			}
			long ago = System.currentTimeMillis() - lastPushTime;
			text.appendText(String.format("  %1$tH %1$tM,%1$tS ago\n", ago));
		}
		if(dynInv.getSizeInventory() > 0){
			text.appendText("\nStored items:\n");
			for (int i = 0; i < dynInv.getSizeInventory(); i++) {
				ItemStack is = dynInv.getStackInSlot(i);
				text.appendText("    ");
				text.appendSibling(is.getTextComponent());
				text.appendText(" * " + is.getCount() + "\n");
			}
		}
		playerIn.sendMessage(text);
	}

	@Override
	public void setReqAPI(Req reqapi) {
		this.reqapi = reqapi;
	}
}
