package io.metersphere.plugin.zentao.enums;

import lombok.Getter;

import java.util.Objects;

@Getter
public enum ZentaoBugPlatformStatus {
    /**
     * 激活
     */
    active("激活"),
    /**
     * 已关闭
     */
    closed("已关闭"),
    /**
     * 已解决
     */
    resolved("已解决");

    private final String name;

    ZentaoBugPlatformStatus(String name) {
        this.name = name;
    }

    public static String getNameByKey(String key) {
        for (ZentaoBugPlatformStatus status : ZentaoBugPlatformStatus.values()) {
            if (Objects.equals(status.name(), key)) {
                return status.getName();
            }
        }
        return key;
    }
}
