package com.tom.logisticsbridge.module;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.entity.player.EntityPlayer;

import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.pipe.ResultPipe;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.DictCraftingTemplate;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.IReqCraftingTemplate;
import logisticspipes.request.ItemCraftingTemplate;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsExtraDictPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class ModuleCrafterExt extends ModuleCrafter {

	@Override
	public ICraftingTemplate addCrafting(IResource toCraft) {
		if(!(_service instanceof CraftingManager))return null;
		CraftingManager mngr = (CraftingManager) _service;
		List<ItemIdentifierStack> stack = getCraftedItems();
		if (stack == null) {
			return null;
		}
		IReqCraftingTemplate template = null;
		if(this.getUpgradeManager().isFuzzyUpgrade() && outputFuzzyFlags.getBitSet().nextSetBit(0) != -1) {
			if(toCraft instanceof DictResource) {
				for (ItemIdentifierStack craftable : stack) {
					DictResource dict = new DictResource(craftable, null);
					dict.loadFromBitSet(outputFuzzyFlags.getBitSet());
					if (toCraft.matches(craftable.getItem(), IResource.MatchSettings.NORMAL) && dict.matches(((DictResource) toCraft).getItem(), IResource.MatchSettings.NORMAL) && dict.getBitSet().equals(((DictResource) toCraft).getBitSet())) {
						template = new DictCraftingTemplate(dict, this, priority);
						break;
					}
				}
			}
		} else {
			for (ItemIdentifierStack craftable : stack) {
				if (toCraft.matches(craftable.getItem(), IResource.MatchSettings.NORMAL)) {
					template = new ItemCraftingTemplate(craftable, this, priority);
					break;
				}
			}
		}
		if (template == null) {
			return null;
		}

		boolean buffered = mngr.isBuffered();

		IRouter defSat = getSatelliteRouterByID(mngr.getSatelliteUUID());
		if(defSat == null)return null;

		IRequestItems[] target = new IRequestItems[9];
		if(buffered){
			for (int i = 0; i < 9; i++) {
				target[i] = this;
			}
		}else{
			for (int i = 0; i < 9; i++) {
				target[i] = defSat.getPipe();
			}

			boolean hasSatellite = isSatelliteConnected();
			if (!hasSatellite) {
				return null;
			}
			if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
				IRouter r = getSatelliteRouter(-1);
				if (r != null) {
					IRequestItems sat = r.getPipe();
					for (int i = 6; i < 9; i++) {
						target[i] = sat;
					}
				}
			} else {
				for (int i = 0; i < 9; i++) {
					IRouter r = getSatelliteRouter(i);
					if (r != null) {
						target[i] = r.getPipe();
					}
				}
			}
		}

		//Check all materials
		for (int i = 0; i < 9; i++) {
			ItemIdentifierStack resourceStack = getMaterials(i);
			if (resourceStack == null || resourceStack.getStackSize() == 0) {
				continue;
			}
			IResource req = null;
			if (getUpgradeManager().isFuzzyUpgrade() && fuzzyCraftingFlagArray[i].getBitSet().nextSetBit(0) != -1) {
				DictResource dict;
				req = dict = new DictResource(resourceStack, target[i]);
				dict.loadFromBitSet(fuzzyCraftingFlagArray[i].getBitSet());
			} else {
				req = new ItemResource(resourceStack, target[i]);
			}
			template.addRequirement(req, new BufferInformation(i, getPositionInt()));
		}

		int liquidCrafter = getUpgradeManager().getFluidCrafter();
		IRequestFluid[] liquidTarget = new IRequestFluid[liquidCrafter];

		if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
			IRouter r = getFluidSatelliteRouter(-1);
			if (r != null) {
				IRequestFluid sat = (IRequestFluid) r.getPipe();
				for (int i = 0; i < liquidCrafter; i++) {
					liquidTarget[i] = sat;
				}
			}
		} else {
			for (int i = 0; i < liquidCrafter; i++) {
				IRouter r = getFluidSatelliteRouter(i);
				if (r != null) {
					liquidTarget[i] = (IRequestFluid) r.getPipe();
				}
			}
		}

		for (int i = 0; i < liquidCrafter; i++) {
			FluidIdentifier liquid = getFluidMaterial(i);
			int amount = getFluidAmount()[i];
			if (liquid == null || amount <= 0 || liquidTarget[i] == null) {
				continue;
			}
			template.addRequirement(new FluidResource(liquid, amount, liquidTarget[i]), null);
		}

		if (getUpgradeManager().hasByproductExtractor() && getByproductItem() != null) {
			template.addByproduct(getByproductItem());
		}

		return template;
	}

	private IRouter getSatelliteRouter(int x) {
		if (x == -1) {
			int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(satelliteUUID);
			return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
		} else {
			int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(advancedSatelliteUUIDArray[x]);
			return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
		}
	}
	private IRouter getFluidSatelliteRouter(int x) {
		if (x == -1) {
			int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(liquidSatelliteUUID);
			return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
		} else {
			int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(liquidSatelliteUUIDArray[x]);
			return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
		}
	}

	public IRouter getSatelliteRouterByID(UUID id) {
		if(id == null)return null;
		int satelliteRouterId = SimpleServiceLocator.routerManager.getIDforUUID(id);
		return SimpleServiceLocator.routerManager.getRouter(satelliteRouterId);
	}
	public IRouter getResultRouterByID(UUID id) {
		if(id == null)return null;
		int resultRouterId = SimpleServiceLocator.routerManager.getIDforUUID(id);
		return SimpleServiceLocator.routerManager.getRouter(resultRouterId);
	}
	@Override
	public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
		if (!_service.getItemOrderManager().hasExtras() || tree.hasBeenQueried(_service.getItemOrderManager())) {
			return;
		}
		if(!(_service instanceof CraftingManager))return;
		CraftingManager mngr = (CraftingManager) _service;
		IRouter resultR = getResultRouterByID(mngr.getResultUUID());
		if(resultR == null)return;
		CoreRoutedPipe coreRoutedPipe = resultR.getPipe();
		if(!(coreRoutedPipe instanceof ResultPipe))return;
		ResultPipe res = (ResultPipe) coreRoutedPipe;

		IResource requestedItem = tree.getRequestType();

		if (!canCraft(requestedItem)) {
			return;
		}

		for (IFilter filter : filters) {
			if (filter.isBlocked() == filter.isFilteredItem(requestedItem) || filter.blockProvider()) {
				return;
			}
		}
		int remaining = 0;
		for (LogisticsItemOrder extra : _service.getItemOrderManager()) {
			if (extra.getType() == ResourceType.EXTRA) {
				if (extra.getResource().getItem().equals(requestedItem.getAsItem())) {
					remaining += extra.getResource().stack.getStackSize();
				}
			}
		}
		remaining -= root.getAllPromissesFor(res, getCraftedItem().getItem());
		if (remaining < 1) {
			return;
		}
		if(this.getUpgradeManager().isFuzzyUpgrade() && outputFuzzyFlags.getBitSet().nextSetBit(0) != -1) {
			DictResource dict = new DictResource(getCraftedItem(), null).loadFromBitSet(outputFuzzyFlags.getBitSet());
			LogisticsExtraDictPromise promise = new LogisticsExtraDictPromise(dict, Math.min(remaining, tree.getMissingAmount()), res, true);
			tree.addPromise(promise);
		} else {
			LogisticsExtraPromise promise = new LogisticsExtraPromise(getCraftedItem().getItem(), Math.min(remaining, tree.getMissingAmount()), res, true);
			tree.addPromise(promise);
		}
		tree.setQueried(_service.getItemOrderManager());
	}

	@Override
	public LogisticsItemOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
		if(!(_service instanceof CraftingManager))return null;
		CraftingManager mngr = (CraftingManager) _service;
		ItemIdentifierStack result = getCraftedItem();
		if(result == null)return null;
		int multiply = (int) Math.ceil(promise.numberOfItems / (float) result.getStackSize());
		if(mngr.isBuffered()){
			List<Pair<IRequestItems, ItemIdentifierStack>> rec = new ArrayList<>();
			IRouter defSat = getSatelliteRouterByID(mngr.getSatelliteUUID());
			if(defSat == null)return null;
			IRequestItems[] target = new IRequestItems[9];
			for (int i = 0; i < 9; i++) {
				target[i] = defSat.getPipe();
			}

			boolean hasSatellite = isSatelliteConnected();
			if (!hasSatellite) {
				return null;
			}
			if (!getUpgradeManager().isAdvancedSatelliteCrafter()) {
				IRouter r = getSatelliteRouter(-1);
				if (r != null) {
					IRequestItems sat = r.getPipe();
					for (int i = 6; i < 9; i++) {
						target[i] = sat;
					}
				}
			} else {
				for (int i = 0; i < 9; i++) {
					IRouter r = getSatelliteRouter(i);
					if (r != null) {
						target[i] = r.getPipe();
					}
				}
			}

			for (int i = 0; i < target.length; i++) {
				ItemIdentifierStack mat = getMaterials(i);
				if(mat != null)rec.add(Pair.of(target[i], mat));
			}

			for(int i = 0;i<multiply;i++)
				mngr.addBuffered(rec);
		}
		IRouter resultR = getResultRouterByID(mngr.getResultUUID());
		if(resultR == null)return null;
		CoreRoutedPipe coreRoutedPipe = resultR.getPipe();
		if(!(coreRoutedPipe instanceof ResultPipe))return null;
		ResultPipe res = (ResultPipe) coreRoutedPipe;
		return res.fullFill(promise, destination, info);
	}
	@Override
	public void openAttachedGui(EntityPlayer player) {
	}
	@Override
	public void registerExtras(IPromise promise) {
		if(!(_service instanceof CraftingManager))return;
		CraftingManager mngr = (CraftingManager) _service;
		IRouter resultR = getResultRouterByID(mngr.getResultUUID());
		if(resultR == null)return;
		CoreRoutedPipe coreRoutedPipe = resultR.getPipe();
		if(!(coreRoutedPipe instanceof ResultPipe))return;
		ResultPipe res = (ResultPipe) coreRoutedPipe;
		res.registerExtras(promise);
	}
	@Override
	public void enabledUpdateEntity() {
		super.enabledUpdateEntity();
		if (!_service.isNthTick(6)) {
			return;
		}

		if(!(_service instanceof CraftingManager))return;
		CraftingManager mngr = (CraftingManager) _service;

		if ((!_service.getItemOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA))) {
			if (getUpgradeManager().getCrafterCleanup() > 0) {
				IRouter resultR = getResultRouterByID(mngr.getResultUUID());
				if(resultR == null)return;
				CoreRoutedPipe coreRoutedPipe = resultR.getPipe();
				if(!(coreRoutedPipe instanceof ResultPipe))return;
				ResultPipe res = (ResultPipe) coreRoutedPipe;
				res.extractCleanup(_cleanupInventory, cleanupModeIsExclude, getUpgradeManager().getCrafterCleanup() * 3);
			}
			return;
		}
	}

	@Override
	public void guiClosedByPlayer(EntityPlayer player) {
		super.guiClosedByPlayer(player);
		if(MainProxy.isClient(_world.getWorld()))return;
		if(!(_service instanceof CraftingManager))return;
		CraftingManager mngr = (CraftingManager) _service;
		ChassiModule m = mngr.getModules();
		for(int i = 0;i<27;i++) {
			if(m.getModule(i) == this) {
				mngr.save(i);
				break;
			}
		}
	}

	public static class BufferInformation extends CraftingChassieInformation {

		public BufferInformation(int craftingSlot, int moduleSlot) {
			super(craftingSlot, moduleSlot);
		}

	}
}
