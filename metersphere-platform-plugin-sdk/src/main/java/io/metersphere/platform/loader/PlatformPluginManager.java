package io.metersphere.platform.loader;

import im.metersphere.plugin.loader.PluginManager;
import io.metersphere.platform.api.Platform;
import io.metersphere.platform.api.PluginMetaInfo;
import io.metersphere.platform.domain.PlatformRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class PlatformPluginManager extends PluginManager {

    public List<PluginMetaInfo> getPluginMetaInfoList() {
        List<PluginMetaInfo> platFormOptions = new ArrayList<>();
        for (String pluginId : getClassLoaderMap().keySet()) {
           platFormOptions.add(getImplInstance(pluginId, PluginMetaInfo.class));
        }
        return platFormOptions;
    }

    public PluginMetaInfo getPluginMetaInfo(String pluginId) {
        return getImplInstance(pluginId, PluginMetaInfo.class);
    }

    public Platform getPlatform(String pluginId, PlatformRequest request) {
        return getImplInstance(pluginId, Platform.class, request);
    }

    public Platform getPlatformByKey(String key, PlatformRequest request) {
        for (String pluginId : getClassLoaderMap().keySet()) {
            // 查找对应 key 的插件
            PluginMetaInfo pluginMetaInfo = getPluginMetaInfo(pluginId);
            if (StringUtils.equals(pluginMetaInfo.getKey(), key)) {
                return getPlatform(pluginId, request);
            }
        }
        return null;
    }

    public PluginMetaInfo getPluginMetaInfoByKey(String key) {
        for (String pluginId : getClassLoaderMap().keySet()) {
            // 查找对应 key 的插件
            PluginMetaInfo pluginMetaInfo = getPluginMetaInfo(pluginId);
            if (StringUtils.equals(pluginMetaInfo.getKey(), key)) {
                return pluginMetaInfo;
            }
        }
        return null;
    }
}
