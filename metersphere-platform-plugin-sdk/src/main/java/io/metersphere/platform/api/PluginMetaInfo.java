package io.metersphere.platform.api;

public interface PluginMetaInfo {
    boolean isXpack();

    String getKey();

    String getLabel();

    String getFrontendMetaData();

    String getVersion();

    /**
     * 是否支持第三方模板
     * @return
     */
    boolean isThirdPartTemplateSupport();
}
