package com.tom.logisticsbridge;

import appeng.api.AEPlugin;
import appeng.api.IAppEngApi;

@AEPlugin
public class AE2Plugin {
	public static AE2Plugin INSTANCE;
	public final IAppEngApi api;
	public AE2Plugin(IAppEngApi api) {
		this.api = api;
		INSTANCE = this;
	}
}
