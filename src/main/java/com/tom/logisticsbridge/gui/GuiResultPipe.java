package com.tom.logisticsbridge.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.InputBar;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.string.StringUtils;

public class GuiResultPipe extends LogisticsBaseGuiScreen {

	private IIdPipe _result;
	private EntityPlayer _player;
	private InputBar input;
	private int slot;

	public GuiResultPipe(IIdPipe result, EntityPlayer player, int slot) {
		super(new Container() {

			@Override
			public boolean canInteractWith(EntityPlayer entityplayer) {
				return true;
			}
		});
		this.slot = slot;
		_result = result;
		_player = player;
		xSize = 116;
		ySize = 77;
	}

	@Override
	public void initGui() {
		super.initGui();
		buttonList.add(new SmallGuiButton(0, (width / 2) - (30 / 2) + 35, (height / 2) + 20, 30, 10, StringUtils.translate("gui.popup.addchannel.save")));
		input = new InputBar(fontRenderer, this, guiLeft + 8, guiTop + 40, 100, 16);
	}

	@Override
	protected void actionPerformed(GuiButton guibutton) throws IOException {
		if (guibutton.id == 0) {
			_result.setPipeID(slot, input.input1 + input.input2, null);
		} else {
			super.actionPerformed(guibutton);
		}
	}

	@Override
	protected void drawGuiContainerForegroundLayer(int par1, int par2) {
		super.drawGuiContainerForegroundLayer(par1, par2);
		mc.fontRenderer.drawString(StringUtils.translate(_result.getName(slot)), 33, 10, 0x404040);
		String name = StringUtils.getCuttedString(_result.getPipeID(slot), 100, mc.fontRenderer);
		mc.fontRenderer.drawString(name, 59 - mc.fontRenderer.getStringWidth(name) / 2, 24, 0x404040);
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
		super.drawGuiContainerBackgroundLayer(f, x, y);
		GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
		input.renderSearchBar();
	}

	@Override
	protected void mouseClicked(int x, int y, int k) throws IOException {
		if(!input.handleClick(x, y, k)) {
			super.mouseClicked(x, y, k);
		}
	}

	@Override
	public void keyTyped(char c, int i) throws IOException {
		if(!input.handleKey(c, i)) {
			super.keyTyped(c, i);
		}
	}
}

