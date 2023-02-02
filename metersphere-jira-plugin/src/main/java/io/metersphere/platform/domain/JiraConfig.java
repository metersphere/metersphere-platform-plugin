package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraConfig {
    private String account;
    private String password;
    private String token;
    private String authType;
    private String url;
    private String issuetype;
    private String storytype;
}
