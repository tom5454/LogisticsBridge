package com.tom.logisticsbridge.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

public class DynamicInventory implements IInventory {
	private List<ItemStack> stacks = new ArrayList<>();
	@Override
	public String getName() {
		return null;
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@Override
	public ITextComponent getDisplayName() {
		return null;
	}

	@Override
	public int getSizeInventory() {
		return stacks.size();
	}

	@Override
	public boolean isEmpty() {
		return stacks.isEmpty();
	}

	@Override
	public ItemStack getStackInSlot(int index) {
		return index >= stacks.size() ? ItemStack.EMPTY : stacks.get(index);
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {
		ItemStack stack = getStackInSlot(index);
		if(stack.isEmpty())return ItemStack.EMPTY;
		ItemStack ret = stack.splitStack(count);
		if(stack.isEmpty())stacks.remove(index);
		return ret;
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		return index >= stacks.size() ? ItemStack.EMPTY : stacks.remove(index);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		if(index == stacks.size())
			stacks.add(stack);
		else if(index > stacks.size()){
			while(index > stacks.size()){
				stacks.add(ItemStack.EMPTY);
			}
			stacks.add(stack);
		}else
			stacks.set(index, stack);
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public void markDirty() {
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return false;
	}

	@Override
	public void openInventory(EntityPlayer player) {
	}

	@Override
	public void closeInventory(EntityPlayer player) {
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return false;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {

	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {
		stacks.clear();
	}
	public void insert(ItemStack stack){
		stack = stack.copy();
		for (ItemStack itemStack : stacks) {
			if(itemStack.getMaxStackSize() > itemStack.getCount() && itemStack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(stack, itemStack)){
				int count = itemStack.getCount();
				int maxCount = itemStack.getMaxStackSize();
				int transferred = Math.min(stack.getCount(), maxCount - count);
				stack.splitStack(transferred);
				itemStack.grow(transferred);
				if(stack.isEmpty())return;
			}
		}
		if(!stack.isEmpty()){
			stacks.add(stack);
		}
	}
	public void removeEmpties(){
		stacks.removeIf(ItemStack::isEmpty);
	}

	public Stream<ItemStack> stream() {
		return stacks.stream().filter(s -> !s.isEmpty());
	}

	public void addStack(ItemStack is) {
		stacks.add(is.copy());
	}
}
