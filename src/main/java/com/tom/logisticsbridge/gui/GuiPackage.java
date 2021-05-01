package com.tom.logisticsbridge.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.inventory.ContainerPackage;
import com.tom.logisticsbridge.inventory.ContainerPackage.SlotPhantom;
import com.tom.logisticsbridge.network.SetIDPacket;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import network.rs485.logisticspipes.util.TextUtil;

public class GuiPackage extends GuiContainer implements Runnable {
	private static final ResourceLocation BG = new ResourceLocation(LogisticsBridge.ID, "textures/gui/package_gui.png");
	private EntityPlayer player;
	private GuiTextField textField;
	public GuiPackage(EntityPlayer player, EnumHand hand) {
		super(new ContainerPackage(player, hand));
		((ContainerPackage)inventorySlots).update = this;
		this.player = player;
		this.ySize = 133;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(BG);
		this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
	}
	/**
	 * Draw the foreground layer for the GuiContainer (everything in front of the items)
	 */
	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		super.drawGuiContainerForegroundLayer(mouseX, mouseY);
		this.fontRenderer.drawString(I18n.format("gui.package"), 8, 6, 4210752);
		this.fontRenderer.drawString(this.player.inventory.getDisplayName().getUnformattedText(), 8, this.ySize - 96 + 2, 4210752);
		String pid = ((ContainerPackage)inventorySlots).id;
		if (pid == null || pid.isEmpty()) {
			mc.fontRenderer.drawString(TextUtil.translate("gui.craftingManager.noConnection"), 80, 14, 0x404040);
		} else {
			mc.fontRenderer.drawString(pid, 105 - mc.fontRenderer.getStringWidth(pid)/2, 14, 0x404040);
		}
	}

	@Override
	public void initGui() {
		super.initGui();
		addButton(new GuiButton(0, guiLeft + 134, guiTop + 25, 30, 20, TextUtil.translate("gui.popup.addchannel.save")));
		textField = new GuiTextField(1, fontRenderer, guiLeft + 80, guiTop + 25, 50, 20);
	}
	//this.mc.playerController.sendEnchantPacket(this.container.windowId, k);
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		if(button.id == 0){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setSide(-2).setName(textField.getText()).setId(0);
			MainProxy.sendPacketToServer(packet);
			((ContainerPackage)inventorySlots).id = textField.getText();
		}
	}
	/**
	 * Draws the screen and all the components in it.
	 */
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks)
	{
		this.drawDefaultBackground();
		super.drawScreen(mouseX, mouseY, partialTicks);
		textField.drawTextBox();
		this.renderHoveredToolTip(mouseX, mouseY);
	}
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if(!textField.mouseClicked(mouseX, mouseY, mouseButton)){
			Slot slot = this.getSlotUnderMouse();
			if (slot instanceof SlotPhantom) {
				SlotPhantom s = (SlotPhantom) slot;
				this.handleMouseClick(s, s.slotNumber, mouseButton, isCtrlKeyDown() ? isShiftKeyDown() ? ClickType.QUICK_CRAFT : ClickType.CLONE : isShiftKeyDown() ? ClickType.PICKUP_ALL : ClickType.PICKUP);
			} else
				super.mouseClicked(mouseX, mouseY, mouseButton);
		}
	}
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if(keyCode == 28){
			final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setSide(-2).setName(textField.getText()).setId(0);
			MainProxy.sendPacketToServer(packet);
			textField.setFocused(false);
			((ContainerPackage)inventorySlots).id = textField.getText();
		}else if(!textField.textboxKeyTyped(typedChar, keyCode))
			super.keyTyped(typedChar, keyCode);
	}

	@Override
	public void run() {
		textField.setText(((ContainerPackage)inventorySlots).id);
	}
}
