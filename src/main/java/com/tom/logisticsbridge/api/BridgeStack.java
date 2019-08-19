package com.tom.logisticsbridge.api;

public class BridgeStack<T> {
	public T obj;
	public long size;
	public boolean craftable;
	public long requestableSize;

	public BridgeStack(T obj, long size, boolean craftable, long requestableSize) {
		this.obj = obj;
		this.size = size;
		this.craftable = craftable;
		this.requestableSize = requestableSize;
	}
	@Override
	public String toString() {
		return "BridgeStack<" + (obj == null ? "null" : obj.getClass()) + "> " + obj + " x " + size + (craftable ? "(Craftable)" : "") + " req " + requestableSize;
	}
}
