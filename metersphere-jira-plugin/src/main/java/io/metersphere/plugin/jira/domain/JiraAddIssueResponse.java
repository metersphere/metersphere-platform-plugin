package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraAddIssueResponse {
    private String id;
    private String key;
    private String self;
}
