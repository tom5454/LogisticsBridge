package com.tom.logisticsbridge.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.tom.logisticsbridge.tileentity.ICraftingManager;

public class ContainerCraftingManagerU extends Container {
	public ContainerCraftingManagerU(EntityPlayer player, ICraftingManager te) {
		for (int k = 0; k < 3; ++k)
		{
			for (int l = 0; l < 9; ++l)
			{
				addSlotToContainer(te.createGuiSlot(l + k * 9, 8 + l * 18, 18 + k * 18));
			}
		}

		addPlayerSlots(player.inventory, 8, 84);
	}
	@Override
	public boolean canInteractWith(EntityPlayer playerIn) {
		return true;
	}
	protected void addPlayerSlots(InventoryPlayer playerInventory, int x, int y) {
		for (int i = 0;i < 3;++i) {
			for (int j = 0;j < 9;++j) {
				addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, x + j * 18, y + i * 18));
			}
		}

		for (int i = 0;i < 9;++i) {
			addSlotToContainer(new Slot(playerInventory, i, x + i * 18, y + 58));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
		return ItemStack.EMPTY;
	}
}
