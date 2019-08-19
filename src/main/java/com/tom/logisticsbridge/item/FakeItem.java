package com.tom.logisticsbridge.item;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import com.tom.logisticsbridge.GuiHandler.GuiIDs;
import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class FakeItem extends Item {
	public final boolean isPackage;
	private boolean displayOverride;
	public FakeItem(boolean isPackage) {
		this.isPackage = isPackage;
	}
	@Override
	public String getUnlocalizedName(ItemStack stack) {
		if(!stack.hasTagCompound())return super.getUnlocalizedName(stack);
		ItemStack st = new ItemStack(stack.getTagCompound());
		return st.getUnlocalizedName();
	}

	@Override
	public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		if(isPackage){
			if(!stack.hasTagCompound()){
				tooltip.add(I18n.format("tooltip.logisticsbridge.packageEmt"));
			}else{
				displayOverride = true;
				if(stack.getTagCompound().getBoolean("__actStack")){
					tooltip.add(I18n.format("tooltip.logisticsbridge.packageAct", stack.getDisplayName()));
				}else{
					tooltip.add(I18n.format("tooltip.logisticsbridge.packageTmp", stack.getDisplayName()));
				}
				displayOverride = false;
				String id = stack.getTagCompound().getString("__pkgDest");
				if(!id.isEmpty())tooltip.add(I18n.format("tooltip.logisticsbridge.satID", id));
			}
		}else{
			if(!stack.hasTagCompound()){
				tooltip.add(I18n.format("tooltip.logisticsbridge.fakeItemNull"));
			}else{
				tooltip.add(I18n.format("tooltip.logisticsbridge.request", stack.getDisplayName()));
			}
			tooltip.add(I18n.format("tooltip.logisticsbridge.techItem"));
		}
	}
	@Override
	public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
		ItemStack is = playerIn.getHeldItem(handIn);
		if(isPackage){
			if(is.hasTagCompound() && is.getTagCompound().getBoolean("__actStack")){
				ItemStack est = new ItemStack(is.getTagCompound());
				playerIn.setHeldItem(handIn, est);
				return new ActionResult<>(EnumActionResult.SUCCESS, est);
			}else{
				playerIn.openGui(LogisticsBridge.modInstance, GuiIDs.TemplatePkg.ordinal(), worldIn, handIn.ordinal(), 0, 0);
				if(!worldIn.isRemote && is.hasTagCompound()){
					final ModernPacket packet = PacketHandler.getPacket(SetIDPacket.class).setSide(-2).setName(is.getTagCompound().getString("__pkgDest")).setId(0);
					MainProxy.sendPacketToPlayer(packet, playerIn);
				}
				return new ActionResult<>(EnumActionResult.SUCCESS, is);
			}
		}
		return super.onItemRightClick(worldIn, playerIn, handIn);
	}
	@SuppressWarnings("deprecation")
	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		if(!displayOverride && isPackage && stack.hasTagCompound()){
			String id = stack.getTagCompound().getString("__pkgDest");
			if(!id.isEmpty()){
				return net.minecraft.util.text.translation.I18n.translateToLocalFormatted("tooltip.logisticsbridge.packageName",
						super.getItemStackDisplayName(stack), id);
			}
		}
		return super.getItemStackDisplayName(stack);
	}
}
