package com.tom.logisticsbridge.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.TextFormatting;

import net.minecraftforge.fml.client.config.GuiUtils;

public class PopupMenu {
	public int x, y;
	public Object additionalData;
	public List<String> options = new ArrayList<>();
	public boolean visible;
	public void show(int x, int y, Object additionalData){
		this.visible = true;
		this.x = x;
		this.y = y;
		this.additionalData = additionalData;
	}
	public void render(int x, int y, FontRenderer renderer, int width, int height){
		if(visible){
			int rx = x - this.x;
			int ry = y - this.y;
			int w = options.stream().mapToInt(renderer::getStringWidth).max().orElse(20);
			List<String> ret = options;
			if(rx <= w){
				ret = new ArrayList<>(options);
				for (int i = 0;i < ret.size();i++) {
					String el = ret.get(i);
					if(((i+1) * renderer.FONT_HEIGHT) > ry && (i * renderer.FONT_HEIGHT) < ry){
						ret.set(i, TextFormatting.UNDERLINE + el);
						break;
					}
				}
			}
			GuiUtils.drawHoveringText(ret, this.x, this.y + ret.size() * renderer.FONT_HEIGHT / 2, width, height, -1, renderer);
		}
	}
	public int mouseClicked(int x, int y, FontRenderer renderer){
		if(!visible)return -100;
		int rx = x - this.x;
		int ry = y - this.y;
		int w = options.stream().mapToInt(renderer::getStringWidth).max().orElse(20);
		if(rx > w){
			visible = false;
			return -1;
		}
		for (int i = 0;i < options.size();i++) {
			if(((i+1) * renderer.FONT_HEIGHT) > ry && (i * renderer.FONT_HEIGHT) < ry){
				visible = false;
				return i;
			}
		}
		visible = false;
		return -1;
	}
	public boolean add(String e) {
		return options.add(e);
	}
}
