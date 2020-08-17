package com.tom.logisticsbridge.module;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.tom.logisticsbridge.network.CSignCraftingManagerData;
import com.tom.logisticsbridge.pipe.CraftingManager;

import logisticspipes.modules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.signs.IPipeSign;
import logisticspipes.renderer.LogisticsRenderPipe;

public class CraftingManagerPipeSign implements IPipeSign {

	public CoreRoutedPipe pipe;
	public EnumFacing dir;

	@Override
	public boolean isAllowedFor(CoreRoutedPipe pipe) {
		return pipe instanceof CraftingManager;
	}

	@Override
	public void addSignTo(CoreRoutedPipe pipe, EnumFacing dir, EntityPlayer player) {
		pipe.addPipeSign(dir, new CraftingManagerPipeSign(), player);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {}

	@Override
	public void writeToNBT(NBTTagCompound tag) {}

	@Override
	public ModernPacket getPacket() {
		CraftingManager cpipe = (CraftingManager) pipe;
		return PacketHandler.getPacket(CSignCraftingManagerData.class).setInventory(cpipe.getModuleInventory()).setType(ModulePositionType.IN_PIPE).setPosX(cpipe.getX()).setPosY(cpipe.getY()).setPosZ(cpipe.getZ());
	}

	@Override
	public void updateServerSide() {}

	@Override
	public void init(CoreRoutedPipe pipe, EnumFacing dir) {
		this.pipe = pipe;
		this.dir = dir;
	}

	@Override
	public void activate(EntityPlayer player) {}

	@Override
	@SideOnly(Side.CLIENT)
	public void render(CoreRoutedPipe pipe, LogisticsRenderPipe renderer) {
		CraftingManager cpipe = (CraftingManager) pipe;
		//FontRenderer var17 = renderer.getFontRenderer();
		if (cpipe != null) {
			/*GlStateManager.depthMask(false);
			GlStateManager.rotate(-180.0F, 1.0F, 0.0F, 0.0F);
			GlStateManager.translate(0.5F, +0.08F, 0.0F);
			GlStateManager.scale(1.0F / 90.0F, 1.0F / 90.0F, 1.0F / 90.0F);*/
			float s = 0.29f;
			GlStateManager.translate(-0.1F, +0.08F, 0.0F);
			GlStateManager.scale(s, s, 1);

			for(int i = 0;i<27;i++) {
				int x = i % 9;
				int y = i / 9;

				ItemStack is = cpipe.getClientModuleInventory().getStackInSlot(i);

				if(!is.isEmpty()) {
					NBTTagCompound output = null;

					if(is.hasTagCompound()) {
						NBTTagCompound info = is.getTagCompound().getCompoundTag("moduleInformation");
						NBTTagList list = info.getTagList("items", 10);
						for(int j = 0;j<list.tagCount();j++){
							NBTTagCompound tag = list.getCompoundTagAt(j);
							if(tag.getInteger("index") == 9){
								output = tag;
							}
						}
					}

					ItemStack toRender = is;

					if(output != null) {
						toRender = new ItemStack(output);
					}

					GlStateManager.pushMatrix();
					GlStateManager.translate(x * 0.35f, -y * 0.35f, 0);
					renderer.renderItemStackOnSign(toRender);
					GlStateManager.popMatrix();
				}
			}

			/*GlStateManager.depthMask(true);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);*/
		}
	}
}
