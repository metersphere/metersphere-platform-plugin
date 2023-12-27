package io.metersphere.plugin.zentao.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoIntegrationConfig {
    private String account;
    private String password;
    private String address;
    private String requestType;
}
