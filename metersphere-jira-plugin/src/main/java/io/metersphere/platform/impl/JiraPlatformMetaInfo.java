package io.metersphere.platform.impl;

import io.metersphere.platform.api.AbstractPlatformMetaInfo;

public class JiraPlatformMetaInfo extends AbstractPlatformMetaInfo {

    public static final String KEY = "Jira";

    public JiraPlatformMetaInfo() {
        super(JiraPlatformMetaInfo.class.getClassLoader());
    }

    @Override
    public String getVersion() {
        return "2.10.4";
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public boolean isThirdPartTemplateSupport() {
        return true;
    }
}
