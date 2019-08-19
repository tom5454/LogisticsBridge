package com.tom.logisticsbridge.network;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.tom.logisticsbridge.gui.GuiSelectIDPopup;

import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.utils.StaticResolve;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SubGuiScreen;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

@StaticResolve
public class ProvideIDListPacket extends ModernPacket {

	private List<String> list;

	public ProvideIDListPacket(int id) {
		super(id);
	}

	@Override
	public void readData(LPDataInput input) {
		super.readData(input);
		list = input.readArrayList(input1 -> input1.readUTF());
	}

	@Override
	public void writeData(LPDataOutput output) {
		super.writeData(output);
		output.writeCollection(list, (output1, object) -> {
			output1.writeUTF(object);
		});
	}

	@Override
	public void processPacket(EntityPlayer player) {
		if (Minecraft.getMinecraft().currentScreen instanceof LogisticsBaseGuiScreen) {
			SubGuiScreen subGUI = ((LogisticsBaseGuiScreen) Minecraft.getMinecraft().currentScreen).getSubGui();
			if (subGUI instanceof GuiSelectIDPopup) {
				((GuiSelectIDPopup)subGUI).handleList(list);
			}
		}
	}

	@Override
	public ModernPacket template() {
		return new ProvideIDListPacket(getId());
	}

	public ModernPacket setList(List<String> list2) {
		this.list = list2;
		return this;
	}
}