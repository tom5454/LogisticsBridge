package com.tom.logisticsbridge.tileentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPatternContainer;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTask;
import com.raoulvdberge.refinedstorage.api.autocrafting.task.ICraftingTaskError;
import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.api.IDynamicPatternDetailsRS;
import com.tom.logisticsbridge.item.VirtualPatternRS;
import com.tom.logisticsbridge.pipe.BridgePipe.OpResult;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;
import com.tom.logisticsbridge.util.DynamicInventory;

public class NetworkNodeBridge extends NetworkNode implements IStorageProvider, IStorage<ItemStack>, IBridge, IItemHandler, IDynamicPatternDetailsRS, ICraftingPatternContainer {
	public static final String ID = "lb.bridge";
	private static final String NBT_UUID = "BridgeUuid";
	private static final String NAME = "tile.lb.bridge.rs.name";
	private long lastInjectTime;
	private Req reqapi;
	private IStackList<ItemStack> list = API.instance().createItemStackList();
	private boolean firstTick = true;
	private DynamicInventory patterns = new DynamicInventory();
	private DynamicInventory craftingItems = new DynamicInventory();
	private InvWrapper craftingItemsWrapper = new InvWrapper(craftingItems) {
		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			lastInjectTime = world.getTotalWorldTime();
			if(stack.getItem() == LogisticsBridge.logisticsFakeItem){
				if(pushPattern(stack, simulate))
					return ItemStack.EMPTY;
				else return stack;
			}
			return super.insertItem(slot, stack, simulate);
		}

		@Override
		public int getSlots() {
			return super.getSlots() + 1;
		}
	};
	private List<ICraftingPattern> craftingPatterns = new ArrayList<>();
	private Deque<ItemStack> requestList = new ArrayDeque<>();
	@Nullable
	private UUID uuid = null;
	private boolean disableLP;

	public NetworkNodeBridge(World world, BlockPos pos) {
		super(world, pos);
	}

	@Override
	public int getEnergyUsage() {
		return 2;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public void addItemStorages(List<IStorage<ItemStack>> storages) {
		storages.add(this);
	}

	@Override public void addFluidStorages(List<IStorage<FluidStack>> storages) {}

	@Override
	public Collection<ItemStack> getStacks() {
		return list.getStacks();
	}

	@Override
	public ItemStack insert(ItemStack stack, int size, Action action) {
		if(stack.getItem() == LogisticsBridge.logisticsFakeItem)return null;
		ItemStack ret = stack.copy();
		ret.setCount(size);
		return ret;
	}

	@Override
	public ItemStack extract(ItemStack stack, int size, int flags, Action action) {
		if(stack.getItem() == LogisticsBridge.logisticsFakeItem) {
			ItemStack st = list.get(stack, flags);
			int min = Math.min(size, st.getCount());
			ItemStack ret = st.copy();
			st.setCount(min);
			if(action == Action.PERFORM){
				list.remove(st, min);
			}
			return ret;
		}
		return null;
	}

	@Override
	public int getStored() {
		return 0;
	}

	@Override
	public int getPriority() {
		return Integer.MAX_VALUE;
	}

	@Override
	public AccessType getAccessType() {
		return AccessType.INSERT_EXTRACT;
	}

	@Override
	public int getCacheDelta(int storedPreInsertion, int size, ItemStack remainder) {
		return 0;
	}

	@Override
	public long countItem(ItemStack stack, boolean requestable) {
		ItemStack is = network.getItemStorageCache().getList().get(stack);
		return is == null ? 0 : is.getCount();
	}

	@Override
	public void craftStack(ItemStack stack, int count, boolean simulate) {
		ICraftingTask task = network.getCraftingManager().create(stack, count);
		if (task == null) {
			return;
		}

		ICraftingTaskError error = task.calculate();
		if (error == null && !task.hasMissing()) {
			network.getCraftingManager().add(task);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<BridgeStack<ItemStack>> getItems() {
		if(disableLP)return Collections.emptyList();
		return LogisticsBridge.concatStreams(
				network.getItemStorageCache().getList().getStacks().stream().
				filter(e -> e != null && e.getItem() != LogisticsBridge.logisticsFakeItem).
				map(s -> new BridgeStack<>(s, s.getCount(), false, 0)),

				network.getCraftingManager().getPatterns().stream().map(ICraftingPattern::getOutputs).flatMap(NonNullList::stream).
				filter(s -> s != null && !s.isEmpty() && s.getItem() != LogisticsBridge.logisticsFakeItem).
				map(s -> new BridgeStack<>(s, 0, true, 0)),

				craftingItems.stream().
				map(s -> new BridgeStack<>(s, s.getCount(), false, 0))
				).collect(Collectors.toList());
	}

	@Override
	public ItemStack extractStack(ItemStack stack, int count, boolean simulate) {
		ItemStack ex = network.extractItem(stack, count, simulate ? Action.SIMULATE : Action.PERFORM);
		//System.out.println(ex + " " + count);
		return ex;
	}

	@Override
	public void setReqAPI(Req reqapi) {
		this.reqapi = reqapi;
	}
	@Override
	public void update() {
		super.update();
		if(!world.isRemote){
			long wt = world.getTotalWorldTime();
			if(reqapi != null && wt % 20 == 0){
				List<ItemStack> stack = new ArrayList<>(list.getStacks());
				List<ItemStack> pi = reqapi.getProvidedItems();
				list.clear();
				List<ItemStack> crafts = patterns.stream().collect(Collectors.toList());
				patterns.clear();
				craftingPatterns.clear();
				List<ItemStack> ci = reqapi.getCraftedItems();
				TileEntityWrapper wr = new TileEntityWrapper(world, pos);
				pi.stream().forEach(i -> {
					ItemStack r = i.copy();
					r.setCount(1);
					list.add(LogisticsBridge.fakeStack(r, i.getCount()));
					ICraftingPattern pattern = VirtualPatternRS.create(r, wr, this);
					patterns.addStack(pattern.getStack());
					craftingPatterns.add(pattern);
				});
				list.add(new ItemStack(LogisticsBridge.logisticsFakeItem, 65536));
				ci.stream().map(i -> VirtualPatternRS.create(i, wr, this)).forEach(pattern -> {
					patterns.addStack(pattern.getStack());
					craftingPatterns.add(pattern);
					pattern = VirtualPatternRS.create(new ItemStack(LogisticsBridge.logisticsFakeItem),
							LogisticsBridge.fakeStack(pattern.getOutputs().get(0), 1), this);
					patterns.addStack(pattern.getStack());
					craftingPatterns.add(pattern);
				});
				if(firstTick || stack.size() != list.getStacks().size() || crafts.size() != patterns.getSizeInventory() ||
						stack.stream().anyMatch(s -> {
							ItemStack st = list.get(s);
							return st == null || st.isEmpty() || st.getCount() != s.getCount();
						}) || patterns.stream().anyMatch(p -> {
							return !crafts.stream().anyMatch(i -> ItemStack.areItemStackTagsEqual(i, p));
						})){
					network.getItemStorageCache().invalidate();
					network.getCraftingManager().rebuild();
				}
				firstTick = false;
			}
			if(lastInjectTime + 200 < wt && !craftingItems.isEmpty()){
				lastInjectTime = wt - 20;
				for(int i = 0;i<craftingItems.getSizeInventory();i++){
					craftingItems.setInventorySlotContents(i, insertItem(0, craftingItems.getStackInSlot(i), false));
				}
				craftingItems.removeEmpties();
			}
			if(wt % 5 == 0 && !requestList.isEmpty()) {
				ItemStack toReq = requestList.pop();
				OpResult opres = reqapi.performRequest(toReq, true);
				boolean pushed = opres.missing.isEmpty();
				if(!pushed){
					requestList.add(toReq);
				}
			}
		}
	}

	@Override
	public int getSlots() {
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		ItemStack is = simulate ? network.insertItem(stack, stack.getCount(), Action.SIMULATE) : network.insertItemTracked(stack, stack.getCount());
		return is == null ? ItemStack.EMPTY : is;
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return ItemStack.EMPTY;
	}

	@Override
	public int getSlotLimit(int slot) {
		return 64;
	}

	@Override
	public IItemHandler getConnectedInventory() {
		return craftingItemsWrapper;
	}

	@Override public IFluidHandler getConnectedFluidInventory() {return null;}
	@Override public TileEntity getConnectedTile() {return null;}

	@Override
	public List<ICraftingPattern> getPatterns() {
		return craftingPatterns;
	}

	@Override
	public IItemHandlerModifiable getPatternInventory() {
		return null;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public BlockPos getPosition() {
		return pos;
	}

	@Override
	public ICraftingPatternContainer getRootContainer() {
		return this;
	}

	@Override
	public UUID getUuid() {
		if (this.uuid == null) {
			this.uuid = UUID.randomUUID();

			markDirty();
		}

		return uuid;
	}
	@Override
	public void read(NBTTagCompound tag) {
		super.read(tag);

		if (tag.hasUniqueId(NBT_UUID)) {
			uuid = tag.getUniqueId(NBT_UUID);
		}

		NBTTagList list = tag.getTagList("reqList", 10);
		requestList.clear();
		for (int i = 0; i < list.tagCount(); i++) {
			requestList.add(new ItemStack(list.getCompoundTagAt(i)));
		}

		LogisticsBridge.loadAllItems(tag.getTagList("intInventory", 10), craftingItems);
	}
	@Override
	public NBTTagCompound write(NBTTagCompound tag) {
		super.write(tag);

		if (uuid != null) {
			tag.setUniqueId(NBT_UUID, uuid);
		}

		NBTTagList list = new NBTTagList();
		requestList.stream().map(s -> s.writeToNBT(new NBTTagCompound())).forEach(list::appendTag);
		tag.setTag("reqList", list);

		craftingItems.removeEmpties();
		tag.setTag("intInventory", LogisticsBridge.saveAllItems(craftingItems));

		return tag;
	}

	@Override
	public NonNullList<ItemStack> getInputs(ItemStack res, NonNullList<ItemStack> def) {
		if(reqapi == null)return def;
		try {
			disableLP = true;
			OpResult r = reqapi.simulateRequest(res, true, true);
			NonNullList<ItemStack> ret = NonNullList.create();
			ret.addAll(r.missing);
			ret.add(LogisticsBridge.fakeStack(res, 1));
			return ret;
		} finally {
			disableLP = false;
		}
	}

	public void infoMessage(EntityPlayer playerIn) {
		firstTick = true;
	}

	public boolean pushPattern(ItemStack stack, boolean simulate) {
		if(reqapi == null || !stack.hasTagCompound())return false;
		ItemStack res = new ItemStack(stack.getTagCompound());
		if(res.isEmpty())return false;
		requestList.add(res);
		return true;
	}
}
