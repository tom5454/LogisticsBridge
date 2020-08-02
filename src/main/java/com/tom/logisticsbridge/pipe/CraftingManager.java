package com.tom.logisticsbridge.pipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.client.FMLClientHandler;

import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.module.BufferUpgrade;
import com.tom.logisticsbridge.module.ModuleCrafterExt;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.LPItems;
import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.modules.LogisticsModule.ModulePositionType;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.pipe.ChassiGuiProvider;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.upgrades.UpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import network.rs485.logisticspipes.connection.NeighborTileEntity;

public class CraftingManager extends PipeLogisticsChassi implements IIdPipe {
	private List<List<Pair<IRequestItems, ItemIdentifierStack>>> buffered = new ArrayList<>();
	public static TextureType TEXTURE = Textures.empty;
	public String satelliteId, resultId;
	private UUID satelliteUUID, resultUUID;
	private BlockingMode blockingMode = BlockingMode.OFF;
	private int sendCooldown = 0;

	public CraftingManager(Item item) {
		super(item);
		upgradeManager = new UpgradeManager(this) {
			@Override
			public boolean hasUpgradeModuleUpgrade() {
				return false;//Crashes with gui
			}
		};
	}

	@Override
	public TextureType getCenterTexture() {
		return TEXTURE;
	}

	@Override
	public int getChassiSize() {
		return 27;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void InventoryChanged(IInventory inventory) {
		ChassiModule _module = (ChassiModule) getLogisticsModule();
		boolean reInitGui = false;
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (stack.isEmpty()) {
				if (_module.hasModule(i)) {
					_module.removeModule(i);
					reInitGui = true;
				}
				continue;
			}

			if (stack.getItem() instanceof ItemModule) {
				LogisticsModule current = _module.getModule(i);
				LogisticsModule next = getModuleForItem(stack, _module.getModule(i), this, this);
				next.registerPosition(ModulePositionType.SLOT, i);
				next.registerCCEventQueuer(this);
				if (current != next) {
					_module.installModule(i, next);
					if (!MainProxy.isClient()) {
						ItemModuleInformationManager.readInformation(stack, next);
					}
				}
				inventory.setInventorySlotContents(i, stack);
			}
		}
		if (reInitGui) {
			if (MainProxy.isClient(getWorld())) {
				if (FMLClientHandler.instance().getClient().currentScreen instanceof GuiChassiPipe) {
					FMLClientHandler.instance().getClient().currentScreen.initGui();
				}
			}
		}
	}

	public LogisticsModule getModuleForItem(ItemStack itemStack, LogisticsModule currentModule, IWorldProvider world, IPipeServiceProvider service) {
		if (itemStack == null) {
			return null;
		}
		if (!isCraftingModule(itemStack)) {
			return null;
		}
		if (currentModule != null) {
			if (ModuleCrafterExt.class.equals(currentModule.getClass())) {
				return currentModule;
			}
		}
		ModuleCrafterExt newmodule = new ModuleCrafterExt();
		newmodule.registerHandler(world, service);
		return newmodule;
	}

	@Override public IHeadUpDisplayRenderer getRenderer() {return null;}
	@Override public void startWatching() {}
	@Override public void stopWatching() {}
	@Override public void playerStartWatching(EntityPlayer player, int mode) {}
	@Override public void playerStopWatching(EntityPlayer player, int mode) {}

	@Override
	public boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
		handleClick0(entityplayer, settings);
		return true;
	}

	private boolean handleClick0(EntityPlayer entityplayer, SecuritySettings settings) {
		if (entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).isEmpty()) {
			return false;
		}
		if(!entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container)){
			if (MainProxy.isServer(getWorld())) {
				if (settings == null || settings.openGui) {
					openGui(entityplayer);
				} else {
					entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
				}
			}
			SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container);
			return true;
		}
		if (entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container)) {
			if (MainProxy.isServer(getWorld())) {
				if (settings == null || settings.openGui) {
					((PipeLogisticsChassi) container.pipe).nextOrientation();
				} else {
					entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
				}
			}
			SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), container);
			return true;
		}

		if (!entityplayer.isSneaking() && entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).getItem() instanceof ItemModule) {
			if (MainProxy.isServer(getWorld())) {
				if (settings == null || settings.openGui) {
					return tryInsertingModule(entityplayer);
				} else {
					entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
				}
			}
			return true;
		}

		return false;
	}

	private boolean tryInsertingModule(EntityPlayer entityplayer) {
		if(!isCraftingModule(entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND)))return false;
		IInventory _moduleInventory = getModuleInventory();
		for (int i = 0; i < _moduleInventory.getSizeInventory(); i++) {
			ItemStack item = _moduleInventory.getStackInSlot(i);
			if (item.isEmpty()) {
				_moduleInventory.setInventorySlotContents(i, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).splitStack(1));
				InventoryChanged(_moduleInventory);
				return true;
			}
		}
		return false;
	}

	public static boolean isCraftingModule(ItemStack itemStack){
		return itemStack.getItem() == Item.REGISTRY.getObject(LPItems.modules.get(ModuleCrafter.getName()));
	}

	public boolean isUpgradeModule(ItemStack itemStack, int slot){
		return ChassiGuiProvider.checkStack(itemStack, this, slot);
	}

	public void openGui(EntityPlayer entityPlayer) {
		ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(isBuffered() ? Integer.toString(blockingMode.ordinal()) : "0").setId(2).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityPlayer);
		entityPlayer.openGui(LogisticsBridge.modInstance, GuiIDs.CraftingManager.ordinal(), getWorld(), getX(), getY(), getZ());
		packet = PacketHandler.getPacket(SetIDPacket.class).setName(satelliteId).setId(0).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityPlayer);
		packet = PacketHandler.getPacket(SetIDPacket.class).setName(resultId).setId(1).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityPlayer);
	}

	@Override
	public String getPipeID(int id) {
		return id == 0 ? satelliteId : id == 1 ? resultId : Integer.toString(blockingMode.ordinal());
	}

	@Override
	public void setPipeID(int id, String pipeID, EntityPlayer player) {
		if (player == null) {
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToServer(packet);
		} else if (MainProxy.isServer(player.world)){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(pipeID).setId(id).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToPlayer(packet, player);
			satelliteUUID = null;
			resultUUID = null;
		}
		if(id == 0)satelliteId = pipeID;
		else if(id == 1)resultId = pipeID;
		else if(id == 2)blockingMode = BlockingMode.VALUES[Math.abs(pipeID.charAt(0) - '0') % BlockingMode.VALUES.length];
	}
	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		if(resultId != null)nbttagcompound.setString("resultname", resultId);
		if(satelliteId != null)nbttagcompound.setString("satellitename", satelliteId);
		nbttagcompound.setByte("blockingMode", (byte) blockingMode.ordinal());
	}
	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		resultId = nbttagcompound.getString("resultname");
		satelliteId = nbttagcompound.getString("satellitename");
		if(nbttagcompound.hasKey("resultid")){
			resultId = Integer.toString(nbttagcompound.getInteger("resultid"));
			satelliteId = Integer.toString(nbttagcompound.getInteger("satelliteid"));
		}
		blockingMode = BlockingMode.VALUES[Math.abs(nbttagcompound.getByte("blockingMode")) % BlockingMode.VALUES.length];
	}
	@Override
	public void collectSpecificInterests(Collection<ItemIdentifier> itemidCollection) {
		for (int i = 0; i < getChassiSize(); i++) {
			LogisticsModule module = getSubModule(i);
			if (module != null) {
				module.collectSpecificInterests(itemidCollection);
			}
		}
	}

	@Override
	public boolean hasGenericInterests() {
		for (int i = 0; i < getChassiSize(); i++) {
			LogisticsModule x = getSubModule(i);

			if (x != null && x.hasGenericInterests()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getName(int id) {
		return null;
	}

	private UUID getUUIDForSatelliteName(String name) {
		for(PipeItemsSatelliteLogistics pipe: PipeItemsSatelliteLogistics.AllSatellites) {
			if (pipe.getSatellitePipeName().equals(name)) {
				return pipe.getRouter().getId();
			}
		}
		return null;
	}
	private UUID getUUIDForResultName(String name) {
		for(ResultPipe pipe: ResultPipe.AllResults) {
			if (pipe.getResultPipeName().equals(name)) {
				return pipe.getRouter().getId();
			}
		}
		return null;
	}

	public UUID getSatelliteUUID() {
		if(satelliteUUID == null){
			satelliteUUID = getUUIDForSatelliteName(satelliteId);
		}
		return satelliteUUID;
	}

	public UUID getResultUUID() {
		if(resultUUID == null){
			resultUUID = getUUIDForResultName(resultId);
		}
		return resultUUID;
	}
	@Override
	public List<String> list(int id) {
		if(id == 0){
			return PipeItemsSatelliteLogistics.AllSatellites.stream()
					.filter(Objects::nonNull)
					.filter(it -> it.getRouter() != null)
					.filter(it -> this.getRouter().getRouteTable().size() > it.getRouterId())
					.filter(it -> this.getRouter().getRouteTable().get(it.getRouterId()) != null)
					.filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
					.sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
					.map(PipeItemsSatelliteLogistics::getSatellitePipeName)
					.collect(Collectors.toList());
		}else if(id == 1){
			return ResultPipe.AllResults.stream()
					.filter(Objects::nonNull)
					.filter(it -> it.getRouter() != null)
					.filter(it -> this.getRouter().getRouteTable().size() > it.getRouterId())
					.filter(it -> this.getRouter().getRouteTable().get(it.getRouterId()) != null)
					.filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
					.sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
					.map(ResultPipe::getResultPipeName)
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}
	public boolean isBuffered(){
		for(int i = 0;i<9;i++){
			if(upgradeManager.getUpgrade(i) instanceof BufferUpgrade)
				return true;
		}
		return false;
	}

	public void addBuffered(List<Pair<IRequestItems, ItemIdentifierStack>> rec) {
		buffered.add(rec);
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

	private boolean checkBlocking() {
		switch (blockingMode) {
		case EMPTY_MAIN_SATELLITE:
		{
			IRouter defSat = getSatelliteRouterByID(getSatelliteUUID());
			if(defSat == null)return false;
			if(!(defSat.getPipe() instanceof PipeItemsSatelliteLogistics))return false;
			IInventoryUtil inv = ((PipeItemsSatelliteLogistics) defSat.getPipe()).getPointedInventory();
			if (inv != null) {
				for (int i = 0; i < inv.getSizeInventory(); i++) {
					ItemStack stackInSlot = inv.getStackInSlot(i);
					if (!stackInSlot.isEmpty()) {
						return false;
					}
				}
			}
			return true;
		}

		case REDSTONE_HIGH:
			if(!getWorld().isBlockPowered(getPos()))return false;
			return true;

		case REDSTONE_LOW:
			if(getWorld().isBlockPowered(getPos()))return false;
			return true;

			/*case WAIT_FOR_RESULT://TODO broken
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
	public void enabledUpdateEntity() {
		super.enabledUpdateEntity();

		if(!isNthTick(5))return;

		if(isBuffered()) {
			if(sendCooldown > 0) {
				spawnParticle(Particles.RedParticle, 1);
				sendCooldown--;
				return;
			}
			if(blockingMode == BlockingMode.NULL)blockingMode = BlockingMode.OFF;
			if(!buffered.isEmpty()) {
				boolean allow = checkBlocking();
				if(!allow) {
					spawnParticle(Particles.RedParticle, 1);
					return;
				}
				final NeighborTileEntity<TileEntity> pointedItemHandler = getPointedItemHandler();
				if(canUseEnergy(neededEnergy()) && pointedItemHandler != null && pointedItemHandler.isItemHandler()){
					IInventoryUtil util = pointedItemHandler.getInventoryUtil();
					for (List<Pair<IRequestItems, ItemIdentifierStack>> map : buffered) {
						if(map.stream().map(Pair::getValue).allMatch(i -> util.itemCount(i.getItem()) >= i.getStackSize())){
							int maxDist = 0;
							for (Pair<IRequestItems, ItemIdentifierStack> en : map) {
								ItemIdentifierStack toSend = en.getValue();
								ItemStack removed = util.getMultipleItems(toSend.getItem(), toSend.getStackSize());
								if (removed != null && !removed.isEmpty()) {
									sendStack(removed, en.getKey().getID(), ItemSendMode.Fast, null);
									maxDist = Math.max(maxDist, (int) en.getKey().getRouter().getPipe().getPos().distanceSq(getPos()));
								}
							}
							useEnergy(neededEnergy(), true);
							buffered.remove(map);
							if(blockingMode == BlockingMode.EMPTY_MAIN_SATELLITE)sendCooldown = Math.min(maxDist, 16);
							break;
						}
					}
				}
			}
		}
	}

	/*private int sendStack(ItemIdentifierStack stack, IRequestItems dest, IInventoryUtil util, IAdditionalTargetInformation info, EnumFacing dir) {
		ItemIdentifier item = stack.getItem();

		int available = util.itemCount(item);
		if (available == 0) {
			return 0;
		}

		int wanted = Math.min(available, stack.getStackSize());
		wanted = Math.min(wanted, item.getMaxStackSize());
		SinkReply reply = LogisticsManager.canSink(dest.getRouter(), null, true, stack.getItem(), null, true, false);
		if (reply != null) {// some pipes are not aware of the space in the adjacent inventory, so they return null
			if (reply.maxNumberOfItems < wanted) {
				wanted = reply.maxNumberOfItems;
				if (wanted <= 0) {
					return 0;
				}
			}
		}
		if (!canUseEnergy(wanted * neededEnergy())) {
			return -1;
		}
		ItemStack removed = util.getMultipleItems(item, wanted);
		if (removed == null || removed.getCount() == 0) {
			return 0;
		}
		int sent = removed.getCount();
		useEnergy(sent * neededEnergy());

		IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(removed);
		routedItem.setDestination(dest.getID());
		routedItem.setTransportMode(TransportMode.Active);
		routedItem.setAdditionalTargetInformation(info);
		super.queueRoutedItem(routedItem, dir != null ? dir : EnumFacing.UP);
		return sent;
	}
	 */
	private int neededEnergy() {
		return 20;
	}

	public void save(int i) {
		ItemStack st = getModuleInventory().getStackInSlot(i);
		if(!st.isEmpty()) {
			ItemModuleInformationManager.saveInformation(st, getModules().getModule(i));
			getModuleInventory().setInventorySlotContents(i, st);
		}
	}

	public static enum BlockingMode {
		NULL,
		OFF,
		//WAIT_FOR_RESULT,
		EMPTY_MAIN_SATELLITE,
		REDSTONE_LOW,
		REDSTONE_HIGH,
		;
		public static final BlockingMode[] VALUES = values();
	}

	public BlockingMode getBlockingMode() {
		return blockingMode;
	}

	/*public class Origin implements IAdditionalTargetInformation {
		private final IAdditionalTargetInformation old;
		public Origin(IAdditionalTargetInformation old) {
			this.old = old;
		}

		public void onSent() {
			canSendNext = true;
		}

		public IAdditionalTargetInformation getOld() {
			return old;
		}
	}

	public IAdditionalTargetInformation wrap(IAdditionalTargetInformation old) {
		return new Origin(old);
	}

	public static IAdditionalTargetInformation unwrap(IAdditionalTargetInformation info, boolean doFinish) {
		if(info instanceof Origin) {
			Origin o = (Origin) info;
			if(doFinish)o.onSent();
			return o.old;
		}
		return info;
	}*/
}
