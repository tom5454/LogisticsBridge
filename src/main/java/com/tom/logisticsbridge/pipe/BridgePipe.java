package com.tom.logisticsbridge.pipe;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.text.TextComponentTranslation;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.tileentity.IBridge;

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.modules.LogisticsModule.ModulePositionType;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeLogisticsChassis.ChassiTargetInformation;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.ItemCraftingTemplate;
import logisticspipes.request.RequestLog;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTree.ActiveRequestType;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.ServerRouter;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import network.rs485.logisticspipes.connection.NeighborTileEntity;
import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

public class BridgePipe extends CoreRoutedPipe implements IProvideItems, IRequestItems, IChangeListener, ICraftItems, IRequireReliableTransport {
	public static TextureType TEXTURE = Textures.empty;
	private BPModule itemSinkModule;
	private IBridge bridge;
	private EnumFacing dir;
	private Req reqapi = new Req();
	public boolean isDefaultRoute;
	//private static Method recurseFailedRequestTree;
	private static Consumer<RequestTreeNode> recurseFailedRequestTree;
	private static Function<RequestTreeNode, List<RequestTreeNode>> subRequests;
	private static Function<RequestTreeNode, List<IExtraPromise>> extrapromises;
	static {
		try {
			Method m = RequestTreeNode.class.getDeclaredMethod("recurseFailedRequestTree");
			m.setAccessible(true);
			Field srf = RequestTreeNode.class.getDeclaredField("subRequests");
			Field ef = RequestTreeNode.class.getDeclaredField("extrapromises");
			Constructor<MethodHandles.Lookup> lookupCons = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
			lookupCons.setAccessible(true);
			MethodHandles.Lookup lookup = lookupCons.newInstance(RequestTreeNode.class);
			MethodType imct = MethodType.methodType(void.class, Object.class);
			MethodHandle mh = lookup.unreflect(m);
			try {
				recurseFailedRequestTree = (Consumer<RequestTreeNode>) LambdaMetafactory.metafactory(lookup, "accept",
						MethodType.methodType(Consumer.class), imct, mh, mh.type()).getTarget().invoke();
			} catch(Exception e){
				e.printStackTrace();
				recurseFailedRequestTree = rtn -> {
					try {
						m.invoke(rtn);
					} catch (Exception e2) {
						e2.printStackTrace();
						return;
					}
				};
			}
			MethodHandle srm = lookup.unreflectGetter(srf);
			MethodHandle em = lookup.unreflectGetter(ef);
			subRequests = t -> {
				try {
					return (List<RequestTreeNode>) srm.invoke(t);
				} catch (Throwable e) {
					e.printStackTrace();
					return Collections.emptyList();
				}
			};
			extrapromises = t -> {
				try {
					return (List<IExtraPromise>) em.invoke(t);
				} catch (Throwable e) {
					e.printStackTrace();
					return Collections.emptyList();
				}
			};
			LogisticsBridge.log.info("Initialized reflection in Bridge Pipe");
		} catch (Throwable e) {
			throw new RuntimeException("Missing methods in LP", e);
		}
	}
	public BridgePipe(Item item) {
		super(item);
		_orderItemManager = new LogisticsItemOrderManager(this, this);
		itemSinkModule = new BPModule();
		itemSinkModule.registerHandler(this, this);
	}

	@Override
	public void setTile(TileEntity tile) {
		super.setTile(tile);
		itemSinkModule.registerPosition(ModulePositionType.IN_PIPE, 0);
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
		return itemSinkModule;
	}

	@Override
	public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
		/*System.out.println("BridgePipe.canProvide()");
		System.out.println(tree);
		System.out.println(root);*/
		if (!isEnabled() || bridge == null || canCraft(tree.getRequestType())) {
			return;
		}
		if (tree.getRequestType() instanceof ItemResource) {
			ItemIdentifier item = ((ItemResource) tree.getRequestType()).getItem();
			for (IFilter filter : filters) {
				if (filter.isBlocked() == filter.isFilteredItem(item.getUndamaged()) || filter.blockProvider()) {
					return;
				}
			}

			// Check the transaction and see if we have helped already
			int canProvide = getAvailableItemCount(item);
			canProvide -= root.getAllPromissesFor(this, item);
			if (canProvide < 1) {
				return;
			}
			LogisticsPromise promise = new LogisticsPromise(item, Math.min(canProvide, tree.getMissingAmount()), this, ResourceType.PROVIDER);
			tree.addPromise(promise);
		} else if (tree.getRequestType() instanceof DictResource) {
			DictResource dict = (DictResource) tree.getRequestType();
			HashMap<ItemIdentifier, Integer> available = new HashMap<>();
			getAllItems(available, filters);
			for (Entry<ItemIdentifier, Integer> item : available.entrySet()) {
				if (!dict.matches(item.getKey(), IResource.MatchSettings.NORMAL)) {
					continue;
				}
				int canProvide = getAvailableItemCount(item.getKey());
				canProvide -= root.getAllPromissesFor(this, item.getKey());
				if (canProvide < 1) {
					continue;
				}
				LogisticsPromise promise = new LogisticsPromise(item.getKey(), Math.min(canProvide, tree.getMissingAmount()), this, ResourceType.PROVIDER);
				tree.addPromise(promise);
				if (tree.getMissingAmount() <= 0) {
					break;
				}
			}
		}
	}

	private int getAvailableItemCount(ItemIdentifier item) {
		if (!isEnabled()) {
			return 0;
		}
		return itemCount(item) - _orderItemManager.totalItemsCountInOrders(item);
	}
	//bridge.craftStack(type.getAsItem().makeNormalStack(1), type.getRequestedAmount(), false);
	@Override
	public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
		if(destination == this || bridge == null)return null;
		/*System.out.println("BridgePipe.fullFill()");
		System.out.println(promise);*/
		spawnParticle(Particles.WhiteParticle, 2);
		long count = bridge.countItem(promise.item.makeNormalStack(1), true);
		if(count < promise.numberOfItems){
			bridge.craftStack(promise.item.makeNormalStack(1), (int) (promise.numberOfItems - count), false);
			return _orderItemManager.addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
		}
		return _orderItemManager.addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.PROVIDER, info);
	}
	@Override
	public void enabledUpdateEntity() {
		super.enabledUpdateEntity();

		if(isNthTick(10)){
			WorldCoordinatesWrapper w = new WorldCoordinatesWrapper(container);
			NeighborTileEntity<TileEntity> ate = null;
			for(EnumFacing f : EnumFacing.HORIZONTALS){
				NeighborTileEntity<TileEntity> n = w.getNeighbor(f);
				if(n != null && n.getTileEntity() instanceof IBridge)ate = n;
			}
			if(ate == null){
				dir = null;
				bridge = null;
			}else{
				dir = ate.getDirection();
				bridge = (IBridge) ate.getTileEntity();
				bridge.setReqAPI(reqapi);
			}
		}

		if (!_orderItemManager.hasOrders(ResourceType.PROVIDER, ResourceType.CRAFTING) || getWorld().getTotalWorldTime() % 6 != 0) {
			return;
		}

		int itemsleft = itemsToExtract();
		int stacksleft = stacksToExtract();
		LogisticsItemOrder firstOrder = null;
		LogisticsItemOrder order = null;
		while (itemsleft > 0 && stacksleft > 0 && _orderItemManager.hasOrders(ResourceType.CRAFTING) && (firstOrder == null || firstOrder != order)) {
			if (firstOrder == null) {
				firstOrder = order;
			}
			order = _orderItemManager.peekAtTopRequest(ResourceType.CRAFTING);
			if(order == null)break;
			int sent = sendStack(order.getResource().stack, itemsleft, order.getRouter().getSimpleID(), order.getInformation(), false);
			spawnParticle(Particles.VioletParticle, 2);
			if (sent < 0) {
				continue;
			}
			stacksleft -= 1;
			itemsleft -= sent;
		}
		while (itemsleft > 0 && stacksleft > 0 && _orderItemManager.hasOrders(ResourceType.PROVIDER) && (firstOrder == null || firstOrder != order)) {
			if (firstOrder == null) {
				firstOrder = order;
			}
			order = _orderItemManager.peekAtTopRequest(ResourceType.PROVIDER);
			if(order == null)break;
			int sent = sendStack(order.getResource().stack, itemsleft, order.getRouter().getSimpleID(), order.getInformation(), true);
			if (sent < 0) {
				break;
			}
			spawnParticle(Particles.VioletParticle, 3);
			stacksleft -= 1;
			itemsleft -= sent;
		}
	}
	protected int neededEnergy() {
		return (int) (10 * Math.pow(1.1, upgradeManager.getItemExtractionUpgrade()) * Math.pow(1.2, upgradeManager.getItemStackExtractionUpgrade()) * 2);
	}

	protected int itemsToExtract() {
		return (int) Math.pow(2,upgradeManager.getItemExtractionUpgrade());
	}

	protected int stacksToExtract() {
		return 1 + upgradeManager.getItemStackExtractionUpgrade();
	}
	private int sendStack(ItemIdentifierStack stack, int maxCount, int destination, IAdditionalTargetInformation info, boolean setFailed) {
		ItemIdentifier item = stack.getItem();

		int available = this.itemCount(item);
		if (available == 0) {
			if(setFailed)_orderItemManager.sendFailed();
			else _orderItemManager.deferSend();
			return 0;
		}

		int wanted = Math.min(available, stack.getStackSize());
		wanted = Math.min(wanted, maxCount);
		wanted = Math.min(wanted, item.getMaxStackSize());
		if (!MainProxy.isServer(getWorld())) {
			_orderItemManager.sendFailed();
			return 0;
		}
		ServerRouter dRtr = SimpleServiceLocator.routerManager.getServerRouter(destination);
		if (dRtr == null) {
			_orderItemManager.sendFailed();
			return 0;
		}
		SinkReply reply = LogisticsManager.canSink(stack.makeNormalStack(), dRtr, null, true, stack.getItem(), null, true, false);
		boolean defersend = false;
		if (reply != null) {// some pipes are not aware of the space in the adjacent inventory, so they return null
			if (reply.maxNumberOfItems < wanted) {
				wanted = reply.maxNumberOfItems;
				if (wanted <= 0) {
					_orderItemManager.deferSend();
					return 0;
				}
				defersend = true;
			}
		}
		if (!canUseEnergy(wanted * neededEnergy())) {
			return -1;
		}
		ItemStack removed = this.getMultipleItems(item, wanted);
		if (removed == null || removed.getCount() == 0) {
			if(setFailed)_orderItemManager.sendFailed();
			else _orderItemManager.deferSend();
			return 0;
		}
		//System.out.println(removed + " " + wanted);
		int sent = removed.getCount();
		useEnergy(sent * neededEnergy());

		IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(removed);
		routedItem.setDestination(destination);
		routedItem.setTransportMode(TransportMode.Active);
		routedItem.setAdditionalTargetInformation(info);
		super.queueRoutedItem(routedItem, dir != null ? dir : EnumFacing.UP);

		_orderItemManager.sendSuccessfull(sent, defersend, routedItem);
		return sent;
	}
	@Override
	public void onAllowedRemoval() {
		while (_orderItemManager.hasOrders(ResourceType.PROVIDER)) {
			_orderItemManager.sendFailed();
		}
	}
	/**
	 * @param items <item, amount>
	 * */
	@Override
	public void getAllItems(Map<ItemIdentifier, Integer> items, List<IFilter> filters) {
		if (!isEnabled()) {
			return;
		}
		//System.out.println("BridgePipe.getAllItems()");
		HashMap<ItemIdentifier, Integer> addedItems = new HashMap<>();
		getItemsAndCount().entrySet().stream().filter(i -> {
			for (IFilter filter : filters) {
				if (filter.isBlocked() == filter.isFilteredItem(i.getKey().getUndamaged()) || filter.blockProvider()) {
					return false;
				}
			}
			return true;
		}).forEach(next -> addedItems.merge(next.getKey(), next.getValue(), (a, b) -> a + b));
		for (Entry<ItemIdentifier, Integer> item : addedItems.entrySet()) {
			int remaining = item.getValue() - _orderItemManager.totalItemsCountInOrders(item.getKey());
			if (remaining < 1) {
				continue;
			}

			items.put(item.getKey(), remaining);
		}
	}

	/*@Override
	public List<ItemStack> getProvidedItems() {
		System.out.println("BridgePipe.getProvidedItems()");
		return null;
	}

	@Override
	public List<ItemStack> getCraftedItems() {
		System.out.println("BridgePipe.getCraftedItems()");
		return null;
	}

	@Override
	public SimulationResult simulateRequest(ItemStack wanted) {
		System.out.println("BridgePipe.simulateRequest()");
		System.out.println(wanted);
		return null;
	}

	@Override
	public List<ItemStack> performRequest(ItemStack wanted) {
		System.out.println("BridgePipe.performRequest()");
		System.out.println(wanted);
		return null;
	}*/
	@Override
	public boolean hasGenericInterests() {
		return true;
	}

	public int itemCount(ItemIdentifier item) {
		if(bridge == null)return 0;
		return (int) bridge.countItem(item.makeNormalStack(1), true);
	}

	public Map<ItemIdentifier, Integer> getItemsAndCount() {
		if(bridge == null){
			return Collections.emptyMap();
		}
		List<ItemStack> prov = reqapi.getProvidedItems();
		return bridge.getItems().stream().filter(st -> !prov.stream().anyMatch(s -> s.isItemEqual(st.obj) && ItemStack.areItemStackTagsEqual(s, st.obj))).
				map(s -> new BridgeStack<>(ItemIdentifier.get(s.obj), s.size + s.requestableSize, s.craftable, 0)).
				collect(Collectors.toMap(s -> s.obj, s -> (int)s.size, (a, b) -> a + b, HashMap::new));
	}

	public ItemStack getMultipleItems(ItemIdentifier item, int count) {
		if(bridge == null)return ItemStack.EMPTY;
		return bridge.extractStack(item.makeNormalStack(1), count, false);
	}

	@Override
	public boolean logisitcsIsPipeConnected(TileEntity tile, EnumFacing dir) {
		return tile instanceof IBridge;
	}

	@Override
	public void registerExtras(IPromise promise) {
		/*System.out.println("BridgePipe.registerExtras()");
		System.out.println(promise);*/
	}

	@Override
	public ICraftingTemplate addCrafting(IResource type) {
		if(bridge == null)return null;
		ItemStack item = type.getAsItem().makeNormalStack(1);
		List<ItemStack> cr = reqapi.getCraftedItems();
		if(!bridge.getItems().stream().anyMatch(b -> b.craftable && item.isItemEqual(b.obj) && !cr.stream().anyMatch(i -> item.isItemEqual(i))))return null;
		/*Map<ItemStack, int[]> res = bridge.craftStack(type.getAsItem().makeNormalStack(1), type.getRequestedAmount(), true);
		if(res == null)return null;*/
		//System.out.println(res);
		return new ItemCraftingTemplate(type.getAsItem().makeStack(type.getRequestedAmount()), this, 0);
	}

	@Override
	public boolean canCraft(IResource toCraft) {
		if(!isEnabled())return false;
		ItemStack item = toCraft.getAsItem().makeNormalStack(1);
		List<ItemStack> cr = reqapi.getCraftedItems();
		return bridge.getItems().stream().anyMatch(b -> b.craftable && item.isItemEqual(b.obj) && !cr.stream().anyMatch(i -> item.isItemEqual(i)));
	}

	@Override public void listenedChanged() {}
	@Override public int getTodo() { return 0; }
	@Override public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {}
	@Override public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

	@Override
	public List<ItemIdentifierStack> getCraftedItems() {
		if(bridge == null){
			return Collections.emptyList();
		}
		return bridge.getItems().stream().filter(b -> b.craftable).
				map(s -> new ItemIdentifierStack(ItemIdentifier.get(s.obj), 1)).
				collect(Collectors.toList());
	}

	public class Req {
		private boolean alreadyProcessing;
		private Set<ItemIdentifier> craftedStacks = new HashSet<>();
		public List<ItemStack> getProvidedItems() {
			if(alreadyProcessing || !isEnabled())return Collections.emptyList();
			if (stillNeedReplace()) {
				return new ArrayList<>();
			}
			try {
				alreadyProcessing = true;
				IRouter myRouter = getRouter();
				List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
				exits.removeIf(e -> e.destination == myRouter);
				Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
				List<ItemStack> list = new ArrayList<>(items.size());
				for (Entry<ItemIdentifier, Integer> item : items.entrySet()) {
					ItemStack is = item.getKey().unsafeMakeNormalStack(item.getValue());
					list.add(is);
				}
				return list;
			} finally {
				alreadyProcessing = false;
			}
		}

		public List<ItemStack> getCraftedItems() {
			if(alreadyProcessing || !isEnabled())return Collections.emptyList();
			if (stillNeedReplace()) {
				return new ArrayList<>();
			}
			try {
				alreadyProcessing = true;
				IRouter myRouter = getRouter();
				List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
				exits.removeIf(e -> e.destination == myRouter);
				LinkedList<ItemIdentifier> items = SimpleServiceLocator.logisticsManager.getCraftableItems(exits);
				List<ItemStack> list = new ArrayList<>(items.size());
				for (ItemIdentifier item : items) {
					ItemStack is = item.unsafeMakeNormalStack(1);
					list.add(is);
				}
				return list;
			} finally {
				alreadyProcessing = false;
			}
		}
		/**
		 * @param craft bits: processExtra,onlyCraft,enableCraft
		 * */
		public OpResult simulateRequest(ItemStack wanted, int craft, boolean allowPartial) {
			if(!isEnabled() || alreadyProcessing){
				OpResult r = new OpResult();
				r.used = Collections.emptyList();
				r.missing = Collections.singletonList(wanted);
				return r;
			}
			alreadyProcessing = true;
			try {
				IRouter myRouter = getRouter();
				List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
				exits.removeIf(e -> e.destination == myRouter);
				Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
				ItemIdentifier req = ItemIdentifier.get(wanted);
				int count = items.getOrDefault(req, 0);
				if(count == 0 && craft == 0){
					OpResult r = new OpResult();
					r.used = Collections.emptyList();
					r.missing = Collections.singletonList(wanted);
					return r;
				}
				ListLog ll = new ListLog();
				/*System.out.println("BridgePipe.Req.simulateRequest()");
			System.out.println(wanted);*/
				ItemIdentifierStack item = ItemIdentifier.get(wanted).makeStack(craft != 0 ? wanted.getCount() : Math.min(count, wanted.getCount()));
				ItemResource reqRes = new ItemResource(item, BridgePipe.this);
				RequestTree tree = new RequestTree(reqRes, null, (craft & 0b0010) != 0 ? EnumSet.of(ActiveRequestType.Craft) : RequestTree.defaultRequestFlags, null);
				if (!tree.isDone()) {
					recurseFailedRequestTree.accept(tree);
				}
				tree.sendUsedMessage(ll);
				OpResult res = ll.asResult();
				if((craft & 0b0100) != 0 && isDefaultRoute){
					List<IExtraPromise> extrasPromises = new ArrayList<>();
					listExras(tree, extrasPromises);
					res.extra = extrasPromises.stream().map(e -> e.getItemType().makeNormalStack(e.getAmount())).collect(Collectors.toList());
				}
				if(craft == 0 && !allowPartial && wanted.getCount() > count){
					int missingCount = wanted.getCount() - count;
					ItemStack missingStack = wanted.copy();
					missingStack.setCount(missingCount);
					res.missing.add(missingStack);
				}

				return res;
			} finally {
				alreadyProcessing = false;
			}
		}

		public OpResult performRequest(ItemStack wanted, boolean craft) {
			if(!isEnabled())return new OpResult(Collections.singletonList(wanted));
			ListLog ll = new ListLog();
			IRouter myRouter = getRouter();
			List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
			exits.removeIf(e -> e.destination == myRouter);
			Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
			ItemIdentifier req = ItemIdentifier.get(wanted);
			int count = items.getOrDefault(req, 0);
			if(count == 0 && !craft){
				return new OpResult(Collections.singletonList(wanted));
			}
			int reqCount = craft ? wanted.getCount() : Math.min(count, wanted.getCount());
			boolean success = request(ItemIdentifier.get(wanted).makeStack(reqCount), BridgePipe.this, ll, null);
			OpResult res = ll.asResult();
			res.success = success;
			if(!craft && wanted.getCount() > count){
				int missingCount = wanted.getCount() - count;
				ItemStack missingStack = wanted.copy();
				missingStack.setCount(missingCount);
				res.missing.add(missingStack);
			}

			return res;
		}

		public boolean isDefaultRoute() {
			return isDefaultRoute;
		}

		public boolean detectChanged() {
			if(alreadyProcessing || !isEnabled())return false;
			if (stillNeedReplace()) {
				return false;
			}
			IRouter myRouter = getRouter();
			List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
			exits.removeIf(e -> e.destination == myRouter);
			LinkedList<ItemIdentifier> items = SimpleServiceLocator.logisticsManager.getCraftableItems(exits);
			if(items.size() != craftedStacks.size()) {
				craftedStacks.clear();
				craftedStacks.addAll(items);
				return true;
			}
			boolean c = craftedStacks.containsAll(items);
			if(!c) {
				craftedStacks.clear();
				craftedStacks.addAll(items);
				return true;
			}
			return false;
		}
	}
	public static class OpResult {
		public List<ItemStack> used;
		public List<ItemStack> missing;
		public List<ItemStack> extra = new ArrayList<>();
		public boolean success;
		public OpResult() {
		}
		public OpResult(List<ItemStack> missing) {
			this.missing = missing;
			used = Collections.emptyList();
		}
		public OpResult(List<ItemStack> used, List<ItemStack> missing) {
			this.used = used;
			this.missing = missing;
		}
	}
	public static boolean request(ItemIdentifierStack item, IRequestItems requester, RequestLog log, IAdditionalTargetInformation info) {
		return RequestTree.request(item, requester, log, false, false, true, true, RequestTree.defaultRequestFlags, info) == item.getStackSize();
	}

	/*public static int simulate(ItemIdentifierStack item, IRequestItems requester, RequestLog log) {
		//return RequestTree.request(item, requester, log, true, true, false, true, RequestTree.defaultRequestFlags, null);

		ItemResource req = new ItemResource(item, requester);
		RequestTree tree = new RequestTree(req, null, RequestTree.defaultRequestFlags, null);
		if (log != null) {
			if (!tree.isDone()) {
				recurseFailedRequestTree.accept(tree);
			}
			tree.sendUsedMessage(log);
		}
		return tree.getPromiseAmount();
	}*/
	public static void listExras(RequestTreeNode node, List<IExtraPromise> list){
		list.addAll(extrapromises.apply(node));
		subRequests.apply(node).forEach(n -> listExras(n, list));
	}
	public static class ListLog implements RequestLog {
		private final List<IResource> missing = new ArrayList<>();
		private final List<IResource> used = new ArrayList<>();
		@Override
		public void handleMissingItems(List<IResource> resources) {
			missing.addAll(resources);
		}

		@Override
		public void handleSucessfullRequestOf(IResource item, LinkedLogisticsOrderList paticipating) {
		}

		@Override
		public void handleSucessfullRequestOfList(List<IResource> resources, LinkedLogisticsOrderList paticipating) {
			used.addAll(resources);
		}
		public List<ItemStack> getMissingItems(){
			return toList(missing);
		}
		public List<ItemStack> getUsedItems(){
			return toList(used);
		}
		public OpResult asResult(){
			return new OpResult(getUsedItems(), getMissingItems());
		}
		private static List<ItemStack> toList(List<IResource> resList){
			List<ItemStack> list = new ArrayList<>(resList.size());
			for (IResource e : resList) {
				if (e instanceof ItemResource) {
					list.add(((ItemResource) e).getItem().unsafeMakeNormalStack(e.getRequestedAmount()));
				} else if (e instanceof DictResource) {
					list.add(((DictResource) e).getItem().unsafeMakeNormalStack(e.getRequestedAmount()));
				}
			}
			return list;
		}
	}
	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		nbttagcompound.setBoolean("isDefaultRoute", isDefaultRoute);
	}
	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		isDefaultRoute = nbttagcompound.getBoolean("isDefaultRoute");
	}
	public class BPModule extends LogisticsModule {
		private SinkReply _sinkReply;
		private SinkReply _sinkReplyDefault;

		@Override
		public void registerPosition(ModulePositionType slot, int positionInt) {
			super.registerPosition(slot, positionInt);
			_sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, new ChassiTargetInformation(getPositionInt()));
			_sinkReplyDefault = new SinkReply(FixedPriority.DefaultRoute, 0, true, true, 1, 0, new ChassiTargetInformation(getPositionInt()));
		}

		@Override
		public SinkReply sinksItem(ItemStack stack, ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit,
				boolean forcePassive) {
			if (isDefaultRoute && !allowDefault) {
				return null;
			}
			if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal() && bestCustomPriority >= _sinkReply.customPriority)) {
				return null;
			}
			if (isDefaultRoute) {
				if (bestPriority > _sinkReplyDefault.fixedPriority.ordinal() || (bestPriority == _sinkReplyDefault.fixedPriority.ordinal() && bestCustomPriority >= _sinkReplyDefault.customPriority)) {
					return null;
				}
				if (_service.canUseEnergy(1)) {
					return _sinkReplyDefault;
				}
				return null;
			}
			return null;
		}

		@Override
		public boolean hasGenericInterests() {
			return isDefaultRoute;
		}

		@Override public void tick() {}
		@Override public void readFromNBT(NBTTagCompound nbttagcompound) {}
		@Override public void writeToNBT(NBTTagCompound nbttagcompound) {}
		@Override public boolean interestedInAttachedInventory() { return false; }
		@Override public boolean interestedInUndamagedID() { return false; }
		@Override public boolean recievePassive() { return true; }

	}
	@Override
	public void onWrenchClicked(EntityPlayer entityplayer) {
		if(!getWorld().isRemote) {
			isDefaultRoute = !isDefaultRoute;
			entityplayer.sendMessage(new TextComponentTranslation("chat.logisticsbridge.bridgeDefaultRoute",
					new TextComponentTranslation("gui.itemsink." + (isDefaultRoute ? "Yes" : "No"))));
		}
	}

	public boolean disconnectPipe(TileEntity tile, EnumFacing dir) {
		return dir.getAxis() == Axis.Y && tile instanceof IBridge ? true : false;
	}
}
