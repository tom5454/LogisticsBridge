package com.tom.logisticsbridge.gui;

import java.io.IOException;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.inventory.ContainerCraftingManager;
import com.tom.logisticsbridge.inventory.ContainerCraftingManager.SlotCraftingCard;
import com.tom.logisticsbridge.pipe.CraftingManager;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;

import logisticspipes.LPItems;
import logisticspipes.modules.LogisticsModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.chassis.ChassisGUI;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.gui.extention.GuiExtention;
import logisticspipes.utils.string.StringUtils;

public class GuiCraftingManager extends LogisticsBaseGuiScreen {
	private static final ResourceLocation BG = new ResourceLocation(LogisticsBridge.ID, "textures/gui/crafting_manager.png");
	private EntityPlayer player;
	private CraftingManager pipe;
	private PopupMenu popup;
	private Slot fakeSlot;
	private InventoryBasic inv;
	public GuiCraftingManager(EntityPlayer player, CraftingManager pipe) {
		super(new ContainerCraftingManager(player, pipe, false));
		this.player = player;
		this.pipe = pipe;
		popup = new PopupMenu();
		popup.add(TextFormatting.BOLD + I18n.format("gui.craftingManager.openCfg"));
		popup.add(I18n.format("gui.craftingManager.pickupUpgrade", 1));
		popup.add(I18n.format("gui.craftingManager.pickupUpgrade", 2));
		ySize++;
		inv = new InventoryBasic("", false, 1);
		fakeSlot = new Slot(inv, 0, 0, 0);
	}
	public void bindTexture(ResourceLocation loc){
		mc.getTextureManager().bindTexture(loc);
	}
	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		bindTexture(BG);
		this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
		super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
	}
	/**
	 * Draws the screen and all the components in it.
	 */
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		super.drawScreen(mouseX, mouseY, partialTicks);
		this.renderHoveredToolTip(mouseX, mouseY);
		popup.render(mouseX, mouseY, fontRenderer, width, height);

	}
	@Override
	public void drawSlot(Slot slotIn) {
		if(slotIn.slotNumber >= 27 && slotIn.slotNumber < 27*3){
			GlStateManager.pushMatrix();
			float f = 1/3f;
			int xp = ((slotIn.slotNumber - 27) / 2 % 9) * 18 + 18;
			int yp = (((slotIn.slotNumber - 27) / 18) % 3) * 18 + (slotIn.slotNumber % 2 == 0 ? -1 : 11) + 18;
			GlStateManager.translate(xp, yp, -50);
			GlStateManager.scale(f, f, f);
			slotIn.xPos = 0;
			slotIn.yPos = 0;
			super.drawSlot(slotIn);
			slotIn.xPos = Integer.MIN_VALUE;
			slotIn.yPos = Integer.MIN_VALUE;
			GlStateManager.popMatrix();
		}else{
			ItemStack stack = slotIn.getStack();
			if(slotIn.slotNumber < 27 && !stack.isEmpty() && stack.hasTagCompound() && isShiftKeyDown()){
				NBTTagCompound info = stack.getTagCompound().getCompoundTag("moduleInformation");
				NBTTagList list = info.getTagList("items", 10);
				NBTTagCompound output = null;
				for(int i = 0;i<list.tagCount();i++){
					NBTTagCompound tag = list.getCompoundTagAt(i);
					if(tag.getInteger("index") == 9){
						output = tag;
					}
				}
				if(output != null){
					inv.setInventorySlotContents(0, new ItemStack(output));
					fakeSlot.xPos = slotIn.xPos;
					fakeSlot.yPos = slotIn.yPos;
					super.drawSlot(fakeSlot);
					inv.setInventorySlotContents(0, ItemStack.EMPTY);
				}else
					super.drawSlot(slotIn);
			}else
				super.drawSlot(slotIn);
		}
	}
	/**
	 * Draw the foreground layer for the GuiContainer (everything in front of the items)
	 */
	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		super.drawGuiContainerForegroundLayer(mouseX, mouseY);
		this.fontRenderer.drawString(I18n.format("gui.craftingManager"), 8, 6, 4210752);
		this.fontRenderer.drawString(this.player.inventory.getDisplayName().getUnformattedText(), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		int pr = popup.mouseClicked(mouseX, mouseY, fontRenderer);
		if(pr == -100){
			Slot sl = getSlotUnderMouse();
			if(sl instanceof SlotCraftingCard){
				if(mouseButton == 0){
					if((held().isEmpty() && sl.getHasStack()) || (!sl.getHasStack() && CraftingManager.isCraftingModule(held())))super.mouseClicked(mouseX, mouseY, mouseButton);
					else if(sl.getHasStack()){
						if(pipe.isUpgradeModule(held(), sl.slotNumber)){
							Slot slot = inventorySlots.inventorySlots.get(sl.slotNumber*2 + 27);
							if((!slot.getHasStack() && slot.isItemValid(held())) || (slot.getStack().isItemEqual(held()) && slot.getItemStackLimit(held()) <= slot.getStack().getCount()+1))windowClick(sl.slotNumber*2 + 27, 1, ClickType.PICKUP);
							slot = inventorySlots.inventorySlots.get(sl.slotNumber*2 + 28);
							if((!slot.getHasStack() && slot.isItemValid(held())) || (slot.getStack().isItemEqual(held()) && slot.getItemStackLimit(held()) <= slot.getStack().getCount()+1))windowClick(sl.slotNumber*2 + 28, 1, ClickType.PICKUP);
						}
					}
				} else if(mouseButton == 1 || mouseButton == 2){
					if(mouseButton == 1){
						if(sl.getHasStack()){
							LogisticsModule module = pipe.getSubModule(sl.slotNumber);
							if (module != null) {
								final ModernPacket packet = PacketHandler.getPacket(ChassisGUI.class).setButtonID(sl.slotNumber).setPosX(pipe.getX()).setPosY(pipe.getY()).setPosZ(pipe.getZ());
								MainProxy.sendPacketToServer(packet);
							}
						}
					}else{
						popup.show(mouseX, mouseY, sl);
					}
				}
			}else super.mouseClicked(mouseX, mouseY, mouseButton);
		}else if(pr != -1){
			SlotCraftingCard sl = (SlotCraftingCard) popup.additionalData;
			if(pr == 0){
				if(sl.getHasStack()){
					LogisticsModule module = pipe.getSubModule(sl.slotNumber);
					if (module != null) {
						final ModernPacket packet = PacketHandler.getPacket(ChassisGUI.class).setButtonID(sl.slotNumber).setPosX(pipe.getX()).setPosY(pipe.getY()).setPosZ(pipe.getZ());
						MainProxy.sendPacketToServer(packet);
					}
				}
			}else if(pr == 1){
				windowClick(sl.slotNumber*2 + 27, 0, ClickType.PICKUP);
			}else if(pr == 2){
				windowClick(sl.slotNumber*2 + 28, 0, ClickType.PICKUP);
			}
		}
	}
	protected void windowClick(int id, int btn, ClickType clickType) {
		mc.playerController.windowClick(this.inventorySlots.windowId, id, btn, clickType, this.mc.player);
	}
	private ItemStack held(){
		return this.mc.player.inventory.getItemStack();
	}
	@Override
	public void initGui() {
		super.initGui();//120 155
		String select = StringUtils.translate("gui.crafting.Select");
		extentionControllerLeft.clear();
		ConfigExtention ce = new ConfigExtention(StringUtils.translate("gui.craftingManager.satellite"), new ItemStack(LPItems.pipeSatellite), 0);
		ce.registerButton(extentionControllerLeft.registerControlledButton(addButton(new SmallGuiButton(0, guiLeft - 45, guiTop + 25, 40, 10, select))));
		extentionControllerLeft.addExtention(ce);
		ce = new ConfigExtention(StringUtils.translate("gui.craftingManager.result"), new ItemStack(LogisticsBridge.pipeResult), 1);
		ce.registerButton(extentionControllerLeft.registerControlledButton(addButton(new SmallGuiButton(2, guiLeft - 45, guiTop + 25, 40, 10, select))));
		extentionControllerLeft.addExtention(ce);

		if(pipe.getBlockingMode() != BlockingMode.NULL) {
			ce = new ConfigExtention(StringUtils.translate("gui.craftingManager.blocking"), new ItemStack(Blocks.BARRIER), 2) {

				@Override
				public String getString() {
					return StringUtils.translate("gui.craftingManager.blocking." + pipe.getBlockingMode().name().toLowerCase());
				}

				@Override
				public int textOff() {
					return 70;
				}

				@Override
				public int getFinalWidth() {
					return 140;
				}
			};
			ce.registerButton(extentionControllerLeft.registerControlledButton(addButton(new SmallGuiButton(4, guiLeft - 45, guiTop + 11, 40, 10, StringUtils.translate("gui.craftingManager.blocking.change")))));
			extentionControllerLeft.addExtention(ce);
		}
	}
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		switch (button.id) {
		case 0:
			openSubGuiForSatelliteSelection(0);
			return;
		case 2:
			openSubGuiForSatelliteSelection(1);
			return;

		case 4:
			BlockingMode m = BlockingMode.VALUES[(pipe.getBlockingMode().ordinal() + 1) % BlockingMode.VALUES.length];
			if(m == BlockingMode.NULL)m = BlockingMode.OFF;
			pipe.setPipeID(2, Integer.toString(m.ordinal()), null);
			return;
		}
	}
	public RenderItem renderItem() {
		return itemRender;
	}
	public FontRenderer fontRenderer() {
		return fontRenderer;
	}
	public class ConfigExtention extends GuiExtention {
		private final String name;
		private final ItemStack stack;
		private final int id;
		public ConfigExtention(String name, ItemStack stack, int id) {
			this.name = name;
			this.stack = stack;
			this.id = id;
		}
		@Override
		public int getFinalWidth() {
			return 120;
		}

		@Override
		public int getFinalHeight() {
			return 40;
		}

		@Override
		public void renderForground(int left, int top) {
			String pid = getString();
			if (!isFullyExtended()) {
				GL11.glEnable(GL12.GL_RESCALE_NORMAL);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240 / 1.0F, 240 / 1.0F);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				RenderHelper.enableGUIStandardItemLighting();
				renderItem().renderItemAndEffectIntoGUI(stack, left + 5, top + 5);
				renderItem().renderItemOverlayIntoGUI(fontRenderer(), stack, left + 5, top + 5, "");
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				renderItem().zLevel = 0.0F;
			} else {
				fontRenderer().drawString(name, left + 9, top + 8, 0x404040);
				if (pid == null || pid.isEmpty()) {
					mc.fontRenderer.drawString(StringUtils.translate("gui.craftingManager.noConnection"), left + 40, top + 22, 0x404040);
				} else {
					mc.fontRenderer.drawString(pid, left + textOff() - mc.fontRenderer.getStringWidth(pid)/2, top + 22, 0x404040);
				}
			}
		}

		public String getString() {
			return pipe.getPipeID(id);
		}

		public int textOff() {
			return 40;
		}
	}
	private void openSubGuiForSatelliteSelection(int id) {
		this.setSubGui(new GuiSelectIDPopup(new BlockPos(this.pipe.getX(), this.pipe.getY(), this.pipe.getZ()), id, 0,
				uuid -> pipe.setPipeID(id, uuid, null)));
	}
}
