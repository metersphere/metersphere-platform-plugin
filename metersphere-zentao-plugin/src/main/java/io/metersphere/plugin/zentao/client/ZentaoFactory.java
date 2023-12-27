package io.metersphere.plugin.zentao.client;

import org.apache.commons.lang3.StringUtils;

public class ZentaoFactory {

    public static final String PATH_INTO_TYPE = "PATH_INFO";
    public static final String GET_TYPE = "GET";

    public static ZentaoClient getInstance(String url, String type) {
        if (StringUtils.equals(type, PATH_INTO_TYPE)) {
            return new ZentaoPathInfoClient(url);
        } else if (StringUtils.equals(type, GET_TYPE)) {
            return new ZentaoGetClient(url);
        }
        return new ZentaoPathInfoClient(url);
    }
}
