package io.metersphere.plugin.zentao.impl;

import io.metersphere.plugin.platform.spi.AbstractPlatformPlugin;

public class ZentaoPlugin extends AbstractPlatformPlugin {

	public static final String ZENTAO_PLUGIN_NAME = "禅道";
	private static final String LOGO_PATH = "static/zentao.jpg";
	private static final String DESCRIPTION = "禅道是专业的研发项目管理软件";

	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String getLogo() {
		return LOGO_PATH;
	}

	@Override
	public boolean isXpack() {
		return false;
	}

	@Override
	public String getName() {
		return ZENTAO_PLUGIN_NAME;
	}
}
