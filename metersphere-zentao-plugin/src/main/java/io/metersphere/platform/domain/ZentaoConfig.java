package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZentaoConfig {
    private String account;
    private String password;
    private String url;
    private String requestType;
    private String request;
}
