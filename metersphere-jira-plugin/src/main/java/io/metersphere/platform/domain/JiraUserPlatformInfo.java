package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraUserPlatformInfo {
    private String jiraAccount;
    private String jiraPassword;
    private String token;
    private String authType;
}
