package io.metersphere.plugin.zentao.enums;

import lombok.Getter;

import java.util.Objects;

@Getter
public enum ZentaoBugPlatformStatus {
    /**
     * 激活
     */
    active("激活", false),
    /**
     * 已关闭
     */
    closed("已关闭", true),
    /**
     * 已解决
     */
    resolved("已解决", true);

    private final String name;

    /**
     * 是否结束状态 (默认closed, resolved 作为结束状态)
     */
    private final Boolean lastStep;

    ZentaoBugPlatformStatus(String name, Boolean lastStep) {
        this.name = name;
        this.lastStep = lastStep;
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
