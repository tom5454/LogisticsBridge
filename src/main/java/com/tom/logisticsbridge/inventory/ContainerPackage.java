package com.tom.logisticsbridge.inventory;

import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;

import com.tom.logisticsbridge.LogisticsBridge;

public class ContainerPackage extends Container implements Consumer<String> {

	public static class SlotLocked extends Slot {
		public SlotLocked(IInventory inventoryIn, int index, int xPosition, int yPosition) {
			super(inventoryIn, index, xPosition, yPosition);
		}

		@Override
		public boolean canTakeStack(EntityPlayer playerIn) {
			return false;
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return false;
		}
	}
	public static class SlotPhantom extends Slot {
		public int maxStackSize;

		public SlotPhantom(IInventory inv, int slotIndex, int posX, int posY) {
			this(inv, slotIndex, posX, posY, 1);
		}

		public SlotPhantom(IInventory inv, int slotIndex, int posX, int posY, int maxStackSize) {
			super(inv, slotIndex, posX, posY);
			this.maxStackSize = maxStackSize;
		}

		@Override
		public int getSlotStackLimit() {
			return 1;
		}

		@Override
		public boolean canTakeStack(EntityPlayer par1EntityPlayer) {
			return false;
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return true;
		}
	}
	private EnumHand hand;
	private InventoryBasic inv;
	public String id;
	public Runnable update;
	public ContainerPackage(EntityPlayer player, EnumHand hand) {
		ItemStack is = player.getHeldItem(hand);
		inv = new InventoryBasic("", false, 1);
		if(is.hasTagCompound()){
			inv.setInventorySlotContents(0, new ItemStack(is.getTagCompound()));
			id = is.getTagCompound().getString("__pkgDest");
		}
		addSlotToContainer(new SlotPhantom(inv, 0, 44, 20));
		addPlayerSlotsExceptHeldItem(player.inventory, 8, 51);
		this.hand = hand;
	}
	@Override
	public void onContainerClosed(EntityPlayer playerIn) {
		super.onContainerClosed(playerIn);
		ItemStack is = playerIn.getHeldItem(hand);
		if(is.getItem() == LogisticsBridge.packageItem && (!is.hasTagCompound() || (is.hasTagCompound() && !is.getTagCompound().getBoolean("__actStack")))){
			is.setTagCompound(inv.getStackInSlot(0).writeToNBT(new NBTTagCompound()));
			if(id != null)is.getTagCompound().setString("__pkgDest", id);
			is.getTagCompound().setBoolean("__actStack", false);
		}
	}
	@Override
	public boolean canInteractWith(EntityPlayer playerIn) {
		return true;
	}
	protected void addPlayerSlotsExceptHeldItem(InventoryPlayer playerInventory, int x, int y) {
		for (int i = 0;i < 3;++i) {
			for (int j = 0;j < 9;++j) {
				addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9, x + j * 18, y + i * 18));
			}
		}
		for (int i = 0;i < 9;++i) {
			if (playerInventory.currentItem == i)
				addSlotToContainer(new SlotLocked(playerInventory, i, x + i * 18, y + 58));
			else
				addSlotToContainer(new Slot(playerInventory, i, x + i * 18, y + 58));
		}
	}
	public static final int phantomSlotChange = 4;
	@Override
	public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer playerIn) {
		Slot slot = slotId > -1 && slotId < inventorySlots.size() ? inventorySlots.get(slotId) : null;
		if (slot instanceof SlotPhantom) {
			ItemStack s = playerIn.inventory.getItemStack().copy();
			if (!s.isEmpty()) {
				slot.putStack(dragType == 1 ? s.splitStack(1) : s);
			} else if (dragType != 1)
				slot.putStack(ItemStack.EMPTY);
			else if (dragType == 1) {
				if (!slot.getStack().isEmpty()) {
					int c = 1;
					if (clickTypeIn == ClickType.PICKUP_ALL)
						c = -1;
					else if (clickTypeIn == ClickType.QUICK_CRAFT)
						c = -phantomSlotChange;
					else if (clickTypeIn == ClickType.CLONE)
						c = phantomSlotChange;
					if (slot.getStack().getMaxStackSize() >= slot.getStack().getCount() + c && slot.getStack().getCount() + c > 0) {
						slot.getStack().grow(c);
					}
				}
			}
			return playerIn.inventory.getItemStack();
		} else
			return super.slotClick(slotId, dragType, clickTypeIn, playerIn);
	}
	@Override
	public void accept(String t) {
		id = t;
		if(update != null)update.run();
	}
}
