package io.metersphere.platform.api;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractPlatformMetaInfo implements PluginMetaInfo {

    private ClassLoader pluginClassLoader;

    public AbstractPlatformMetaInfo(ClassLoader pluginClassLoader) {
        this.pluginClassLoader = pluginClassLoader;
    }

    @Override
    public boolean isXpack() {
        return false;
    }


    protected InputStream readResource(String name) {
        return pluginClassLoader.getResourceAsStream(name);
    }

    @Override
    public String getFrontendMetaData() {
        try {
            InputStream in = this.readResource("json/frontend.json");
            return in == null ? StringUtils.EMPTY : IOUtils.toString(in);
        } catch (IOException e) {
            return StringUtils.EMPTY;
        }
    }

    @Override
    public String getVersion() {
        return this.getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getLabel() {
        return getKey();
    }

    @Override
    public boolean isThirdPartTemplateSupport() {
        return false;
    }
}
