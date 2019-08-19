package com.tom.logisticsbridge.pipe;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.client.FMLClientHandler;

import com.tom.logisticsbridge.ASMUtil;
import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.module.ModuleCrafterExt;
import com.tom.logisticsbridge.network.SetIDPacket;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.LPItems;
import logisticspipes.api.ILPPipeTile;
import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.items.ItemModule;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.pipe.ChassiGuiProvider;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.item.ItemIdentifier;

public class CraftingManager extends PipeLogisticsChassi implements IIdPipe {
	public static TextureType TEXTURE = Textures.empty;
	public static Function<CoreUnroutedPipe, ILPPipeTile> getContainer;
	static {
		try {
			getContainer = ASMUtil.<CoreUnroutedPipe, ILPPipeTile>getfield(CoreUnroutedPipe.class.getDeclaredField("container"));
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}
	public String satelliteId, resultId;
	private UUID satelliteUUID, resultUUID;
	public CraftingManager(Item item) {
		super(item);
	}

	@Override
	public TextureType getCenterTexture() {
		return TEXTURE;
	}

	@Override
	public int getChassiSize() {
		return 27;
	}

	@Override
	public ResourceLocation getChassiGUITexture() {
		return new ResourceLocation("minecraft:textures/items/barrier.png");
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
	@Override
	public IHeadUpDisplayRenderer getRenderer() {
		return null;
	}
	@Override
	public void startWatching() {
	}
	@Override
	public void stopWatching() {
	}
	@Override
	public void playerStartWatching(EntityPlayer player, int mode) {
	}
	@Override
	public void playerStopWatching(EntityPlayer player, int mode) {
	}
	@Override
	public void nextOrientation() {
		super.nextOrientation();
	}
	@Override
	public boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
		handleClick0(entityplayer, settings);
		return true;
	}
	private boolean handleClick0(EntityPlayer entityplayer, SecuritySettings settings) {
		if (entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).isEmpty()) {
			return false;
		}
		if(!entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), getContainerHidden())){
			if (MainProxy.isServer(getWorld())) {
				if (settings == null || settings.openGui) {
					openGui(entityplayer);
				} else {
					entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
				}
			}
			SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), getContainerHidden());
			return true;
		}
		if (entityplayer.isSneaking() && SimpleServiceLocator.configToolHandler.canWrench(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), getContainerHidden())) {
			if (MainProxy.isServer(getWorld())) {
				if (settings == null || settings.openGui) {
					((PipeLogisticsChassi) container.pipe).nextOrientation();
				} else {
					entityplayer.sendMessage(new TextComponentTranslation("lp.chat.permissiondenied"));
				}
			}
			SimpleServiceLocator.configToolHandler.wrenchUsed(entityplayer, entityplayer.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND), getContainerHidden());
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

	private ILPPipeTile getContainerHidden() {
		return getContainer.apply(this);
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
		return itemStack.getItem() == LPItems.modules.get(ModuleCrafter.class);
	}
	public boolean isUpgradeModule(ItemStack itemStack, int slot){
		return ChassiGuiProvider.checkStack(itemStack, this, slot);
	}

	public void openGui(EntityPlayer entityPlayer) {
		entityPlayer.openGui(LogisticsBridge.modInstance, GuiIDs.CraftingManager.ordinal(), getWorld(), getX(), getY(), getZ());
		ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setName(satelliteId).setId(0).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityPlayer);
		packet = PacketHandler.getPacket(SetIDPacket.class).setName(resultId).setId(1).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
		MainProxy.sendPacketToPlayer(packet, entityPlayer);
	}

	/*@Override
	public void setNextId(EntityPlayer player, int fid) {
		if(fid == 0)setNextSatellite(player);
		else setNextResult(player);
	}

	@Override
	public void setPrevId(EntityPlayer player, int fid) {
		if(fid == 0)setPrevSatellite(player);
		else setPrevResult(player);
	}*/

	@Override
	public String getPipeID(int id) {
		return id == 0 ? satelliteId : resultId;
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
		else resultId = pipeID;
	}
	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		if(resultId != null)nbttagcompound.setString("resultname", resultId);
		if(satelliteId != null)nbttagcompound.setString("satellitename", satelliteId);
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
	}
	@Override
	public Set<ItemIdentifier> getSpecificInterests() {
		Set<ItemIdentifier> l1 = new TreeSet<>();
		for (int i = 0; i < getChassiSize(); i++) {
			LogisticsModule module = getModules().getSubModule(i);
			if (module != null) {
				Collection<ItemIdentifier> current = module.getSpecificInterests();
				if (current != null) {
					l1.addAll(current);
				}
			}
		}
		return l1;
	}

	@Override
	public boolean hasGenericInterests() {
		for (int i = 0; i < getChassiSize(); i++) {
			LogisticsModule x = getModules().getSubModule(i);

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
	/*public void setNextSatellite(EntityPlayer player) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setPos(CraftingManager.this).setInc(true).setId(0);
			MainProxy.sendPacketToServer(packet);
		} else {
			satelliteId = getNextConnectSatelliteId(false);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(satelliteId).setId(0).setPos(CraftingManager.this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}

	public void setPrevSatellite(EntityPlayer player) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setPos(CraftingManager.this).setInc(false).setId(0);
			MainProxy.sendPacketToServer(packet);
		} else {
			satelliteId = getNextConnectSatelliteId(true);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(satelliteId).setId(0).setPos(CraftingManager.this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}
	public void setNextResult(EntityPlayer player) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setPos(CraftingManager.this).setInc(true).setId(1);
			MainProxy.sendPacketToServer(packet);
		} else {
			resultId = getNextConnectResultId(false);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(resultId).setId(1).setPos(CraftingManager.this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}
	public void setPrevResult(EntityPlayer player) {
		if (MainProxy.isClient(player.world)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(SetIDPacket.class).setPos(CraftingManager.this).setInc(false).setId(1);
			MainProxy.sendPacketToServer(packet);
		} else {
			resultId = getNextConnectResultId(true);
			final CoordinatesPacket packet = PacketHandler.getPacket(ResultPipeID.class).setPipeID(resultId).setId(1).setPos(CraftingManager.this);
			MainProxy.sendPacketToPlayer(packet, player);
		}
	}*/
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
					.filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
					.sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
					.map(PipeItemsSatelliteLogistics::getSatellitePipeName)
					.collect(Collectors.toList());
		}else if(id == 1){
			return ResultPipe.AllResults.stream()
					.filter(Objects::nonNull)
					.filter(it -> it.getRouter() != null)
					.filter(it -> this.getRouter().getRouteTable().size() > it.getRouterId())
					.filter(it -> !this.getRouter().getRouteTable().get(it.getRouterId()).isEmpty())
					.sorted(Comparator.comparingDouble(it -> this.getRouter().getRouteTable().get(it.getRouterId()).stream().map(it1 -> it1.distanceToDestination).min(Double::compare).get()))
					.map(ResultPipe::getResultPipeName)
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}
}
