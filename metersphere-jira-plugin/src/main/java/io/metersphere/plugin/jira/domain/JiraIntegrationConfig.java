package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * @author jianxing
 */
@Getter
@Setter
public class JiraIntegrationConfig {
    private String account;
    private String password;
    private String token;
    private String authType;
    private String address;
    private String version;
}
