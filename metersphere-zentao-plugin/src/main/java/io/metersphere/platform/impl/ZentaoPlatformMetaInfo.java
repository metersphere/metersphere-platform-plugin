package io.metersphere.platform.impl;

import io.metersphere.platform.api.AbstractPlatformMetaInfo;

public class ZentaoPlatformMetaInfo extends AbstractPlatformMetaInfo {

    public static final String KEY = "Zentao";

    public ZentaoPlatformMetaInfo() {
        super(ZentaoPlatformMetaInfo.class.getClassLoader());
    }

    @Override
    public String getVersion() {
        return "2.5.0";
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public boolean isThirdPartTemplateSupport() {
        return false;
    }
}
