package com.tom.logisticsbridge.tileentity;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import com.raoulvdberge.refinedstorage.api.storage.AccessType;
import com.raoulvdberge.refinedstorage.api.storage.IStorage;
import com.raoulvdberge.refinedstorage.api.storage.IStorageProvider;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IStackList;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.network.node.NetworkNode;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.pipe.BridgePipe.Req;

public class NetworkNodeBridge extends NetworkNode implements IStorageProvider, IStorage<ItemStack>, IBridge, IItemHandler {
	public static final String ID = "lb.bridge";
	private Req reqapi;
	private IStackList<ItemStack> list = API.instance().createItemStackList();

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
		//storages.add(this);
	}

	@Override public void addFluidStorages(List<IStorage<FluidStack>> storages) {}

	@Override
	public Collection<ItemStack> getStacks() {
		//return list.getStacks();
		return Collections.emptyList();
	}

	@Override
	public ItemStack insert(ItemStack stack, int size, Action action) {
		if(stack.getItem() == LogisticsBridge.logisticsFakeItem)return null;
		return stack;
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

	}

	@Override
	public List<BridgeStack<ItemStack>> getItems() {
		return network.getItemStorageCache().getList().getStacks().stream().map(s -> new BridgeStack<>(s, s.getCount(), false, 0)).collect(Collectors.toList());
	}

	@Override
	public ItemStack extractStack(ItemStack stack, int count, boolean simulate) {
		return network.extractItem(stack, count, simulate ? Action.SIMULATE : Action.PERFORM);
	}

	@Override
	public void setReqAPI(Req reqapi) {
		this.reqapi = reqapi;
	}
	@Override
	public void update() {
		super.update();
		/*if(reqapi != null && world.getTotalWorldTime() % 20 == 0){
			List<ItemStack> old = new ArrayList<>(list.getStacks());
			List<ItemStack> pi = reqapi.getProvidedItems();
			list.clear();
			//List<ItemStack> ci = reqapi.getCraftedItems();
			/*pi.stream().map(ItemStack::copy).map(s -> {
				s.setCraftable(true);
				s.setCountRequestable(s.getStackSize());
				s.setStackSize(0);
				return s;
			}).forEach(out::add);*/
		//ci.stream().map(ITEMS::createStack).forEach(out::addCrafting);
		//TileEntityWrapper wr = new TileEntityWrapper(this);
		/*pi.stream().forEach(i -> {
				ItemStack r = i.copy();
				r.setCount(1);
				list.add(LogisticsBridge.fakeStack(r, i.getCount()));
				//return VirtualPattern.create(r, wr);
			});//.forEach(craftings::add);
			//ci.stream().map(i -> VirtualPattern.create(i, wr)).forEach(craftings::add);
			network.getItemStorageCache().invalidate();
		}*/
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
		ItemStack is = network.insertItem(stack, stack.getCount(), simulate ? Action.SIMULATE : Action.PERFORM);
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
}
