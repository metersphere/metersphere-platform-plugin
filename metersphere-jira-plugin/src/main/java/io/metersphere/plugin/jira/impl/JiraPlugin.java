package io.metersphere.plugin.jira.impl;


import io.metersphere.plugin.platform.spi.AbstractPlatformPlugin;

/**
 * @author jianxing
 */
public class JiraPlugin extends AbstractPlatformPlugin {

    public static final String JIRA_PLUGIN_NAME = "JIRA";
    private static final String LOGO_PATH = "static/jira.jpg";
    private static final String DESCRIPTION = "共同迅速行动、保持一致并构建更优秀的产品";

    @Override
    public boolean isXpack() {
        return false;
    }

    @Override
    public String getName() {
        return JIRA_PLUGIN_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getLogo() {
        return LOGO_PATH;
    }
}
