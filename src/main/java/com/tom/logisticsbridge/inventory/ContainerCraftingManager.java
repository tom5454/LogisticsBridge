package com.tom.logisticsbridge.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.tom.logisticsbridge.pipe.CraftingManager;

import logisticspipes.network.guis.pipe.ChassisGuiProvider;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.ModuleSlot;

public class ContainerCraftingManager extends DummyContainer {

	public class SlotCraftingCard extends ModuleSlot {

		public SlotCraftingCard(CraftingManager pipe, int ind, int x, int y) {
			super(pipe.getModuleInventory(), ind, x, y, pipe);
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return CraftingManager.isCraftingModule(stack);
		}
		@Override
		public int getSlotStackLimit() {
			return 1;
		}
	}
	public ContainerCraftingManager(EntityPlayer player, CraftingManager pipe, boolean server) {
		super(player.inventory, null);
		for (int k = 0; k < 3; ++k)
		{
			for (int l = 0; l < 9; ++l)
			{
				this.addSlotToContainer(new SlotCraftingCard(pipe, l + k * 9, 8 + l * 18, 18 + k * 18));
			}
		}

		//if(server){
		for (int k = 0; k < 3; ++k)
		{
			for (int l = 0; l < 9; ++l)
			{
				final int fI = l + k * 9;
				ModuleUpgradeManager upgradeManager = pipe.getModuleUpgradeManager(fI);
				addUpgradeSlot(0, upgradeManager, 0, Integer.MIN_VALUE, Integer.MIN_VALUE, itemStack -> ChassisGuiProvider.checkStack(itemStack, pipe, fI));
				addUpgradeSlot(1, upgradeManager, 1, Integer.MIN_VALUE, Integer.MIN_VALUE, itemStack -> ChassisGuiProvider.checkStack(itemStack, pipe, fI));
			}
		}
		//}

		addNormalSlotsForPlayerInventory(8, 84);
	}

	@Override
	public boolean canInteractWith(EntityPlayer playerIn) {
		return true;
	}
}
